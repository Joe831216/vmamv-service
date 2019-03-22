package com.soselab.microservicegraphplatform.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.soselab.microservicegraphplatform.repositories.GeneralRepository;
import com.soselab.microservicegraphplatform.tasks.RefreshScheduledTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/web-page")
public class WebPageController {

    private static final Logger logger = LoggerFactory.getLogger(WebPageController.class);

    @Autowired
    private RefreshScheduledTask refreshScheduledTask;
    @Autowired
    private GeneralRepository generalRepository;

    private ObjectMapper mapper = new ObjectMapper();

    @GetMapping("/system-names")
    public String getSystems() {
        List<String> sysNames = generalRepository.getAllSystemName();
        String result = null;
        try {
            result = mapper.writeValueAsString(sysNames);
        } catch (JsonProcessingException e) {
            logger.error(e.getMessage(), e);
        }
        return result;
    }

    /*
    @GetMapping("/graph")
    public String getGraph() {
        return refreshScheduledTask.getGraphJson();
    }
    */

    @GetMapping("/graph/{systemName}")
    public String getSystemGraph(@PathVariable("systemName") String systemName) {
        return refreshScheduledTask.getGraphJson(systemName);
    }

}
