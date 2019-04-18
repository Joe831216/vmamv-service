package com.soselab.microservicegraphplatform.bean.mgp.monitor;

import java.util.Map;

public class FailureStatusRateSPC {

    private float cl;
    private float ucl;
    private float lcl;
    private Map<String, Float> rates;

    public FailureStatusRateSPC() {
    }

    public FailureStatusRateSPC(float cl, float ucl, float lcl, Map<String, Float> rates) {
        this.cl = cl;
        this.ucl = ucl;
        this.lcl = lcl;
        this.rates = rates;
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

    public Map<String, Float> getRates() {
        return rates;
    }

    public void setRates(Map<String, Float> rates) {
        this.rates = rates;
    }

}
