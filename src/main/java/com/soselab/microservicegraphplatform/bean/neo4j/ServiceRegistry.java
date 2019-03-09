package com.soselab.microservicegraphplatform.bean.neo4j;

import org.neo4j.ogm.annotation.GraphId;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Relationship;

import java.util.HashSet;
import java.util.Set;

@NodeEntity
public class ServiceRegistry {

    @GraphId
    private Long id;

    private String appId;
    private String scsName;
    private String appName;
    private String version;

    public ServiceRegistry() {}

    public ServiceRegistry(String scsName, String appName, String version) {
        this.appId = scsName + ":" + appName + ":" + version;
        this.scsName = scsName;
        this.appName = appName;
        this.version = version;
    }

    public Long getId() {
        return id;
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

    @Relationship(type = "OWN", direction = Relationship.OUTGOING)
    public Set<Instance> instances;

    public void ownInstance(Instance instance) {
        if (instances == null) {
            instances = new HashSet<>();
        }
        instances.add(instance);
    }

}
