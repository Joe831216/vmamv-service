package com.soselab.microservicegraphplatform.bean.mgp.notification.warning;

import com.soselab.microservicegraphplatform.bean.mgp.notification.WarningNotification;

public class FailureStatusRateWarningNotification extends WarningNotification {
    public static final String TYPE_ACTUATOR = "Spring Actuator";
    public static final String TYPE_ELASTICSEARCH = "Elasticsearch";

    private String appName;
    private String version;

    public FailureStatusRateWarningNotification() {
    }

    public FailureStatusRateWarningNotification(String appName, String version, Float value, Float threshold, String dataType) {
        super("High failure status rate", createContent(appName, version, value, threshold, dataType));
        this.appName = appName;
        this.version = version;
    }

    private static String createContent(String appName, String version, Float value, Float threshold, String dataType) {
        return "Service <strong>" + appName + ":" + version +
                "</strong> exceeded the threshold of <strong>failure status rate</strong>: current value (" + dataType + ") = " +
                value * 100 + "%, threshold = " + threshold + "%";
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
