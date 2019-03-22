package com.soselab.microservicegraphplatform.repositories;

import com.soselab.microservicegraphplatform.bean.neo4j.NullService;
import com.soselab.microservicegraphplatform.bean.neo4j.Service;
import org.springframework.data.neo4j.annotation.Query;
import org.springframework.data.neo4j.repository.GraphRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ServiceRepository extends GraphRepository<Service> {

    Service findByAppId(String appId);

    @Query("MATCH (s:Service {systemName:{systemName}, appName:{appName}}) WHERE NOT s.version = {ver} RETURN s")
    Service findOtherVerInSameSysBySysNameAndAppNameAndVersion(@Param("systemName") String systemName, @Param("appName") String appName, @Param("ver") String version);

    @Query("MATCH (s:Service {appId:{appId}})-[:OWN]->(e:Endpoint) " +
            "OPTIONAL MATCH (e)<-[:HTTP_REQUEST]-(:Endpoint)<-[:OWN]-(o1:Service) " +
            "OPTIONAL MATCH (e)<-[:HTTP_REQUEST]-(o2:Service) " +
            "RETURN o1, o2")
    List<Service> findDependentOnThisAppByAppId(@Param("appId") String sourceAppId);

    @Query("MATCH (sm:Service)-[:REGISTER]->(:ServiceRegistry)<-[:REGISTER]-(tm:Service) " +
            "WHERE sm.appId = {smAppId} AND tm.appName = {tmAppName} RETURN tm")
    List<Service> findByAppNameInSameSys(@Param("smAppId") String sourceAppId, @Param("tmAppName") String targetAppName);

    @Query("MATCH (m:Service {systemName:{systemName}}) WHERE NOT (m:NullService) RETURN m")
    List<Service> findBySysName(@Param("systemName") String systemName);

    @Query("MATCH (n:NullService {systemName:{systemName}}) RETURN n")
    List<NullService> findNullBySysName(@Param("systemName") String systemName);

    @Query("MATCH (n:NullService {appId:{appId}}) RETURN n")
    Service findNullByAppId(@Param("appId") String appId);

    @Query("MATCH (m:Service) WHERE m.appId = {appId} DETACH DELETE m")
    void deleteByAppId(@Param("appId") String appId);

    @Query("MATCH (m:Service {appId: {appId}}) " +
            "OPTIONAL MATCH (m)-[:OWN]->(e:Endpoint) " +
            "DETACH DELETE m, e")
    void deleteWithEndpointsByAppId(@Param("appId") String appId);

    @Query("MATCH (nm:NullService) WHERE NOT (nm)-[:OWN]->() DETACH DELETE nm")
    void deleteUselessNullService();

    @Query("MATCH (s:Service {appId:{appId}}) " +
            "OPTIONAL MATCH (s)-[r1:HTTP_REQUEST]->() " +
            "OPTIONAL MATCH (s)-[:OWN]->(:Endpoint)-[r2:HTTP_REQUEST]->() " +
            "DELETE r1, r2")
    void deleteDependencyByAppId(@Param("appId") String appId);

    @Query("MATCH (s:NullService {appId:{appId}}) REMOVE s:NullService")
    void removeNullLabelByAppId(@Param("appId") String appId);

    @Query("MATCH (s:NullService {appId:{appId}}) SET s.number = {num} REMOVE s:NullService")
    void removeNullLabelAndSetNumByAppId(@Param("appId") String appId, @Param("num") int num);

    @Query("MATCH (s:NullService {appId: {noVerAppId}}) " +
            "SET s.appId = {appId}, s.version = {ver}, s.number = {num}" +
            "REMOVE s:NullService" +
            "RETURN count(s)>0 as result ")
    boolean removeNullLabelAndSetVerAndNumByAppId(@Param("noVerAppId") String noVerAppId, @Param("appId") String appId,
                                                  @Param("ver") String version, @Param("num") int num);

    @Query("MATCH (m:Service {appId: {appId}}) " +
            "OPTIONAL MATCH (m)-[:OWN]->(e:Endpoint) " +
            "SET m:NullService, m.number = 0, e:NullEndpoint")
    void setNumToZeroAndAddNullLabelWithEndpointsByAppId(@Param("appId") String appId);

    @Query("MATCH (s:Service {appId: {appId}}) WITH s, s.number = {num} as result SET s.number = {num} RETURN result")
    boolean setNumberByAppId(@Param("appId") String appId, @Param("num") int number);

    @Query("MATCH (:Service{appId:{appId}})-[:OWN]->(:Endpoint)<-[:HTTP_REQUEST]-(n) RETURN count(n)>0 AS result")
    boolean isBeDependentByAppId(@Param("appId") String appId);

}
