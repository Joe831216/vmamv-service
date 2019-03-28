package com.soselab.microservicegraphplatform.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.soselab.microservicegraphplatform.EurekaAndServicesRestTool;
import com.soselab.microservicegraphplatform.bean.mgp.WebNotification;
import com.soselab.microservicegraphplatform.repositories.GeneralRepository;
import com.soselab.microservicegraphplatform.tasks.RefreshScheduledTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
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
    private GeneralRepository generalRepository;
    @Autowired
    private RefreshScheduledTask refreshScheduledTask;
    @Autowired
    private EurekaAndServicesRestTool eurekaAndServicesRestTool;
    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    @Autowired
    private ObjectMapper mapper;


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

    @GetMapping("/graph/providers/{id}")
    public String getProviders(@PathVariable("id") Long id) {
        return generalRepository.getProviders(id);
    }

    @GetMapping("/graph/consumers/{id}")
    public String getComsumers(@PathVariable("id") Long id) {
        return generalRepository.getConsumers(id);
    }

    @GetMapping("/graph/strong-dependent-chain/{id}")
    public String getStrongDependencyChain(@PathVariable("id") Long id) {
        return generalRepository.getStrongDependencyChainById(id);
    }

    @GetMapping("/graph/weak-dependent-chain/{id}")
    public String getWeakDependencyChain(@PathVariable("id") Long id) {
        return generalRepository.getWeakDependencyChainById(id);
    }

    @GetMapping("/graph/strong-subordinate-chain/{id}")
    public String getStrongSubordinateChain(@PathVariable("id") Long id) {
        return generalRepository.getStrongSubordinateChainById(id);
    }

    @GetMapping("/graph/weak-subordinate-chain/{id}")
    public String getWeakSubordinateChain(@PathVariable("id") Long id) {
        return generalRepository.getWeakSubordinateChainById(id);
    }

    @GetMapping("/app/swagger/{appId}")
    public String getSwagger(@PathVariable("appId") String appId) {
        String[] appInfo = appId.split(":");
        return eurekaAndServicesRestTool.getSwaggerFromRemoteApp(appInfo[0], appInfo[1], appInfo[2]);
    }

    @MessageMapping("/graph/{systemName}")
    @SendTo("/topic/graph/{systemName}")
    public String getGraph(@DestinationVariable String systemName) throws Exception {
        return refreshScheduledTask.getGraphJson(systemName);
    }

    public void sendGraph(String systemName, String data) {
        messagingTemplate.convertAndSend("/topic/graph/" + systemName, data);
    }

    public void sendNotification(String systemName, WebNotification notification) {
        messagingTemplate.convertAndSend("/topic/notification/" + systemName, notification);
    }

}
