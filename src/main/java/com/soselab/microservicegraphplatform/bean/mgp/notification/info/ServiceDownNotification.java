package com.soselab.microservicegraphplatform.bean.mgp.notification.info;

import com.soselab.microservicegraphplatform.bean.mgp.WebNotification;

import java.time.LocalDateTime;

public class ServiceDownNotification extends WebNotification {
    private String appName;
    private String version;

    public ServiceDownNotification() {
    }

    public ServiceDownNotification(String appName, String version) {
        super(WebNotification.LEVEL_INFO, "Service down", createContent(appName, version),
                createHtmlContent(appName, version), LocalDateTime.now());
        this.appName = appName;
        this.version = version;
    }

    private static String createContent(String appName, String version) {
        return "Service \"" + appName + " : " + version + "\" is down.";
    }

    private static String createHtmlContent(String appName, String version) {
        return "Service <strong>" + appName + " : " + version + "</strong> is down.";
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

}
