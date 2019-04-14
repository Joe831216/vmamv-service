package com.soselab.microservicegraphplatform.services;

import com.soselab.microservicegraphplatform.bean.mgp.AppMetrics;
import com.soselab.microservicegraphplatform.bean.mgp.Status;
import com.soselab.microservicegraphplatform.bean.mgp.WebNotification;
import com.soselab.microservicegraphplatform.bean.mgp.notification.warning.FailureErrorNotification;
import com.soselab.microservicegraphplatform.bean.mgp.notification.warning.FailureStatusRateWarningNotification;
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
    @Autowired
    private WebNotificationService notificationService;

    public void checkAlert(String systemName) {
        List<Service> services = serviceRepository.findBySystemNameWithSettingNotNull(systemName);
        checkMetricsOfAppsInSystem (systemName, services);
    }

    public void checkMetricsOfAppsInSystem (String systemName, List<Service> services) {
        for (Service service : services) {
            Setting setting = service.getSetting();
            // Using log (Elasticsearch) metrics
            if (setting.getEnableLogFailureAlert()) {
                AppMetrics metrics = logAnalyzer.getMetrics(service.getSystemName(), service.getAppName(), service.getVersion());
                // Failure status rate
                Pair<Boolean, Float> failureStatusRateResult = isFailureStatusRateExceededThreshold
                        (metrics, setting.getFailureStatusRate());
                if (failureStatusRateResult.getKey()) {
                    WebNotification notification = new FailureStatusRateWarningNotification(service.getAppName(),
                            service.getVersion(), failureStatusRateResult.getValue(), setting.getFailureStatusRate(),
                            FailureStatusRateWarningNotification.TYPE_ELASTICSEARCH);
                    notificationService.pushNotificationToSystem(systemName, notification);
                }
                // Error
                if (setting.getFailureErrorCount() != null && metrics.getErrorCount() > setting.getFailureErrorCount()) {
                    WebNotification notification = new FailureErrorNotification(service.getAppName(), service.getVersion(),
                            metrics.getErrorCount(), setting.getFailureErrorCount(), FailureErrorNotification.TYPE_ELASTICSEARCH);
                    notificationService.pushNotificationToSystem(systemName, notification);
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
                    WebNotification notification = new FailureStatusRateWarningNotification(service.getAppName(),
                            service.getVersion(), failureStatusRateResult.getValue(), setting.getFailureStatusRate(),
                            FailureStatusRateWarningNotification.TYPE_ACTUATOR);
                    notificationService.pushNotificationToSystem(systemName, notification);
                }
            }
        }
    }

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
