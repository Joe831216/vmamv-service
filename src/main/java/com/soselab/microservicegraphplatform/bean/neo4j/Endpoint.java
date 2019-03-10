package com.soselab.microservicegraphplatform.bean.neo4j;

import org.neo4j.ogm.annotation.GraphId;
import org.neo4j.ogm.annotation.Relationship;

import java.util.HashSet;
import java.util.Set;

public class Endpoint {

    @GraphId
    private Long id;

    private String endpointId;
    private String method;
    private String path;

    public Endpoint() {
    }

    public Endpoint(String method, String path) {
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
    public Microservice microservice;

    public void ownBy(Microservice microservice) {
        this.microservice = microservice;
    }

    public Microservice getOwner() {
        return microservice;
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
