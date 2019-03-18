package com.soselab.microservicegraphplatform.tasks;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.soselab.microservicegraphplatform.bean.actuators.Info;
import com.soselab.microservicegraphplatform.bean.eureka.AppInstance;
import com.soselab.microservicegraphplatform.bean.mgp.MgpApplication;
import com.soselab.microservicegraphplatform.bean.mgp.MgpInstance;
import com.soselab.microservicegraphplatform.bean.neo4j.*;
import com.soselab.microservicegraphplatform.bean.eureka.Application;
import com.soselab.microservicegraphplatform.bean.eureka.AppsList;
import com.soselab.microservicegraphplatform.repositories.*;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.*;

@Configuration
public class RefreshScheduledTask {

    private static final Logger logger = LoggerFactory.getLogger(RefreshScheduledTask.class);

    @Autowired
    private GeneralRepository generalRepository;
    @Autowired
    private ServiceRegistryRepository serviceRegistryRepository;
    @Autowired
    private InstanceRepository instanceRepository;
    @Autowired
    private ServiceRepository serviceRepository;
    @Autowired
    private EndpointRepository endpointRepository;

    private RestTemplate restTemplate = new RestTemplate();
    private ObjectMapper mapper = new ObjectMapper();

    private String graphJson;

    @PostConstruct
    private void init() {
        // Different sub system should have their own graph json.
        graphJson = generalRepository.getGraphJson();
    }

    @Scheduled(fixedDelay = 10000)
    public void run() {
        graphJson = refreshGraphDB() ? generalRepository.getGraphJson() : graphJson;
    }

    public String getGraphJson() {
        return graphJson;
    }

    private boolean refreshGraphDB() {
        boolean updated = false;
        // For each service registry
        ArrayList<ServiceRegistry> registries = serviceRegistryRepository.findAll();
        if (registries.size() > 0) {
            for (ServiceRegistry serviceRegistry : registries) {
                // Get latest app list by request the first instance that own by this service registry
                String scsName = serviceRegistry.getScsName();
                ArrayList<Instance> instances = instanceRepository.findByServiceRegistryAppId(serviceRegistry.getAppId());
                if (instances.size() > 0) {
                    Instance instance = instances.get(0);
                    try {
                        String url = "http://" + instance.getIpAddr() + ":" + instance.getPort() + "/eureka/apps/";
                        AppsList eurekaAppsList = restTemplate.getForObject(url, AppsList.class);
                        Map<String, Pair<MgpApplication, Integer>> eurekaAppsInfoAndNum = getAppsInfoAndNumFromEurekaAppList(scsName, eurekaAppsList);
                        List<Service> ServicesInDB = serviceRepository.findByScsName(serviceRegistry.getScsName());
                        List<NullService> nullServiceInDB = serviceRepository.findNullByScsName(serviceRegistry.getScsName());
                        // Check the service should be created or updated or removed in graph DB.
                        Map<String, Pair<MgpApplication, Integer>> newAppsMap = new HashMap<>();
                        Map<String, Pair<MgpApplication, Integer>> recoveryAppsMap = new HashMap<>();
                        Map<String, Pair<MgpApplication, Integer>> updateAppsMap;
                        Set<String> removeAppsSet = new HashSet<>();
                        // Find new apps and recover apps, then remove from eurekaAppsInfoAndNum.
                        for (Iterator<Map.Entry<String, Pair<MgpApplication, Integer>>> it = eurekaAppsInfoAndNum.entrySet().iterator(); it.hasNext();) {
                            Map.Entry<String, Pair<MgpApplication, Integer>> entry = it.next();
                            boolean isInDB = false;
                            boolean isNull = false;
                            for (Service dbApp : ServicesInDB) {
                                if (entry.getKey().equals(dbApp.getAppId())) {
                                    isInDB = true;
                                    break;
                                }
                            }
                            for (NullService nullDbApp: nullServiceInDB) {
                                if (entry.getKey().equals(nullDbApp.getAppId())) {
                                    isNull = true;
                                    break;
                                }
                            }
                            if (!isInDB) {
                                if (isNull) {
                                    recoveryAppsMap.put(entry.getKey(), entry.getValue());
                                } else {
                                    newAppsMap.put(entry.getKey(), entry.getValue());
                                }
                                it.remove();
                            }
                        }
                        // The remaining apps in eurekaAppsInfoAndNum should be updated.
                        updateAppsMap = eurekaAppsInfoAndNum;
                        // Find apps not in eurekaAppsInfoAndNum.
                        for (Service dbApp : ServicesInDB) {
                            boolean isInRefreshList = false;
                            for (Map.Entry<String, Pair<MgpApplication, Integer>> entry: eurekaAppsInfoAndNum.entrySet()) {
                                if (dbApp.getAppId().equals(entry.getKey())) {
                                    isInRefreshList = true;
                                    break;
                                }
                            }
                            if (!isInRefreshList) {
                                removeAppsSet.add(dbApp.getAppId());
                            }
                        }

                        // Update the dependency graph.
                        Map<String, Map<String, Object>> appSwaggers = addServices(serviceRegistry, newAppsMap);
                        appSwaggers.putAll(recoverServices(serviceRegistry, recoveryAppsMap));
                        newAppsMap.putAll(recoveryAppsMap);
                        addDependency(newAppsMap, appSwaggers);
                        boolean appsUpdated = updateServices(updateAppsMap);
                        removeServices(removeAppsSet);

                        // If the graph was be updated then return true.
                        if (newAppsMap.size() > 0 || removeAppsSet.size() > 0 || appsUpdated) {
                            updated = true;
                        }
                    } catch (ResourceAccessException e) {
                        logger.error(e.getMessage(), e);
                    }
                }
            }
        }

        return updated;
    }

    // Map<appId, Pair<appInfo, number of apps>>
    private Map<String, Pair<MgpApplication, Integer>> getAppsInfoAndNumFromEurekaAppList(String scsName, AppsList appsList) {
        Map<String, Pair<MgpApplication, Integer>> appInfoAndNum = new HashMap<>();
        ArrayList<Application> apps = appsList.getApplications().getApplication();
        for (Application app: apps) {
            String appName = app.getName();
            for (AppInstance instance: app.getInstance()) {
                if (instance.getStatus().equals("UP")) {
                    String url = "http://" + instance.getIpAddr() + ":" + instance.getPort().get$();
                    String version = restTemplate.getForObject( url + "/info", Info.class).getVersion();
                    String appId = scsName + ":" + appName + ":" + version;
                    Pair<MgpApplication, Integer> appInfo= appInfoAndNum.get(appId);
                    if (appInfo == null) {
                        MgpInstance mgpInstance = new MgpInstance(instance.getHostName(), appName, instance.getIpAddr(), instance.getPort().get$());
                        MgpApplication mgpApplication = new MgpApplication(scsName, appName, version, new ArrayList<>());
                        mgpApplication.addInstance(mgpInstance);
                        appInfo = new MutablePair<>(mgpApplication, 1);
                        appInfoAndNum.put(appId, appInfo);
                    } else {
                        appInfo.setValue(appInfo.getValue() + 1);
                        appInfoAndNum.put(appId, appInfo);
                    }
                } /*else if (instance.getStatus().equals("DOWN")){
                    // Do something when found a "DOWN" service.
                }*/
            }
        }

        return appInfoAndNum;
    }

    // Add new apps to neo4j
    private Map<String, Map<String, Object>> addServices(ServiceRegistry serviceRegistry, Map<String, Pair<MgpApplication, Integer>> newAppsMap) {
        Map<String, Map<String, Object>> appSwaggers = new HashMap<>();
        // Add services to graph DB.
        newAppsMap.forEach((appId, appInfoAndNum) -> {
            MgpInstance instance = appInfoAndNum.getKey().getInstances().get(0);
            String serviceUrl = "http://" + instance.getIpAddr() + ":" + instance.getPort();
            Map<String, Object> swaggerMap = getSwaggerMap(serviceUrl);
            if (swaggerMap != null) {
                appSwaggers.put(appId, swaggerMap);
                MgpApplication app = appInfoAndNum.getKey();
                Service service = new Service(app.getScsName(), app.getAppName(), app.getVersion(), appInfoAndNum.getValue());
                service.registerTo(serviceRegistry);
                Map<String, Object> pathsMap = mapper.convertValue(swaggerMap.get("paths"), new TypeReference<Map<String, Object>>(){});
                pathsMap.forEach((pathKey, pathValue) -> {
                    Map<String, Object> methodMap = mapper.convertValue(pathValue, new TypeReference<Map<String, Object>>(){});
                    methodMap.forEach((methodKey, methodValue) -> {
                        Endpoint endpoint = new Endpoint(app.getAppName(), methodKey, pathKey);
                        service.ownEndpoint(endpoint);
                    });
                });
                serviceRepository.save(service);
                logger.info("Add service: " + service.getAppId());
            }
        });

        return appSwaggers;
    }

    // Recover apps in neo4j
    private Map<String, Map<String, Object>> recoverServices(ServiceRegistry serviceRegistry, Map<String, Pair<MgpApplication, Integer>> recoveryAppsMap) {
        Map<String, Map<String, Object>> appSwaggers = new HashMap<>();
        // Add services to graph DB.
        // CASE: Replace services
        recoveryAppsMap.forEach((appId, appInfoAndNum) -> {
            MgpInstance instance = appInfoAndNum.getKey().getInstances().get(0);
            String serviceUrl = "http://" + instance.getIpAddr() + ":" + instance.getPort();
            Map<String, Object> swaggerMap = getSwaggerMap(serviceUrl);
            if (swaggerMap != null) {
                appSwaggers.put(appId, swaggerMap);
                serviceRepository.removeNullLabelAndSetNumByAppId(appId, appInfoAndNum.getValue());
                List<Endpoint> nullEndpoints = endpointRepository.findByAppId(appId);
                List<Endpoint> newEndpoints = new ArrayList<>();
                Map<String, Object> pathsMap = mapper.convertValue(swaggerMap.get("paths"), new TypeReference<Map<String, Object>>(){});
                pathsMap.forEach((path, pathValue) -> {
                    Map<String, Object> methodMap = mapper.convertValue(pathValue, new TypeReference<Map<String, Object>>(){});
                    methodMap.forEach((method, methodValue) -> {
                        boolean recoveryEndpoint = false;
                        for (Endpoint nullEndpoint: nullEndpoints) {
                            if (path.equals(nullEndpoint.getPath()) && method.equals(nullEndpoint.getMethod())) {
                                endpointRepository.removeNullLabelByAppIdAAndEndpointId(appId, method + ":" + path);
                                recoveryEndpoint = true;
                                break;
                            }
                        }
                        if (!recoveryEndpoint) {
                            Endpoint endpoint = new Endpoint(appInfoAndNum.getKey().getAppName(), method, path);
                            newEndpoints.add(endpoint);
                        }
                    });
                });
                Service service = serviceRepository.findByAppId(appId);
                service.registerTo(serviceRegistry);
                service.ownEndpoint(newEndpoints);
                serviceRepository.save(service);
                logger.info("Recover service: " + service.getAppId());
            }
        });

        return appSwaggers;
    }

    private Map<String, Object> getSwaggerMap(String serviceUrl) {
        String swagger = restTemplate.getForObject(serviceUrl + "/v2/api-docs", String.class);
        Map<String, Object> swaggerMap = null;
        try {
            swaggerMap = mapper.readValue(swagger, new TypeReference<Map<String, Object>>(){});
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
        return swaggerMap;
    }

    // Add dependency relationships to neo4j
    private void addDependency(Map<String, Pair<MgpApplication, Integer>> newAndRecoveryAppsMap, Map<String, Map<String, Object>> appSwaggers) {
        newAndRecoveryAppsMap.forEach((sourceAppId, sourceAppInfo) -> {
            Map<String, Object> swaggerMap = appSwaggers.get(sourceAppId);
            if (swaggerMap != null) {
                Map<String, Object> dependencyMap = mapper.convertValue(swaggerMap.get("x-serviceDependency"), new TypeReference<Map<String, Object>>(){});
                if (dependencyMap.get("httpRequest") != null) {
                    Map<String, Object> sourcePathMap = mapper.convertValue(dependencyMap.get("httpRequest"), new TypeReference<Map<String, Object>>(){});
                    sourcePathMap.forEach((sourcePathKey, sourcePathValue) -> {
                        if (sourcePathKey.equals("none")) {
                            Service sourceService = serviceRepository.findByAppId(sourceAppId);
                            Object targets = mapper.convertValue(sourcePathValue, Map.class).get("targets");
                            addTargetEndpoints(sourceService, mapper.convertValue(targets, new TypeReference<Map<String, Object>>(){}));
                        } else {
                            Map<String, Object> sourceMethodMap = mapper.convertValue(sourcePathValue, new TypeReference<Map<String, Object>>(){});
                            sourceMethodMap.forEach((sourceMethodKey, sourceMethodValue) -> {
                                String sourceEndpointId = sourceMethodKey + ":" + sourcePathKey;
                                Endpoint sourceEndpoint = endpointRepository.findByEndpointIdAndAppId(sourceEndpointId, sourceAppId);
                                Object targets = mapper.convertValue(sourceMethodValue, Map.class).get("targets");
                                addTargetEndpoints(sourceAppId, sourceEndpoint, mapper.convertValue(targets, new TypeReference<Map<String, Object>>(){}));
                            });
                        }
                    });
                }
            }
        });
    }

    private void addTargetEndpoints(Service sourceService, Map<String, Object> targets) {
        Set<Endpoint> targetEndpoints = getTargetEndpoints(sourceService.getAppId(), targets);
        for (Endpoint targetEndpoint: targetEndpoints) {
            sourceService.httpRequestToEndpoint(targetEndpoint);
        }
        serviceRepository.save(sourceService);
    }

    private void addTargetEndpoints(String sourceAppId, Endpoint sourceEndpoint, Map<String, Object> targets) {
        Set<Endpoint> targetEndpoints = getTargetEndpoints(sourceAppId, targets);
        for (Endpoint targetEndpoint: targetEndpoints) {
            sourceEndpoint.httpRequestToEndpoint(targetEndpoint);
        }
        endpointRepository.save(sourceEndpoint);
    }

    private Set<Endpoint> getTargetEndpoints(String sourceAppId, Map<String, Object> targets) {
        Set<Endpoint> targetEndpoints = new HashSet<>();
        targets.forEach((targetServiceKey, targetServiceValue) -> {
            String targetServiceName = targetServiceKey.toUpperCase();
            Map<String, Object> targetVersionMap =
                    mapper.convertValue(targetServiceValue, new TypeReference<Map<String, Object>>(){});
            targetVersionMap.forEach((targetVersionKey, targetVersionValue) -> {
                Map<String, Object> targetPathMap =
                        mapper.convertValue(targetVersionValue, new TypeReference<Map<String, Object>>(){});
                targetPathMap.forEach((targetPathKey, targetPathValue) -> {
                    ArrayList<String> targetMethods = (ArrayList<String>) targetPathValue;
                    for (String targetMethod: targetMethods) {
                        String targetEndpointId =  targetMethod + ":" + targetPathKey;
                        if (targetVersionKey.equals("notSpecified")) {
                            List<Endpoint> targetEndpoint = endpointRepository.findTargetEndpointNotSpecVer(
                                    sourceAppId, targetServiceName, targetEndpointId
                            );
                            targetEndpoints.addAll(nullTargetDetector(targetEndpoint, sourceAppId, targetServiceName,
                                    targetMethod, targetPathKey));
                        } else {
                            Endpoint targetEndpoint = endpointRepository.findTargetEndpoint(
                                    sourceAppId, targetServiceName, targetVersionKey,
                                    targetEndpointId);
                            targetEndpoints.add(nullTargetDetector(targetEndpoint, sourceAppId, targetServiceName,
                                    targetMethod, targetPathKey, targetVersionKey));
                        }
                    }
                });
            });
        });

        return targetEndpoints;
    }

    private List<Endpoint> nullTargetDetector(List<Endpoint> targetEndpoints, String sourceAppId, String targetName,
                                              String targetMethod, String targetPath) {
        if (targetEndpoints != null && targetEndpoints.size() > 0) {
            // Normal situation
            return targetEndpoints;
        } else {
            List<Endpoint> nullEndpoints = new ArrayList<>();
            List<Service> targetServices = serviceRepository.findByAppNameInSameSys(
                    sourceAppId, targetName);
            if (targetServices != null && targetServices.size() > 0) {
                // Found null target endpoints.
                for (Service targetService: targetServices) {
                    Endpoint nullTargetEndpoint = new NullEndpoint(targetName, targetMethod, targetPath);
                    nullTargetEndpoint.ownBy(targetService);
                    nullEndpoints.add(nullTargetEndpoint);
                    logger.info("Found null endpoint: " + nullTargetEndpoint.getEndpointId());
                }
            } else {
                // Found null target service and endpoint.
                Service nullTargetService = new NullService(
                        sourceAppId.split(":")[0], targetName, null, 0);
                Endpoint nullTargetEndpoint = new NullEndpoint(targetName, targetMethod, targetPath);
                nullTargetEndpoint.ownBy(nullTargetService);
                nullEndpoints.add(nullTargetEndpoint);
                logger.info("Found null service and endpoint: " + nullTargetService.getAppId() + " " + nullTargetEndpoint.getEndpointId());
            }
            return nullEndpoints;
        }
    }

    private Endpoint nullTargetDetector(Endpoint targetEndpoint, String sourceAppId, String targetName,
                                        String targetMethod, String targetPath, String targetVersion) {
        if (targetEndpoint != null) {
            // Normal situation
            return targetEndpoint;
        } else {
            String targetAppId = sourceAppId.split(":")[0] + ":" + targetName + ":" + targetVersion;
            String targetEndpointId = targetMethod + ":" + targetPath;
            Service targetService = serviceRepository.findByAppId(targetAppId);
            Endpoint nullTargetEndpoint;
            if (targetService != null) {
                // Found null target endpoint.
                nullTargetEndpoint = endpointRepository.findByNullEndpointAndAppId(targetEndpointId, targetService.getAppId());
                if (nullTargetEndpoint == null) {
                    nullTargetEndpoint = new NullEndpoint(targetName, targetMethod, targetPath);
                    nullTargetEndpoint.ownBy(targetService);
                }
                logger.info("Found null endpoint: " + nullTargetEndpoint.getEndpointId());
            } else {
                // Found null target service and endpoint.
                Service nullTargetService = new NullService(
                        sourceAppId.split(":")[0], targetName, targetVersion, 0
                );
                nullTargetEndpoint = new NullEndpoint(targetName, targetMethod, targetPath);
                nullTargetEndpoint.ownBy(nullTargetService);
                logger.info("Found null service and endpoint: " + nullTargetService.getAppId() + " " + nullTargetEndpoint.getEndpointId());
            }
            return nullTargetEndpoint;
        }
    }

    private boolean updateServices(Map<String, Pair<MgpApplication, Integer>> updateAppsMap) {
        boolean updated = false;
        for (Map.Entry<String, Pair<MgpApplication, Integer>> entry: updateAppsMap.entrySet()) {
            if (!serviceRepository.setNumberByAppId(entry.getKey(), entry.getValue().getValue())){
                updated = true;
            }
        }

        return updated;
    }

    // Remove apps that are not in eureka's app list
    private void removeServices(Set<String> removeAppsSet) {
        for (String appId : removeAppsSet) {
            if (serviceRepository.isBeDependentByAppId(appId)) {
                serviceRepository.addNullLabelWithEndpointsByAppId(appId);
            } else {
                serviceRepository.deleteWithEndpointsByAppId(appId);
            }
            logger.info("Remove service: " + appId);
        }
        if (removeAppsSet.size() > 0) {
            endpointRepository.deleteUselessNullEndpoint();
            serviceRepository.deleteUselessNullService();
        }
    }

}
