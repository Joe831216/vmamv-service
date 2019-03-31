package com.soselab.microservicegraphplatform.repositories.elasticsearch;

import com.soselab.microservicegraphplatform.bean.elasticsearch.HttpRequestAndResponseLog;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;

public interface HttpRequestAndResponseRepository extends ElasticsearchRepository<HttpRequestAndResponseLog,String> {

    @Query("{\"bool\":{\"must\":[{\"match\":{\"systemName\":\"?0\"}},{\"match\":{\"appName\":\"?1\"}},{\"match\":{\"version\":\"?2\"}},{\"match\":{\"logger_name\":\"org.zalando.logbook.Logbook\"}}]}}")
    List<HttpRequestAndResponseLog> findBySystemNameAndAndAppNameAndVersion(String systemName, String appName, String version);

    @Query("{\"bool\":{\"must\":[{\"match\":{\"systemName\":\"?0\"}},{\"match\":{\"appName\":\"?1\"}},{\"match\":{\"version\":\"?2\"}},{\"match\":{\"logger_name\":\"org.zalando.logbook.Logbook\"}},{\"match\":{\"message\":\"response\"}}]}}")
    List<HttpRequestAndResponseLog> findResponseBySystemNameAndAndAppNameAndVersion(String systemName, String appName, String version);

}
