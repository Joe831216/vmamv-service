package com.soselab.microservicegraphplatform.bean.mgp;

public class WebNotification {

    private String title;
    private String content;

    public WebNotification() {
    }

    public WebNotification(String title, String content) {
        this.title = title;
        this.content = content;
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
