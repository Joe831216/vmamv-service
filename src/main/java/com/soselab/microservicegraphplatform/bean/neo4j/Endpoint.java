package com.soselab.microservicegraphplatform.bean.neo4j;

import org.neo4j.ogm.annotation.GraphId;
import org.neo4j.ogm.annotation.Relationship;

import java.util.HashSet;
import java.util.Set;

public class Endpoint {

    @GraphId
    private Long id;

    private String endpointId;
    private String appName;
    private String method;
    private String path;

    public Endpoint() {
    }

    public Endpoint(String appName, String method, String path) {
        this.appName = appName;
        this.method = method;
        this.path = path;
        this.endpointId = method + ":" + path;
    }

    public String getEndpointId() {
        return endpointId;
    }

    public void setEndpointId(String endpointId) {
        this.endpointId = endpointId;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    @Relationship(type = "OWN", direction = Relationship.INCOMING)
    public Service service;

    public void ownBy(Service service) {
        this.service = service;
    }

    public Service getOwner() {
        return service;
    }

    @Relationship(type = "HTTP_REQUEST", direction = Relationship.OUTGOING)
    public Set<Endpoint> httpRequestEndpoints;

    public void httpRequestToEndpoint(Endpoint endpoint) {
        if (httpRequestEndpoints == null) {
            httpRequestEndpoints = new HashSet<>();
        }
        httpRequestEndpoints.add(endpoint);
    }

    public Set<Endpoint> getHttpRequestEndpoints() {
        return httpRequestEndpoints;
    }

}
