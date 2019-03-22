package com.soselab.microservicegraphplatform.repositories;

import com.soselab.microservicegraphplatform.bean.neo4j.ServiceRegistry;
import org.springframework.data.neo4j.annotation.Query;
import org.springframework.data.neo4j.repository.GraphRepository;
import org.springframework.data.repository.query.Param;

import java.util.ArrayList;

public interface ServiceRegistryRepository extends GraphRepository<ServiceRegistry> {

    @Query("MATCH (n:ServiceRegistry) RETURN n")
    ArrayList<ServiceRegistry> findAll();

    ServiceRegistry findByAppId(String appId);

    @Query("MATCH (s:ServiceRegistry)-[:OWN]->(n:Instance) WHERE n.instanceId = {instanceId} RETURN s")
    ServiceRegistry findByInstanceId(@Param("instanceId") String instanceId);

    ServiceRegistry findBySystemName(String systemName);

    ServiceRegistry findByAppName(String appName);

    @Query("MATCH (s:ServiceRegistry) WHERE s.appId = {appId} DETACH DELETE s")
    ServiceRegistry deleteByAppId(@Param("appId") String appId);

}
