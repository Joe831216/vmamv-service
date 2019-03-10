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

    @Query("MATCH (m:Microservice)-[:OWN]->(e:Endpoint)-[:HTTP_REQUEST]->(ne:NullEndpoint)<-[:OWN]-(nm:NullMicroservice) " +
            "OPTIONAL MATCH (nm)-[:OWN]->(nes:NullEndpoint) " +
            "WHERE m.appId = {appId} " +
            "WITH  m, e, ne, nm, CASE count(nes) WHEN 1 THEN nm END AS result " +
            "DETACH DELETE  m, e, ne, result")
    Microservice deleteWithRelateByAppId(@Param("appId") String appId);

    @Query("MATCH (m:Microservice)-[:REGISTER]->(s:ServiceRegistry) WHERE s.appId = {appId} RETURN m")
    ArrayList<Microservice> findAllByServiceRegistryAppId(@Param("appId") String appId);

    Microservice findByAppName(String appName);

    Microservice findByScsName(String scsName);

}
