/*
 * Copyright 2017, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.androidthings.assistant;

import android.app.Activity;
import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.google.android.things.contrib.driver.button.Button;
import com.google.android.things.pio.Gpio;

import com.google.android.things.pio.PeripheralManagerService;
import com.google.assistant.embedded.v1alpha1.AudioInConfig;
import com.google.assistant.embedded.v1alpha1.AudioOutConfig;
import com.google.assistant.embedded.v1alpha1.ConverseConfig;
import com.google.assistant.embedded.v1alpha1.ConverseRequest;
import com.google.assistant.embedded.v1alpha1.ConverseResponse;
import com.google.assistant.embedded.v1alpha1.EmbeddedAssistantGrpc;
import com.google.protobuf.ByteString;

import org.json.JSONException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.auth.MoreCallCredentials;
import io.grpc.stub.StreamObserver;


public class AssistantActivity extends Activity implements Button.OnButtonEventListener {
    private static final String TAG = AssistantActivity.class.getSimpleName();

    // Peripheral and drivers constants.
    private static final boolean AUDIO_USE_I2S_VOICEHAT_IF_AVAILABLE = false;
    private static final int BUTTON_DEBOUNCE_DELAY_MS = 20;



    // Audio constants.
    private static final int SAMPLE_RATE = 16000;
    private static final int ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    private static AudioInConfig.Encoding ENCODING_INPUT = AudioInConfig.Encoding.LINEAR16;
    private static AudioOutConfig.Encoding ENCODING_OUTPUT = AudioOutConfig.Encoding.LINEAR16;
    private static final AudioInConfig ASSISTANT_AUDIO_REQUEST_CONFIG =
            AudioInConfig.newBuilder()
                         .setEncoding(ENCODING_INPUT)
                         .setSampleRateHertz(SAMPLE_RATE)
                         .build();
    private static final AudioOutConfig ASSISTANT_AUDIO_RESPONSE_CONFIG =
            AudioOutConfig.newBuilder()
                    .setEncoding(ENCODING_OUTPUT)
                    .setSampleRateHertz(SAMPLE_RATE)
                    .build();
    private static final AudioFormat AUDIO_FORMAT_STEREO =
            new AudioFormat.Builder()
            .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
            .setEncoding(ENCODING)
            .setSampleRate(SAMPLE_RATE)
            .build();
    private static final AudioFormat AUDIO_FORMAT_OUT_MONO =
            new AudioFormat.Builder()
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .setEncoding(ENCODING)
            .setSampleRate(SAMPLE_RATE)
            .build();
    private static final AudioFormat AUDIO_FORMAT_IN_MONO =
            new AudioFormat.Builder()
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .setEncoding(ENCODING)
            .setSampleRate(SAMPLE_RATE)
            .build();
    private static final int SAMPLE_BLOCK_SIZE = 1024;

    // Google Assistant API constants.
    private static final String ASSISTANT_ENDPOINT = "embeddedassistant.googleapis.com";

    // gRPC client and stream observers.
    private EmbeddedAssistantGrpc.EmbeddedAssistantStub mAssistantService;
    private StreamObserver<ConverseRequest> mAssistantRequestObserver;
    private StreamObserver<ConverseResponse> mAssistantResponseObserver =
            new StreamObserver<ConverseResponse>() {
        @Override
        public void onNext(ConverseResponse value) {
            switch (value.getConverseResponseCase()) {
                case EVENT_TYPE:
                    Log.d(TAG, "converse response event: " + value.getEventType());
                    break;
                case RESULT:
                    final String spokenRequestText = value.getResult().getSpokenRequestText();
                    final String spokenResponseText = value.getResult().getSpokenResponseText();
                    if (!spokenRequestText.isEmpty()) {
                        Log.i(TAG, "assistant request text: " + spokenRequestText);
                        mMainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                mAssistantRequestsAdapter.add(spokenRequestText);
                            }
                        });
                    }

                    if (!spokenResponseText.isEmpty()) {
                        Log.i(TAG, "assistant response text: " + spokenResponseText);
                    }
                    break;
                case AUDIO_OUT:

                    final ByteBuffer audioData =
                            ByteBuffer.wrap(value.getAudioOut().getAudioData().toByteArray());
                    //Log.d(TAG, "converse audio size: " + audioData.remaining());
                    mAudioTrack.write(audioData, audioData.remaining(), AudioTrack.WRITE_BLOCKING);
                    if (mLed != null) {
                        try {
                            mLed.setValue(!mLed.getValue());
                        } catch (IOException e) {
                            Log.w(TAG, "error toggling LED:", e);
                        }
                    }
                    break;
                case ERROR:
                    Log.e(TAG, "converse response error: " + value.getError());
                    break;
            }
        }

        @Override
        public void onError(Throwable t) {
            Log.e(TAG, "converse error:", t);
        }

        @Override
        public void onCompleted() {
            Log.i(TAG, "assistant response finished");
            if (mLed != null) {
                try {
                    mLed.setValue(false);
                } catch (IOException e) {
                    Log.e(TAG, "error turning off LED:", e);
                }
            }
        }
    };

    // Audio playback and recording objects.
    private AudioTrack mAudioTrack;
    private AudioRecord mAudioRecord;

    // Hardware peripherals.
    private VoiceHatDriver mVoiceHat;
    private Button mButton;
    private Gpio mLed;

    // Assistant Thread and Runnables implementing the push-to-talk functionality.
    private HandlerThread mAssistantThread;
    private Handler mAssistantHandler;
    private Runnable mStartAssistantRequest = new Runnable() {
        @Override
        public void run() {
            Log.i(TAG, "starting assistant request");
            mAudioRecord.startRecording();
            mAssistantRequestObserver = mAssistantService.converse(mAssistantResponseObserver);
            mAssistantRequestObserver.onNext(ConverseRequest.newBuilder().setConfig(
                    ConverseConfig.newBuilder()
                            .setAudioInConfig(ASSISTANT_AUDIO_REQUEST_CONFIG)
                            .setAudioOutConfig(ASSISTANT_AUDIO_RESPONSE_CONFIG)
                            .build()).build());
            mAssistantHandler.post(mStreamAssistantRequest);
        }
    };
    private Runnable mStreamAssistantRequest = new Runnable() {
        @Override
        public void run() {
            ByteBuffer audioData = ByteBuffer.allocateDirect(SAMPLE_BLOCK_SIZE);
            int result =
                    mAudioRecord.read(audioData, audioData.capacity(), AudioRecord.READ_BLOCKING);
            if (result < 0) {
                Log.e(TAG, "error reading from audio stream:" + result);
                return;
            }
            Log.d(TAG, "streaming ConverseRequest: " + result);
            mAssistantRequestObserver.onNext(ConverseRequest.newBuilder()
                    .setAudioIn(ByteString.copyFrom(audioData))
                    .build());
            mAssistantHandler.post(mStreamAssistantRequest);
        }
    };
    private Runnable mStopAssistantRequest = new Runnable() {
        @Override
        public void run() {
            Log.i(TAG, "ending assistant request");
            mAssistantHandler.removeCallbacks(mStreamAssistantRequest);
            if (mAssistantRequestObserver != null) {
                mAssistantRequestObserver.onCompleted();
                mAssistantRequestObserver = null;
            }
            mAudioRecord.stop();
            mAudioTrack.play();
        }
    };
    private Handler mMainHandler;

    // List & adapter to store and display the history of Assistant Requests.
    private ArrayList<String> mAssistantRequests = new ArrayList<>();
    private ArrayAdapter<String> mAssistantRequestsAdapter;
    private RealtimeDatabase myDB = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "starting assistant demo");

        setContentView(R.layout.activity_main);
        ListView assistantRequestsListView = (ListView)findViewById(R.id.assistantRequestsListView);
        mAssistantRequestsAdapter =
                new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1,
                        mAssistantRequests);
        assistantRequestsListView.setAdapter(mAssistantRequestsAdapter);
        mMainHandler = new Handler(getMainLooper());

        mAssistantThread = new HandlerThread("assistantThread");
        mAssistantThread.start();
        mAssistantHandler = new Handler(mAssistantThread.getLooper());
        myDB= new RealtimeDatabase(mMainHandler);


        try {
            if (AUDIO_USE_I2S_VOICEHAT_IF_AVAILABLE) {
                PeripheralManagerService pioService = new PeripheralManagerService();
                List<String> i2sDevices = pioService.getI2sDeviceList();
                if (i2sDevices.size() > 0) {
                    try {
                        Log.i(TAG, "creating voice hat driver");
                        mVoiceHat = new VoiceHatDriver(
                                BoardDefaults.getI2SDeviceForVoiceHat(),
                                BoardDefaults.getGPIOForVoiceHatTrigger(),
                                AUDIO_FORMAT_STEREO
                        );
                        mVoiceHat.registerAudioInputDriver();
                        mVoiceHat.registerAudioOutputDriver();
                    } catch (IllegalStateException e) {
                        Log.w(TAG, "Unsupported board, falling back on default audio device:", e);
                    }
                }
            }
            mButton = new Button(BoardDefaults.getGPIOForButton(), Button.LogicState.PRESSED_WHEN_HIGH);
            mButton.setDebounceDelay(BUTTON_DEBOUNCE_DELAY_MS);
            mButton.setOnButtonEventListener(this);
            PeripheralManagerService pioService = new PeripheralManagerService();
            mLed = pioService.openGpio(BoardDefaults.getGPIOForLED());
            mLed.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);


        } catch (IOException e) {
            Log.e(TAG, "error configuring peripherals:", e);
            return;
        }

        AudioManager manager = (AudioManager)this.getSystemService(Context.AUDIO_SERVICE);

        //manager.startBluetoothSco();

        AudioDeviceInfo[] ado = manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
        AudioDeviceInfo[] adi = manager.getDevices(AudioManager.GET_DEVICES_INPUTS);

        for(int i=0; i<adi.length; i++){
            Log.d(TAG,"Input Name["+i+"]: "+ adi[i].getProductName());
        }

        for(int i=0; i<ado.length; i++){
            Log.d(TAG,"Output Name["+i+"]: "+ ado[i].getProductName());
        }

        if(manager.isBluetoothA2dpOn()){
            Log.d(TAG,"Bluetooth A2dp is On");
        }

        int maxVolume = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        maxVolume = maxVolume/2;
        Log.i(TAG, "setting volume to: " + maxVolume);
        manager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume, 0);
        int outputBufferSize = AudioTrack.getMinBufferSize(AUDIO_FORMAT_OUT_MONO.getSampleRate(),
                AUDIO_FORMAT_OUT_MONO.getChannelMask(),
                AUDIO_FORMAT_OUT_MONO.getEncoding());
        mAudioTrack = new AudioTrack.Builder()
                .setAudioFormat(AUDIO_FORMAT_OUT_MONO)
                .setBufferSizeInBytes(outputBufferSize)
                .build();
        mAudioTrack.play();
        int inputBufferSize = AudioRecord.getMinBufferSize(AUDIO_FORMAT_STEREO.getSampleRate(),
                AUDIO_FORMAT_STEREO.getChannelMask(),
                AUDIO_FORMAT_STEREO.getEncoding());
        mAudioRecord = new AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(AUDIO_FORMAT_IN_MONO)
                .setBufferSizeInBytes(inputBufferSize)
                .build();
        ManagedChannel channel = ManagedChannelBuilder.forTarget(ASSISTANT_ENDPOINT).build();
        try {
            mAssistantService = EmbeddedAssistantGrpc.newStub(channel)
                    .withCallCredentials(MoreCallCredentials.from(
                            Credentials.fromResource(this, R.raw.credentials)
                    ));
        } catch (IOException|JSONException e) {
            Log.e(TAG, "error creating assistant service:", e);
        }
    }





    @Override
    public void onButtonEvent(Button button, boolean pressed) {
        try {
            if (mLed != null) {
                mLed.setValue(pressed);
            }
        } catch (IOException e) {
            Log.d(TAG, "error toggling LED:", e);
        }
        if (pressed) {
            mAssistantHandler.post(mStartAssistantRequest);
        } else {
            mAssistantHandler.post(mStopAssistantRequest);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "destroying assistant demo");

        if (myDB != null){
            myDB.Destroy();
            myDB = null;
        }

        if (mAudioRecord != null) {
            mAudioRecord.stop();
            mAudioRecord = null;
        }

        if (mAudioTrack != null) {
            mAudioTrack.stop();
            mAudioTrack = null;
        }
        if (mLed != null) {
            try {
                mLed.close();
            } catch (IOException e) {
                Log.w(TAG, "error closing LED", e);
            }
            mLed = null;
        }
        if (mButton != null) {
            try {
                mButton.close();
            } catch (IOException e) {
                Log.w(TAG, "error closing button", e);
            }
            mButton = null;
        }

        if (mVoiceHat != null) {
            try {
                mVoiceHat.unregisterAudioOutputDriver();
                mVoiceHat.unregisterAudioInputDriver();
                mVoiceHat.close();
            } catch (IOException e) {
                Log.w(TAG, "error closing voice hat driver", e);
            }
            mVoiceHat = null;
        }
        mAssistantHandler.post(new Runnable() {
            @Override
            public void run() {
                mAssistantHandler.removeCallbacks(mStreamAssistantRequest);
            }
        });
        mAssistantThread.quitSafely();
    }
}
