package com.soselab.microservicegraphplatform.bean.actuators.trace;

public class Trace {

    private Long timestamp;
    private TraceInfo info;
    private String timeTaken;

    public Trace() {
    }

    public Trace(Long timestamp, TraceInfo info, String timeTaken) {
        this.timestamp = timestamp;
        this.info = info;
        this.timeTaken = timeTaken;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    public TraceInfo getInfo() {
        return info;
    }

    public void setInfo(TraceInfo info) {
        this.info = info;
    }

    public String getTimeTaken() {
        return timeTaken;
    }

    public void setTimeTaken(String timeTaken) {
        this.timeTaken = timeTaken;
    }

}
