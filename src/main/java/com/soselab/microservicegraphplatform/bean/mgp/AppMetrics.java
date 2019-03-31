package com.soselab.microservicegraphplatform.bean.mgp;

public class AppMetrics {

    private int averageDuration;

    public AppMetrics() {
    }

    public AppMetrics(int averageDuration) {
        this.averageDuration = averageDuration;
    }

    public int getAverageDuration() {
        return averageDuration;
    }

    public void setAverageDuration(int averageDuration) {
        this.averageDuration = averageDuration;
    }
}
