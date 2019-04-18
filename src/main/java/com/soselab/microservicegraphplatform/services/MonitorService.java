package com.soselab.microservicegraphplatform.services;

import com.soselab.microservicegraphplatform.bean.mgp.AppMetrics;
import com.soselab.microservicegraphplatform.bean.mgp.WebNotification;
import com.soselab.microservicegraphplatform.bean.mgp.monitor.FailureStatusRateSPC;
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
    private Map<String, FailureStatusRateSPC> failureStatusRateSPCMap = new HashMap<>();

    public void runScheduled(String systemName) {
        //List<Service> services = serviceRepository.findBySystemNameWithSettingNotNull(systemName);
        List<Service> services = serviceRepository.findBySystemNameWithOptionalSettingNotNull(systemName);
        checkAlert(systemName, services);
        updateSPCData(systemName, services);
    }

    public void checkAlert(String systemName, List<Service> services) {
        for (Service service : services) {
            Setting setting = service.getSetting();
            if (setting == null) {
                continue;
            }
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
        float failureStatusRate = metrics.getFailureStatusRate();
        return new ImmutablePair<>(failureStatusRate > threshold, failureStatusRate);
    }

    public FailureStatusRateSPC getFailureStatusRateSPC(String systemName) {
        return failureStatusRateSPCMap.get(systemName);
    }

    private void updateSPCData(String systemName, List<Service> services) {
        Map<String, AppMetrics> metricsMap = new HashMap<>();
        for (Service service : services) {
            metricsMap.put(service.getAppName() + ":" + service.getVersion(), logAnalyzer.getMetrics(service.getSystemName(), service.getAppName(), service.getVersion()));
        }
        FailureStatusRateSPC failureStatusRateSPC = updateFailureStatusRateSPC(metricsMap);
        //logger.info(systemName + " failure status rate control chart -> CL: " + cl + ", UCL: " + ucl + ", LCL: " + lcl);
        webPageController.sendFailureStatusRateSPC(systemName, failureStatusRateSPC);
        failureStatusRateSPCMap.put(systemName, failureStatusRateSPC);
    }

    private FailureStatusRateSPC updateFailureStatusRateSPC(Map<String, AppMetrics> metricsMap) {
        float rateCount = 0;
        float samplesGroupNum = 0;
        long samplesCount = 0;
        Map<String, Float> rates = new HashMap<>();
        for (Map.Entry<String, AppMetrics> entry : metricsMap.entrySet()) {
            String app = entry.getKey();
            AppMetrics metrics = entry.getValue();
            int samplesNum = metrics.getFailureStatusSamplesNum();
            if (samplesNum > 0) {
                float rate = metrics.getFailureStatusRate();
                rateCount += rate;
                samplesGroupNum ++;
                samplesCount += samplesNum;
                rates.put(app, rate);
            }
        }
        float cl = rateCount / samplesGroupNum;
        float sd = (float) Math.sqrt((cl*(1-cl))/(samplesCount/samplesGroupNum));
        float ucl = cl + 3*sd;
        float lcl = cl - 3*sd;
        if (lcl < 0) {
            lcl = 0;
        }
        return new FailureStatusRateSPC(cl, ucl, lcl, rates);
    }

}
