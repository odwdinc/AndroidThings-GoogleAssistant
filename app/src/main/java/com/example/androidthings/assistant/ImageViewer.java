package com.example.androidthings.assistant;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.ImageView;

import java.io.IOException;
import java.io.InputStream;

/**
 * Created by antho on 9/12/2017.
 */

public class ImageViewer extends Activity {

    private static final String TAG = ImageViewer.class.getSimpleName();
    private Handler mMainHandler;
    private Runnable finish = new Runnable() {

        @Override
        public void run() {
            finish();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        Intent intent = getIntent();
        String subject = intent.getStringExtra("subject");
        mMainHandler = new Handler(getMainLooper());

        Log.i(TAG, "starting Image Viewer for subject [ " + subject + " ]");
        setContentView(R.layout.images);


        ImageView mImage = (ImageView) findViewById(R.id.mImage);

        String fileName = "oregon-trail-dysentery_5.jpg";
        switch (subject) {
            case "dogs":
                fileName = "dogs.jpg";
                break;
            case "cats":
                fileName = "cats.jpg";
                break;
            case "vacation":
                fileName = "vacation.jpg";
                break;
            case "Intel":
                fileName = "intel.jpg";
                break;
        }

        InputStream ims = null;
        try {
            ims = getAssets().open(fileName);
            // load image as Drawable
            Drawable d = Drawable.createFromStream(ims, null);
            // set image to ImageView
            mImage.setImageDrawable(d);
        } catch (IOException e) {
            e.printStackTrace();
        }

        mMainHandler.postDelayed(finish, 5000);

    }
}
