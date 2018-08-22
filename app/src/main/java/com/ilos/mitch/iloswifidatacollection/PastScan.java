package com.ilos.mitch.iloswifidatacollection;

import android.graphics.drawable.Icon;


//Class used to store the scan information
public class PastScan {
    int level;
    double latitude;
    double longitude;
    Icon icon;
    String time;
    public PastScan(int level, double latitude, double longitude, String time){
        this.level = level;
        this.latitude = latitude;
        this.longitude = longitude;
        this.time = time;
    }
    public PastScan(int level, double latitude, double longitude){
        this.level = level;
        this.latitude = latitude;
        this.longitude = longitude;
        this.time = "";
    }
    public double getLatitude(){
        return this.latitude;
    }
    public double getLongitude(){
        return this.longitude;
    }
    public double getLevel(){
        return this.level;
    }
    public String getTime(){
        return this.time;
    }

}
