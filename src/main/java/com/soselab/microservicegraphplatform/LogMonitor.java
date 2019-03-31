package com.soselab.microservicegraphplatform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.soselab.microservicegraphplatform.bean.elasticsearch.HttpRequestAndResponseLog;
import com.soselab.microservicegraphplatform.bean.elasticsearch.RequestAndResponseMessage;
import com.soselab.microservicegraphplatform.repositories.elasticsearch.HttpRequestAndResponseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.List;

@Configuration
public class LogMonitor {
    private static final Logger logger = LoggerFactory.getLogger(LogMonitor.class);

    @Autowired
    private HttpRequestAndResponseRepository httpRequestAndResponseRepository;
    @Autowired
    private ObjectMapper mapper;

    public Integer getAverageResponseDuration(String systemName, String appName, String version) {
        List<HttpRequestAndResponseLog> logs = httpRequestAndResponseRepository.findResponseBySystemNameAndAndAppNameAndVersion("scs-a", "a-service", "0.0.1-SNAPSHOT");
        Integer averageDuration = null;
        if (logs.size() > 0) {
            int durationCount = 0;
            for (HttpRequestAndResponseLog log : logs) {
                RequestAndResponseMessage message = null;
                try {
                    message = mapper.readValue(log.getMessage(), RequestAndResponseMessage.class);
                } catch (IOException e) {
                    logger.error(e.getMessage(), e);
                }
                if (message != null) {
                    durationCount += message.getDuration();
                }
            }
            if (durationCount > 0) {
                averageDuration = durationCount / logs.size();
            }
        }
        return averageDuration;
    }

}
