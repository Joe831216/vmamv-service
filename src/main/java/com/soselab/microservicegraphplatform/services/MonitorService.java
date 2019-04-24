package com.soselab.microservicegraphplatform.services;

import com.soselab.microservicegraphplatform.bean.elasticsearch.MgpLog;
import com.soselab.microservicegraphplatform.bean.mgp.AppMetrics;
import com.soselab.microservicegraphplatform.bean.mgp.WebNotification;
import com.soselab.microservicegraphplatform.bean.mgp.monitor.SpcData;
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
import org.springframework.scheduling.annotation.Scheduled;

import java.text.SimpleDateFormat;
import java.util.*;

@Configuration
public class MonitorService {
    private static final Logger logger = LoggerFactory.getLogger(MonitorService.class);
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

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
    private Map<String, SpcData> failureStatusRateSPCMap = new HashMap<>();
    private Map<String, SpcData> averageDurationSPCMap = new HashMap<>();

    @Scheduled(cron = "0 0 0/1 1/1 * ?")
    private void everyHoursScheduled() {
        List<String> systemNames = generalRepository.getAllSystemName();
        for (String systemName : systemNames) {
            List<Service> services = serviceRepository.findBySystemNameWithOptionalSettingNotNull(systemName);
            checkLowUsage(systemName, services, 60);
        }
    }

    public void runScheduled(String systemName) {
        List<Service> services = serviceRepository.findBySystemNameWithOptionalSettingNotNull(systemName);
        updateSPCData(systemName, services);
        checkUserAlert(systemName, services);
        checkSPCAlert(systemName);
    }

    public void checkUserAlert(String systemName, List<Service> services) {
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
                            FailureStatusRateWarningNotification.DATA_ELASTICSEARCH, FailureStatusRateWarningNotification.THRESHOLD_USER);
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
                            FailureStatusRateWarningNotification.DATA_ACTUATOR, FailureStatusRateWarningNotification.THRESHOLD_USER);
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

    private void checkSPCAlert(String systemName) {
        SpcData spcData = failureStatusRateSPCMap.get(systemName);
        if (spcData != null) {
            float ucl = spcData.getUcl();
            spcData.getValues().forEach((app, value) -> {
                if (value > ucl) {
                    String appName = app.split(":")[0];
                    String version = app.split(":")[1];
                    notificationService.pushNotificationToSystem(systemName, new FailureStatusRateWarningNotification(appName, version, value, ucl,
                            FailureStatusRateWarningNotification.DATA_ELASTICSEARCH, FailureStatusRateWarningNotification.THRESHOLD_SPC));
                }
            });
        }
    }

    private void updateSPCData(String systemName, List<Service> services) {
        Map<String, AppMetrics> metricsMap = new HashMap<>();
        for (Service service : services) {
            metricsMap.put(service.getAppName() + ":" + service.getVersion(), logAnalyzer.getMetrics(service.getSystemName(), service.getAppName(), service.getVersion()));
        }
        SpcData failureStatusRateSpcData = getNowFailureStatusRateSPC(metricsMap);
        webPageController.sendAppsFailureStatusRateSPC(systemName, failureStatusRateSpcData);
        failureStatusRateSPCMap.put(systemName, failureStatusRateSpcData);

        SpcData durationSpcData = getNowAverageDurationSPC(metricsMap);
        webPageController.sendAppsAverageDurationSPC(systemName, durationSpcData);
        averageDurationSPCMap.put(systemName, durationSpcData);

    }

    // P chart
    private SpcData getNowFailureStatusRateSPC(Map<String, AppMetrics> metricsMap) {
        float valueCount = 0;
        int sampleGroupsNum = 0;
        long samplesCount = 0;
        Map<String, Float> values = new HashMap<>();
        for (Map.Entry<String, AppMetrics> entry : metricsMap.entrySet()) {
            String app = entry.getKey();
            AppMetrics metrics = entry.getValue();
            int samplesNum = metrics.getFailureStatusSamplesNum();
            if (samplesNum > 0) {
                float value = metrics.getFailureStatusRate();
                valueCount += value;
                sampleGroupsNum ++;
                samplesCount += samplesNum;
                values.put(app, value);
            }
        }
        float cl = valueCount / sampleGroupsNum;
        float n = (float) samplesCount/sampleGroupsNum;
        float sd = getPChartSD(cl, n);
        float ucl = cl + 3*sd;
        float lcl = cl - 3*sd;
        if (lcl < 0) {
            lcl = 0;
        }
        return new SpcData(cl, ucl, lcl, values);
    }

    public SpcData getFailureStatusRateSPC(String systemName) {
        return failureStatusRateSPCMap.get(systemName);
    }

    // U chart
    private SpcData getNowAverageDurationSPC(Map<String, AppMetrics> metricsMap) {
        float valueCount = 0;
        int sampleGroupsNum = 0;
        long samplesCount = 0;
        Map<String, Float> values = new HashMap<>();
        for (Map.Entry<String, AppMetrics> entry : metricsMap.entrySet()) {
            String app = entry.getKey();
            AppMetrics metrics = entry.getValue();
            int samplesNum = metrics.getDurationSamplesNum();
            if (samplesNum > 0) {
                float value = metrics.getAverageDuration();
                valueCount += value;
                sampleGroupsNum ++;
                samplesCount += samplesNum;
                values.put(app, value);
            }
        }
        float cl = valueCount / sampleGroupsNum;
        float n = (float) samplesCount/sampleGroupsNum;
        float sd = getUChartSD(cl, n);
        float ucl = cl + 3*sd;
        float lcl = cl - 3*sd;
        if (lcl < 0) {
            lcl = 0;
        }
        return new SpcData(cl, ucl, lcl, values);
    }

    public SpcData getAverageDurationSPC(String systemName) {
        return averageDurationSPCMap.get(systemName);
    }

    // C chart
    public SpcData getAppDurationSPC(String appId) {
        String[] appInfo = appId.split(":");
        List<MgpLog> logs = logAnalyzer.getRecentResponseLogs(appInfo[0], appInfo[1], appInfo[2], 100);
        float valueCount = 0;
        int samplesNum = 0;
        Map<String, Float> values = new LinkedHashMap<>();
        for (MgpLog log : logs) {
            Integer duration = logAnalyzer.getResponseDuration(log);
            if (duration != null) {
                String time = dateFormat.format(log.getTimestamp());
                valueCount += duration;
                samplesNum ++;
                values.put(time, (float) duration);
            }
        }
        float cl = valueCount / samplesNum;
        float sd = getCChartSD(cl);
        float ucl = cl + 3*sd;
        float lcl = cl - 3*sd;
        if (lcl < 0) {
            lcl = 0;
        }
        return new SpcData(cl, ucl, lcl, values);
    }

    private float getPChartSD(float cl, float n) {
        return (float) Math.sqrt(cl*(1-cl)/n);
    }

    private float getUChartSD(float cl, float n) {
        return (float) Math.sqrt(cl/n);
    }

    private float getCChartSD(float cl) {
        return (float) Math.sqrt(cl);
    }

    // Find low-usage version of apps
    private void checkLowUsage(String systemName, List<Service> services, int samplingDurationMinutes) {
        Set<String> checkedApp = new HashSet<>();
        Map<String, Set<String>> appNameAndVerSetMap = new HashMap<>();
        for (Service service : services) {
            appNameAndVerSetMap.merge(service.getAppName(), new HashSet<>(Arrays.asList(service.getVersion())),
                    (oldSet, newSet) -> {
                oldSet.add(service.getVersion());
                return oldSet;
            });

            checkedApp.add(service.getAppName());
        }
        appNameAndVerSetMap.forEach((appName, versions) -> {
            for (String version : versions) {
                logger.info(systemName + ":" + appName + ":" + version + " usage metrics: " +
                        logAnalyzer.getAppUsageMetrics(systemName, appName, version, samplingDurationMinutes));
            }
        });
    }

}
