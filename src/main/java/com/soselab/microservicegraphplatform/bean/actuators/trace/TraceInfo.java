package com.soselab.microservicegraphplatform.bean.actuators.trace;

public class TraceInfo {

    private String method;
    private String path;
    private Headers headers;

    public TraceInfo() {
    }

    public TraceInfo(String method, String path, Headers headers) {
        this.method = method;
        this.path = path;
        this.headers = headers;
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

    public Headers getHeaders() {
        return headers;
    }

    public void setHeaders(Headers headers) {
        this.headers = headers;
    }

}
