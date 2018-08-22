package com.ilos.mitch.iloswifidatacollection;


import android.hardware.SensorManager;
import android.os.SystemClock;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import static android.content.ContentValues.TAG;

public class GyroHeading {
    private double heading = 0;
    List<Double> speeds = new ArrayList<>();


    List<Long> correspondingTimes = new ArrayList<>();
    GyroHeading(){
        Log.e(TAG, "calculateHeading Initial: " + heading);
    }
    void addTime(long time){
        correspondingTimes.add(time);
    }
    double sensorChanged(double speed, long time){
        speeds.add(Math.toDegrees(speed));
        correspondingTimes.add(time);
        return getHeading();
    }
    private double getHeading(){
        //CW rotation is negative vector
        heading = heading + (-1)*speeds.get(speeds.size()-1)* ((double)correspondingTimes.get(correspondingTimes.size()-1)-(double)correspondingTimes.get(correspondingTimes.size()-2))/(1e9);
        Log.e(TAG, "calculateHeading: post " + heading);
        return heading;
    }
    void resetSpeeds(){
        correspondingTimes = new ArrayList<Long>();
        speeds = new ArrayList<Double>();
    }
    void setHeading(double heading){
        this.heading = heading;
    }

    public List<Long> getCorrespondingTimes() {
        return correspondingTimes;
    }
}
