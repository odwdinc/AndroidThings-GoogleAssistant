package com.example.androidthings.assistant;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;

import java.io.IOException;
import java.io.InputStream;

/**
 * Created by antho on 9/12/2017.
 */

public class ImageViewer extends Activity {

    private static final String TAG = ImageViewer.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        Intent intent = getIntent();
        String subject = intent.getStringExtra("subject");

        Log.i(TAG, "starting Image Viewer for subject [ " + subject + " ]");
        setContentView(R.layout.images);


        ImageView mImage = (ImageView) findViewById(R.id.mImage);

        InputStream ims = null;
        try {
            ims = getAssets().open("oregon-trail-dysentery_5.jpg");
            // load image as Drawable
            Drawable d = Drawable.createFromStream(ims, null);
            // set image to ImageView
            mImage.setImageDrawable(d);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
