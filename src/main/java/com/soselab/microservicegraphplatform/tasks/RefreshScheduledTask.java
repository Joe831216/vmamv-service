package com.soselab.microservicegraphplatform.tasks;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.soselab.microservicegraphplatform.bean.actuators.Info;
import com.soselab.microservicegraphplatform.bean.eureka.AppInstance;
import com.soselab.microservicegraphplatform.bean.neo4j.*;
import com.soselab.microservicegraphplatform.bean.eureka.Application;
import com.soselab.microservicegraphplatform.bean.eureka.AppsList;
import com.soselab.microservicegraphplatform.repositories.*;
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

    private boolean graphUpdated = false;
    private String graphJson;

    @PostConstruct
    private void init() {
        graphJson = generalRepository.getGraphJson();
    }

    @Scheduled(fixedDelay = 10000)
    public void run() {
        refreshGraphDB();
        graphJson = graphUpdated ? generalRepository.getGraphJson() : graphJson;
    }

    public String getGraphJson() {
        return graphJson;
    }

    private void refreshGraphDB() {
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
                        HashMap<String, String> eurekaAppIdsAndUrl = getAppIdsAndUrlFromEurekaAppList(scsName, eurekaAppsList);
                        List<Service> ServicesInDB = serviceRepository.findByScsName(serviceRegistry.getScsName());
                        List<NullService> nullServiceInDB = serviceRepository.findNullByScsName(serviceRegistry.getScsName());
                        addNewService(serviceRegistry, eurekaAppIdsAndUrl, ServicesInDB, nullServiceInDB);
                        removeOldService(eurekaAppIdsAndUrl, ServicesInDB);
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
    private void addNewService(ServiceRegistry serviceRegistry, HashMap<String, String> eurekaAppIdsAndUrl, List<Service> appsInDB, List<NullService> nullAppInDB) {
        // Check the service should be created or replaced in graph DB.
        Map<String, String> newAppIdsAndUrl = new HashMap<>();
        Map<String, String> replaceIdsAndUrl = new HashMap<>();
        eurekaAppIdsAndUrl.forEach((appId, url) -> {
            boolean isInDB = false;
            boolean isNull = false;
            for (Service dbApp : appsInDB) {
                if (appId.equals(dbApp.getAppId())) {
                    isInDB = true;
                    break;
                }
            }
            for (NullService nullDbApp: nullAppInDB) {
                if (appId.equals(nullDbApp.getAppId())) {
                    isNull = true;
                    break;
                }
            }
            if (!isInDB) {
                if (isNull) {
                    replaceIdsAndUrl.put(appId, url);
                } else {
                    newAppIdsAndUrl.put(appId, url);
                }
            }
        });
        graphUpdated = !newAppIdsAndUrl.isEmpty() || !replaceIdsAndUrl.isEmpty();
        Map<String, Map<String, Object>> appSwaggers = new HashMap<>();
        // Add services to graph DB.
        // CASE: Add services
        newAppIdsAndUrl.forEach((appId, url) -> {
            Map<String, Object> swaggerMap = getSwaggerMap(url);
            if (swaggerMap != null) {
                appSwaggers.put(appId, swaggerMap);
                String[] appIdInfo = appId.split(":");
                Service service = new Service(appIdInfo[0], appIdInfo[1], appIdInfo[2]);
                service.registerTo(serviceRegistry);
                Map<String, Object> pathsMap = mapper.convertValue(swaggerMap.get("paths"), new TypeReference<Map<String, Object>>(){});
                pathsMap.forEach((pathKey, pathValue) -> {
                    Map<String, Object> methodMap = mapper.convertValue(pathValue, new TypeReference<Map<String, Object>>(){});
                    methodMap.forEach((methodKey, methodValue) -> {
                        Endpoint endpoint = new Endpoint(appIdInfo[1], methodKey, pathKey);
                        service.ownEndpoint(endpoint);
                    });
                });
                serviceRepository.save(service);
                logger.info("Add service: " + service.getAppId());
            }
        });
        // CASE: Replace services
        replaceIdsAndUrl.forEach((appId, url) -> {
            Map<String, Object> swaggerMap = getSwaggerMap(url);
            if (swaggerMap != null) {
                appSwaggers.put(appId, swaggerMap);
                serviceRepository.removeNullLabelByAppId(appId);
                List<Endpoint> nullEndpoints = endpointRepository.findByAppId(appId);
                List<Endpoint> newEndpoints = new ArrayList<>();
                Map<String, Object> pathsMap = mapper.convertValue(swaggerMap.get("paths"), new TypeReference<Map<String, Object>>(){});
                pathsMap.forEach((path, pathValue) -> {
                    Map<String, Object> methodMap = mapper.convertValue(pathValue, new TypeReference<Map<String, Object>>(){});
                    methodMap.forEach((method, methodValue) -> {
                        boolean endpointReplaced = false;
                        for (Endpoint nullEndpoint: nullEndpoints) {
                            if (path.equals(nullEndpoint.getPath()) && method.equals(nullEndpoint.getMethod())) {
                                endpointRepository.removeNullLabelByAppIdAAndEndpointId(appId, method + ":" + path);
                                endpointReplaced = true;
                                break;
                            }
                        }
                        if (!endpointReplaced) {
                            Endpoint endpoint = new Endpoint(appId.split(":")[1], method, path);
                            newEndpoints.add(endpoint);
                        }
                    });
                });
                Service service = serviceRepository.findByAppId(appId);
                service.ownEndpoint(newEndpoints);
                serviceRepository.save(service);
                logger.info("Replace service: " + service.getAppId());
            }
        });
        newAppIdsAndUrl.putAll(replaceIdsAndUrl);
        // Add dependency relationships to graph DB.
        newAppIdsAndUrl.forEach((sourceAppId, sourceAppUrl) -> {
            Map<String, Object> swaggerMap = appSwaggers.get(sourceAppId);
            if (swaggerMap != null) {
                Map<String, Object> dependencyMap = mapper.convertValue(swaggerMap.get("x-serviceDependency"), new TypeReference<Map<String, Object>>(){});
                if (dependencyMap.get("httpRequest") != null) {
                    Map<String, Object> sourcePathMap = mapper.convertValue(dependencyMap.get("httpRequest"), new TypeReference<Map<String, Object>>(){});
                    sourcePathMap.forEach((sourcePathKey, sourcePathValue) -> {
                        if (sourcePathKey.equals("none")) {
                            Service sourceService = serviceRepository.findByAppId(sourceAppId);
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

    private Map<String, Object> getSwaggerMap(String url) {
        String swagger = restTemplate.getForObject(url + "/v2/api-docs", String.class);
        Map<String, Object> swaggerMap = null;
        try {
            swaggerMap = mapper.readValue(swagger, new TypeReference<Map<String, Object>>(){});
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
        return swaggerMap;
    }

    private void addTargetEndpoints(String sourceAppId, Map<String, Object> targets, Service sourceService) {
        Set<Endpoint> targetEndpoints = getTargetEndpoints(sourceAppId, targets);
        for (Endpoint targetEndpoint: targetEndpoints) {
            sourceService.httpRequestToEndpoint(targetEndpoint);
        }
        serviceRepository.save(sourceService);
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
                        sourceAppId.split(":")[0], targetName, null);
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
                        sourceAppId.split(":")[0], targetName, targetVersion
                );
                nullTargetEndpoint = new NullEndpoint(targetName, targetMethod, targetPath);
                nullTargetEndpoint.ownBy(nullTargetService);
                logger.info("Found null service and endpoint: " + nullTargetService.getAppId() + " " + nullTargetEndpoint.getEndpointId());
            }
            return nullTargetEndpoint;
        }
    }

    // Remove old apps that are not in refresh list
    private void removeOldService(HashMap<String, String> appIdsAndUrl, List<Service> appsInDB) {
        for (Service dbApp : appsInDB) {
            boolean isInRefreshList = false;
            for (Map.Entry<String, String> entry: appIdsAndUrl.entrySet()) {
                if (entry.getKey().equals(dbApp.getAppId())) {
                    isInRefreshList = true;
                    break;
                }
            }
            if (!isInRefreshList) {
                String appId = dbApp.getAppId();
                if (serviceRepository.isBeDependentByAppId(appId)) {
                    serviceRepository.addNullLabelWithEndpointsByAppId(appId);
                } else {
                    serviceRepository.deleteWithEndpointsByAppId(dbApp.getAppId());
                }
                graphUpdated = true;
                logger.info("Remove service: " + dbApp.getAppId());
            }
        }
        endpointRepository.deleteUselessNullEndpoint();
        serviceRepository.deleteUselessNullService();
    }

}
