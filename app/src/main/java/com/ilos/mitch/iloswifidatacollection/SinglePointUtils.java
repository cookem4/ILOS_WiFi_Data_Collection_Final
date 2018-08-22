package com.ilos.mitch.iloswifidatacollection;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.SensorManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.mapbox.mapboxsdk.geometry.LatLng;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class SinglePointUtils extends AppCompatActivity {
    public static double collectionLat;
    public static double collectionLong;
    public static int FLOOR_NUMBER;
    public static String BUILDING_NAME;
    public static boolean doServerUpload;
    Switch aSwitch;
    TextView pointDataText;
    TextView wifiText;
    volatile boolean switchChecked = false;
    Handler mRepeatHandler;
    WifiManager wifiManager;
    volatile List<String> outputData = new ArrayList<>();
    volatile int clickCounter = 0;
    public volatile boolean pulling = true;
    volatile String titleNum = "1";
    //Max number of scans before stopping collection
    int maxClicks = 30;
    public volatile long globalStart;
    public volatile long globalEnd;
    HashMap<String,String> scanInfo = new HashMap<>();
    List<Long> broadCastTimeList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_single_point_utils);
        getPermissions();
        pointDataText = (TextView)findViewById(R.id.pointText);
        wifiText = findViewById(R.id.wifiText);
        aSwitch = (Switch)findViewById(R.id.startSwitch);
        android.support.v7.app.ActionBar actionBar = getSupportActionBar();
        actionBar.setTitle("Single Point Collection");
        mRepeatHandler = new Handler();
        aSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, final boolean isChecked) {
                aSwitch.setClickable(false);
                if(!isChecked){
                    //Once switch becomes unchecked after data is collected
                    saveData();
                    if(doServerUpload){
                        prepToPost();
                    }
                    switchChecked = false;
                }
                else{
                    //When switch is flipped to begin collection
                    switchChecked = true;
                    makeWifiThread();
                }
            }
        });
    }
    void makeWifiThread(){
        //Separate thread for collecting wifi data
        Activity activity = this;
        new Thread(new Runnable() {
            @Override
            public void run() {
                globalStart = System.currentTimeMillis();
                wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                setFrequencyBand2Hz(true, wifiManager);
                WifiManager.WifiLock wifilock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_SCAN_ONLY,"MyLock");
                wifilock.setReferenceCounted(true);
                wifilock.acquire();
                if(!wifilock.isHeld()) {

                    wifilock.acquire();
                }
                checkToReRegister();
                broadCastTimeList.add(SystemClock.elapsedRealtimeNanos());
                //Broadcast receiver updates when new wifi information is received
                activity.registerReceiver(cycleWifiReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
            }
        }).start();

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
                        if(switchChecked){
                            Thread.sleep(1000);
                            long lastTime = (broadCastTimeList.get(broadCastTimeList.size()-1)) - broadCastTimeList.get(broadCastTimeList.size()-2);
                            //Will re-register if time exceeds twice the past broadcast time
                            if(SystemClock.elapsedRealtimeNanos() - broadCastTimeList.get(broadCastTimeList.size()-1) > lastTime*2) {
                                activity.unregisterReceiver(cycleWifiReceiver);
                                wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                                setFrequencyBand2Hz(true, wifiManager);
                                WifiManager.WifiLock wifilock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_SCAN_ONLY, "MyLock");
                                wifilock.setReferenceCounted(true);
                                wifilock.acquire();
                                if (!wifilock.isHeld()) {
                                    wifilock.acquire();
                                }
                                activity.registerReceiver(cycleWifiReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
                            }
                        }
                    }
                    catch(Exception e){}

                }
            }
        }).start();
    }
    //Very important to receive and add all of the wifi information to lists & manage UI output based on info received
    private final BroadcastReceiver cycleWifiReceiver = new BroadcastReceiver() {
        @SuppressLint("UseSparseArrays")
        @Override
        public void onReceive(Context context, Intent intent) {
            //Click counter is used to track the number of scans that have occured
            clickCounter++;
            wifiManager.startScan();
            broadCastTimeList.add(SystemClock.elapsedRealtimeNanos());
            if (wifiManager.isWifiEnabled()) {
                StringBuffer stringBuffer = new StringBuffer();
                //List contains all the information for each access point
                List<ScanResult> list = wifiManager.getScanResults();
                long tsLong = System.currentTimeMillis();
                for (ScanResult scanResult : list) {
                    //Adds all the wifi information from each acceess point to a list of strings that is later used for output
                    outputData.add("SCAN#" + Integer.toString(clickCounter) + "," + Long.toString(tsLong) + "," + Double.toString(collectionLat) + "," + Double.toString(collectionLong) + "," + BUILDING_NAME + FLOOR_NUMBER + "," + scanResult.BSSID + "," + scanResult.SSID + "," + scanResult.level);
                    //Used for output to UI
                    stringBuffer.append(scanResult.SSID + "     " + scanResult.BSSID + "     " + scanResult.level + "\n");

                    //Does not exist as dictionary item yet
                    if(scanInfo.keySet().toString().indexOf(scanResult.BSSID)==-1){
                        scanInfo.put(scanResult.BSSID, (Double.toString(scanResult.level) + "]"));
                    }
                    else{
                        String infoAlreadyIn = scanInfo.get(scanResult.BSSID);
                        scanInfo.put(scanResult.BSSID, (Double.toString(scanResult.level) + "," + infoAlreadyIn));
                    }
                }
                //Posts the data
                //Following runonnUI threads simply update text on the screen with the different routers that information has been accessed from
                if (clickCounter < maxClicks) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            pointDataText.setText("Collecting " + Integer.toString(maxClicks) + " scans for best results!" + "\n\n" + "Scan #" + (clickCounter) + ":\t" + list.size() + " networks scanned " + "\n" + BUILDING_NAME + " " + FLOOR_NUMBER + "\n\n" + stringBuffer);
                        }
                    });
                } else { //If we are done collecting data
                    if (doServerUpload) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                pointDataText.setText("Data Stored on Server!" + "\n" + "Press back to select a new point of data collection");
                            }
                        });
                    } else {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                pointDataText.setText("Data Stored!" + "\n" + "Press back to select a new point of data collection");
                            }
                        });
                    }
                    aSwitch.setChecked(false);
                    switchChecked = false;
                }
            }
            else{
                aSwitch.setChecked(false);
            }
        }
    };
    void prepToPost(){
        WifiManager manager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        android.net.wifi.WifiInfo info = manager.getConnectionInfo();
        String address = info.getMacAddress();

        String finalPostString = "";
        finalPostString += "{";
        finalPostString+="\"phonemacid\":\"";
        finalPostString+= address;
        finalPostString += "\",\"collector\":\"";
        finalPostString += "none";
        finalPostString += "\",\"purpose\":\"";
        finalPostString += "evadata\"";
        finalPostString += ",\"building\":\"";
        finalPostString = finalPostString + BUILDING_NAME;
        finalPostString += "\",\"floor\":\"";
        finalPostString += FLOOR_NUMBER;
        finalPostString += "\",\"loc\":";
        finalPostString = finalPostString + "[" + Double.toString(collectionLat) + "," + Double.toString(collectionLong) + "]";
        finalPostString += ",\"fp\":";
        finalPostString += "{";
        String[] macIDs = scanInfo.keySet().toString().substring(1, scanInfo.keySet().toString().length() - 1).split(",");
        String fpString = "";
        for (String macID : macIDs) {
            macID = macID.replaceAll("\\s+", "");
            fpString = fpString + "\"" + macID + "\":[" + scanInfo.get(macID) + ",";
        }
        fpString = fpString.substring(0, fpString.length() - 1);
        finalPostString += fpString;
        finalPostString += "}}";
        System.out.println(finalPostString);
        postScan("http://macquest2.cas.mcmaster.ca/ilos/fp_upload_point/", finalPostString);
    }
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
                //TODO make this 2 for 2.4GHz
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

    //Saves the data in a text file titled with the point at which data was collected. Needed to later plot out points where data has already been collected
    void saveData(){
        try {
            //save data
            File sdCard = Environment.getExternalStorageDirectory();
            File directory = new File(sdCard.getAbsolutePath() + "/DataCollectSinglePoint");
            Log.i("Save Dir", sdCard.getAbsolutePath() + "/DataCollectSinglePoint");
            directory.mkdirs();
            String filename = BUILDING_NAME + FLOOR_NUMBER + "(" + collectionLat + "," + collectionLong + ")" + ".txt";
            File file = new File(directory, filename);
            PrintWriter out = new PrintWriter(file);
            for (int i = 0; i<outputData.size();i++) {
                out.write(outputData.get(i));
                out.write("\r\n");
            }
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    //This function just posts the start and end coordinates to the server
    public void savePointData(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                String data = "";
                HttpURLConnection httpURLConnection = null;
                String pathDataServer = "http://18.188.107.179:8000/point-data/";
                try {
                    long startTime = System.nanoTime();
                    httpURLConnection = (HttpURLConnection) new URL(pathDataServer).openConnection();
                    httpURLConnection.setUseCaches(false);
                    httpURLConnection.setDoOutput(true);
                    httpURLConnection.setReadTimeout(3000);
                    httpURLConnection.setConnectTimeout(3000);
                    httpURLConnection.setRequestMethod("POST");
                    httpURLConnection.setRequestProperty("Content-Type", "application/json");
                    httpURLConnection.connect();
                    DataOutputStream wr = new DataOutputStream(httpURLConnection.getOutputStream());
                    wr.writeBytes("{\"floor_number\":"+FLOOR_NUMBER +
                            ",\"latitude\":" + Double.toString(collectionLat) +
                            ",\"longitude\":" + Double.toString(collectionLong)+
                            "}");
                    wr.flush();
                    wr.close();

                    InputStream in = httpURLConnection.getInputStream();
                    InputStreamReader inputStreamReader = new InputStreamReader(in);
                    int inputStreamData = inputStreamReader.read();
                    while (inputStreamData != -1) {
                        char current = (char) inputStreamData;
                        inputStreamData = inputStreamReader.read();
                        data += current;
                    }
                    long endTime = System.nanoTime();
                    Log.i("TIME TO POST:", Long.toString((endTime-startTime)/1000000));
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    if (httpURLConnection != null) {
                        httpURLConnection.disconnect();
                    }
                }
            }
        }).start();
    }
    //Used to upload to the server
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
                    if(httpURLConnection.getResponseCode() == HttpURLConnection.HTTP_CREATED || httpURLConnection.getResponseCode() == HttpURLConnection.HTTP_CREATED){
                        InputStream in = httpURLConnection.getInputStream();
                        BufferedReader br = new BufferedReader(new InputStreamReader(in));
                        String str = null;
                        StringBuffer buffer = new StringBuffer();
                        while ((str = br.readLine()) != null) {
                            buffer.append(str);
                        }
                        in.close();
                        br.close();
                    }
                    //Flag variable to tell us that we are on the last thread call

                    long endTime = System.nanoTime();
                    Log.i("TIME TO POST:", Long.toString((endTime - startTime) / 1000000));
                } catch (Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            pointDataText.setText("ERROR UPLOADING TO SERVER!");
                        }
                    });
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
