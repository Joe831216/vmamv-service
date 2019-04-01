package com.soselab.microservicegraphplatform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.soselab.microservicegraphplatform.bean.elasticsearch.MgpLog;
import com.soselab.microservicegraphplatform.bean.elasticsearch.RequestAndResponseMessage;
import com.soselab.microservicegraphplatform.bean.mgp.AppMetrics;
import com.soselab.microservicegraphplatform.bean.mgp.Status;
import com.soselab.microservicegraphplatform.repositories.elasticsearch.HttpRequestAndResponseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.io.IOException;
import java.util.*;

@Configuration
public class LogAnalyzer {
    private static final Logger logger = LoggerFactory.getLogger(LogAnalyzer.class);

    @Autowired
    private HttpRequestAndResponseRepository httpRequestAndResponseRepository;
    @Autowired
    private ObjectMapper mapper;

    public AppMetrics getMetrics(String systemName, String appName, String version) {
        List<MgpLog> responseLogs = httpRequestAndResponseRepository.findResponseBySystemNameAndAppNameAndVersion
                (systemName, appName, version, new PageRequest(0,1000, new Sort(Sort.Direction.ASC, "@timestamp")));
        AppMetrics metrics = new AppMetrics();
        Integer averageDuration = getAverageResponseDuration(responseLogs);
        if (averageDuration != null) {
            metrics.setAverageDuration(averageDuration);
        }
        logger.info(systemName + ":" + appName + ":" + version + " : average duration calculate by recent " + responseLogs.size() + " responses: " + metrics.getAverageDuration() + "ms");
        metrics.setStatuses(getResponseStausMetrics(responseLogs));
        List<MgpLog> errors = httpRequestAndResponseRepository.findErrorsBySystemNameAndAppNameAndVersion(systemName, appName, version);
        metrics.setErrorCount(errors.size());
        logger.info(systemName + ":" + appName + ":" + version + " : error count: " + metrics.getErrorCount());
        return metrics;
    }

    private Integer getAverageResponseDuration(List<MgpLog> logs) {
        Integer averageDuration = null;
        if (logs.size() > 0) {
            int logCount = 0;
            int durationCount = 0;
            for (MgpLog log : logs) {
                RequestAndResponseMessage message = null;
                try {
                    message = mapper.readValue(log.getMessage(), RequestAndResponseMessage.class);
                    if (message != null) {
                        logCount++;
                        durationCount += message.getDuration();
                    }
                } catch (IOException e) {
                    logger.error(e.getMessage(), e);
                }
            }
            if (durationCount > 0) {
                averageDuration = durationCount / logCount;
            }
        }
        return averageDuration;
    }

    private List<Status> getResponseStausMetrics(List<MgpLog> logs) {
        Map<Integer, Integer> statusCount = new HashMap<>();
        if (logs.size() > 0) {
            for (MgpLog log : logs) {
                RequestAndResponseMessage message = null;
                try {
                    message = mapper.readValue(log.getMessage(), RequestAndResponseMessage.class);
                    if (message != null) {
                        statusCount.merge(message.getStatus(), 1, (oldCount, newCount) -> oldCount + 1);
                    }
                } catch (IOException e) {
                    logger.error(e.getMessage(), e);
                }
            }
        }
        List<Status> statuses = new ArrayList<>();
        statusCount.forEach((status, count) -> {
            statuses.add(new Status(status, count, (float) count/logs.size()));
        });

        return statuses;
    }

}
