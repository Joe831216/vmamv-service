package com.soselab.microservicegraphplatform.repositories;

import com.soselab.microservicegraphplatform.bean.neo4j.NullService;
import com.soselab.microservicegraphplatform.bean.neo4j.Service;
import org.springframework.data.neo4j.annotation.Query;
import org.springframework.data.neo4j.repository.GraphRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ServiceRepository extends GraphRepository<Service> {

    Service findByAppId(String appId);

    @Query("MATCH (sm:Service)-[:REGISTER]->(:ServiceRegistry)<-[:REGISTER]-(tm:Service) " +
            "WHERE sm.appId = {smAppId} AND tm.appName = {tmAppName} RETURN tm")
    List<Service> findByAppNameInSameSys(@Param("smAppId") String sourceAppId, @Param("tmAppName") String targetAppName);

    @Query("MATCH (m:Service {scsName:{scsName}}) WHERE NOT (m:NullService) RETURN m")
    List<Service> findByScsName(@Param("scsName") String scsName);

    @Query("MATCH (n:NullService {scsName:{scsName}}) RETURN n")
    List<NullService> findNullByScsName(@Param("scsName") String scsName);

    @Query("MATCH (m:Service) WHERE m.appId = {appId} DETACH DELETE m")
    void deleteByAppId(@Param("appId") String appId);

    @Query("MATCH (m:Service {appId: {appId}}) " +
            "OPTIONAL MATCH (m)-[:OWN]->(e:Endpoint) " +
            "DETACH DELETE m, e")
    void deleteWithEndpointsByAppId(@Param("appId") String appId);

    @Query("MATCH (nm:NullService) WHERE NOT (nm)-[:OWN]->() DETACH DELETE nm")
    void deleteUselessNullService();

    @Query("MATCH (s:NullService {appId:{appId}}) REMOVE s:NullService")
    void removeNullLabelByAppId(@Param("appId") String appId);

    @Query("MATCH (s:NullService {appId:{appId}}) SET s.number = {num} REMOVE s:NullService")
    void removeNullLabelAndSetNumByAppId(@Param("appId") String appId, @Param("num") int num);

    @Query("MATCH (m:Service {appId: {appId}}) " +
            "OPTIONAL MATCH (m)-[:OWN]->(e:Endpoint) " +
            "SET m:NullService, e:NullEndpoint")
    void addNullLabelWithEndpointsByAppId(@Param("appId") String appId);

    @Query("MATCH (s:Service {appId: {appId}}) WITH s, s.number = {num} as result SET s.number = {num} RETURN result")
    boolean setNumberByAppId(@Param("appId") String appId, @Param("num") int number);

    @Query("MATCH (:Service{appId:{appId}})-[:OWN]->(:Endpoint)<-[:HTTP_REQUEST]-(n) RETURN count(n)>0 AS result")
    boolean isBeDependentByAppId(@Param("appId") String appId);

}
