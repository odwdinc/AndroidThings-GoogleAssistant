package com.example.androidthings.assistant;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.util.Log;

import com.google.android.things.pio.PeripheralManagerService;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

//import mraa.mraa;
//import upm_jhd1313m1.Jhd1313m1;

import static com.example.androidthings.assistant.BoardDefaults.getBoardVariant;
import static com.example.androidthings.assistant.BoardDefaults.getI2CPort;

/**
 * Created by antho on 6/17/2017.
 */

public class RealtimeDatabase {
    private static final String TAG = "RealtimeDatabase";
    private final Context appContext;
    private DatabaseReference myRefMessage;
    private DatabaseReference myRefDevice;
    //public Jhd1313m1 mLcd = null;
    private int mHRInterval = 30000; // 30 seconds by default, can be changed later
    HashMap FulfillmentMap = new HashMap<String, String>();
    private Handler mMainHandler;

    ValueEventListener mesgListner = new ValueEventListener() {


        @Override
        public void onDataChange(DataSnapshot dataSnapshot) {
            // This method is called once with the initial value and again
            // whenever data at this location is updated.
            //mLcd.clear();
            FulfillmentMap.clear();

            FulfillmentMap.put("speech", "Ok");
            FulfillmentMap.put("displayText", "Hello World");
            FulfillmentMap.put("source", "android-things");
            String intentName =   dataSnapshot.child("intentName").getValue(String.class);


            String Device =   dataSnapshot.child(intentName).child("Device").getValue(String.class);
            String number =   dataSnapshot.child(intentName).child("number").getValue(String.class);
            String Status =   dataSnapshot.child(intentName).child("state").getValue(String.class);
            String color =   dataSnapshot.child(intentName).child("color").getValue(String.class);
            String text =   dataSnapshot.child(intentName).child("text").getValue(String.class);

            String media = dataSnapshot.child(intentName).child("media").getValue(String.class);
            String subject = dataSnapshot.child(intentName).child("subject").getValue(String.class);


            if (media != null) {
                switch (media) {
                    case "pictures":
                        FulfillmentMap.put("speech", "Now Showing " + media + " of " + subject);
                        dataSnapshot.child(intentName).child("Fulfillment").getRef().setValue(FulfillmentMap);
                        displayImage(subject);
                        dataSnapshot.child(intentName).child("media").getRef().setValue(null);
                        break;
                    case "movie":
                        FulfillmentMap.put("speech", "Now Showing " + media + " of " + subject);
                        dataSnapshot.child(intentName).child("Fulfillment").getRef().setValue(FulfillmentMap);
                        displayVideos(subject);
                        dataSnapshot.child(intentName).child("media").getRef().setValue(null);
                        break;
                    default:
                        FulfillmentMap.put("speech", "Unknow media" + media);
                        return;
                }
            }

            if(color != null){
                if (Device.contains("lcd")){
                   /* mLcd.setCursor(1,0);
                    mLcd.write( Device + " ["+Status+"] "+color);*/
                }
                //else FulfillmentMap.put("speech","No LCD Device found");
            }else if(text != null){
                if (Device.contains("lcd")){
                    /*
                    mLcd.setCursor(0, 0);
                    mLcd.write( "Showing:");
                    mLcd.setCursor(1,0);
                    mLcd.write( text);
                    */
                }
            }else {
                /*mLcd.setCursor(0, 0);*/
                //String value = dataSnapshot.getValue(String.class);
                if (number != null) {
                    Log.d(TAG, intentName + " is: " + Device + "[" + number + "] " + Status);
                    //mLcd.write(Device + " [" + number + "] " + Status);
                } else {
                    Log.d(TAG, intentName);
                    //mLcd.write(intentName);
                }
            }


            dataSnapshot.child(intentName).child("Fulfillment").getRef().setValue(FulfillmentMap);
        }

        @Override
        public void onCancelled(DatabaseError error) {
            // Failed to read value
            Log.w(TAG, "Failed to read value.", error.toException());
        }
    };

    private void displayImage(String subject) {
        Intent intent = new Intent(appContext, ImageViewer.class);
        intent.putExtra("subject", subject);
        appContext.startActivity(intent);
    }

    private void displayVideos(String subject) {
        Intent intent = new Intent(appContext, VideoPlayer.class);
        intent.putExtra("subject", subject);
        appContext.startActivity(intent);

    }


    ValueEventListener devicListner = new ValueEventListener() {
        @Override
        public void onDataChange(DataSnapshot dataSnapshot) {
            //TODO allow chaging of device settings indvigiwal
        }

        @Override
        public void onCancelled(DatabaseError databaseError) {
        // Failed to read value
            Log.w(TAG, "Failed to read value.", databaseError.toException());
        }
    };


    public RealtimeDatabase(Handler _mMainHandler, Context _appContext) {
        appContext = _appContext;
        mMainHandler = _mMainHandler;
    /*    int i2cIndex = mraa.getI2cLookup(getI2CPort());
        mLcd = new upm_jhd1313m1.Jhd1313m1(i2cIndex);
        mLcd.clear();
        mLcd.write("Starting..");
        mLcd.setCursor(1,0);*/
        Log.d(TAG,"Settng up SN:"+Build.SERIAL);
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        myRefMessage = database.getReference("message");
        myRefDevice = database.getReference("device/"+ Build.SERIAL);
        Map<String,Object> data = new HashMap<>();
        PeripheralManagerService Pmanager = new PeripheralManagerService();

        data.put("BoardVariant",getBoardVariant());
        data.put("Gpio",Pmanager.getGpioList());
        data.put("Pwm",Pmanager.getPwmList());
        data.put("I2c",Pmanager.getI2cBusList());
        data.put("Spi",Pmanager.getSpiBusList());
        data.put("Uart",Pmanager.getUartDeviceList());
        data.put("I2s",Pmanager.getI2sDeviceList());

        Long tsLong = System.currentTimeMillis()/1000;
        data.put("Hartrate",tsLong);
        myRefDevice.setValue(data);

        //mMainHandler.postDelayed(mStatusHartrate, mHRInterval);

        myRefMessage.addValueEventListener(mesgListner);
        myRefDevice.addValueEventListener(devicListner);

    }

    void Destroy() {
        stopHartrateTask();
        if (myRefDevice != null){
            myRefDevice.removeValue();
        }
   /*     if(mLcd != null){
            mLcd.delete();
            mLcd = null;
        }*/
    }


    Runnable mStatusHartrate = new Runnable() {
        @Override
        public void run() {
            try {
                Long tsLong = System.currentTimeMillis()/1000;
                myRefDevice.child("Hartrate").setValue(tsLong);
            } finally {
                // 100% guarantee that this always happens, even if
                // your update method throws an exception
                mMainHandler.postDelayed(mStatusHartrate, mHRInterval);
            }
        }
    };

    void stopHartrateTask() {
        mMainHandler.removeCallbacks(mStatusHartrate);
    }




}
