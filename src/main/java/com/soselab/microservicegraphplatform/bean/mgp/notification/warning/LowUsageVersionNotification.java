package com.soselab.microservicegraphplatform.bean.mgp.notification.warning;

import com.soselab.microservicegraphplatform.bean.mgp.notification.WarningNotification;

public class LowUsageVersionNotification extends WarningNotification {
    private String appName;
    private String version;

    public LowUsageVersionNotification() {
    }

    public LowUsageVersionNotification(String appName, String version) {
        super("Low usage version", createContent(appName, version), createHtmlContent(appName, version));
        this.appName = appName;
        this.version = version;
    }

    private static String createContent(String appName, String version) {
        return "Found low-usage service version \"" + appName +":" + version + "\"";
    }

    private static String createHtmlContent(String appName, String version) {
        return "Found low-usage service version <strong>" + appName +":" + version + "</strong>";
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
