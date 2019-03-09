package com.soselab.microservicegraphplatform.registry;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.soselab.microservicegraphplatform.bean.actuators.Info;
import com.soselab.microservicegraphplatform.bean.eureka.AppInstance;
import com.soselab.microservicegraphplatform.bean.neo4j.Endpoint;
import com.soselab.microservicegraphplatform.bean.neo4j.Instance;
import com.soselab.microservicegraphplatform.bean.neo4j.Microservice;
import com.soselab.microservicegraphplatform.bean.neo4j.ServiceRegistry;
import com.soselab.microservicegraphplatform.bean.eureka.Application;
import com.soselab.microservicegraphplatform.bean.eureka.AppsList;
import com.soselab.microservicegraphplatform.repositories.EndpointRepository;
import com.soselab.microservicegraphplatform.repositories.InstanceRepository;
import com.soselab.microservicegraphplatform.repositories.MicroserviceRepository;
import com.soselab.microservicegraphplatform.repositories.ServiceRegistryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.*;

public class RefreshScheduledTask extends TimerTask {

    private static final Logger logger = LoggerFactory.getLogger(RefreshScheduledTask.class);

    private ServiceRegistryRepository serviceRegistryRepository;
    private InstanceRepository instanceRepository;
    private MicroserviceRepository microserviceRepository;
    private EndpointRepository endpointRepository;

    private RestTemplate restTemplate = new RestTemplate();
    private ObjectMapper mapper = new ObjectMapper();

    public RefreshScheduledTask(ServiceRegistryRepository serviceRegistryRepository,
                                InstanceRepository instanceRepository, MicroserviceRepository microserviceRepository,
                                EndpointRepository endpointRepository) {
        this.serviceRegistryRepository = serviceRegistryRepository;
        this.instanceRepository = instanceRepository;
        this.microserviceRepository = microserviceRepository;
        this.endpointRepository = endpointRepository;
    }

    public void run() {
        // Loop each registry
        ArrayList<ServiceRegistry> registries = serviceRegistryRepository.findAll();
        if (registries.size() > 0) {
            for (ServiceRegistry serviceRegistry : registries) {
                // Get latest app list by request the first instance that own by this registry
                String scsName = serviceRegistry.getScsName();
                ArrayList<Instance> instances = instanceRepository.findByServiceRegistryAppId(serviceRegistry.getAppId());
                if (instances.size() > 0) {
                    Instance instance = instances.get(0);
                    try {
                        String url = "http://" + instance.getIpAddr() + ":" + instance.getPort() + "/eureka/apps/";
                        AppsList eurekaAppsList = restTemplate.getForObject(url, AppsList.class);
                        HashMap<String, String> appIdsAndUrl = getAppIdsAndUrlFromEurekaAppList(scsName, eurekaAppsList);
                        ArrayList<Microservice> microservicesInDB = microserviceRepository.findAllByServiceRegistryAppId(serviceRegistry.getAppId());
                        addNewService(serviceRegistry, appIdsAndUrl, microservicesInDB);
                        removeOldService(appIdsAndUrl, microservicesInDB);
                    } catch (ResourceAccessException e) {
                        logger.error(e.getMessage(), e);
                    }
                }
            }
        }
    }

    private HashMap<String, String> getAppIdsAndUrlFromEurekaAppList(String scsName, AppsList appsList) {
        HashMap<String, String> appIdsAndUrl = new HashMap<>();
        ArrayList<Application> apps = appsList.getApplications().getApplication();
        for (Application app: apps) {
            String appName = app.getName();
            for (AppInstance instance: app.getInstance()) {
                if (instance.getStatus().equals("UP")) {
                    String url = "http://" + instance.getIpAddr() + ":" + instance.getPort().get$();
                    String version = restTemplate.getForObject( url + "/info", Info.class).getVersion();
                    appIdsAndUrl.put(scsName + ":" + appName + ":" + version, url);
                }
            }
        }

        return appIdsAndUrl;
    }

    // Add new apps to neo4j
    private void addNewService(ServiceRegistry serviceRegistry, HashMap<String, String> appIdsAndUrl, ArrayList<Microservice> appsInDB) {
        HashMap<String, String> newAppIdsAndUrl = new HashMap<>();
        appIdsAndUrl.forEach((key, value) -> {
            boolean isInDB = false;
            for (Microservice dbApp : appsInDB) {
                if (key.equals(dbApp.getAppId())) {
                    isInDB = true;
                }
            }
            if (!isInDB) {
                newAppIdsAndUrl.put(key, value);
            }
        });
        HashMap<String, Map<String, Object>> newAppSwaggers = new HashMap<>();
        // Add nodes
        newAppIdsAndUrl.forEach((key, value) -> {
            String swagger = restTemplate.getForObject(value + "/v2/api-docs", String.class);
            Map<String, Object> swaggerMap = null;
            try {
                swaggerMap = mapper.readValue(swagger, new TypeReference<Map<String, Object>>(){});
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
            }
            if (swaggerMap != null) {
                newAppSwaggers.put(key, swaggerMap);
                String[] appIdInfo = key.split(":");
                Microservice microservice = new Microservice(appIdInfo[0], appIdInfo[1], appIdInfo[2]);
                microservice.registerTo(serviceRegistry);
                Map<String, Object> pathsMap = mapper.convertValue(swaggerMap.get("paths"), new TypeReference<Map<String, Object>>(){});
                pathsMap.forEach((pathKey, pathValue) -> {
                    Map<String, Object> methodMap = mapper.convertValue(pathValue, new TypeReference<Map<String, Object>>(){});
                    methodMap.forEach((methodKey, methodValue) -> {
                        Endpoint endpoint = new Endpoint(methodKey, pathKey);
                        microservice.ownEndpoint(endpoint);
                    });
                });
                microserviceRepository.save(microservice);
                logger.info("Add microservice: " + microservice.getAppId());
            }
        });
        // Add links
        newAppIdsAndUrl.forEach((sourceAppId, sourceAppUrl) -> {
            Map<String, Object> swaggerMap = newAppSwaggers.get(sourceAppId);
            if (swaggerMap != null) {
                Map<String, Object> dependencyMap = mapper.convertValue(swaggerMap.get("x-serviceDependency"), new TypeReference<Map<String, Object>>(){});
                if (dependencyMap.get("httpRequest") != null) {
                    Map<String, Object> sourcePathMap = mapper.convertValue(dependencyMap.get("httpRequest"), new TypeReference<Map<String, Object>>(){});
                    sourcePathMap.forEach((sourcePathKey, sourcePathValue) -> {
                        if (sourcePathKey.equals("none")) {
                            Microservice sourceService = microserviceRepository.findByAppId(sourceAppId);
                            Object targets = mapper.convertValue(sourcePathValue, Map.class).get("targets");
                            addTargetEndpoints(sourceAppId, mapper.convertValue(targets, new TypeReference<Map<String, Object>>(){}), sourceService);
                        } else {
                            Map<String, Object> sourceMethodMap = mapper.convertValue(sourcePathValue, new TypeReference<Map<String, Object>>(){});
                            sourceMethodMap.forEach((sourceMethodKey, sourceMethodValue) -> {
                                String sourceEndpointId = sourceMethodKey + ":" + sourcePathKey;
                                Endpoint sourceEndpoint = endpointRepository.findByEndpointIdAndAppId(sourceEndpointId, sourceAppId);
                                Object targets = mapper.convertValue(sourceMethodValue, Map.class).get("targets");
                                addTargetEndpoints(sourceAppId, mapper.convertValue(targets, new TypeReference<Map<String, Object>>(){}), sourceEndpoint);
                            });
                        }
                    });
                }
            }
        });
    }

    private void addTargetEndpoints(String sourceAppId, Map<String, Object> targets, Microservice sourceService) {
        Set<Endpoint> targetEndpoints = getTargetEndpoints(sourceAppId, targets);
        for (Endpoint targetEndpoint: targetEndpoints) {
            sourceService.httpRequestToEndpoint(targetEndpoint);
        }
        microserviceRepository.save(sourceService);
    }

    private void addTargetEndpoints(String sourceAppId, Map<String, Object> targets, Endpoint sourceEndpoint) {
        Set<Endpoint> targetEndpoints = getTargetEndpoints(sourceAppId, targets);
        for (Endpoint targetEndpoint: targetEndpoints) {
            sourceEndpoint.httpRequestToEndpoint(targetEndpoint);
        }
        endpointRepository.save(sourceEndpoint);
    }

    private Set<Endpoint> getTargetEndpoints(String sourceAppId, Map<String, Object> targets) {
        Set<Endpoint> targetEndpoints = new HashSet<>();
        targets.forEach((targetServiceKey, targetServiceValue) -> {
            Map<String, Object> targetVersionMap = mapper.convertValue(targetServiceValue, new TypeReference<Map<String, Object>>(){});
            targetVersionMap.forEach((targetVersionKey, targetVersionValue) -> {
                Map<String, Object> targetPathMap = mapper.convertValue(targetVersionValue, new TypeReference<Map<String, Object>>(){});
                targetPathMap.forEach((targetPathKey, targetPathValue) -> {
                    ArrayList<String> targetMethods = (ArrayList<String>) targetPathValue;
                    for (String targetMethod: targetMethods) {
                        String targetEndpointId =  targetMethod + ":" + targetPathKey;
                        if (targetVersionKey.equals("notSpecified")) {
                            List<Endpoint> targetEndpoint = endpointRepository.findTargetEndpointNotSpecVer(
                                    sourceAppId, targetServiceKey.toUpperCase(), targetEndpointId
                            );
                            targetEndpoints.addAll(targetEndpoint);
                        } else {
                            Endpoint targetEndpoint = endpointRepository.findTargetEndpoint(
                                    sourceAppId, targetServiceKey.toUpperCase(), targetVersionKey,
                                    targetEndpointId);
                            targetEndpoints.add(targetEndpoint);
                        }
                    }
                });
            });
        });

        return targetEndpoints;
    }

    // Remove old apps that are not in refresh list
    private void removeOldService(HashMap<String, String> appIdsAndUrl, ArrayList<Microservice> appsInDB) {
        for (Microservice dbApp : appsInDB) {
            boolean isInRefreshList = false;
            for (Map.Entry<String, String> entry: appIdsAndUrl.entrySet()) {
                if (entry.getKey().equals(dbApp.getAppId())) {
                    isInRefreshList = true;
                }
            }
            if (!isInRefreshList) {
                microserviceRepository.deleteWithEndpointsByAppId(dbApp.getAppId());
                logger.info("Remove microservice: " + dbApp.getAppId());
            }
        }
    }

}
