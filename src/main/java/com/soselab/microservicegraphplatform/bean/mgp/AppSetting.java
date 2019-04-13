package com.soselab.microservicegraphplatform.bean.mgp;

public class AppSetting {

    private Boolean enableRestFailureAlert;
    private Boolean enableLogFailureAlert;
    private Float failureStatusRate;
    private Long failureErrorCount;
    private Boolean enableStrongDependencyAlert;
    private Integer strongUpperDependencyCount;
    private Integer strongLowerDependencyCount;
    private Boolean enableWeakDependencyAlert;
    private Integer weakUpperDependencyCount;
    private Integer weakLowerDependencyCount;

    public AppSetting() {
    }

    public AppSetting(Boolean enableRestFailureAlert, Boolean enableLogFailureAlert, Float failureStatusRate,
                      Long failureErrorCount, Boolean enableStrongDependencyAlert, Integer strongUpperDependencyCount,
                      Integer strongLowerDependencyCount, Boolean enableWeakDependencyAlert, Integer weakUpperDependencyCount,
                      Integer weakLowerDependencyCount) {
        this.enableRestFailureAlert = enableRestFailureAlert;
        this.enableLogFailureAlert = enableLogFailureAlert;
        this.failureStatusRate = failureStatusRate;
        this.failureErrorCount = failureErrorCount;
        this.enableStrongDependencyAlert = enableStrongDependencyAlert;
        this.strongUpperDependencyCount = strongUpperDependencyCount;
        this.strongLowerDependencyCount = strongLowerDependencyCount;
        this.enableWeakDependencyAlert = enableWeakDependencyAlert;
        this.weakUpperDependencyCount = weakUpperDependencyCount;
        this.weakLowerDependencyCount = weakLowerDependencyCount;
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

    public Boolean getEnableStrongDependencyAlert() {
        return enableStrongDependencyAlert;
    }

    public void setEnableStrongDependencyAlert(Boolean enableStrongDependencyAlert) {
        this.enableStrongDependencyAlert = enableStrongDependencyAlert;
    }

    public Integer getStrongUpperDependencyCount() {
        return strongUpperDependencyCount;
    }

    public void setStrongUpperDependencyCount(Integer strongUpperDependencyCount) {
        this.strongUpperDependencyCount = strongUpperDependencyCount;
    }

    public Integer getStrongLowerDependencyCount() {
        return strongLowerDependencyCount;
    }

    public void setStrongLowerDependencyCount(Integer strongLowerDependencyCount) {
        this.strongLowerDependencyCount = strongLowerDependencyCount;
    }

    public Boolean getEnableWeakDependencyAlert() {
        return enableWeakDependencyAlert;
    }

    public void setEnableWeakDependencyAlert(Boolean enableWeakDependencyAlert) {
        this.enableWeakDependencyAlert = enableWeakDependencyAlert;
    }

    public Integer getWeakUpperDependencyCount() {
        return weakUpperDependencyCount;
    }

    public void setWeakUpperDependencyCount(Integer weakUpperDependencyCount) {
        this.weakUpperDependencyCount = weakUpperDependencyCount;
    }

    public Integer getWeakLowerDependencyCount() {
        return weakLowerDependencyCount;
    }

    public void setWeakLowerDependencyCount(Integer weakLowerDependencyCount) {
        this.weakLowerDependencyCount = weakLowerDependencyCount;
    }
}
