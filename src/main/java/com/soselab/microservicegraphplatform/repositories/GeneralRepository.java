package com.soselab.microservicegraphplatform.repositories;

import org.springframework.data.neo4j.annotation.Query;
import org.springframework.data.neo4j.repository.GraphRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface GeneralRepository extends GraphRepository {

    @Query("MATCH (s:ServiceRegistry) WITH DISTINCT s.systemName as result RETURN result")
    List<String> getAllSystemName();

    @Query("MATCH (n) WHERE n:Service OR n:Endpoint OR n:Queue " +
            "MATCH ()-[r]->() WHERE (:Service)-[r:OWN]->(:Endpoint) OR ()-[r:HTTP_REQUEST]->() OR ()-[r:AMQP_PUBLISH]->() OR ()-[r:AMQP_SUBSCRIBE]-() " +
            "WITH collect(DISTINCT n) as ns, collect(DISTINCT r) as rs " +
            "WITH [node in ns | node {.*, id:id(node), labels:labels(node)}] as nodes, " +
            "[rel in rs | rel {.*, type:type(rel), " +
            "source:id(startNode(rel)), target:id(endNode(rel))}] as rels " +
            "RETURN apoc.convert.toJson({nodes:nodes, links:rels})")
    String getGraphJson();

    @Query("MATCH (n {systemName:{systemName}}) WHERE n:Service OR n:Endpoint OR n:Queue " +
            "MATCH (n)-[r]-() WHERE (:Service)-[r:OWN]->(:Endpoint) OR ()-[r:HTTP_REQUEST]->() OR ()-[r:AMQP_PUBLISH]->() OR ()-[r:AMQP_SUBSCRIBE]->() " +
            "WITH collect(DISTINCT n) as ns, collect(DISTINCT r) as rs " +
            "WITH [node in ns | node {.*, id:id(node), labels:labels(node)}] as nodes, " +
            "[rel in rs | rel {.*, type:type(rel), " +
            "source:id(startNode(rel)), target:id(endNode(rel))}] as rels " +
            "RETURN apoc.convert.toJson({nodes:nodes, links:rels})")
    String getSystemGraphJson(@Param("systemName") String systemName);

}
