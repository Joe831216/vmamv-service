package com.soselab.microservicegraphplatform.repositories;

import org.springframework.data.neo4j.annotation.Query;
import org.springframework.data.neo4j.repository.GraphRepository;

public interface GeneralRepository extends GraphRepository {

    @Query("MATCH (n) WHERE n:Microservice OR n:Endpoint " +
            "MATCH ()-[r]->() WHERE ()-[r:OWN]->() OR ()-[r:HTTP_REQUEST]-() " +
            "WITH collect(DISTINCT n) as ns, collect(DISTINCT r) as rs " +
            "WITH [node in ns | node {.*, id:id(node), label:labels(node)}] as nodes, " +
            "[rel in rs | rel {.*, type:type(rel), " +
            "source:{id:id(startNode(rel))}, target:{id:id(endNode(rel))}}] as rels " +
            "RETURN apoc.convert.toJson({nodes:nodes, links:rels})")
    String getServicesAndEndpointsRelJson();

}
