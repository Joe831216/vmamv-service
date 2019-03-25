package com.soselab.microservicegraphplatform.repositories;

import org.springframework.data.neo4j.annotation.Query;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GeneralRepository extends Neo4jRepository {

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
            "MATCH (n)-[r]-() WHERE (:Service)-[r:OWN]->(:Endpoint) OR ()-[r:HTTP_REQUEST]->() OR ()-[r:AMQP_PUBLISH]->() OR ()-[r:AMQP_SUBSCRIBE]->() OR ()-[r:NEWER_PATCH_VERSION]->() " +
            "WITH collect(DISTINCT n) as ns, collect(DISTINCT r) as rs " +
            "WITH [node in ns | node {.*, id:id(node), labels:labels(node)}] as nodes, " +
            "[rel in rs | rel {.*, type:type(rel), " +
            "source:id(startNode(rel)), target:id(endNode(rel))}] as rels " +
            "RETURN apoc.convert.toJson({nodes:nodes, links:rels})")
    String getSystemGraphJson(@Param("systemName") String systemName);

    @Query("MATCH (n) WHERE ID(n) = {id} " +
            "CALL apoc.path.subgraphAll(n, {relationshipFilter:\"OWN>|HTTP_REQUEST>|AMQP_PUBLISH>|AMQP_SUBSCRIBE>\"}) YIELD nodes, relationships " +
            "WITH [node in nodes | node {id:id(node)}] as nodes, " +
            "[rel in relationships | rel {type:type(rel), source:id(startNode(rel)), target:id(endNode(rel))}] as rels " +
            "RETURN apoc.convert.toJson({nodes:nodes, links:rels})")
    String getDependentOnChainFromServiceUsingWeakAlgorithmById(@Param("id") Long appId);

    @Query("MATCH (n) WHERE ID(n) = {id} " +
            "CALL apoc.path.subgraphAll(n, {relationshipFilter:\"OWN>|<HTTP_REQUEST|<AMQP_PUBLISH|<AMQP_SUBSCRIBE\"}) YIELD nodes, relationships " +
            "WITH [node in nodes | node {id:id(node)}] as nodes, " +
            "[rel in relationships | rel {type:type(rel), source:id(startNode(rel)), target:id(endNode(rel))}] as rels " +
            "RETURN apoc.convert.toJson({nodes:nodes, links:rels})")
    String getBeDependentOnChainFromServiceUsingWeakAlgorithmById(@Param("id") Long appId);

    @Query("MATCH (n) WHERE ID(n) = {id} " +
            "CALL apoc.path.subgraphAll(n, {relationshipFilter:\"OWN|HTTP_REQUEST>|AMQP_PUBLISH>|AMQP_SUBSCRIBE>\"}) YIELD nodes, relationships " +
            "WITH [node in nodes | node {id:id(node)}] as nodes, " +
            "[rel in relationships | rel {type:type(rel), source:id(startNode(rel)), target:id(endNode(rel))}] as rels " +
            "RETURN apoc.convert.toJson({nodes:nodes, links:rels})")
    String getDependentOnChainFromServiceUsingStrongAlgorithmById(@Param("id") Long appId);

    @Query("MATCH (n) WHERE ID(n) = {id} " +
            "CALL apoc.path.subgraphAll(n, {relationshipFilter:\"OWN|<HTTP_REQUEST|<AMQP_PUBLISH|<AMQP_SUBSCRIBE\"}) YIELD nodes, relationships " +
            "WITH [node in nodes | node {id:id(node)}] as nodes, " +
            "[rel in relationships | rel {type:type(rel), source:id(startNode(rel)), target:id(endNode(rel))}] as rels " +
            "RETURN apoc.convert.toJson({nodes:nodes, links:rels})")
    String getBeDependentOnChainFromServiceUsingStrongAlgorithmById(@Param("id") Long appId);

}
