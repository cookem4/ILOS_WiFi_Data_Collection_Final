package com.ilos.mitch.iloswifidatacollection;

//TODO an interface for the step counter

public interface StepListener {
    //Interface for the pedometer
    void step(long timeNs);
}
