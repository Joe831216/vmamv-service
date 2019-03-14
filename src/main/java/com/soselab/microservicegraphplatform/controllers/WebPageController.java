package com.soselab.microservicegraphplatform.controllers;

import com.soselab.microservicegraphplatform.tasks.RefreshScheduledTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/web-page")
public class WebPageController {

    private static final Logger logger = LoggerFactory.getLogger(WebPageController.class);

    @Autowired
    RefreshScheduledTask refreshScheduledTask;

    @GetMapping("/graph")
    public String getGraph() {
        return refreshScheduledTask.getGraphJson();
    }

}
