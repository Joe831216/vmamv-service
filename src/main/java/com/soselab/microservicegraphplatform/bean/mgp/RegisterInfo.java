package com.soselab.microservicegraphplatform.bean.mgp;

import java.util.ArrayList;

public class RegisterInfo {

    private ArrayList<Application> applications;

    public RegisterInfo() {
    }

    public RegisterInfo(ArrayList<Application> applications) {
        this.applications = applications;
    }

    public ArrayList<Application> getApplications() {
        return applications;
    }

    public void setApplications(ArrayList<Application> applications) {
        this.applications = applications;
    }

}
