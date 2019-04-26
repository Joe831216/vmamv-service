package com.soselab.microservicegraphplatform.bean.mgp.monitor;

import java.util.Map;

public class SpcData {

    private float cl;
    private float ucl;
    private float lcl;
    private Map<String, Float> values;
    private String valueName;
    private String samplingName;

    public SpcData() {
    }

    public SpcData(float cl, float ucl, float lcl, Map<String, Float> values, String valueName, String samplingName) {
        this.cl = cl;
        this.ucl = ucl;
        this.lcl = lcl;
        this.values = values;
        this.valueName = valueName;
        this.samplingName = samplingName;
    }

    public float getCl() {
        return cl;
    }

    public void setCl(float cl) {
        this.cl = cl;
    }

    public float getUcl() {
        return ucl;
    }

    public void setUcl(float ucl) {
        this.ucl = ucl;
    }

    public float getLcl() {
        return lcl;
    }

    public void setLcl(float lcl) {
        this.lcl = lcl;
    }

    public Map<String, Float> getValues() {
        return values;
    }

    public void setValues(Map<String, Float> values) {
        this.values = values;
    }

    public String getValueName() {
        return valueName;
    }

    public void setValueName(String valueName) {
        this.valueName = valueName;
    }

    public String getSamplingName() {
        return samplingName;
    }

    public void setSamplingName(String samplingName) {
        this.samplingName = samplingName;
    }
}
