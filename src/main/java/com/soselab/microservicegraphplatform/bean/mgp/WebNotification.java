package com.soselab.microservicegraphplatform.bean.mgp;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class WebNotification {

    @JsonIgnore
    public static final String LEVEL_INFO = "info";
    @JsonIgnore
    public static final String LEVEL_WARNING = "warning";
    @JsonIgnore
    public static final String LEVEL_ERROR = "error";
    private String level;
    private String title;
    private String content;

    public WebNotification() {
    }

    public WebNotification(String level, String title, String content) {
        this.level = level;
        this.title = title;
        this.content = content;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

}
