package com.soselab.microservicegraphplatform.repositories;

import com.soselab.microservicegraphplatform.bean.neo4j.Queue;
import org.springframework.data.neo4j.annotation.Query;
import org.springframework.data.neo4j.repository.GraphRepository;

public interface QueueRepository extends GraphRepository<Queue> {

    Queue findByQueueId(String queueId);

    @Query("MATCH (q:Queue) WHERE NOT (q)<-[:AMQP_SUBSCRIBE]-() OR (q)-[:AMQP_PUBLISH]->() DETACH DELETE q")
    void deleteUselessQueues();

}
