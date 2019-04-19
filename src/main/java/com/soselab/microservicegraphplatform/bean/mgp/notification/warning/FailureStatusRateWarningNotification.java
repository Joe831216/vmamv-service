package com.soselab.microservicegraphplatform.bean.mgp.notification.warning;

import com.soselab.microservicegraphplatform.bean.mgp.notification.WarningNotification;

public class FailureStatusRateWarningNotification extends WarningNotification {
    public static final String DATA_ACTUATOR = "Spring Actuator";
    public static final String DATA_ELASTICSEARCH = "Elasticsearch";
    public static final String THRESHOLD_USER = "user";
    public static final String THRESHOLD_SPC = "spc";

    private String appName;
    private String version;

    public FailureStatusRateWarningNotification() {
    }

    public FailureStatusRateWarningNotification(String appName, String version, Float value, Float threshold, String dataType, String thresholdType) {
        super("High failure status rate", createContent(appName, version, value, threshold, dataType, thresholdType));
        this.appName = appName;
        this.version = version;
    }

    private static String createContent(String appName, String version, Float value, Float threshold, String dataType, String thresholdType) {
        String content = "";
        if (thresholdType.equals(THRESHOLD_USER)) {
            content = "Service <strong>" + appName + ":" + version +
                    "</strong> exceeded the threshold of <strong>failure status rate</strong>: current value (" + dataType + ") = " +
                    value * 100 + "%, threshold = " + threshold + "%";
        } else if (thresholdType.equals(THRESHOLD_SPC)){
            content = "The <strong>failure status rate</strong> of service <strong>" + appName + ":" + version +
                    "</strong> exceeds the system's <strong>UCL</strong>: current value (" + dataType + ") = " +
                    value * 100 + "%, UCL = " + threshold + "%";
        }
        return content;
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
