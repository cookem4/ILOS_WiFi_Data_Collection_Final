package com.ilos.mitch.iloswifidatacollection;


import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.StrictMode;
import android.os.SystemClock;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Adapter;
import android.widget.CompoundButton;
import android.widget.ProgressBar;
import android.widget.SimpleAdapter;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.mapbox.mapboxsdk.annotations.MarkerOptions;
import com.mapbox.mapboxsdk.geometry.LatLng;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.Object;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.spec.ECField;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;


public class WifiInfo extends AppCompatActivity implements SensorEventListener, StepListener {

    volatile Switch aSwitch;
    //Scan text displays heading, scan number, ets
    volatile TextView scanText;
    volatile TextView titleText;
    //Displays wifi scan results for each round
    volatile TextView wifiText;
    private Sensor myMagnetometer;
    float azimuth = 0f;
    float firstAzimuth = 0f;
    //Used to sum and average values of heading since it does not stabilize values well
    float firstAzimuthSum = 0f;
    int azimuthCount = 0;
    boolean displayAlertDialog = false;
    //This is not being used now but gets angle from start and end coordinates. Code later on in the class can be commented out to give this its functionality
    public static float expectedHeading = 0f;
    float[] mGravity = new float[3];
    float[] mGeomagnetic = new float[3];
    private SensorManager SM;
    private SensorManager SMgyro;
    SensorEventListener SMgyroListener;
    volatile WifiManager wifiManager;
    //Counts the number of wifi scans
    volatile int clickCounter = 0;
    private Handler mRepeatHandler;
    volatile List<String> outputData = new ArrayList<>();
    //Replaces coordinates based on algorithm from Dr. Zheng and YongYong's paper
    volatile List<String> replacedOutput = new ArrayList<>();
    volatile List<String> replacedOutput2 = new ArrayList<>();
    List<String> outputDataRemoved = new ArrayList<>();
    volatile boolean switchChecked = false;
    public static double startLat;
    public static double startLong;
    public static double endLat;
    public static double endLong;
    public static boolean doServerUpload;
    public static List<Double> mapHeadings= new ArrayList<>();
    public static List<LatLng> mapCheckPoints= new ArrayList<>();
    double xCompMotion = 0;
    double yCompMotion = 0;
    double pathDistance = 0;
    double totalNumSteps = 0;
    double degToMRatio = 0;
    //For men multiply height in cm by 0.415
    //For women multiply height in cm by 0.413
    public static double STEP_LENGTH = 0;
    double STEP_TIME_ORIGINAL = 0;
    //Adaptive time is based on the last 5 steps the user takes
    volatile double STEP_TIME_ADAPTIVE = 0;
    volatile boolean walkingTooFast = false;
    volatile boolean walkingTooSlow = false;
    volatile ArrayList<Long> adaptiveTimeList = new ArrayList<Long>();
    volatile ArrayList<Long> scanTimeStampList = new ArrayList<Long>();
    volatile ArrayList<Long> broadCastTimeList = new ArrayList<>();
    volatile int stepCount = 0;
    private StepDetector simpleStepDetector;
    private Sensor accel;
    private Sensor rotation;
    public static String FLOOR_NUMBER = null;
    public static String BUILDING_NAME = null;
    public static String USER_NAME = null;
    //Used for updating to file
    double lat = 0;
    double lon = 0;
    //Used for updating to display
    double displayLat = 0;
    double dispalyLon = 0;
    boolean xIncreasing = false;
    boolean yIncreasing = false;
    //TitleNum is used for bookkeeping to keep track of how many paths have been collected for the server
    volatile String titleNum = "1";
    public volatile boolean pulling  = true;
    public volatile boolean serverError = false;
    public volatile CountDownLatch latch;
    public volatile boolean isError = false;
    //for magnetic field fingerprinting
    List<float[]> magCompList = new ArrayList<>();
    List<Long> magTimeStamps = new ArrayList<>();
    List<String> magOutput = new ArrayList<>();
    SensorEventListener listener;
    float SENSITIVITY;
    public static boolean allowPDRMovement;
    List<LatLng> stepCoordsPDR = new ArrayList<>();
    List<Double> stepHeadings = new ArrayList<>();
    List<LatLng> scanCoordsPDR = new ArrayList<>();
    List<Long> scanTimeStampListCheck = new ArrayList<>();
    LatLng currentCheckPoint;
    double currentHeading;
    GyroHeading gyroHeading;
    boolean enableGyro = false;
    List<Long> checkPointTimeStamps = new ArrayList<>();
    List<LatLng[]> turnDegDiff = new ArrayList<>();




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wifi_info);
        getPermissions();
        getStepProfile();
        lat = startLat;
        lon = startLong;
        displayLat = startLat;
        dispalyLon = startLong;
        android.support.v7.app.ActionBar actionBar = getSupportActionBar();
        actionBar.setTitle("Path Data Collection");
        //Determines weather to increment or decrement the latitude and longitude
        if(endLat - startLat > 0){
            xIncreasing = true;
        }
        else{
            xIncreasing = false;
        }
        if(endLong - startLong > 0){
            yIncreasing = true;
        }
        else{
            yIncreasing = false;
        }
        aSwitch = findViewById(R.id.collectSwitch);
        scanText = findViewById(R.id.scanText);
        wifiText = findViewById(R.id.wifiText);
        titleText = findViewById(R.id.displayTitle);
        titleText.setText("Wifi Data");
        scanText.setText("Flip switch to begin data collection." + "\n" + "Please ensure phone is oriented in same direction as collection path");
        SM = (SensorManager) getSystemService(SENSOR_SERVICE);
        accel = SM.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        rotation = SM.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        //Step detector from class StepDetector
        simpleStepDetector = new StepDetector();
        if(SENSITIVITY == 0.0){
            SENSITIVITY = 20f;
        }
        simpleStepDetector.STEP_THRESHOLD = SENSITIVITY;
        System.out.println(simpleStepDetector.STEP_THRESHOLD);
        simpleStepDetector.registerListener(this);
        myMagnetometer = SM.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        SM.registerListener(this, myMagnetometer, SensorManager.SENSOR_DELAY_FASTEST);
        SM.registerListener(this, rotation, SensorManager.SENSOR_DELAY_FASTEST);
        if(!allowPDRMovement) {
            getMotionInfo();
        }
        else{
            getMotionInfo();
            stepCoordsPDR.add(new LatLng(startLat, startLong));
            stepHeadings.add((double)mapHeadings.get(0));
            //Very large number of steps
            totalNumSteps = 100000000;
        }
        listener = this;
        mRepeatHandler = new Handler();
        //Listener for switch at the top right hand corner of the activity
        aSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, final boolean isChecked) {
                aSwitch.setClickable(true);
                //After becomes unchecked i.e. collection is finsihed
                if(!isChecked){
                    if(!(stepCount < totalNumSteps) && !isError) {
                        SM.unregisterListener(listener);
                        //Automatic finish
                        //Once switch becomes unchecked
                        //First get correct coordinates for each wifi scan based on Dr. Zheng's algorithm
                        unregisterReceiver(cycleWifiReceiver);
                        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                        //Removes errenous timestamps that occur before first step
                        List<Long> scanTimeStampListRemoved = new ArrayList<>();
                        int removedCount = 0;
                        for(int i = 0; i < scanTimeStampList.size(); i++){
                            if(scanTimeStampList.get(i) < adaptiveTimeList.get(0)){
                                System.out.println("start time: " + adaptiveTimeList.get(0));
                                System.out.println("timestamp time " + scanTimeStampList.get(i));
                                removedCount++;
                            }
                            else{
                                scanTimeStampListRemoved.add(scanTimeStampList.get(i));
                                outputDataRemoved.add(outputData.get(i));
                            }
                        }
                        //Removes erroneous timestamps after last step in mag data
                        while(magTimeStamps.get(magTimeStamps.size()-1)>adaptiveTimeList.get(adaptiveTimeList.size()-1)){
                            System.out.println("Had to remove mag list data");
                            magTimeStamps.remove(magTimeStamps.size()-1);
                            magCompList.remove(magCompList.size()-1);
                        }
                        processScanCoordinates(scanTimeStampListRemoved, 0);
                        processScanCoordinates(magTimeStamps , 1);
                        storeData();
                        aSwitch.setClickable(false);
                        switchChecked = false;
                        //resets the number of scans back to zero
                        clickCounter = 0;
                    }
                    else if(!isError){ //Finished before the maximum number of steps is reached without an error occuring. i.e. manual finish
                        //Adds the last "step" as when the switch is checked off if a manual finish is done instead of an automatic finish
                        SM.unregisterListener(listener);
                        adaptiveTimeList.add(SystemClock.elapsedRealtimeNanos()/1000);
                        //adaptiveTimeList.add(SystemClock.elapsedRealtimeNanos());
                        //Manual finish
                        aSwitch.setClickable(false);
                        unregisterReceiver(cycleWifiReceiver);
                        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                        //Removes errenous timestamps that occur before first step
                        List<Long> scanTimeStampListRemoved = new ArrayList<>();
                        int removedCount = 0;
                        for(int i = 0; i < scanTimeStampList.size(); i++){
                            if(scanTimeStampList.get(i) < adaptiveTimeList.get(0)){
                                System.out.println("start time: " + adaptiveTimeList.get(0));
                                System.out.println("timestamp time " + scanTimeStampList.get(i));
                                removedCount++;
                            }
                            else{
                                scanTimeStampListRemoved.add(scanTimeStampList.get(i));
                                outputDataRemoved.add(outputData.get(i));
                            }
                        }

                        scanTimeStampListCheck = scanTimeStampListRemoved;
                        //Removes erroneous timestamps after last step in mag data
                        while(magTimeStamps.get(magTimeStamps.size()-1)>adaptiveTimeList.get(adaptiveTimeList.size()-1)){
                            System.out.println("Had to remove mag list data");
                            magTimeStamps.remove(magTimeStamps.size()-1);
                            magCompList.remove(magCompList.size()-1);
                        }
                        if(allowPDRMovement){
                            checkPointTimeStamps.add(SystemClock.elapsedRealtimeNanos()/1000);
                            //Must process scan coordinates differently here
                            processScanCoordinatesForMultiPath(scanTimeStampListRemoved);
                            saveMultiPathPoints();
                            storeMultiPathData();
                        }
                        else {
                            processScanCoordinates(scanTimeStampListRemoved, 0);
                            processScanCoordinates(magTimeStamps, 1);
                            storeData();
                        }
                        switchChecked = false;
                        clickCounter = 0;
                    }
                    //User has done something to warrant errenous data collection
                    else{
                        SM.unregisterListener(listener);
                        aSwitch.setClickable(false);
                        unregisterReceiver(cycleWifiReceiver);
                        wifiText.setText("");
                        scanText.setText("Press back to select a new data collection path." + "\n" + "User collected data became innacurate");
                        clickCounter = 0;
                    }
                }
                else{ // The start of collection whe switch is first flipped on
                    //To start off sets the adaptive step time as the average step time over collection path
                    if(allowPDRMovement){
                        checkPointTimeStamps.add(SystemClock.elapsedRealtimeNanos()/1000);
                        gyroHeading = new GyroHeading();
                        currentHeading = mapHeadings.get(0);
                        currentCheckPoint = mapCheckPoints.get(1);
                    }
                    STEP_TIME_ADAPTIVE = STEP_TIME_ORIGINAL;
                    //Timestamps when the program starts onto the list of step locations
                    adaptiveTimeList.add(SystemClock.elapsedRealtimeNanos()/1000);
                    //adaptiveTimeList.add(SystemClock.elapsedRealtimeNanos());
                    stepCount = 0;
                    SM.registerListener(WifiInfo.this, accel, SensorManager.SENSOR_DELAY_FASTEST);
                    switchChecked = true;
                    makeWifiBroadcast();
                }
            }
        });
    }
    //Initializes the broadcast receiver for the wifi scan information. Updates when new wifi information is available
    void makeWifiBroadcast(){
        Activity activity = this;
        new Thread(new Runnable() {
            @Override
            public void run() {
                wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                setFrequencyBand2Hz(true, wifiManager);
                WifiManager.WifiLock wifilock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_SCAN_ONLY,"MyLock");
                wifilock.setReferenceCounted(true);
                wifilock.acquire();
                if(!wifilock.isHeld()) {
                    wifilock.acquire();
                }
                //Start time of first broadcast
                broadCastTimeList.add(SystemClock.elapsedRealtimeNanos());
                checkToReRegister();
                activity.registerReceiver(cycleWifiReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
            }
        }).start();
    }
    //Used to set a lock to 2.4GHz networks. This speeds up the frequency of scanning
    public void setFrequencyBand2Hz(boolean enable, WifiManager mWifiManager) {
        int band; //WIFI_FREQUENCY_BAND_AUTO = 0,  WIFI_FREQUENCY_BAND_2GHZ = 2
        try {
            Field field = Class.forName(WifiManager.class.getName())
                    .getDeclaredField("mService");
            field.setAccessible(true);
            Object obj = field.get(mWifiManager);
            Class myClass = Class.forName(obj.getClass().getName());

            Method method = myClass.getDeclaredMethod("setFrequencyBand", int.class, boolean.class);
            method.setAccessible(true);
            if (enable) {
                band = 2;
            } else {
                band = 0;
            }
            method.invoke(obj, band, false);

        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
    //Used to check how long since the last broadcast receiver call. If it has been longer than 2 seconds it will re-register the receiver
    //Inherent android scanresult API problems that cause receiver to not update after some time
    void checkToReRegister(){
        Activity activity = this;
        new Thread(new Runnable() {
            @Override
            public void run() {
                while(true){
                    try {
                        Thread.sleep(1000);
                        long lastTime = (broadCastTimeList.get(broadCastTimeList.size()-1)) - broadCastTimeList.get(broadCastTimeList.size()-2);
                        //Will re-register if time exceeds twice the past broadcast time
                        if(SystemClock.elapsedRealtimeNanos() - broadCastTimeList.get(broadCastTimeList.size()-1) > lastTime*2){
                            activity.unregisterReceiver(cycleWifiReceiver);
                            wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                            setFrequencyBand2Hz(true, wifiManager);
                            WifiManager.WifiLock wifilock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_SCAN_ONLY,"MyLock");
                            wifilock.setReferenceCounted(true);
                            wifilock.acquire();
                            if(!wifilock.isHeld()) {
                                wifilock.acquire();
                            }
                            activity.registerReceiver(cycleWifiReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
                        }
                    }
                    catch(Exception e){}

                }
            }
        }).start();
    }

    //receiver will freeze!!!!! This can be fixed by unregistering and re-registering the receiver which is done above this
    //This is where all the important wifi signal information is receiver and put into global variables
    private final BroadcastReceiver cycleWifiReceiver = new BroadcastReceiver() {
        @SuppressLint("UseSparseArrays")
        @Override
        public void onReceive(Context context, Intent intent) {
            //Weather to flip between one of two items in the start time array. Needed to time a full cycle of wifi scans
            broadCastTimeList.add(SystemClock.elapsedRealtimeNanos());
            System.out.println("BROADCAST TIME: " + (broadCastTimeList.get(broadCastTimeList.size()-1) - broadCastTimeList.get(broadCastTimeList.size()-2)));
            long broadCastTime = (broadCastTimeList.get(broadCastTimeList.size()-1) - broadCastTimeList.get(broadCastTimeList.size()-2));
            if(wifiManager.isWifiEnabled()){
                clickCounter++;
                wifiManager.startScan();
                StringBuffer stringBuffer = new StringBuffer();
                //List contains all the important wifi information -> all the scan results and their respective fields
                List<ScanResult> list = wifiManager.getScanResults();

                //updateScanTimes();
                int itemCount = 0;
                for(ScanResult scanResult:list) {
                    if (list.indexOf(scanResult) > 0) {
                        String prevMAC = list.get(list.indexOf(scanResult) - 1).BSSID;
                        String currentMAC = scanResult.BSSID;
                        prevMAC = prevMAC.substring(0, prevMAC.length() - 1);
                        currentMAC = currentMAC.substring(0, currentMAC.length() - 1);
                        System.out.println(prevMAC);
                        if (!currentMAC.equals(prevMAC)) {
                            itemCount++;
                        }
                        //Replace any commas in SSID with a space
                        String ssid = scanResult.SSID;
                        String ssidOut;
                        ssidOut = ssid.replace(",", " ");
                        long itemTime = 0;
                        itemTime = (long) ((double) broadCastTimeList.get((broadCastTimeList.size() - 2)) + ((double) broadCastTime) * ((double) itemCount / (double) list.size()));
                        System.out.println("BROADCAST FRACTION: " + ((double) itemCount / (double) list.size()));
                        System.out.println("ITEM TIME: " + itemTime);
                        //Adds the time at which the broadcast from each router was receiver in the scan batch
                        //Can change to use the scanResults timestamp or a manual timestamp. However if it is changed between one or the other the adaptiveTimeStamp list must be modified to be either micro or nano seconds
                        scanTimeStampList.add(scanResult.timestamp);
                        //Formats and adds output data. This is later put into a text file
                        outputData.add("SCAN#" + Integer.toString(clickCounter) + "," + Long.toString(scanResult.timestamp) + "," + Double.toString(lat) + "," + Double.toString(lon) + "," + BUILDING_NAME + FLOOR_NUMBER + "," + scanResult.BSSID + "," + ssidOut + "," + scanResult.level);
                        //Stringbuffer is used for output to the screen
                        stringBuffer.append(scanResult.SSID + "     " + scanResult.BSSID + "     " + scanResult.level + "\n");
                    }
                }

                //Following text setters just update display to user
                if(stepCount < totalNumSteps) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            //String buffer contains all of the wifi info to display to text. However, this is being excluded for now
                            wifiText.setText(list.size() + " networks scanned");
                        }
                    });

                }
            }
            else{
                aSwitch.setChecked(false);
            }

        }

    };
    //Does some cool geometry in order to convert a difference in global degrees to metres. Has been verified to be accurate to about half a metre after 60 metres
    void getMotionInfo(){
        //Finds distance between two coordinates in metres
        double degreeDistance = Math.sqrt(Math.pow((startLat - endLat),2) + Math.pow((startLong - endLong),2));
        double R = 6378.137; // Radius of earth in KM
        double dLat = startLat * Math.PI / 180 - endLat* Math.PI / 180;
        dLat = Math.abs(dLat);
        double dLon = startLong * Math.PI / 180 - endLong* Math.PI / 180;
        dLon = Math.abs(dLon);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(endLat * Math.PI / 180) * Math.cos(startLat* Math.PI / 180) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distance;
        distance = R * c;
        distance = distance * 1000f;
        pathDistance = distance;
        degToMRatio = degreeDistance/distance;
        double angle = Math.atan(dLat/dLon);
        xCompMotion = Math.cos(angle);
        yCompMotion = Math.sin(angle);
        totalNumSteps = pathDistance/STEP_LENGTH;
        //Rounds number of steps to nearest whole number
        totalNumSteps = Math.round(totalNumSteps);
    }
    double[] findDistance(LatLng point1, LatLng point2){
        double degreeDistance = Math.sqrt(Math.pow((point1.getLatitude() - point2.getLatitude()),2) + Math.pow((point1.getLongitude() - point2.getLongitude()),2));
        double R = 6378.137; // Radius of earth in KM
        double dLat = point1.getLatitude() * Math.PI / 180 - point2.getLatitude()* Math.PI / 180;
        dLat = Math.abs(dLat);
        double dLon = point1.getLongitude() * Math.PI / 180 - point2.getLongitude()* Math.PI / 180;
        dLon = Math.abs(dLon);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(point2.getLatitude() * Math.PI / 180) * Math.cos(point1.getLatitude()* Math.PI / 180) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distance;
        distance = R * c;
        distance = distance * 1000f;
        double degToMRatioReturn = degreeDistance/distance;
        double[] output = new double[]{distance, degToMRatioReturn};
        return output;
    }
    //Updates the coordinates of the user after each step based on degrees to metres conversion and the orientation of the walking path
    void updateCoordinates(int stepType){
        //For a full step, display lat & lon are used to update screen info while lat and lon are used to help tag each scan with its own coordinate
        if(stepType == 0){
            if(xIncreasing) {
                //yCompMotion is the sine of the angle the path makes with the horizontal
                lat = lat + STEP_LENGTH * yCompMotion * degToMRatio;
                displayLat = displayLat + STEP_LENGTH*yCompMotion*degToMRatio;
            }
            else{
                lat = lat - STEP_LENGTH * yCompMotion * degToMRatio;
                displayLat = displayLat - STEP_LENGTH*yCompMotion*degToMRatio;
            }
            if(yIncreasing) {
                lon = lon + STEP_LENGTH * xCompMotion * degToMRatio;
                dispalyLon= dispalyLon + STEP_LENGTH*xCompMotion*degToMRatio;
            }
            else{
                lon = lon - STEP_LENGTH * xCompMotion * degToMRatio;
                dispalyLon= dispalyLon - STEP_LENGTH*xCompMotion*degToMRatio;
            }
        }
    }
    boolean checkGyroAngle(double expectedAngle, double currentAngle){
        expectedAngle = (Math.toDegrees(expectedAngle) + 360)%360;
        currentAngle = (currentAngle + 360)%360;
        System.out.println("TURNED TURN ANGLE" + currentAngle);
        System.out.println("TURNED EXPECTED ANGLE" + Math.toDegrees(expectedAngle));
        System.out.println("TURNED TURN DIFF " + (expectedAngle - currentAngle));
        if(Math.abs(expectedAngle - currentAngle) < 5){
            gyroHeading.resetSpeeds();
            return true;
        }
        else{
            return false;
        }
    }
    void newTurn(){
        //Called when the gyroscope detects a turn angle near the expected turn angle
        long currentTime = SystemClock.elapsedRealtimeNanos()/1000;

        checkPointTimeStamps.add(currentTime);
        lat = currentCheckPoint.getLatitude();
        displayLat = currentCheckPoint.getLatitude();
        lon = currentCheckPoint.getLongitude();
        dispalyLon = currentCheckPoint.getLongitude();
        currentHeading = mapHeadings.get(mapHeadings.indexOf(currentHeading) + 1);

        currentCheckPoint = mapCheckPoints.get(mapCheckPoints.indexOf(currentCheckPoint)+1);
        System.out.println("TURNED HERE");
        System.out.println("TURNED CURRENT HEADING " + currentHeading);
        System.out.println("TURNED LAT AND LON" + lat +  ", " + lon);
        enableGyro = false;
        gyroHeading = new GyroHeading();
    }
    void multiPathMovement(){
        //Called during steps
        stepHeadings.add(currentHeading);
        System.out.println("TURNED angle " + currentHeading);
        double xCompMotion = Math.cos(currentHeading);
        double yCompMotion = Math.sin(currentHeading);
        System.out.println("TURNED COMPS " + xCompMotion + "," + yCompMotion + "," + degToMRatio);
        lat = lat + STEP_LENGTH * yCompMotion * degToMRatio;
        displayLat = displayLat + STEP_LENGTH*yCompMotion*degToMRatio;
        lon = lon + STEP_LENGTH * xCompMotion * degToMRatio;
        dispalyLon= dispalyLon + STEP_LENGTH*xCompMotion*degToMRatio;
        stepCoordsPDR.add(new LatLng(lat, lon));

        //y is sin of theta
        //x is cos of theta
        System.out.println("TURNED INFO " + findDistance(new LatLng(lat, lon), currentCheckPoint)[0] + ", " + currentCheckPoint.toString());
        if(!enableGyro && mapCheckPoints.indexOf(currentCheckPoint)!=mapCheckPoints.size()-1 && mapHeadings.indexOf(currentHeading)!=mapHeadings.size()-1 && findDistance(new LatLng(lat, lon), currentCheckPoint)[0] < 1.5*STEP_LENGTH){
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getBaseContext(), "TURN NOW", Toast.LENGTH_SHORT).show();
                }
            });
            enableGyro = true;
        }
        else if(enableGyro && findDistance(new LatLng(lat, lon), currentCheckPoint)[0] > 1.5*STEP_LENGTH){
            newTurn();
        }
        else if(mapCheckPoints.indexOf(currentCheckPoint)==mapCheckPoints.size()-1 && mapHeadings.indexOf(currentHeading)==mapHeadings.size()-1 &&findDistance(new LatLng(lat, lon), currentCheckPoint)[0] < 1*STEP_LENGTH){
            //Display for last checkpoint only
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getBaseContext(), "END COLLECTION", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }
    void processScanCoordinatesForMultiPath(List<Long> scanTimeStampList){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                scanText.setText("Collection Complete!" + "\n" + "Data Stored");
                wifiText.setText("");
            }
        });
        //Treat as n number of individual paths
        //new function for tagging individual coords for each scan from multi path navigation
        for (int i = 0; i < scanTimeStampList.size(); i++) {
            long macIDTime = scanTimeStampList.get(i);
            for (int j = 0; j < adaptiveTimeList.size() - 1; j++) {
                long lowerTimeBound = adaptiveTimeList.get(j);
                long upperTimeBound = adaptiveTimeList.get(j + 1);
                if (macIDTime <= upperTimeBound && macIDTime > lowerTimeBound) {
                    //If the scan falls within the range of a certain step
                    LatLng prevStep = stepCoordsPDR.get(j);
                    double headingOnStep = stepHeadings.get(j);
                    long checkPointTimeStamp = checkPointTimeStamps.get(mapHeadings.indexOf(headingOnStep)+1);
                    double tempLat;
                    double tempLon;
                    //If a step had a turn within it
                    if(lowerTimeBound < checkPointTimeStamp  && upperTimeBound > checkPointTimeStamp){
                        //If the current macID time is less than when the checkpoint occurred, calculate based on the checkpoint time as the upper time bound
                        if(macIDTime < checkPointTimeStamp){
                            tempLat = prevStep.getLatitude() + findDistance(prevStep, mapCheckPoints.get(checkPointTimeStamps.indexOf(checkPointTimeStamp)))[0]* ((double) (macIDTime - lowerTimeBound)) / ((double) (checkPointTimeStamp - lowerTimeBound))*Math.sin(headingOnStep)*findDistance(prevStep, mapCheckPoints.get(checkPointTimeStamps.indexOf(checkPointTimeStamp)))[1];
                            tempLon = prevStep.getLatitude() + findDistance(prevStep, mapCheckPoints.get(checkPointTimeStamps.indexOf(checkPointTimeStamp)))[0]* ((double) (macIDTime - lowerTimeBound)) / ((double) (checkPointTimeStamp - lowerTimeBound))*Math.cos(headingOnStep)*findDistance(prevStep, mapCheckPoints.get(checkPointTimeStamps.indexOf(checkPointTimeStamp)))[1];
                        }
                        else if(j<stepCoordsPDR.size()){
                            //If the current macID time is greater than when the checkpoint occurred, calculate based on the checkpoint as lower time bound
                            tempLat = mapCheckPoints.get(checkPointTimeStamps.indexOf(checkPointTimeStamp)).getLatitude() + findDistance(mapCheckPoints.get(checkPointTimeStamps.indexOf(checkPointTimeStamp)),stepCoordsPDR.get(j+1))[0]* ((double) (macIDTime - checkPointTimeStamp)) / ((double) (upperTimeBound - checkPointTimeStamp))* Math.sin(mapHeadings.get(mapHeadings.indexOf(headingOnStep)+1))* findDistance(mapCheckPoints.get(checkPointTimeStamps.indexOf(checkPointTimeStamp)),stepCoordsPDR.get(j+1))[1];
                            tempLon = mapCheckPoints.get(checkPointTimeStamps.indexOf(checkPointTimeStamp)).getLongitude() + findDistance(mapCheckPoints.get(checkPointTimeStamps.indexOf(checkPointTimeStamp)),stepCoordsPDR.get(j+1))[0]* ((double) (macIDTime - checkPointTimeStamp)) / ((double) (upperTimeBound - checkPointTimeStamp))* Math.sin(mapHeadings.get(mapHeadings.indexOf(headingOnStep)+1))* findDistance(mapCheckPoints.get(checkPointTimeStamps.indexOf(checkPointTimeStamp)),stepCoordsPDR.get(j+1))[1];

                        }
                        else{
                            tempLat =  mapCheckPoints.get(checkPointTimeStamps.indexOf(checkPointTimeStamp)).getLatitude();
                            tempLon =  mapCheckPoints.get(checkPointTimeStamps.indexOf(checkPointTimeStamp)).getLongitude();
                        }
                    }
                    else {
                        tempLat = prevStep.getLatitude() + STEP_LENGTH * ((double) (macIDTime - lowerTimeBound)) / ((double) (upperTimeBound - lowerTimeBound)) * Math.sin(headingOnStep) * findDistance(prevStep, mapCheckPoints.get(mapHeadings.indexOf(headingOnStep)+1))[1];
                        tempLon = prevStep.getLongitude() + STEP_LENGTH * ((double) (macIDTime - lowerTimeBound)) / ((double) (upperTimeBound - lowerTimeBound)) * Math.cos(headingOnStep) * findDistance(prevStep, mapCheckPoints.get(mapHeadings.indexOf(headingOnStep)+1))[1];
                    }
                    scanCoordsPDR.add(new LatLng(tempLat, tempLon));
                }
            }
        }
        //Re-writes coordinates in outputdata and the list of strings to post
        for (int i = 0; i < outputDataRemoved.size(); i++) {
            String line = outputDataRemoved.get(i);
            String[] entries = line.split(",");
            //Final hashmap for creating a single string for the entire path
            //Entries at indices 2 and 3 represent latitude and longitude
            entries[2] = Double.toString(scanCoordsPDR.get(i).getLatitude());
            entries[3] = Double.toString(scanCoordsPDR.get(i).getLongitude());
            String output = "";
                for (int j = 0; j < entries.length; j++) {
                    if (j == entries.length - 1) {
                        output = output + entries[j];
                    } else {
                        output = output + entries[j] + ",";
                    }
                //Creates new output list by replacing the latitude and longitude in the original. This list will later be written to storage
                replacedOutput.add(output);
            }
        }
        if (doServerUpload) {
            //Re-writes coordinates in outputdata and the list of strings to post
            for (int i = 0; i < outputDataRemoved.size(); i++) {
                boolean add = true;
                String line = outputDataRemoved.get(i);
                String[] entries = line.split(",");
                //Final hashmap for creating a single string for the entire path
                //Entries at indices 2 and 3 represent latitude and longitude
                entries[2] = Double.toString(scanCoordsPDR.get(i).getLatitude());
                entries[3] = Double.toString(scanCoordsPDR.get(i).getLongitude());
                String output = "";
                for (int j = 0; j < entries.length; j++) {
                    if (j != 4 && j != 6) {
                        if (j == entries.length - 1) {
                            output = output + entries[j];
                        } else {
                            if (j == 0 || j == 5) {
                                output = output + "\"" + entries[j] + "\"" + ",";
                            } else {
                                output = output + entries[j] + ",";
                            }
                        }
                    }
                }
                //Creates new output list by replacing the latitude and longitude in the original. This list will be used for server upload
                if(add) {
                    replacedOutput2.add(output);
                }
            }
            WifiManager manager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            android.net.wifi.WifiInfo info = manager.getConnectionInfo();
            String address = info.getMacAddress();

            //This posts the information to the server
            //Creates a single string that posts all information in a single string, not a list
            String finalPostString = "";
            finalPostString += "{";
            finalPostString += "\"phonemacid\":\"";
            finalPostString += address;
            finalPostString += "\",\"collector\":\"";
            finalPostString += USER_NAME;
            finalPostString += "\",\"purpose\":\"";
            finalPostString += "trainingdata\"";
            finalPostString += ",\"building\":\"";
            //Create separate names for straight paths and multi-paths
            finalPostString = finalPostString + BUILDING_NAME + "MULTIPATH";
            finalPostString += "\",\"floor\":\"";
            finalPostString += FLOOR_NUMBER;
            finalPostString += "\",\"coltime\":\"";
            finalPostString += Long.toString(SystemClock.elapsedRealtime());
            finalPostString += "\",\"checkpoints\":";
            finalPostString+="[";
            for(int i = 0; i < mapCheckPoints.size(); i++){
                if(i == mapCheckPoints.size()-1){
                    finalPostString += "\"(" + Double.toString(mapCheckPoints.get(i).getLatitude()) + "," + Double.toString(mapCheckPoints.get(i).getLongitude()) + ")\"";
                }
                else {
                    finalPostString += "\"(" + Double.toString(mapCheckPoints.get(i).getLatitude()) + "," + Double.toString(mapCheckPoints.get(i).getLongitude()) + ")\",";
                }
            }
            finalPostString+="]";
            finalPostString += ",\"fp\":";
            finalPostString += "[";
            for (int j = 0; j < replacedOutput2.size(); j++) {
                finalPostString = finalPostString + "[" + replacedOutput2.get(j) + "]" + ",";
            }
            finalPostString = finalPostString.substring(0, finalPostString.length() - 1);
            finalPostString += "]}";
            System.out.println(finalPostString);
            postScan("http://macquest2.cas.mcmaster.ca/ilos/fp_upload/", finalPostString);
        }
    }
    //Does post processing after all the data is collected. We-writes the coordinates in output data with correct coordinates based on time fractions between steps
    void processScanCoordinates(List<Long> scanTimeStampList, int outputListType){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                scanText.setText("Collection Complete!" + "\n" + "Data Stored");
                wifiText.setText("");
            }
        });
        //Make a list to store coordinates of each scan. The index number represents the scan number minus 1
        List<LatLng> scanCoordinates = new ArrayList<>();
        //Loops through each scan timestamp and puts it at its appropriate coordinate based on what two steps it is between andt he time fraction at which it is between those steps
        for(int i = 0; i < scanTimeStampList.size(); i++){
            long macIDTime = scanTimeStampList.get(i);
            for(int j = 0; j < adaptiveTimeList.size()-1;j++){
                long lowerTimeBound = adaptiveTimeList.get(j);
                long upperTimeBound = adaptiveTimeList.get(j+1);
                if(macIDTime <= upperTimeBound && macIDTime > lowerTimeBound){
                    //falls within the range of a certain step
                    //System.out.println(macIDTime + " BETWEEN " + lowerTimeBound + " AND " + upperTimeBound + " at step number " + j + " with step fraction " + (double)(macIDTime - lowerTimeBound)/(double)(upperTimeBound-lowerTimeBound));
                    //temp lat and temp long are the coordinates of each scan as we loop through all teh scans
                    double tempLat;
                    double tempLong;
                    //j is the number of steps
                    if (xIncreasing) {
                        tempLat = startLat + STEP_LENGTH * (j + (double) (macIDTime - lowerTimeBound) / (double) (upperTimeBound - lowerTimeBound)) * yCompMotion * degToMRatio;
                    } else {
                        tempLat = startLat - STEP_LENGTH * (j + (double) (macIDTime - lowerTimeBound) / (double) (upperTimeBound - lowerTimeBound)) * yCompMotion * degToMRatio;
                    }
                    if (yIncreasing) {
                        tempLong = startLong + STEP_LENGTH * (j + (double) (macIDTime - lowerTimeBound) / (double) (upperTimeBound - lowerTimeBound)) * xCompMotion * degToMRatio;
                    } else {
                        tempLong = startLong - STEP_LENGTH * (j + (double) (macIDTime - lowerTimeBound) / (double) (upperTimeBound - lowerTimeBound)) * xCompMotion * degToMRatio;
                    }
                    scanCoordinates.add(new LatLng(tempLat, tempLong));
                }
                else if (lowerTimeBound < macIDTime){

                }
            }
        }
        if(outputListType == 0) {
            //Each index of the list corresponds to a scan
            //Re-writes coordinates in outputdata and the list of strings to post
            for (int i = 0; i < outputDataRemoved.size(); i++) {
                String line = outputDataRemoved.get(i);
                String[] entries = line.split(",");
                //Final hashmap for creating a single string for the entire path
                //Entries at indices 2 and 3 represent latitude and longitude
                entries[2] = Double.toString(scanCoordinates.get(i).getLatitude());
                entries[3] = Double.toString(scanCoordinates.get(i).getLongitude());
                String output2 = "";
                for (int j = 0; j < entries.length; j++) {
                    if (j == entries.length - 1) {
                        output2 = output2 + entries[j];
                    } else {
                        output2 = output2 + entries[j] + ",";
                    }
                }
                //Creates new output list by replacing the latitude and longitude in the original. This list will later be written to storage
                replacedOutput.add(output2);
            }

            if (doServerUpload && !allowPDRMovement) {

                //Re-writes coordinates in outputdata and the list of strings to post
                for (int i = 0; i < outputDataRemoved.size(); i++) {
                    String line = outputDataRemoved.get(i);
                    String[] entries = line.split(",");
                    //Final hashmap for creating a single string for the entire path
                    //Entries at indices 2 and 3 represent latitude and longitude
                    entries[2] = Double.toString(scanCoordinates.get(i).getLatitude());
                    entries[3] = Double.toString(scanCoordinates.get(i).getLongitude());
                    String output = "";
                    for (int j = 0; j < entries.length; j++) {
                        if (j != 4 && j != 6) {
                            if (j == entries.length - 1) {
                                output = output + entries[j];
                            } else {
                                if (j == 0 || j == 5) {
                                    output = output + "\"" + entries[j] + "\"" + ",";
                                } else {
                                    output = output + entries[j] + ",";
                                }
                            }
                        }
                    }
                    //Creates new output list by replacing the latitude and longitude in the original. This list will be used for server upload
                    replacedOutput2.add(output);
                }
                WifiManager manager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                android.net.wifi.WifiInfo info = manager.getConnectionInfo();
                String address = info.getMacAddress();

                //This posts the information to the server
                //Creates a single string that posts all information in a single string, not a list
                String finalPostString = "";
                finalPostString += "{";
                finalPostString += "\"phonemacid\":\"";
                finalPostString += address;
                finalPostString += "\",\"collector\":\"";
                finalPostString += USER_NAME;
                finalPostString += "\",\"purpose\":\"";
                finalPostString += "trainingdata\"";
                finalPostString += ",\"building\":\"";
                finalPostString = finalPostString + BUILDING_NAME;
                finalPostString += "\",\"floor\":\"";
                finalPostString += FLOOR_NUMBER;
                finalPostString += "\",\"coltime\":\"";
                finalPostString += Long.toString(SystemClock.elapsedRealtime());
                finalPostString += "\",\"startpos\":";
                finalPostString += "[" + Double.toString(startLat) + "," + Double.toString(startLong) + "]";
                finalPostString += ",\"endpos\":";
                finalPostString += "[" + Double.toString(endLat) + "," + Double.toString(endLong) + "]";
                finalPostString += ",\"fp\":";
                finalPostString += "[";
                for (int j = 0; j < replacedOutput2.size(); j++) {
                    finalPostString = finalPostString + "[" + replacedOutput2.get(j) + "]" + ",";
                }
                finalPostString = finalPostString.substring(0, finalPostString.length() - 1);
                finalPostString += "]}";
                System.out.println(finalPostString);
                postScan("http://macquest2.cas.mcmaster.ca/ilos/fp_upload/", finalPostString);
            }
        }
        else if (outputListType == 1){
            //Builds and writes to string for mag data. This can be used for fingerprinting as well
            for (int i = 0; i < magCompList.size(); i++){
                String outString = "";
                outString+=Float.toString(magCompList.get(i)[0]);
                outString+=",";
                outString+=Float.toString(magCompList.get(i)[1]);
                outString+=",";
                outString+=Float.toString(magCompList.get(i)[2]);
                outString+=",";
                outString+=Long.toString(magTimeStamps.get(i));
                outString+=",";
                outString+=Double.toString(scanCoordinates.get(i).getLatitude());
                outString+=",";
                outString+=Double.toString(scanCoordinates.get(i).getLongitude());
                magOutput.add(outString);
            }
        }

    }
    //UI updates for the user information such as heading, step time, number of scans, number of steps. NOT the wifi information listed out. Called by the sensor onAccuracy changed
    void updateUI(){
        if(stepCount < totalNumSteps) {
            if(allowPDRMovement){
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        scanText.setText(USER_NAME + "'s" + " Step Length: " + String.format("%.3f", STEP_LENGTH) + "m" + "\n" + "Distance from next turn: " + String.format("%.3f", findDistance(new LatLng(lat,lon), currentCheckPoint)[0]) + "m" + "\n" + "Expected Speed: " + STEP_TIME_ORIGINAL + "\n" + "Actual Speed: " + String.format("%.3f", STEP_TIME_ADAPTIVE ) + "\n" +"Scan #" + (clickCounter) + "\n" + BUILDING_NAME + " " + FLOOR_NUMBER + "\n" + "Number of Steps: " + Integer.toString(stepCount));
                    }
                });
            }
            else {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        scanText.setText(USER_NAME + "'s" + " Step Length: " + String.format("%.3f", STEP_LENGTH) + "m" + "\n" + "Expected Speed: " + STEP_TIME_ORIGINAL + "\n" + "Actual Speed: " + String.format("%.3f", STEP_TIME_ADAPTIVE ) + "\n"+ "Scan #" + (clickCounter) + "\n" + BUILDING_NAME + " " + FLOOR_NUMBER + "\n" + "Number of Steps: " + Integer.toString(stepCount));
                    }
                });
            }
        }
        else{
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    scanText.setText("Collection Complete!" + "\n" + "Data Stored to Local Storage");
                    wifiText.setText("");
                }
            });
        }
    }
    //Used for all the important sensor data that does not have to do with wifi
    //Accelerometer for step counter, magnetic field for heading, gyroscope for rotation speed
    @Override
    public void onSensorChanged(SensorEvent event) {
        //For heading values, step counting, and magnetic field information
        final float alpha = 0.97f;
        float rotationSpeed = 0f;
        if(switchChecked){
            // synchronized (this) {
            if (switchChecked && event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                mGravity[0] = alpha * mGravity[0] + (1 - alpha) * event.values[0];
                mGravity[1] = alpha * mGravity[1] + (1 - alpha) * event.values[1];
                mGravity[2] = alpha * mGravity[2] + (1 - alpha) * event.values[2];
                simpleStepDetector.updateAccel(
                        event.timestamp, event.values[0], event.values[1], event.values[2]);
            }
            //Note that heading from magnetic field and gycroscope data are used to give the user feedback on the accuracy of their collected path. Ensures that the phone is held in the same direction as the path
            if (switchChecked && event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                mGeomagnetic[0] = alpha * mGeomagnetic[0] + (1 - alpha) * event.values[0];
                mGeomagnetic[1] = alpha * mGeomagnetic[1] + (1 - alpha) * event.values[1];
                mGeomagnetic[2] = alpha * mGeomagnetic[2] + (1 - alpha) * event.values[2];
                magTimeStamps.add(SystemClock.elapsedRealtimeNanos() / 1000);
                magCompList.add(new float[]{event.values[0], event.values[1], event.values[2]});
            }
            if (switchChecked && event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
                rotationSpeed = event.values[2];
                if(enableGyro){
                    if(gyroHeading.getCorrespondingTimes().size()==0){
                        gyroHeading.addTime(SystemClock.elapsedRealtimeNanos());
                    }
                    else{
                        double gyroCheckAngle = currentHeading - mapHeadings.get(mapHeadings.indexOf(currentHeading) + 1);
                        double heading = gyroHeading.sensorChanged(rotationSpeed, SystemClock.elapsedRealtimeNanos());
                        if(checkGyroAngle(gyroCheckAngle, heading)){
                            newTurn();
                            enableGyro = false;
                        }
                    }
                }
            }
            //1.5 is arbitraty threshold for rotation speed that will alert the user to re-collect the data
            if (rotationSpeed > 1.5) {
                //TODO later enable this to prevent erroneous data collection
                //displayAlertDialog = true;
                SM.unregisterListener(this);
            }

            float R[] = new float[9];
            float I[] = new float[9];
            boolean success = SensorManager.getRotationMatrix(R, I, mGravity, mGeomagnetic);
            if (success) {
                float[] orientation = new float[3];
                //Give a few seconds for magnetic reading to stabilize then take an average for a few seconds to find acceptable orientation
                if (clickCounter < 60 && clickCounter > 30) {
                    azimuthCount++;
                    firstAzimuthSum += azimuth;
                }
                if (clickCounter == 60) {
                    //Gives average from a few seconds since reading does not stabalize when walking
                    firstAzimuth = firstAzimuthSum / azimuthCount;
                }
                SM.getOrientation(R, orientation);
                azimuth = (float) (Math.toDegrees(orientation[0]));
                azimuth = removeMagLoops(azimuth);
                if (Math.abs(firstAzimuth - azimuth) > 35 && clickCounter > 60) {
                    //TODO disable this to allow for easier testing without orientation
                    //displayAlertDialog = true;
                    //SM.unregisterListener(this);
                    //   }

                }

            }

        }
        //Tells user to recollect the data since their heading has become innacurate. This skews results
        if (displayAlertDialog) {
            AlertDialog.Builder builder1 = new AlertDialog.Builder(this);
            builder1.setMessage("Phone orientation no longer matches path orientation!");
            builder1.setCancelable(true);
            switchChecked = false;
            builder1.setPositiveButton(
                    "Restart Collection",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            isError = true;
                            aSwitch.setChecked(false);
                            dialog.cancel();
                        }
                    });
            AlertDialog alert11 = builder1.create();
            alert11.show();
            displayAlertDialog = false;
        }
        if(switchChecked) {
            updateUI();
        }
    }
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        System.out.println("ACCURACY CHANGED");
        Log.e("ACC", "onAccuracyChanged: ");
    }

    //Override for the class
    @Override
    public void onResume(){
        Log.e("", "onResume: ");
        System.out.println("RESUME");
        super.onResume();
        SM.registerListener((SensorEventListener)this,SM.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),SensorManager.SENSOR_DELAY_FASTEST);
        SM.registerListener((SensorEventListener)this,SM.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),SensorManager.SENSOR_DELAY_FASTEST);
    }
    //Override for the class
    @Override
    public void onPause(){
        Log.e("", "onPause: " );
        System.out.println("PAUSED");
        super.onPause();
        SM.unregisterListener(this);
        SM.unregisterListener(listener);
    }
    //Removed full multiples of 360deg rotations on magnetic orientation
    float removeMagLoops(float val){
        while(val < 0){
            val+=360;
        }
        val%=360;
        return val;
    }
    //Stores multi path points
    void saveMultiPathPoints(){
        List<String> pdrOutputPoints = new ArrayList<>();
        for(int i = 0 ; i < mapCheckPoints.size(); i++){
            pdrOutputPoints.add(Double.toString(mapCheckPoints.get(i).getLatitude()) + "," + Double.toString(mapCheckPoints.get(i).getLongitude()));
        }
        try {
            File sdCard = Environment.getExternalStorageDirectory();
            File directory = new File(sdCard.getAbsolutePath() + "/DataCollectPDRPath");
            Log.i("Save Dir", sdCard.getAbsolutePath() + "/DataCollectPDRPath");
            directory.mkdirs();
            //Each path gets a unique name. This title is used at a different point in the app to draw out the colelcted paths
            String filename = BUILDING_NAME + FLOOR_NUMBER + "(" + startLat + "," + startLong + ")" + "to" + "(" + endLat + "," + endLong + ")" + ".txt";
            File file = new File(directory, filename);
            PrintWriter out = new PrintWriter(file);
            for(int i = 0; i < pdrOutputPoints.size(); i++){
                out.write(pdrOutputPoints.get(i));
                out.write("\r\n");
            }
            out.close();

        }
        catch(Exception e){}
    }
    void storeMultiPathData(){
        try {
            //save path data
            File sdCard = Environment.getExternalStorageDirectory();
            File directory = new File(sdCard.getAbsolutePath() + "/DataCollectPDRData");
            Log.i("Save Dir", sdCard.getAbsolutePath() + "/DataCollectPDRData");
            directory.mkdirs();
            //Each path gets a unique name. This title is used at a different point in the app to draw out the colelcted paths
            String filename = BUILDING_NAME + FLOOR_NUMBER + "(" + startLat + "," + startLong + ")" + "to" + "(" + endLat + "," + endLong + ")" + ".txt";
            File file = new File(directory, filename);
            PrintWriter out = new PrintWriter(file);
            for (int i = 0; i < replacedOutput.size(); i++) {
                out.write(replacedOutput.get(i));
                out.write("\r\n");
            }
            out.close();
        }
        catch (Exception e){}
    }
    //Used to store sensor and wifi data
    void storeData(){
        try {
            //save path data
            File sdCard = Environment.getExternalStorageDirectory();
            File directory = new File(sdCard.getAbsolutePath() + "/DataCollect");
            Log.i("Save Dir", sdCard.getAbsolutePath() + "/DataCollect");
            directory.mkdirs();
            //Each path gets a unique name. This title is used at a different point in the app to draw out the colelcted paths
            String filename = BUILDING_NAME + FLOOR_NUMBER + "(" + startLat + "," + startLong + ")" + "to" + "(" + endLat + "," + endLong + ")"+ ".txt";
            File file = new File(directory, filename);
            PrintWriter out = new PrintWriter(file);
            for (int i = 0; i<replacedOutput.size();i++) {
                out.write(replacedOutput.get(i));
                out.write("\r\n");
            }
            out.close();
            //save mag data
            File sdCard2 = Environment.getExternalStorageDirectory();
            File directory2 = new File(sdCard2.getAbsolutePath() + "/DataCollectMag");
            directory2.mkdirs();
            //Each path gets a unique name. This title is used at a different point in the app to draw out the colelcted paths
            String filename2 = BUILDING_NAME + FLOOR_NUMBER + "(" + startLat + "," + startLong + ")" + "to" + "(" + endLat + "," + endLong + ")"+ ".txt";
            File file2 = new File(directory2, filename2);
            PrintWriter out2 = new PrintWriter(file2);
            for (int i = 0; i<magOutput.size();i++) {
                out2.write(magOutput.get(i));
                out2.write("\r\n");
            }
            out2.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    //Step activity based on accelerometer. An override from teh StepDetector class
    @Override
    public void step(long timeNs) {
        //here is for multiPath steps
        if(switchChecked && allowPDRMovement){
            stepCount++;
            if(stepCount <=3 && stepCount>=1){
                //Adaptive time list is used to verify that the user is collecting data properly. If they speed up or slow down this can change step length
                adaptiveTimeList.add(timeNs/1000);
                //adaptiveTimeList.add(timeNs);
            }
            else if(stepCount >3){
                STEP_TIME_ADAPTIVE = (timeNs/1000 - adaptiveTimeList.get(adaptiveTimeList.size()-3))/3;
                adaptiveTimeList.add(timeNs/1000);
                //adaptiveTimeList.add(timeNs);

                //Divide b 10^6 to display in seconds
                STEP_TIME_ADAPTIVE/=1000000;
            }
            //0.1 here is a threshold in seconds
            if((STEP_TIME_ADAPTIVE  - STEP_TIME_ORIGINAL) > 0.15 && stepCount > 3){
                //TODO disable this to allow for easier testing without step speed notifications
                walkingTooFast = true;
                SM.unregisterListener(this);

            }
            else if(STEP_TIME_ADAPTIVE - STEP_TIME_ORIGINAL < -0.15 && stepCount > 3){
                //TODO diable this to allow for easier testing without step speed notifications
                walkingTooSlow = true;
                SM.unregisterListener(this);

            }
            multiPathMovement();
        }
        //For single path steps
        //If we haven't walked the whole path yet
        if(stepCount < (totalNumSteps -1) && switchChecked && !allowPDRMovement) {
            stepCount++;
            if(stepCount <=3 && stepCount>=1){
                //Adaptive time list is used to verify that the user is collecting data properly. If they speed up or slow down this can change step length
                adaptiveTimeList.add(timeNs/1000);
                //adaptiveTimeList.add(timeNs);
            }
            else if(stepCount >3){
                STEP_TIME_ADAPTIVE = (timeNs/1000 - adaptiveTimeList.get(adaptiveTimeList.size()-3))/3;
                adaptiveTimeList.add(timeNs/1000);
                //adaptiveTimeList.add(timeNs);

                //Divide b 10^6 to display in seconds
                STEP_TIME_ADAPTIVE/=1000000;
            }
            updateCoordinates(0);
            //0.1 here is a threshold in seconds
            if((STEP_TIME_ADAPTIVE  - STEP_TIME_ORIGINAL) > 0.15 && stepCount > 3){
                //TODO disable this to allow for easier testing without step speed notifications
                walkingTooFast = true;
                SM.unregisterListener(this);

            }
            else if(STEP_TIME_ADAPTIVE - STEP_TIME_ORIGINAL < -0.15 && stepCount > 3){
                //TODO diable this to allow for easier testing without step speed notifications
                walkingTooSlow = true;
                SM.unregisterListener(this);

            }
        }
        //Last step
        else if(stepCount == totalNumSteps - 1){
            stepCount++;
            SM.unregisterListener(listener);
            switchChecked = false;
            System.out.println("finished");
            if(stepCount <=3 && stepCount>=1){
                adaptiveTimeList.add(timeNs/1000);
                //adaptiveTimeList.add(timeNs);

            }
            else if(stepCount >3){
                STEP_TIME_ADAPTIVE = (timeNs/1000 - adaptiveTimeList.get(adaptiveTimeList.size()-3))/3;
                adaptiveTimeList.add(timeNs/1000);
                //adaptiveTimeList.add(timeNs);

                STEP_TIME_ADAPTIVE/=1000000;
            }
            updateCoordinates(0);
            //Change switch to unchecked, concluding collection
            aSwitch.setChecked(false);
            //scanText.setText("Scan complete, flip switch to begin new data collection");
            stepCount = 0;
        }
        //Tells the user to re-collect data if they have began to walk too fast
        if(walkingTooFast){
            AlertDialog.Builder builder1 = new AlertDialog.Builder(this);
            builder1.setMessage("User walking speed of " + String.format("%.3f",STEP_TIME_ADAPTIVE) + "s exceeded acceptable range!"+ "\n" + "This can change step length");
            builder1.setCancelable(true);
            switchChecked = false;
            builder1.setPositiveButton(
                    "Restart Collection",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            isError = true;
                            //Concludes collection while letting the program know that there was an error in the data
                            aSwitch.setChecked(false);
                            dialog.cancel();
                        }
                    });
            AlertDialog alert11 = builder1.create();
            alert11.show();
            walkingTooFast = false;
        }
        else if(walkingTooSlow){
            AlertDialog.Builder builder1 = new AlertDialog.Builder(this);
            builder1.setMessage("User walking speed of " + String.format("%.3f",STEP_TIME_ADAPTIVE) + "s exceeded acceptable range!"+ "\n" + "This can change step length");
            builder1.setCancelable(true);
            switchChecked = false;
            builder1.setPositiveButton(
                    "Restart Collection",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            isError = true;
                            aSwitch.setChecked(false);
                            dialog.cancel();
                        }
                    });
            AlertDialog alert11 = builder1.create();
            alert11.show();
            walkingTooSlow = false;
        }
    }
    //Retrieves the user's step length and their average time per step. Writes to global variables
    public void getStepProfile() {
        try {
            Toast.makeText(getBaseContext(), "Reading Data", Toast.LENGTH_LONG).show();
            //save data
            File sdCard = Environment.getExternalStorageDirectory();
            File directory = new File(sdCard.getAbsolutePath() + "/DataCollectProfiles");
            directory.mkdirs();
            File file = new File(directory, USER_NAME + ".txt");
            FileReader fr = new FileReader(file);
            boolean readStepTime = false;
            boolean readSensitivity = false;
            int commaCount = 0;
            String stepTime = "";
            String fileInfo = "";
            String sensitivity = "";
            int intNextChar;
            char nextChar;
            //Numbers correspond the ASCII table values
            intNextChar = fr.read();
            while (intNextChar < 58 && intNextChar > 43) {
                if(intNextChar == ',' && !readStepTime){
                    commaCount++;
                    intNextChar = fr.read();
                    readStepTime = true;
                }
                if(intNextChar == ',' && !readSensitivity){
                    commaCount++;
                    intNextChar = fr.read();
                    readSensitivity = true;
                    readStepTime = false;
                }
                if(readStepTime && !readSensitivity){
                    nextChar = (char) intNextChar;
                    stepTime = stepTime + nextChar;
                    intNextChar = fr.read();
                }
                else if (readSensitivity) {
                    System.out.println("here");
                    nextChar = (char) intNextChar;
                    sensitivity = sensitivity + nextChar;
                    intNextChar = fr.read();
                }
                else {
                    nextChar = (char) intNextChar;
                    fileInfo = fileInfo + nextChar;
                    intNextChar = fr.read();
                }
            }
            //Global constants
            STEP_TIME_ORIGINAL = Double.parseDouble(stepTime);
            STEP_LENGTH = Double.parseDouble(fileInfo);
            SENSITIVITY = Float.parseFloat(sensitivity);


        } catch (Exception e) {
        }

    }
    void postScan(String serverURL, String outputInfo){
        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection httpURLConnection = null;
                try {
                    long startTime = System.nanoTime();
                    httpURLConnection = (HttpURLConnection) new URL(serverURL).openConnection();
                    httpURLConnection.setUseCaches(false);
                    httpURLConnection.setDoOutput(true);
                    httpURLConnection.setReadTimeout(3000);
                    httpURLConnection.setConnectTimeout(3000);
                    httpURLConnection.setRequestMethod("POST");
                    httpURLConnection.setRequestProperty("Content-Type", "application/json");
                    httpURLConnection.connect();
                    OutputStream out = httpURLConnection.getOutputStream();
                    BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(out));
                    bw.write(outputInfo);
                    bw.flush();
                    out.close();
                    bw.close();
                    InputStream in = httpURLConnection.getInputStream();
                    BufferedReader br = new BufferedReader(new InputStreamReader(in));
                    String str = null;
                    StringBuffer buffer = new StringBuffer();
                    while ((str = br.readLine()) != null) {
                        buffer.append(str);
                    }
                    in.close();
                    br.close();
                    //Flag variable to tell us that we are on the last thread call

                    long endTime = System.nanoTime();
                    Log.i("TIME TO POST:", Long.toString((endTime - startTime) / 1000000));

                    //latch.countDown();

                } catch (Exception e) {
                    serverError = false;
                    //latch.countDown();
                    e.printStackTrace();
                } finally {
                    if (httpURLConnection != null) {
                        httpURLConnection.disconnect();
                    }
                }

            }
        }).start();
    }
    void getPermissions(){
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED){
            if(ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)){
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1000);
                }
            }
            else{
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},1);

            }
        }
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED){
            if(ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_EXTERNAL_STORAGE)){
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 1000);
                }
            }
            else{
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},2);

            }
        }
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_WIFI_STATE) != PackageManager.PERMISSION_GRANTED){
            if(ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_WIFI_STATE)){
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    requestPermissions(new String[]{Manifest.permission.ACCESS_WIFI_STATE}, 1000);
                }
            }
            else{
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_WIFI_STATE},3);

            }
        }
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_NETWORK_STATE) != PackageManager.PERMISSION_GRANTED){
            if(ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_NETWORK_STATE)){
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    requestPermissions(new String[]{Manifest.permission.ACCESS_NETWORK_STATE}, 1000);
                }
            }
            else{
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_NETWORK_STATE},3);

            }
        }
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.CHANGE_NETWORK_STATE) != PackageManager.PERMISSION_GRANTED){
            if(ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CHANGE_NETWORK_STATE)){
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    requestPermissions(new String[]{Manifest.permission.CHANGE_NETWORK_STATE}, 1000);
                }
            }
            else{
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CHANGE_NETWORK_STATE},3);

            }
        }
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.CHANGE_WIFI_MULTICAST_STATE) != PackageManager.PERMISSION_GRANTED){
            if(ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CHANGE_WIFI_MULTICAST_STATE)){
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    requestPermissions(new String[]{Manifest.permission.CHANGE_WIFI_MULTICAST_STATE}, 1000);
                }
            }
            else{
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CHANGE_WIFI_MULTICAST_STATE},3);

            }
        }
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.CHANGE_WIFI_STATE) != PackageManager.PERMISSION_GRANTED){
            if(ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CHANGE_WIFI_STATE)){
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    requestPermissions(new String[]{Manifest.permission.CHANGE_WIFI_STATE}, 1000);
                }
            }
            else{
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CHANGE_WIFI_STATE},3);

            }
        }
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED){
            if(ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.INTERNET)){
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    requestPermissions(new String[]{Manifest.permission.INTERNET}, 1000);
                }
            }
            else{
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.INTERNET},3);

            }
        }
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED){
            if(ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)){
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1000);
                }
            }
            else{
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION},1);

            }
        }
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED){
            if(ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_COARSE_LOCATION)){
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 1000);
                }
            }
            else{
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},1);

            }
        }
    }
}