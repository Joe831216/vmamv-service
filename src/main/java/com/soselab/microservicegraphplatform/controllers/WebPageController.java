package com.soselab.microservicegraphplatform.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.soselab.microservicegraphplatform.SpringRestTool;
import com.soselab.microservicegraphplatform.bean.mgp.AppSetting;
import com.soselab.microservicegraphplatform.bean.neo4j.Service;
import com.soselab.microservicegraphplatform.bean.neo4j.Setting;
import com.soselab.microservicegraphplatform.repositories.neo4j.ServiceRepository;
import com.soselab.microservicegraphplatform.repositories.neo4j.SettingRepository;
import com.soselab.microservicegraphplatform.services.LogAnalyzer;
import com.soselab.microservicegraphplatform.services.MonitorService;
import com.soselab.microservicegraphplatform.bean.mgp.AppMetrics;
import com.soselab.microservicegraphplatform.bean.mgp.WebNotification;
import com.soselab.microservicegraphplatform.repositories.neo4j.GeneralRepository;
import com.soselab.microservicegraphplatform.services.RefreshScheduledTask;
import com.soselab.microservicegraphplatform.services.RestInfoAnalyzer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/web-page")
public class WebPageController {

    private static final Logger logger = LoggerFactory.getLogger(WebPageController.class);

    @Autowired
    private GeneralRepository generalRepository;
    @Autowired
    private ServiceRepository serviceRepository;
    @Autowired
    private SettingRepository settingRepository;
    @Autowired
    private RefreshScheduledTask refreshScheduledTask;
    @Autowired
    private SpringRestTool springRestTool;
    @Autowired
    private MonitorService monitorService;
    @Autowired
    private LogAnalyzer logAnalyzer;
    @Autowired
    private RestInfoAnalyzer restInfoAnalyzer;
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

    @GetMapping("/graph/strong-upper-dependency-chain/{id}")
    public String getStrongDependencyChain(@PathVariable("id") Long id) {
        return generalRepository.getStrongUpperDependencyChainById(id);
    }

    @GetMapping("/graph/weak-upper-dependency-chain/{id}")
    public String getWeakDependencyChain(@PathVariable("id") Long id) {
        return generalRepository.getWeakUpperDependencyChainById(id);
    }

    @GetMapping("/graph/strong-lower-dependency-chain/{id}")
    public String getStrongSubordinateChain(@PathVariable("id") Long id) {
        return generalRepository.getStrongLowerDependencyChainById(id);
    }

    @GetMapping("/graph/weak-lower-dependency-chain/{id}")
    public String getWeakSubordinateChain(@PathVariable("id") Long id) {
        return generalRepository.getWeakLowerDependencyChainById(id);
    }

    @GetMapping("/app/swagger/{appId}")
    public String getSwagger(@PathVariable("appId") String appId) {
        String[] appInfo = appId.split(":");
        return springRestTool.getSwaggerFromRemoteApp(appInfo[0], appInfo[1], appInfo[2]);
    }

    @GetMapping("/app/metrics/log/{appId}")
    public AppMetrics getLogMetrics(@PathVariable("appId") String appId) {
        String[] appInfo = appId.split(":");
        return logAnalyzer.getMetrics(appInfo[0], appInfo[1], appInfo[2]);
    }

    @GetMapping("/app/metrics/rest/{appId}")
    public AppMetrics getRestMetrics(@PathVariable("appId") String appId) {
        String[] appInfo = appId.split(":");
        return restInfoAnalyzer.getMetrics(appInfo[0], appInfo[1], appInfo[2]);
    }

    @GetMapping("/app/setting/{appId}")
    public AppSetting getSetting(@PathVariable("appId") String appId) {
        Setting settingNode = settingRepository.findByConfigServiceAppId(appId);
        AppSetting setting = new AppSetting();
        if (settingNode != null) {
            if (settingNode.getEnableRestFailureAlert() != null) setting.setEnableRestFailureAlert(settingNode.getEnableRestFailureAlert());
            if (settingNode.getEnableLogFailureAlert() != null) setting.setEnableLogFailureAlert(settingNode.getEnableLogFailureAlert());
            if (settingNode.getFailureStatusRate() != null) setting.setFailureStatusRate(settingNode.getFailureStatusRate());
            if (settingNode.getFailureErrorCount() != null) setting.setFailureErrorCount(settingNode.getFailureErrorCount());
        }
        return setting;
    }

    @PostMapping("/app/setting/{appId}")
    public void postSetting(@PathVariable("appId") String appId, @RequestBody Setting setting) {
        if (setting != null) {
            Setting oldSetting = settingRepository.findByConfigServiceAppId(appId);
            if (oldSetting != null) {
                if (setting.getFailureStatusRate() != null) oldSetting.setFailureStatusRate(setting.getFailureStatusRate());
                if (setting.getFailureErrorCount() != null) oldSetting.setFailureErrorCount(setting.getFailureErrorCount());
                if (setting.getEnableRestFailureAlert() != null) oldSetting.setEnableRestFailureAlert(setting.getEnableRestFailureAlert());
                if (setting.getEnableLogFailureAlert() != null) oldSetting.setEnableLogFailureAlert(setting.getEnableLogFailureAlert());
                settingRepository.save(oldSetting);
            } else {
                Service service = serviceRepository.findByAppId(appId);
                if (service != null) {
                    setting.setConfigService(service);
                    settingRepository.save(setting);
                }
            }
            logger.info(appId + " setting updated");
        }
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
