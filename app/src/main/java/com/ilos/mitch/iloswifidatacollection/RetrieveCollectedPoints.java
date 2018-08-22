package com.ilos.mitch.iloswifidatacollection;

import android.os.Environment;
import android.util.Base64;
import android.util.Log;

import com.mapbox.mapboxsdk.annotations.PolylineOptions;
import com.mapbox.mapboxsdk.geometry.LatLng;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class RetrieveCollectedPoints {
    //Note that odd numbered entries will be starting points and even numbered entries will be end points
    public volatile List<LatLng> pointList = new ArrayList<>();
    public volatile List<Integer> taggedFloors = new ArrayList<>();
    public volatile boolean serverError = false;
    public volatile CountDownLatch latch;
    public List<PastScan> scanList = new ArrayList<>();
    public  boolean emptyServer = false;
    String BUILDING_NAME;
    String FLOOR_NUM;
    RetrieveCollectedPoints(String BUILDING_NAME, String FLOOR_NUM){
        this.BUILDING_NAME = BUILDING_NAME;
        this.FLOOR_NUM = FLOOR_NUM;
        //Gets points based on server data
        pullFromServer();
    }
    RetrieveCollectedPoints(){
        //Gets points based on phone's storage
        getPoints();
    }
    void pullFromServer(){
        //Retrieves points while the main thread waits for it to execute
        fetchFromMacQuestServer();
        latch = new CountDownLatch(1);
        try{
            latch.await();
        }
        catch(Exception e){}

    }
    void fetchFromMacQuestServer(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection httpURLConnection = null;
                try {
                    httpURLConnection = (HttpURLConnection) new URL("http://macquest2.cas.mcmaster.ca/ilos/fp_query_point/").openConnection();
                    httpURLConnection.setDoOutput(true);
                    //httpURLConnection.setRequestMethod("POST");
                    httpURLConnection.setRequestProperty("Content-Type", "application/json");
                    //httpURLConnection.connect();
                    //utputStream out = httpURLConnection.getOutputStream();
                    //BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(out));
                    String postString = "{\"purpose\":\"evadata\",\"building\":\""+ BUILDING_NAME + "\",\"floor\":\""+ FLOOR_NUM +"\"}";
                    System.out.println(postString);
                    OutputStream outputStream = new BufferedOutputStream(httpURLConnection.getOutputStream());
                    BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream));
                    writer.write(postString);
                    writer.flush();
                    writer.close();
                    outputStream.close();

                    InputStream inputStream;
                    // get stream
                    if (httpURLConnection.getResponseCode() < HttpURLConnection.HTTP_BAD_REQUEST) {
                        inputStream = httpURLConnection.getInputStream();
                    } else {
                        inputStream = httpURLConnection.getErrorStream();
                    }
                    // parse stream
                    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
                    String temp, response = "";
                    while ((temp = bufferedReader.readLine()) != null) {
                        response += temp;
                    }
                    System.out.println(response);
                    System.out.println(response.length());
                    //Empty server response
                    if(response.length() == 24){
                        emptyServer = true;
                    }
                    else{
                        String[] entries = response.split("loc");
                        for(int i = 1; i < entries.length; i++){
                            System.out.println(entries[i]);
                            int end = entries[i].indexOf("],");
                            String lonLatString = entries[i].substring(4, end);
                            System.out.println(lonLatString);
                            double lat = Double.parseDouble(lonLatString.split(",")[0]);
                            double lon = Double.parseDouble(lonLatString.split(",")[1]);
                            PastScan scan = new PastScan(0, lat, lon);
                            scanList.add(scan);
                        }
                        /*
                        int startIndex = response.indexOf("[[");
                        int endIndex = response.indexOf("]]}");
                        response = response.substring(startIndex, endIndex);
                        String[] entries = response.split("]");
                        for (int i = 0; i < entries.length; i++) {
                            if (entries[i].indexOf("[[") == -1) {
                                int start = entries[i].indexOf("[");
                                entries[i] = entries[i].substring(start + 1);
                            } else {
                                int start = entries[i].indexOf("[[");
                                entries[i] = entries[i].substring(start + 2);
                            }
                        }
                        for (String entry : entries) {
                            String[] info = entry.split(",");
                            if (info[0].length() > 1 && info[1].length() > 1 && info[2].length() > 1) {
                                int level = (int)Double.parseDouble(info[2].substring(2, info[2].length() - 1));
                                double lat = Double.parseDouble(info[0].substring(1, info[0].length() - 1));
                                double lon = Double.parseDouble(info[1].substring(2, info[1].length() - 1));
                                PastScan scan = new PastScan(level, lat, lon);
                                scanList.add(scan);
                            }
                        }
                        */
                    }

                    latch.countDown();

                } catch (Exception e) {
                    serverError = true;
                    latch.countDown();
                    System.out.println(e.toString());
                }
            }
        }).start();
    }
    //Retrieves points from phones storage based on the title of the text file
    void getPoints(){
        try {
            List<String> fileNames = new ArrayList<>();
            File sdCard = Environment.getExternalStorageDirectory();
            File[] directory = new File(sdCard.getAbsolutePath() + "/DataCollectSinglePoint").listFiles();
            //Adds all file names to a list
            for (int i = 0; i < directory.length; i++) {
                if (directory[i].isFile() && directory[i].toString().indexOf("README")==-1) {
                    fileNames.add(directory[i].toString());
                }
            }
            //File names are formatted: BUILDING(lat,lon).txt
            for (int i = 0; i < fileNames.size(); i++) {
                String point;
                int openBracket = fileNames.get(i).indexOf("(");
                int closeBracket = fileNames.get(i).indexOf(")");
                point = fileNames.get(i).substring(openBracket + 1, closeBracket);
                //0th index is lat, 1st index is lon
                String[] latAndLon = point.split(",");
                taggedFloors.add(Integer.parseInt(Character.toString(fileNames.get(i).charAt(openBracket - 1))));
                pointList.add(new LatLng(Double.parseDouble(latAndLon[0]), Double.parseDouble(latAndLon[1])));
            }
        }
        catch(Exception e){
            pointList = null;
            taggedFloors = null;
        }
    }
}
