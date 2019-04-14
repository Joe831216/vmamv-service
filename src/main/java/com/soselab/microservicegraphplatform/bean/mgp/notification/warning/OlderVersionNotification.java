package com.soselab.microservicegraphplatform.bean.mgp.notification.warning;

import com.soselab.microservicegraphplatform.bean.mgp.notification.WarningNotification;

public class OlderVersionNotification extends WarningNotification {
    public static final String LEVEL_MAJOR = "major";
    public static final String LEVEL_MINOR  = "minor";
    public static final String LEVEL_PATCH   = "patch";

    private String appName;
    private String version;

    public OlderVersionNotification() {
    }

    public OlderVersionNotification(String appName, String version, String olderVersion, String level) {
        super("Found older version", createContent(appName, version, olderVersion, level));
        this.appName = appName;
        this.version = version;
    }

    private static String createContent(String appName, String version, String olderVersion, String level) {
        return "Found older " + level + " version of service: <strong>" + appName + ":" + version + " → " + olderVersion + "</strong>";
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
