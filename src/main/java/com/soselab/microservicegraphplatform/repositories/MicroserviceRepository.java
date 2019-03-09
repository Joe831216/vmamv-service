package com.soselab.microservicegraphplatform.repositories;

import com.soselab.microservicegraphplatform.bean.neo4j.Microservice;
import org.springframework.data.neo4j.annotation.Query;
import org.springframework.data.neo4j.repository.GraphRepository;
import org.springframework.data.repository.query.Param;

import java.util.ArrayList;

public interface MicroserviceRepository extends GraphRepository<Microservice> {

    Microservice findByAppId(String appId);

    @Query("MATCH (m:Microservice) WHERE m.appId = {appId} DETACH DELETE m")
    Microservice deleteByAppId(@Param("appId") String appId);

    @Query("MATCH (m:Microservice)-[:OWN]->(e:Endpoint) WHERE m.appId = {appId} DETACH DELETE m, e")
    Microservice deleteWithEndpointsByAppId(@Param("appId") String appId);

    @Query("MATCH (m:Microservice)-[:REGISTER]->(s:ServiceRegistry) WHERE s.appId = {appId} RETURN m")
    ArrayList<Microservice> findAllByServiceRegistryAppId(@Param("appId") String appId);

    Microservice findByAppName(String appName);

    Microservice findByScsName(String scsName);

}
