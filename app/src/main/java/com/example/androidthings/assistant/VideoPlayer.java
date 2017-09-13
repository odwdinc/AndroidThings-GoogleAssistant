package com.example.androidthings.assistant;

import android.app.Activity;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.SurfaceHolder;
import android.widget.VideoView;

/**
 * Created by antho on 9/12/2017.
 */

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.res.Configuration;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnPreparedListener;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.MediaController;
import android.widget.VideoView;

import java.io.File;

public class VideoPlayer extends Activity {
    private static final String TAG = VideoPlayer.class.getSimpleName();

    private VideoView myVideoView;
    private int position = 0;
    private ProgressDialog progressDialog;
    private MediaController mediaControls;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFormat(PixelFormat.TRANSLUCENT);

        Intent intent = getIntent();
        String subject = intent.getStringExtra("subject");

        Log.i(TAG, "starting Image Viewer for subject [ " + subject + " ]");

        // set the main layout of the activity
        setContentView(R.layout.video);

        //initialize the VideoView
        myVideoView = (VideoView) findViewById(R.id.video_view);
        //MediaPlayer player = new MediaPlayer();

        File clip = new File(Environment.getExternalStorageDirectory(),
                "bigbuckbunny.mp4");

        if (clip.exists()) {
            myVideoView.setVideoPath(clip.getAbsolutePath());
            MediaController ctlr = new MediaController(this);
            ctlr.setMediaPlayer(myVideoView);
            myVideoView.setMediaController(ctlr);

            //player.setDisplay(myVideoView.getHolder());
            AssetFileDescriptor afd;


            myVideoView.requestFocus();
            myVideoView.start();
        }

//        try {
//            afd = getAssets().openFd("bigbuckbunny.mp4");
//            player.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
//            player.prepareAsync();
//            player.setOnPreparedListener(new OnPreparedListener() {
//
//                @Override
//                public void onPrepared(MediaPlayer mp) {
//
//                    mp.start();
//                }
//            });
//
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
    }
}