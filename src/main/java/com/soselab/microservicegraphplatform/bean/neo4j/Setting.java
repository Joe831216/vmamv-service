package com.soselab.microservicegraphplatform.bean.neo4j;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.neo4j.ogm.annotation.GraphId;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Relationship;

import java.util.Set;

@NodeEntity
public class Setting {

    @GraphId
    private Long id;

    private boolean enableRestFailureAlert;
    private boolean enableLogFailureAlert;
    private float failureStatusRate;
    private long failureErrorCount;

    public Setting() {
    }

    public Setting(boolean enableRestFailureAlert, boolean enableLogFailureAlert, float failureStatusRate, long failureErrorCount) {
        this.enableRestFailureAlert = enableRestFailureAlert;
        this.enableLogFailureAlert = enableLogFailureAlert;
        this.failureStatusRate = failureStatusRate;
        this.failureErrorCount = failureErrorCount;
    }

    public Long getId() {
        return id;
    }

    public boolean isEnableRestFailureAlert() {
        return enableRestFailureAlert;
    }

    public void setEnableRestFailureAlert(boolean enableRestFailureAlert) {
        this.enableRestFailureAlert = enableRestFailureAlert;
    }

    public boolean isEnableLogFailureAlert() {
        return enableLogFailureAlert;
    }

    public void setEnableLogFailureAlert(boolean enableLogFailureAlert) {
        this.enableLogFailureAlert = enableLogFailureAlert;
    }

    public float getFailureStatusRate() {
        return failureStatusRate;
    }

    public void setFailureStatusRate(float failureStatusRate) {
        this.failureStatusRate = failureStatusRate;
    }

    public long getFailureErrorCount() {
        return failureErrorCount;
    }

    public void setFailureErrorCount(long failureErrorCount) {
        this.failureErrorCount = failureErrorCount;
    }

    @Relationship(type = "MGP_CONFIG", direction = Relationship.INCOMING)
    private Service configService;

    public Service getConfigService() {
        return configService;
    }

    public void setConfigService(Service configService) {
        this.configService = configService;
    }

}
