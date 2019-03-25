package com.soselab.microservicegraphplatform.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    @GetMapping("/graph/dependent-chain/weak/{id}")
    public String getDependentChainWeak(@PathVariable("id") Long id) {
        return generalRepository.getDependentOnChainFromServiceUsingWeakAlgorithmById(id);
    }

    @GetMapping("/graph/be-dependent-chain/weak/{id}")
    public String getBeDependentChainWeak(@PathVariable("id") Long id) {
        return generalRepository.getBeDependentOnChainFromServiceUsingWeakAlgorithmById(id);
    }

    @GetMapping("/graph/dependent-chain/strong/{id}")
    public String getDependentChainStrong(@PathVariable("id") Long id) {
        return generalRepository.getDependentOnChainFromServiceUsingStrongAlgorithmById(id);
    }

    @GetMapping("/graph/be-dependent-chain/strong/{id}")
    public String getBeDependentChainStrong(@PathVariable("id") Long id) {
        return generalRepository.getBeDependentOnChainFromServiceUsingStrongAlgorithmById(id);
    }

    @MessageMapping("/graph/{systemName}")
    @SendTo("/topic/graph/{systemName}")
    public String getGraph(@DestinationVariable String systemName) throws Exception {
        return refreshScheduledTask.getGraphJson(systemName);
    }

    public void sendGraph(String systemName, String data) {
        messagingTemplate.convertAndSend("/topic/graph/" + systemName, data);
    }

}
