package com.soselab.microservicegraphplatform.services;

import com.soselab.microservicegraphplatform.bean.mgp.AppMetrics;
import com.soselab.microservicegraphplatform.bean.mgp.Status;
import com.soselab.microservicegraphplatform.bean.mgp.WebNotification;
import com.soselab.microservicegraphplatform.bean.neo4j.Service;
import com.soselab.microservicegraphplatform.bean.neo4j.Setting;
import com.soselab.microservicegraphplatform.controllers.WebPageController;
import com.soselab.microservicegraphplatform.repositories.neo4j.GeneralRepository;
import com.soselab.microservicegraphplatform.repositories.neo4j.ServiceRepository;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import java.util.*;

@Configuration
public class MonitorService {
    private static final Logger logger = LoggerFactory.getLogger(MonitorService.class);

    @Autowired
    private GeneralRepository generalRepository;
    @Autowired
    private ServiceRepository serviceRepository;
    @Autowired
    private LogAnalyzer logAnalyzer;
    @Autowired
    private RestInfoAnalyzer restInfoAnalyzer;
    @Autowired
    private WebPageController webPageController;

    public void checkAlert(String systemName) {
        List<Service> services = serviceRepository.findBySystemNameWithSettingNotNull(systemName);
        checkMetricsOfAppsInSystem (systemName, services);
    }

    public void checkMetricsOfAppsInSystem (String systemName, List<Service> services) {
        String notiTitle = "Found exception";
        for (Service service : services) {
            Setting setting = service.getSetting();
            // Using log (Elasticsearch) metrics
            if (setting.getEnableLogFailureAlert()) {
                AppMetrics metrics = logAnalyzer.getMetrics(service.getSystemName(), service.getAppName(), service.getVersion());
                // Failure status rate
                Pair<Boolean, Float> failureStatusRateResult = isFailureStatusRateExceededThreshold
                        (metrics, setting.getFailureStatusRate());
                if (failureStatusRateResult.getKey()) {
                    String content = "<strong>" + service.getAppName() + ":" + service.getVersion() +
                            "</strong> exceeded the threshold of failure status rate: current value (Elasticsearch) = " +
                            failureStatusRateResult.getValue() * 100 + "%, threshold = " + setting.getFailureStatusRate() * 100 + "%";
                    WebNotification notification = new WebNotification(WebNotification.LEVEL_WARNING, notiTitle, content);
                    webPageController.sendNotification(systemName, notification);
                }
                // Error
                if (setting.getFailureErrorCount() != null && metrics.getErrorCount() > setting.getFailureErrorCount()) {
                    String content = "<strong>" + service.getAppName() + ":" + service.getVersion() +
                            "</strong> exceeded the threshold of error count: current value (Elasticsearch) = " +
                            metrics.getErrorCount() + ", threshold = " + setting.getFailureErrorCount();
                    WebNotification notification = new WebNotification(WebNotification.LEVEL_WARNING, notiTitle, content);
                    webPageController.sendNotification(systemName, notification);
                    logger.info("Found service " + service.getAppId() + " exception: error count = " +
                            metrics.getErrorCount() + " (threshold = " + setting.getFailureErrorCount() + ")");
                }
            }
            // Using rest (Spring Actuator) metrics
            if (setting.getEnableRestFailureAlert()) {
                AppMetrics metrics = restInfoAnalyzer.getMetrics(service.getSystemName(), service.getAppName(), service.getVersion());
                Pair<Boolean, Float> failureStatusRateResult = isFailureStatusRateExceededThreshold
                        (metrics, setting.getFailureStatusRate());
                if (failureStatusRateResult.getKey()) {
                    String content = "<strong>" + service.getAppName() + ":" + service.getVersion() +
                            "</strong> exceeded the threshold of failure status rate: current value (Spring Actuator) = " +
                            failureStatusRateResult.getValue() * 100 + "%, threshold = " + setting.getFailureStatusRate() * 100 + "%";
                    WebNotification notification = new WebNotification(WebNotification.LEVEL_WARNING, notiTitle, content);
                    webPageController.sendNotification(systemName, notification);
                }
            }
        }
    }

    /*public void checkDependencysOfAppsInSystem (String systemName, List<Service> services) {
        String notiTitle = "Found exception";
        for (Service service : services) {
            Setting setting = service.getSetting();
            if (setting.getEnableStrongDependencyAlert()) {
                if (setting.getStrongUpperDependencyCount() != null) {
                    Long strongUpperDependencyCount = generalRepository.getStrongUpperDependencyServiceCountByIdAndSystemName(service.getId(), systemName);
                    if (strongUpperDependencyCount != null && strongUpperDependencyCount > setting.getStrongUpperDependencyCount()) {
                        String content = "<strong>" + service.getAppName() + ":" + service.getVersion() +
                                "</strong> exceeded the threshold of <strong>strong upper dependency count</strong>: current value = " +
                                strongUpperDependencyCount + ", threshold = " + setting.getStrongUpperDependencyCount();
                        WebNotification notification = new WebNotification(WebNotification.LEVEL_WARNING, notiTitle, content);
                        webPageController.sendNotification(systemName, notification);
                    }
                }
                if (setting.getStrongLowerDependencyCount() != null) {
                    Long strongLowerDependencyCount = generalRepository.getStrongLowerDependencyServiceCountByIdAndSystemName(service.getId(), systemName);
                    if (strongLowerDependencyCount != null && strongLowerDependencyCount > setting.getStrongLowerDependencyCount()) {
                        String content = "<strong>" + service.getAppName() + ":" + service.getVersion() +
                                "</strong> exceeded the threshold of <strong>strong lower dependency count</strong>: current value = " +
                                strongLowerDependencyCount + ", threshold = " + setting.getStrongLowerDependencyCount();
                        WebNotification notification = new WebNotification(WebNotification.LEVEL_WARNING, notiTitle, content);
                        webPageController.sendNotification(systemName, notification);
                    }
                }
            }
            if (setting.getEnableWeakDependencyAlert()) {
                if (setting.getWeakUpperDependencyCount() != null) {
                    Long weakUpperDependencyCount = generalRepository.getWeakUpperDependencyServiceCountByIdAndSystemName(service.getId(), systemName);
                    if (weakUpperDependencyCount != null && weakUpperDependencyCount > setting.getWeakUpperDependencyCount()) {
                        String content = "<strong>" + service.getAppName() + ":" + service.getVersion() +
                                "</strong> exceeded the threshold of <strong>weak upper dependency count</strong>: current value = " +
                                weakUpperDependencyCount + ", threshold = " + setting.getWeakUpperDependencyCount();
                        WebNotification notification = new WebNotification(WebNotification.LEVEL_WARNING, notiTitle, content);
                        webPageController.sendNotification(systemName, notification);
                    }
                }
                if (setting.getWeakLowerDependencyCount() != null) {
                    Long weakLowrDependencyCount = generalRepository.getWeakLowerDependencyServiceCountByIdAndSystemName(service.getId(), systemName);
                    if (weakLowrDependencyCount != null && weakLowrDependencyCount > setting.getWeakLowerDependencyCount()) {
                        String content = "<strong>" + service.getAppName() + ":" + service.getVersion() +
                                "</strong> exceeded the threshold of <strong>weak lower dependency count</strong>: current value = " +
                                weakLowrDependencyCount + ", threshold = " + setting.getWeakLowerDependencyCount();
                        WebNotification notification = new WebNotification(WebNotification.LEVEL_WARNING, notiTitle, content);
                        webPageController.sendNotification(systemName, notification);
                    }
                }
            }
        }
    }*/

    // Pair<isExceededThreshold, failureStatusRate>
    private Pair<Boolean, Float> isFailureStatusRateExceededThreshold(AppMetrics metrics, float threshold) {
        float failureStatusRate = 1;
        for (Status status : metrics.getStatuses()) {
            if (status.getCode() == 200) {
                failureStatusRate -= status.getRatio();
                break;
            }
        }
        return new ImmutablePair<>(failureStatusRate > threshold, failureStatusRate);
    }

}
