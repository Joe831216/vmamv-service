package com.soselab.microservicegraphplatform.bean.neo4j;

import org.neo4j.ogm.annotation.GraphId;
import org.neo4j.ogm.annotation.Relationship;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;

public class Microservice {

    @GraphId
    private Long id;

    private String appId;
    private String scsName;
    private String appName;
    private String version;

    public Microservice(){}

    public Microservice(@Nullable String scsName, String appName, @Nullable String version) {
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

    @Relationship(type = "REGISTER", direction = Relationship.OUTGOING)
    public ServiceRegistry serviceRegistry;

    public void registerTo(ServiceRegistry serviceRegistry) {
        this.serviceRegistry = serviceRegistry;
    }

    @Relationship(type = "OWN", direction = Relationship.OUTGOING)
    public Set<Endpoint> endpoints;

    public void ownEndpoint(Endpoint endpoint) {
        if (endpoints == null) {
            endpoints = new HashSet<>();
        }
        endpoints.add(endpoint);
    }

    public Set<Endpoint> getOwnEndpoints() {
        return endpoints;
    }

    @Relationship(type = "HTTP_REQUEST", direction = Relationship.OUTGOING)
    public Set<Endpoint> httpRequestEndpoints;

    public void httpRequestToEndpoint(Endpoint endpoint) {
        if (httpRequestEndpoints == null) {
            httpRequestEndpoints = new HashSet<>();
        }
        httpRequestEndpoints.add(endpoint);
    }

}
