package com.ilos.mitch.iloswifidatacollection;

import android.app.Activity;
import android.graphics.Color;
import android.os.Environment;
import android.os.Looper;
import android.os.SystemClock;
import android.text.method.PasswordTransformationMethod;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.widget.TextView;

import com.mapbox.mapboxsdk.annotations.Polyline;
import com.mapbox.mapboxsdk.annotations.PolylineOptions;
import com.mapbox.mapboxsdk.geometry.LatLng;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class RetrieveCollectedPaths {
    //Note that odd numbered entries will be starting points and even numbered entries will be end points
    public volatile List<LatLng> pointList = new ArrayList<>();
    public volatile List<Integer> taggedFloors = new ArrayList<>();
    //Poly line list for drawing paths
    public volatile List<PolylineOptions> polyLinesList = new ArrayList<>();
    //latch is used to make the main thread wait while the server thread executes
    public volatile CountDownLatch latch;
    public volatile boolean serverError = false;
    public List<LatLng[]> coordList = new ArrayList<>();
    public boolean emptyServer = false;
    String BUILDING_NAME;
    String FLOOR_NUM;
    List<LatLng> pdrPoints = new ArrayList<>();
    public volatile TextView loadingText;

    public RetrieveCollectedPaths(int type){
        //Called from the activity where the data collection paths are set
        if(type == 0) {
            getPoints("DataCollect");
        }
        //Called from the openCV step counter to draw paths where openCV foot data has been collected
        else if(type ==2){
            //Can either do blue or red here
            getPoints("GaitFootCoordsBlue");
        }
        else if(type == 3){
            getMultiPathPoints();
        }
    }
    public RetrieveCollectedPaths(String BUILDING_NAME, String FLOOR_NUM, int type){
        if(type == 0) {
            this.FLOOR_NUM = FLOOR_NUM;
            this.BUILDING_NAME = BUILDING_NAME;
            //Called when the user selects the checkbox to get the paths based on the server data
            pullFromServer(0);
        }
        else if (type == 1){
            this.FLOOR_NUM = FLOOR_NUM;
            this.BUILDING_NAME = BUILDING_NAME + "MULTIPATH";
            pullFromServer(1);
        }
    }
    void pullFromServer(int type){
        //Calls server thread then waits
        fetchFromMacQuestServer(type);
        latch = new CountDownLatch(1);
        try{
            latch.await();
        }
        catch(Exception e){}

    }
    void fetchFromMacQuestServer(int type){
        new Thread(new Runnable() {

            @Override
            public void run() {
                HttpURLConnection httpURLConnection = null;
                try {
                    //Note that one can only connect to macquest2 if they are on campus WiFi

                    httpURLConnection = (HttpURLConnection) new URL("http://macquest2.cas.mcmaster.ca/ilos/fp_query/").openConnection();
                    httpURLConnection.setDoOutput(true);
                    httpURLConnection.setRequestProperty("Content-Type", "application/json");
                    String postString = "";
                    postString = "{\"purpose\":\"trainingdata\",\"building\":\"" + BUILDING_NAME + "\",\"floor\":\"" + FLOOR_NUM + "\"}";

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
                    //One single JSON string
                    System.out.println("SERVER RESPONSE " + response);
                    //Length of which there is response from a empty server
                    if(response.length() == 24){
                        emptyServer = true;
                        latch.countDown();
                    }
                    else {
                        if (type == 0) {
                            String[] starts = response.split("startpos");
                            String[] ends = response.split("endpos");
                            //Disregard starts[0] and ends[1]
                            for (int i = 1; i < starts.length; i++) {
                                String start = (starts[i].substring(4, starts[i].indexOf("],")));
                                String end = (ends[i].substring(4, ends[i].indexOf("]}")));
                                LatLng[] latLngList = new LatLng[2];
                                latLngList[0] = new LatLng(Double.parseDouble(start.split(",")[0]), Double.parseDouble(start.split(",")[1].substring(0, start.split(",")[1].length() - 1)));
                                latLngList[1] = new LatLng(Double.parseDouble(end.split(",")[0]), Double.parseDouble(end.split(",")[1].substring(0, end.split(",")[1].length() - 1)));
                                coordList.add(latLngList);
                            }

                        } else if (type == 1) {

                            //Parses server info for multi turn paths
                            //For multipaths here
                            String[] firstCheckPoints = response.split("checkpoint");
                            for(int i = 1; i < firstCheckPoints.length; i++){
                                List<LatLng> coordPairs = new ArrayList<>();
                                String start = "[\"(";
                                String end = ")\"]";
                                String orderedCoords = firstCheckPoints[i].substring(firstCheckPoints[i].indexOf(start)+3, firstCheckPoints[i].indexOf(end));
                                String[] eachPoint = orderedCoords.split("\\)");
                                for(int j = 0; j < eachPoint.length; j++){
                                    System.out.println(eachPoint[j]);
                                    String coords;
                                    if(j != 0){
                                        coords = eachPoint[j].substring(5,eachPoint[j].length());
                                    }
                                    else{
                                        coords = eachPoint[j];
                                    }
                                    String[] latandLon = coords.split(",");
                                    coordPairs.add(new LatLng(Double.parseDouble(latandLon[0]), Double.parseDouble(latandLon[1])));
                                }
                                PolylineOptions polyItem = new PolylineOptions();
                                for(int j = 0; j < coordPairs.size(); j++){
                                    polyItem.add(coordPairs.get(j));
                                }
                                polyLinesList.add(polyItem);

                            }

                        }

                        latch.countDown();
                    }
                } catch (Exception e) {
                    serverError = false;
                    latch.countDown();
                    System.out.println(e.toString());
                }
            }
        }).start();
    }
    //Gets points based on filenames in local storage
    void getPoints(String fileSource){
        try {
            List<String> fileNames = new ArrayList<>();
            File sdCard = Environment.getExternalStorageDirectory();
            File[] directory = new File(sdCard.getAbsolutePath() + "/" + fileSource).listFiles();
            //Adds all file names to a list
            for (int i = 0; i < directory.length; i++) {
                if (directory[i].isFile() && directory[i].toString().indexOf("README")==-1) {
                    fileNames.add(directory[i].toString());
                }
            }
            //File names are formatted: BUILDING(startLat,startLon)to(endLat,endLon).txt
            for (int i = 0; i < fileNames.size(); i++) {
                String startPoint;
                String endPoint;
                int openBracket = fileNames.get(i).indexOf("(");
                int closeBracket = fileNames.get(i).indexOf(")");
                startPoint = fileNames.get(i).substring(openBracket + 1, closeBracket);
                endPoint = fileNames.get(i).substring(closeBracket + 4, fileNames.get(i).length() - 5);
                //0th index is lat, 1st index is lon
                String[] start = startPoint.split(",");
                String[] end = endPoint.split(",");
                taggedFloors.add(Integer.parseInt(Character.toString(fileNames.get(i).charAt(openBracket - 1))));
                taggedFloors.add(Integer.parseInt(Character.toString(fileNames.get(i).charAt(openBracket - 1))));
                pointList.add(new LatLng(Double.parseDouble(start[0]), Double.parseDouble(start[1])));
                pointList.add(new LatLng(Double.parseDouble(end[0]), Double.parseDouble(end[1])));
                //A single polyItem is added to two different numbered entries corresponding to the start and ending numbers
                PolylineOptions polyItem = new PolylineOptions();
                polyLinesList.add(polyItem);
                polyLinesList.add(polyItem);
            }
        }
        catch(Exception e){
            polyLinesList = null;
            pointList = null;
            taggedFloors = null;
        }
    }
    void getMultiPathPoints(){
        try {
            List<String> fileNames = new ArrayList<>();
            File sdCard = Environment.getExternalStorageDirectory();
            File[] directory = new File(sdCard.getAbsolutePath() + "/DataCollectPDRPath" ).listFiles();
            //Adds all file names to a list
            for (int i = 0; i < directory.length; i++) {
                if (directory[i].isFile() && directory[i].toString().indexOf("README")==-1) {
                    fileNames.add(directory[i].toString());
                }
            }
            //File names are formatted: BUILDING(startLat,startLon)to(endLat,endLon).txt
            for (int i = 0; i < fileNames.size(); i++) {
                int intNextChar;
                //Numbers correspond the ASCII table values
                FileReader fr = new FileReader(fileNames.get(i));
                intNextChar = fr.read();
                String fileInfo = "";
                boolean readLat = true;
                while (intNextChar < 58 && intNextChar > 43 || intNextChar == 10 || intNextChar == 12 || intNextChar == 13) {
                    fileInfo+=(char)intNextChar;
                    intNextChar = fr.read();
                }
                String[] lonLatItems = fileInfo.split("\r\n");
                List<LatLng> coordPairs = new ArrayList<>();
                for(int j = 0; j < lonLatItems.length; j++){
                    coordPairs.add(new LatLng(Double.parseDouble(lonLatItems[j].split(",")[0]), Double.parseDouble(lonLatItems[j].split(",")[1])));
                }
                PolylineOptions polyItem = new PolylineOptions();
                for(int j = 0; j < coordPairs.size(); j++){
                    polyItem.add(coordPairs.get(j));
                }
                polyLinesList.add(polyItem);
                int openBracket = fileNames.get(i).indexOf("(");
                taggedFloors.add(Integer.parseInt(Character.toString(fileNames.get(i).charAt(openBracket - 1))));


            }

        }
        catch(Exception e){
        }
    }
}
