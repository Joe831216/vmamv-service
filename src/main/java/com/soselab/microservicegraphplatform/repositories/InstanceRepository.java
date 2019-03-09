package com.soselab.microservicegraphplatform.repositories;

import com.soselab.microservicegraphplatform.bean.neo4j.Instance;
import org.springframework.data.neo4j.annotation.Query;
import org.springframework.data.neo4j.repository.GraphRepository;
import org.springframework.data.repository.query.Param;

import java.util.ArrayList;

public interface InstanceRepository extends GraphRepository<Instance> {

    Instance findByInstanceId(String instanceId);

    @Query("MATCH (n:Instance) WHERE n.instanceId = {instanceId} DETACH DELETE n")
    Instance deleteByInstanceId(@Param("instanceId") String instanceId);

    @Query("MATCH (n:Instance)<-[:OWN]-(s:ServiceRegistry) WHERE s.appId = {appId} RETURN n")
    ArrayList<Instance> findByServiceRegistryAppId(@Param("appId") String appId);

    Instance findByHostName(String hostName);

    Instance findByAppName(String appName);

    Instance findByIpAddr(String ipAddr);

    Instance findByPort(int port);

}
