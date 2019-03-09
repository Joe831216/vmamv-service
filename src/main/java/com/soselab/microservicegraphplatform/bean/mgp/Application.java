package com.soselab.microservicegraphplatform.bean.mgp;

import java.util.ArrayList;

public class Application {

    private String appId;
    private String scsName;
    private String appName;
    private String version;
    private ArrayList<MgpInstance> instances;

    public Application() {
    }

    public Application(String scsName, String appName, String version, ArrayList<MgpInstance> instances) {
        this.appId = scsName + ":" + appName + ":" + version;
        this.scsName = scsName;
        this.appName = appName;
        this.version = version;
        this.instances = instances;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getScsName() {
        return scsName;
    }

    public void setScsName(String scsName) {
        this.scsName = scsName;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public ArrayList<MgpInstance> getInstances() {
        return instances;
    }

    public void setInstances(ArrayList<MgpInstance> instances) {
        this.instances = instances;
    }

}
