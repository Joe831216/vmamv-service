package com.soselab.microservicegraphplatform.bean.mgp;

public class AppSetting {

    private Boolean enableRestFailureAlert;
    private Boolean enableLogFailureAlert;
    private Float failureStatusRate;
    private Long failureErrorCount;

    public AppSetting() {
    }

    public AppSetting(Boolean enableRestFailureAlert, Boolean enableLogFailureAlert, Float failureStatusRate, Long failureErrorCount) {
        this.enableRestFailureAlert = enableRestFailureAlert;
        this.enableLogFailureAlert = enableLogFailureAlert;
        this.failureStatusRate = failureStatusRate;
        this.failureErrorCount = failureErrorCount;
    }

    public Boolean getEnableRestFailureAlert() {
        return enableRestFailureAlert;
    }

    public void setEnableRestFailureAlert(Boolean enableRestFailureAlert) {
        this.enableRestFailureAlert = enableRestFailureAlert;
    }

    public Boolean getEnableLogFailureAlert() {
        return enableLogFailureAlert;
    }

    public void setEnableLogFailureAlert(Boolean enableLogFailureAlert) {
        this.enableLogFailureAlert = enableLogFailureAlert;
    }

    public Float getFailureStatusRate() {
        return failureStatusRate;
    }

    public void setFailureStatusRate(Float failureStatusRate) {
        this.failureStatusRate = failureStatusRate;
    }

    public Long getFailureErrorCount() {
        return failureErrorCount;
    }

    public void setFailureErrorCount(Long failureErrorCount) {
        this.failureErrorCount = failureErrorCount;
    }
}
