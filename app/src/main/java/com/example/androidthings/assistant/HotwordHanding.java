package com.example.androidthings.assistant;

import android.app.Activity;
import android.content.Context;
import android.os.AsyncTask;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import edu.cmu.pocketsphinx.Assets;
import edu.cmu.pocketsphinx.Hypothesis;
import edu.cmu.pocketsphinx.RecognitionListener;
import edu.cmu.pocketsphinx.SpeechRecognizer;
import edu.cmu.pocketsphinx.SpeechRecognizerSetup;

import static android.widget.Toast.makeText;

/**
 * Created by antho on 9/12/2017.
 */

interface AppUpdates {
    public void updateTextView(String Message);

    public void StartAssistan(int StartDelay);

    public void StopAssistan(int StopDelay);
}

public class HotwordHanding implements RecognitionListener {
    private Activity appActivity;

    /* Named searches allow to quickly reconfigure the decoder */
    private static final String KWS_SEARCH = "wakeup";

    /* Keyword we are looking for to activate menu */
    private static String KEYPHRASE = "ok intel";
    private final AppUpdates appUpdates;

    private int StartDelay = 10;
    private int StopDelay = 3500;


    private HashMap<String, String> captions;
    private SpeechRecognizer recognizer;
    private Toast toast;


    public HotwordHanding(Activity _appActivity, AppUpdates _appUpdates) {
        this.appActivity = _appActivity;
        this.appUpdates = _appUpdates;
        // Prepare the data for UI
        captions = new HashMap<String, String>();
        captions.put(KWS_SEARCH, "To start demonstration say \"" + KEYPHRASE + "\".");
        toast = Toast.makeText(appActivity.getApplicationContext(), "", Toast.LENGTH_LONG);
        runRecognizerSetup();
    }

    public void onDestroy() {
        if (recognizer != null) {
            recognizer.cancel();
            recognizer.shutdown();
        }
    }


    public void setStartDelay(int startDelay) {
        StartDelay = startDelay;
    }

    public void setStopDelay(int stopDelay) {
        StopDelay = stopDelay;
    }

    private void runRecognizerSetup() {
        // Recognizer initialization is a time-consuming and it involves IO,
        // so we execute it in async task
        new AsyncTask<Void, Void, Exception>() {
            @Override
            protected Exception doInBackground(Void... params) {
                try {
                    Assets assets = new Assets(appActivity);
                    File assetDir = assets.syncAssets();
                    setupRecognizer(assetDir);
                } catch (IOException e) {
                    return e;
                }
                return null;
            }

            @Override
            protected void onPostExecute(Exception result) {
                if (result != null) {
                    appUpdates.updateTextView("Failed to init recognizer " + result);
                } else {
                    switchSearch(KWS_SEARCH);
                }
            }
        }.execute();
    }

    private void setupRecognizer(File assetsDir) throws IOException {
        // The recognizer can be configured to perform multiple searches
        // of different kind and switch between them

        recognizer = SpeechRecognizerSetup.defaultSetup()
                .setAcousticModel(new File(assetsDir, "en-us-ptm"))
                .setDictionary(new File(assetsDir, "cmudict-en-us.dict"))

                .setRawLogDir(assetsDir) // To disable logging of raw audio comment out this call (takes a lot of space on the device)

                .getRecognizer();
        recognizer.addListener(this);

        /** In your application you might not need to add all those searches.
         * They are added here for demonstration. You can leave just one.
         */

        // Create keyword-activation search.
        recognizer.addKeyphraseSearch(KWS_SEARCH, KEYPHRASE);
    }


    private void switchSearch(String searchName) {
        recognizer.stop();
        String caption = captions.get(KWS_SEARCH);
        appUpdates.updateTextView(caption);


        // If we are not spotting, start listening with timeout (10000 ms or 10 seconds).
        if (searchName.equals(KWS_SEARCH))
            recognizer.startListening(searchName);
        else
            recognizer.startListening(searchName, 10000);

    }///////////////////////////////////////////--------------------------------\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\

    @Override
    public void onBeginningOfSpeech() {

    }

    @Override
    public void onEndOfSpeech() {
        if (!recognizer.getSearchName().equals(KWS_SEARCH))
            switchSearch(KWS_SEARCH);
    }

    /**
     * In partial result we get quick updates about current hypothesis. In
     * keyword spotting mode we can react here, in other modes we need to wait
     * for final result in onResult.
     */
    @Override
    public void onPartialResult(Hypothesis hypothesis) {
        if (hypothesis == null)
            return;

        String text = hypothesis.getHypstr();
        if (text.equals(KEYPHRASE)) {
            //((TextView) findViewById(R.id.caption_text)).setText("KEYPHRASE");
            recognizer.stop();
            appUpdates.StartAssistan(StartDelay);
            appUpdates.StopAssistan(StopDelay + StartDelay);


        } else {
            appUpdates.updateTextView(text);
        }
    }


    public void showText(String message) {
        toast.setText(message);
        toast.show();
    }

    /**
     * This callback is called when we stop the recognizer.
     */
    @Override
    public void onResult(Hypothesis hypothesis) {
        appUpdates.updateTextView("Try Saying \"Talk to my home device\" ");
        if (hypothesis != null) {
            String text = hypothesis.getHypstr();
            //makeText(appActivity.getApplicationContext(), text, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onError(Exception error) {
        appUpdates.updateTextView(error.getMessage());
    }

    @Override
    public void onTimeout() {
        switchSearch(KWS_SEARCH);
    }

    public void restart() {
        switchSearch(KWS_SEARCH);
    }
}
