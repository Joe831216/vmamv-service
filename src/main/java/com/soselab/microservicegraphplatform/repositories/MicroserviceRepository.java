package com.soselab.microservicegraphplatform.repositories;

import com.soselab.microservicegraphplatform.bean.neo4j.Microservice;
import org.springframework.data.neo4j.annotation.Query;
import org.springframework.data.neo4j.repository.GraphRepository;
import org.springframework.data.repository.query.Param;

import java.util.ArrayList;
import java.util.List;

public interface MicroserviceRepository extends GraphRepository<Microservice> {

    Microservice findByAppId(String appId);

    @Query("MATCH (m:Microservice) WHERE m.appId = {appId} DETACH DELETE m")
    Microservice deleteByAppId(@Param("appId") String appId);

    @Query("MATCH (sm:Microservice)-[:REGISTER]->(:ServiceRegistry)<-[:REGISTER]-(tm:Microservice) " +
            "WHERE sm.appId = {smAppId} AND tm.appName = {tmAppName} AND tm.version = {tmVer} RETURN tm")
    Microservice findByAppNameAndVersionInSameSys(@Param("smAppId") String sourceAppId,
                                              @Param("tmAppName") String targetAppName,
                                              @Param("tmVer") String targetVersion);

    @Query("MATCH (sm:Microservice)-[:REGISTER]->(:ServiceRegistry)<-[:REGISTER]-(tm:Microservice) " +
            "WHERE sm.appId = {smAppId} AND tm.appName = {tmAppName} RETURN tm")
    List<Microservice> findByAppNameInSameSys(@Param("smAppId") String sourceAppId, @Param("tmAppName") String targetAppName);

    @Query("MATCH (m:Microservice)-[:OWN]->(e:Endpoint) WHERE m.appId = {appId} DETACH DELETE m, e")
    Microservice deleteWithEndpointsByAppId(@Param("appId") String appId);

    /*
    @Query("MATCH (sm:Microservice {appId: {appId}}) " +
            "OPTIONAL MATCH (sm)-[:HTTP_REQUEST]->(tne1:NullEndpoint)<-[:OWN]-(tm1:Microservice) " +
            "OPTIONAL MATCH (tm1)-[:OWN]->(es1:Endpoint) " +
            "OPTIONAL MATCH (sm)-[:OWN]->(se:Endpoint)-[:HTTP_REQUEST]->(tne2:NullEndpoint)<-[:OWN]-(tm2:Microservice) " +
            "OPTIONAL MATCH (tm2)-[:OWN]->(es2:Endpoint) " +
            "WITH sm, se, tne1, tne2, CASE count(es1) WHEN 1 THEN tm1 END AS result1, CASE count(es2) WHEN 1 THEN tm2 END AS result2 " +
            "DETACH DELETE sm, se, tne1, tne2, result1, result2")*/
    @Query("MATCH (m:Microservice {appId: {appId}}) " +
            "OPTIONAL MATCH (m)-[:OWN]->(e:Endpoint) " +
            "DETACH DELETE m, e")
    Microservice deleteWithRelateByAppId(@Param("appId") String appId);

    @Query("MATCH (nm:NullMicroservice) WHERE NOT (nm)-[:OWN]->() DETACH DELETE nm")
    Microservice deleteUselessNullMicroservice();

    @Query("MATCH (m:Microservice)-[:REGISTER]->(s:ServiceRegistry) WHERE s.appId = {appId} RETURN m")
    ArrayList<Microservice> findAllByServiceRegistryAppId(@Param("appId") String appId);

    Microservice findByAppName(String appName);

    Microservice findByScsName(String scsName);

}
