package com.soselab.microservicegraphplatform.tasks;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.primitives.Ints;
import com.soselab.microservicegraphplatform.bean.actuators.Info;
import com.soselab.microservicegraphplatform.bean.eureka.AppInstance;
import com.soselab.microservicegraphplatform.bean.eureka.AppList;
import com.soselab.microservicegraphplatform.bean.mgp.MgpApplication;
import com.soselab.microservicegraphplatform.bean.mgp.MgpInstance;
import com.soselab.microservicegraphplatform.bean.neo4j.*;
import com.soselab.microservicegraphplatform.bean.eureka.Application;
import com.soselab.microservicegraphplatform.bean.eureka.AppsList;
import com.soselab.microservicegraphplatform.bean.neo4j.Queue;
import com.soselab.microservicegraphplatform.controllers.WebPageController;
import com.soselab.microservicegraphplatform.repositories.*;
import org.apache.commons.lang3.tuple.ImmutablePair;
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
    @Autowired
    private QueueRepository queueRepository;
    @Autowired
    private WebPageController webPageController;

    private RestTemplate restTemplate = new RestTemplate();
    private ObjectMapper mapper = new ObjectMapper();

    private Map<String, String> graphJson = new HashMap<>();

    @PostConstruct
    private void init() {
        // Different sub system should have their own graph json.
        List<String> systemNames = generalRepository.getAllSystemName();
        for (String systemName : systemNames) {
            graphJson.put(systemName, generalRepository.getSystemGraphJson(systemName));
        }
        //graphJson = generalRepository.getGraphJson();
    }

    @Scheduled(fixedDelay = 10000)
    public void run() {
        Map<String, Boolean> systemIsUpdatedMap = updateGraphDB();
        systemIsUpdatedMap.forEach((systemName, isUpdated) -> {
            if (isUpdated) {
                graphJson.put(systemName, generalRepository.getSystemGraphJson(systemName));
                webPageController.sendGraph(systemName, graphJson.get(systemName));
            }
        });
    }

    public String getGraphJson(String systemName) {
        return graphJson.get(systemName);
    }

    private Map<String, Boolean> updateGraphDB() {
        Map<String, Boolean> updated = new HashMap<>();
        // For each service registry
        ArrayList<ServiceRegistry> registries = serviceRegistryRepository.findAll();
        if (registries.size() > 0) {
            for (ServiceRegistry serviceRegistry : registries) {
                // Get latest app list by request the first instance that own by this service registry
                String systemName = serviceRegistry.getSystemName();
                ArrayList<Instance> instances = instanceRepository.findByServiceRegistryAppId(serviceRegistry.getAppId());
                if (instances.size() > 0) {
                    Instance instance = instances.get(0);
                    try {
                        String url = "http://" + instance.getIpAddr() + ":" + instance.getPort() + "/eureka/apps/";
                        AppsList eurekaAppsList = restTemplate.getForObject(url, AppsList.class);
                        Map<String, Pair<MgpApplication, Integer>> eurekaAppsInfoAndNum = getAppsInfoAndNumFromEurekaAppList(systemName, eurekaAppsList);
                        List<Service> ServicesInDB = serviceRepository.findBySysName(serviceRegistry.getSystemName());
                        List<NullService> nullServiceInDB = serviceRepository.findNullBySysName(serviceRegistry.getSystemName());
                        // Check the service should be created or updated or removed in graph DB.
                        Map<String, Pair<MgpApplication, Integer>> newAppsMap = new HashMap<>();
                        Map<String, Pair<MgpApplication, Integer>> recoveryAppsMap = new HashMap<>();
                        Map<String, Pair<MgpApplication, Integer>> updateAppsMap;
                        Set<String> removeAppsSet = new HashSet<>();
                        // Find new apps and recover apps, then remove from eurekaAppsInfoAndNum.
                        for (Iterator<Map.Entry<String, Pair<MgpApplication, Integer>>> it = eurekaAppsInfoAndNum.entrySet().iterator(); it.hasNext();) {
                            Map.Entry<String, Pair<MgpApplication, Integer>> entry = it.next();
                            boolean isUpInDB = false;
                            boolean isNull = false;
                            for (Service dbApp : ServicesInDB) {
                                if (entry.getKey().equals(dbApp.getAppId())) {
                                    isUpInDB = true;
                                    break;
                                }
                            }
                            for (NullService nullDbApp: nullServiceInDB) {
                                if (entry.getKey().equals(nullDbApp.getAppId())) {
                                    isNull = true;
                                    break;
                                }
                            }
                            if (!isUpInDB) {
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
                        addDependencies(newAppsMap, appSwaggers);

                        boolean appsUpdated = updateServices(updateAppsMap);
                        removeServices(removeAppsSet);

                        // If the graph was be updated then return true.
                        if (newAppsMap.size() > 0 || removeAppsSet.size() > 0 || appsUpdated) {
                            updated.put(systemName, true);
                        } else {
                            updated.put(systemName, false);
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
    private Map<String, Pair<MgpApplication, Integer>> getAppsInfoAndNumFromEurekaAppList(String systemName, AppsList appsList) {
        Map<String, Pair<MgpApplication, Integer>> appInfoAndNum = new HashMap<>();
        ArrayList<Application> apps = appsList.getApplications().getApplication();
        for (Application app: apps) {
            String appName = app.getName();
            for (AppInstance instance: app.getInstance()) {
                if (instance.getStatus().equals("UP")) {
                    String url = "http://" + instance.getIpAddr() + ":" + instance.getPort().get$();
                    String version = getVersionFromRemoteApp(url);
                    // Ignore this service if can't get the version.
                    if (version == null) {
                        continue;
                    }
                    String appId = systemName + ":" + appName + ":" + version;
                    Pair<MgpApplication, Integer> appInfo= appInfoAndNum.get(appId);
                    if (appInfo == null) {
                        MgpInstance mgpInstance = new MgpInstance(instance.getHostName(), appName, instance.getIpAddr(), instance.getPort().get$());
                        MgpApplication mgpApplication = new MgpApplication(systemName, appName, version, new ArrayList<>());
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

    private MgpApplication getAppFromEureka(ServiceRegistry serviceRegistry, String appName, String version) {
        List<Instance> registryInstance = instanceRepository.findByServiceRegistryAppId(serviceRegistry.getAppId());
        String registryUrl = "http://" + registryInstance.get(0).getIpAddr() + ":" + registryInstance.get(0).getPort() + "/eureka/apps/" + appName;
        AppList eurekaApp = restTemplate.getForObject
                (registryUrl, AppList.class);
        MgpApplication mgpApplication = null;
        for (AppInstance instance: eurekaApp.getApplication().getInstance()) {
            if (instance.getStatus().equals("UP")) {
                String url = "http://" + instance.getIpAddr() + ":" + instance.getPort().get$();
                String ver = restTemplate.getForObject( url + "/info", Info.class).getVersion();
                if (ver.equals(version)) {
                    MgpInstance mgpInstance = new MgpInstance(instance.getHostName(), appName, instance.getIpAddr(), instance.getPort().get$());
                    mgpApplication = new MgpApplication(serviceRegistry.getSystemName(), appName, version, new ArrayList<>());
                    mgpApplication.addInstance(mgpInstance);
                }
            }
        }

        return mgpApplication;
    }

    private String getVersionFromRemoteApp(String serviceUrl) {
        try {
            return restTemplate.getForObject( serviceUrl + "/info", Info.class).getVersion();
        } catch (ResourceAccessException e) {
            logger.error(e.getMessage(), e);
            return null;
        }
    }

    private Map<String, Object> getSwaggerMapFromRemoteApp(String serviceUrl) {
        String swagger = restTemplate.getForObject(serviceUrl + "/v2/api-docs", String.class);
        Map<String, Object> swaggerMap = null;
        try {
            swaggerMap = mapper.readValue(swagger, new TypeReference<Map<String, Object>>(){});
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
        return swaggerMap;
    }

    // Add new apps to neo4j
    private Map<String, Map<String, Object>> addServices(ServiceRegistry serviceRegistry, Map<String, Pair<MgpApplication, Integer>> newAppsMap) {
        Map<String, Map<String, Object>> appSwaggers = new HashMap<>();
        Map<String, Pair<MgpApplication, Integer>> updateDependencyAppsMap = new HashMap<>();
        Map<String, Map<String, Object>> updateDependencyAppsSwagger = new HashMap<>();
        // Add services to graph DB.
        newAppsMap.forEach((appId, appInfoAndNum) -> {
            MgpInstance instance = appInfoAndNum.getKey().getInstances().get(0);
            String serviceUrl = "http://" + instance.getIpAddr() + ":" + instance.getPort();
            Map<String, Object> swaggerMap = getSwaggerMapFromRemoteApp(serviceUrl);
            if (swaggerMap != null) {
                appSwaggers.put(appId, swaggerMap);
                MgpApplication app = appInfoAndNum.getKey();
                Service service = new Service(app.getSystemName(), app.getAppName(), app.getVersion(), appInfoAndNum.getValue());
                service.registerTo(serviceRegistry);
                Map<String, Object> pathsMap = mapper.convertValue(swaggerMap.get("paths"), new TypeReference<Map<String, Object>>(){});
                pathsMap.forEach((pathKey, pathValue) -> {
                    Map<String, Object> methodMap = mapper.convertValue(pathValue, new TypeReference<Map<String, Object>>(){});
                    methodMap.forEach((methodKey, methodValue) -> {
                        Endpoint endpoint = new Endpoint(serviceRegistry.getSystemName(), app.getAppName(), methodKey, pathKey);
                        service.ownEndpoint(endpoint);
                    });
                });
                // Find newer patch version service.
                Service newerPatchService = newerPatchVersionDetector(appInfoAndNum.getKey());
                if (newerPatchService != null) {
                    service.addLabel("OutdatedVersion");
                    service.foundNewPatchVersion(newerPatchService);
                } else {
                    List<Service> olderPatchServices = olderPatchVersionDetector(appInfoAndNum.getKey());
                    for (Service olderPatchService : olderPatchServices) {
                        serviceRepository.addOutdatedVersionLabelAndDeleteNewrPatchVerRelByAppId(olderPatchService.getAppId());
                    }
                    service.foundOldPatchVersions(olderPatchServices);
                }
                serviceRepository.save(service);
                logger.info("Add service: " + service.getAppId());
            }
            List<Pair<MgpApplication, Map<String, Object>>> dependencyDetectResult = dependencyDetector(serviceRegistry, appInfoAndNum.getKey());
            for (Pair<MgpApplication, Map<String, Object>> app : dependencyDetectResult) {
                updateDependencyAppsMap.put(app.getKey().getAppId(), new MutablePair<>(app.getKey(), null));
                updateDependencyAppsSwagger.put(app.getKey().getAppId(), app.getValue());
            }
            updateDependencies(updateDependencyAppsMap, updateDependencyAppsSwagger);
        });

        return appSwaggers;
    }

    // Find services that is caused by an input service and needs to update dependencies.
    // List<Pair<App info, Swagger>> Apps
    private List<Pair<MgpApplication, Map<String, Object>>> dependencyDetector(ServiceRegistry serviceRegistry, MgpApplication mgpApplication) {
        List<Pair<MgpApplication, Map<String, Object>>> updateDependencyApps = new ArrayList<>();
        Service noVerNullApp = serviceRepository.findNullByAppId(mgpApplication.getSystemName() + ":" + mgpApplication.getAppName() + ":null");
        if (noVerNullApp != null) {
            List<Service> dependentApps = serviceRepository.findDependentOnThisAppByAppId(noVerNullApp.getAppId());
            for (Service dependentApp : dependentApps) {
                MgpApplication appInfo = getAppFromEureka(serviceRegistry, dependentApp.getAppName(), dependentApp.getVersion());
                String ipAddr = appInfo.getInstances().get(0).getIpAddr();
                int port = appInfo.getInstances().get(0).getPort();
                String serviceUrl = "http://" + ipAddr + ":" + port;
                Map<String, Object> swaggerMap = getSwaggerMapFromRemoteApp(serviceUrl);
                updateDependencyApps.add(new MutablePair<>(appInfo, swaggerMap));
            }
        } else {
            Service otherVerApp = serviceRepository.findOtherVerInSameSysBySysNameAndAppNameAndVersion
                    (mgpApplication.getSystemName(), mgpApplication.getAppName(), mgpApplication.getVersion());
            if (otherVerApp != null) {
                List<Service> dependentApps = serviceRepository.findDependentOnThisAppByAppId(otherVerApp.getAppId());
                if (dependentApps != null) {
                    for (Service dependentApp : dependentApps) {
                        MgpApplication appInfo = getAppFromEureka(serviceRegistry, dependentApp.getAppName(), dependentApp.getVersion());
                        String ipAddr = appInfo.getInstances().get(0).getIpAddr();
                        int port = appInfo.getInstances().get(0).getPort();
                        String serviceUrl = "http://" + ipAddr + ":" + port;
                        Map<String, Object> swaggerMap = getSwaggerMapFromRemoteApp(serviceUrl);
                        if (swaggerMap != null) {
                            Map<String, Object> dependencyMap = mapper.convertValue(swaggerMap.get("x-serviceDependency"), new TypeReference<Map<String, Object>>(){});
                            if (dependencyMap.get("httpRequest") != null) {
                                Map<String, Object> sourcePathMap = mapper.convertValue(dependencyMap.get("httpRequest"), new TypeReference<Map<String, Object>>(){});
                                sourcePathMap.forEach((sourcePathKey, sourcePathValue) -> {
                                    Map<String, Object> targetsMap = new HashMap<>();
                                    if (sourcePathKey.equals("none")) {
                                        Object targets = mapper.convertValue(sourcePathValue, Map.class).get("targets");
                                        targetsMap = mapper.convertValue(targets, new TypeReference<Map<String, Object>>(){});
                                    } else {
                                        Map<String, Object> sourceMethodMap = mapper.convertValue(sourcePathValue, new TypeReference<Map<String, Object>>(){});
                                        for (Map.Entry<String, Object> methodEntry : sourceMethodMap.entrySet()) {
                                            Object targets = mapper.convertValue(methodEntry.getValue(), Map.class).get("targets");
                                            targetsMap = mapper.convertValue(targets, new TypeReference<Map<String, Object>>(){});
                                        }
                                    }
                                    if (isTargetsExistNotSpecifiedCalltoApp(mgpApplication.getAppName(), targetsMap)) {
                                        updateDependencyApps.add(new MutablePair<>(appInfo, swaggerMap));
                                    }
                                });
                            }
                        }
                    }
                }
            }
        }



        return updateDependencyApps;
    }

    private boolean isTargetsExistNotSpecifiedCalltoApp(String targetAppName, Map<String, Object> targets) {
        boolean result = false;
        // key = targetName
        for (Map.Entry<String, Object> targetEntry : targets.entrySet()) {
            if (targetEntry.getKey().toUpperCase().equals(targetAppName)) {
                Map<String, Object> targetVersionMap =
                        mapper.convertValue(targetEntry.getValue(), new TypeReference<Map<String, Object>>(){});
                // key = version
                for (Map.Entry<String, Object> versionEntry : targetVersionMap.entrySet()) {
                    if (versionEntry.getKey().equals("notSpecified")) {
                        result = true;
                        break;
                    }
                }
                break;
            }
        }

        return result;
    }

    // Add dependency relationships to neo4j
    private void updateDependencies(Map<String, Pair<MgpApplication, Integer>> appsMap, Map<String, Map<String, Object>> appSwaggers) {
        appsMap.forEach((appId, appInfo) -> {
            serviceRepository.deleteDependencyByAppId(appInfo.getKey().getAppId());
        });
        if (appsMap.size() > 0) {
            endpointRepository.deleteUselessNullEndpoint();
            serviceRepository.deleteUselessNullService();
        }
        addDependencies(appsMap, appSwaggers);
    }

    private Service newerPatchVersionDetector(MgpApplication mgpApplication) {
        int[] thisAppVerCode = getVersionCode(mgpApplication.getVersion());
        if (thisAppVerCode != null) {
            List<Service> otherVerServices = serviceRepository.findOtherVersInSameSysBySysNameAndAppNameAndVersion
                    (mgpApplication.getSystemName(), mgpApplication.getAppName(), mgpApplication.getVersion());
            if (otherVerServices.size() > 0) {
                Pair<Service, int[]> latestPathApp = new ImmutablePair<>(null, thisAppVerCode);
                for (Service otherVerService : otherVerServices) {
                    int[] otherAppVerCode = getVersionCode(otherVerService.getVersion());
                    if (otherAppVerCode != null) {
                        if (thisAppVerCode[0] == otherAppVerCode [0] && thisAppVerCode[1] == otherAppVerCode[1]) {
                            if (otherAppVerCode[2] > latestPathApp.getValue()[2]) {
                                latestPathApp = new ImmutablePair<>(otherVerService, otherAppVerCode);
                            }
                        }
                    }
                }
                if (latestPathApp.getKey() != null) {
                    logger.info("Found newer patch version: " + mgpApplication.getAppId() + " -> " + latestPathApp.getKey().getVersion());
                    return latestPathApp.getKey();
                }
            }
        }
        return null;
    }

    private List<Service> olderPatchVersionDetector(MgpApplication mgpApplication) {
        int[] thisAppVerCode = getVersionCode(mgpApplication.getVersion());
        List<Service> olderVerServices = new ArrayList<>();
        if (thisAppVerCode != null) {
            List<Service> otherVerServices = serviceRepository.findOtherVersInSameSysBySysNameAndAppNameAndVersion
                    (mgpApplication.getSystemName(), mgpApplication.getAppName(), mgpApplication.getVersion());
            if (otherVerServices.size() > 0) {
                for (Service otherVerService : otherVerServices) {
                    int[] otherAppVerCode = getVersionCode(otherVerService.getVersion());
                    if (otherAppVerCode != null) {
                        if (thisAppVerCode[0] == otherAppVerCode [0] && thisAppVerCode[1] == otherAppVerCode[1]) {
                            if (otherAppVerCode[2] < thisAppVerCode[2]) {
                                olderVerServices.add(otherVerService);
                                logger.info("Found older patch version: " + mgpApplication.getAppId() + " -> " + otherVerService.getVersion());
                            }
                        }
                    }
                }
            }
        }
        return olderVerServices;
    }

    // Get an version number array form a semantic versioning string.
    private int[] getVersionCode(String version) {
        String[] versionParts = version.split("\\.");
        if (versionParts.length >= 3) {
            List<Integer> versionCode = new ArrayList<>();
            for (String part : versionParts) {
                if (versionCode.size() == 0 && String.valueOf(part.charAt(part.length() - 1)).matches("\\d")) {
                    String[] numsInPart = part.split("\\D+");
                    versionCode.add(Integer.parseInt(numsInPart[numsInPart.length - 1]));
                } else if (versionCode.size() == 1){
                    if (part.matches("\\d+")) {
                        versionCode.add(Integer.parseInt(part));
                    } else {
                        versionCode.clear();
                    }
                } else if (versionCode.size() == 2) {
                    if (String.valueOf(part.charAt(0)).matches("\\d")) {
                        String[] numsInPart = part.split("\\D+");
                        versionCode.add(Integer.parseInt(numsInPart[0]));
                    } else {
                        versionCode.clear();
                    }
                } else {
                    if (versionCode.size() == 3) {
                        break;
                    }
                }
            }
            if (versionCode.size() == 3) {
                return Ints.toArray(versionCode);
            }
        }
        return null;
    }

    // Recover apps in neo4j
    private Map<String, Map<String, Object>> recoverServices(ServiceRegistry serviceRegistry, Map<String, Pair<MgpApplication, Integer>> recoveryAppsMap) {
        Map<String, Map<String, Object>> appSwaggers = new HashMap<>();
        // Add services to graph DB.
        // CASE: Replace services
        recoveryAppsMap.forEach((appId, appInfoAndNum) -> {
            MgpInstance instance = appInfoAndNum.getKey().getInstances().get(0);
            String serviceUrl = "http://" + instance.getIpAddr() + ":" + instance.getPort();
            Map<String, Object> swaggerMap = getSwaggerMapFromRemoteApp(serviceUrl);
            if (swaggerMap != null) {
                appSwaggers.put(appId, swaggerMap);
                //String noVerAppId = appInfoAndNum.getKey().getSystemName() + ":" + appInfoAndNum.getKey().getAppName() + ":null";
                //if (!serviceRepository.removeNullLabelAndSetVerAndNumByAppId(noVerAppId, appInfoAndNum.getKey().getAppId(),
                //        appInfoAndNum.getKey().getVersion(), appInfoAndNum.getValue())) {
                    serviceRepository.removeNullLabelAndSetNumByAppId(appId, appInfoAndNum.getValue());
                //}
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
                            Endpoint endpoint = new Endpoint(serviceRegistry.getSystemName(), appInfoAndNum.getKey().getAppName(), method, path);
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
            newerPatchVersionDetector(appInfoAndNum.getKey());
        });

        return appSwaggers;
    }

    // Add dependency relationships to neo4j
    private void addDependencies(Map<String, Pair<MgpApplication, Integer>> appsMap, Map<String, Map<String, Object>> appSwaggers) {
        appsMap.forEach((sourceAppId, sourceAppInfo) -> {
            Map<String, Object> swaggerMap = appSwaggers.get(sourceAppId);
            if (swaggerMap != null) {
                Map<String, Object> dependencyMap = mapper.convertValue(swaggerMap.get("x-serviceDependency"), new TypeReference<Map<String, Object>>(){});
                if (dependencyMap.get("httpRequest") != null) {
                    Map<String, Object> sourcePathMap = mapper.convertValue(dependencyMap.get("httpRequest"), new TypeReference<Map<String, Object>>(){});
                    sourcePathMap.forEach((sourcePath, sourcePathValue) -> {
                        if (sourcePath.equals("none")) {
                            Service sourceService = serviceRepository.findByAppId(sourceAppId);
                            Object targets = mapper.convertValue(sourcePathValue, Map.class).get("targets");
                            addHttpTargetEndpoints(sourceService, mapper.convertValue(targets, new TypeReference<Map<String, Object>>(){}));
                        } else {
                            Map<String, Object> sourceMethodMap = mapper.convertValue(sourcePathValue, new TypeReference<Map<String, Object>>(){});
                            sourceMethodMap.forEach((sourceMethodKey, sourceMethodValue) -> {
                                String sourceEndpointId = sourceMethodKey + ":" + sourcePath;
                                Endpoint sourceEndpoint = endpointRepository.findByEndpointIdAndAppId(sourceEndpointId, sourceAppId);
                                Object targets = mapper.convertValue(sourceMethodValue, Map.class).get("targets");
                                addHttpTargetEndpoints(sourceAppId, sourceEndpoint, mapper.convertValue(targets, new TypeReference<Map<String, Object>>(){}));
                            });
                        }
                    });
                }
                if (dependencyMap.get("amqp") != null) {
                    Map<String, Object> typeMap = mapper.convertValue(dependencyMap.get("amqp"), new TypeReference<Map<String, Object>>(){});
                    if (typeMap.get("publish") != null) {
                        Map<String, Object> publishMap = mapper.convertValue(typeMap.get("publish"), new TypeReference<Map<String, Object>>(){});
                        publishMap.forEach((sourcePath, sourcePathValue) -> {
                            if (sourcePath.equals("none")) {
                                Service sourceService = serviceRepository.findByAppId(sourceAppId);
                                Map<String, List<String>> sourceTargetMap = mapper.convertValue(sourcePathValue, new TypeReference<Map<String, ArrayList<String>>>(){});
                                addAmqpPublishQueue(sourceService, sourceTargetMap.get("targets"));
                            } else {
                                Map<String, Object> sourceMethodMap = mapper.convertValue(sourcePathValue, new TypeReference<Map<String, Object>>(){});
                                sourceMethodMap.forEach((sourceMethod, sourceMethodValue) -> {
                                    String sourceEndpointId = sourceMethod + ":" + sourcePath;
                                    Endpoint sourceEndpoint = endpointRepository.findByEndpointIdAndAppId(sourceEndpointId, sourceAppId);
                                    Map<String, List<String>> sourceTargetMap = mapper.convertValue(sourceMethodValue, new TypeReference<Map<String, ArrayList<String>>>(){});
                                    addAmqpPublishQueue(sourceAppInfo.getKey().getSystemName(), sourceEndpoint, sourceTargetMap.get("targets"));
                                });
                            }
                        });
                    }
                    if (typeMap.get("subscribe") != null) {
                        Map<String, Object> subscribeMap = mapper.convertValue(typeMap.get("subscribe"), new TypeReference<Map<String, Object>>(){});
                        subscribeMap.forEach((sourcePath, sourcePathValue) -> {
                            if (sourcePath.equals("none")) {
                                Service sourceService = serviceRepository.findByAppId(sourceAppId);
                                Map<String, List<String>> sourceTargetMap = mapper.convertValue(sourcePathValue, new TypeReference<Map<String, ArrayList<String>>>(){});
                                addAmqpSubscribeQueue(sourceService, sourceTargetMap.get("targets"));
                            } else {
                                Map<String, Object> sourceMethodMap = mapper.convertValue(sourcePathValue, new TypeReference<Map<String, Object>>(){});
                                sourceMethodMap.forEach((sourceMethod, sourceMethodValue) -> {
                                    String sourceEndpointId = sourceMethod + ":" + sourcePath;
                                    Endpoint sourceEndpoint = endpointRepository.findByEndpointIdAndAppId(sourceEndpointId, sourceAppId);
                                    Map<String, List<String>> sourceTargetMap = mapper.convertValue(sourceMethodValue, new TypeReference<Map<String, ArrayList<String>>>(){});
                                    addAmqpSubscribeQueue(sourceAppInfo.getKey().getSystemName(), sourceEndpoint, sourceTargetMap.get("targets"));
                                });
                            }
                        });
                    }
                }
            }
        });
    }

    private void addHttpTargetEndpoints(Service sourceService, Map<String, Object> targets) {
        Set<Endpoint> targetEndpoints = getHttpTargetEndpoints(sourceService.getAppId(), targets);
        for (Endpoint targetEndpoint: targetEndpoints) {
            sourceService.httpRequestToEndpoint(targetEndpoint);
        }
        serviceRepository.save(sourceService);
    }

    private void addHttpTargetEndpoints(String sourceAppId, Endpoint sourceEndpoint, Map<String, Object> targets) {
        Set<Endpoint> targetEndpoints = getHttpTargetEndpoints(sourceAppId, targets);
        for (Endpoint targetEndpoint: targetEndpoints) {
            sourceEndpoint.httpRequestToEndpoint(targetEndpoint);
        }
        endpointRepository.save(sourceEndpoint);
    }

    private Set<Endpoint> getHttpTargetEndpoints(String sourceAppId, Map<String, Object> targets) {
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
                            targetEndpoints.addAll(nullHttpTargetDetector(targetEndpoint, sourceAppId, targetServiceName,
                                    targetMethod, targetPathKey));
                        } else {
                            Endpoint targetEndpoint = endpointRepository.findTargetEndpoint(
                                    sourceAppId, targetServiceName, targetVersionKey,
                                    targetEndpointId);
                            targetEndpoints.add(nullHttpTargetDetector(targetEndpoint, sourceAppId, targetServiceName,
                                    targetMethod, targetPathKey, targetVersionKey));
                        }
                    }
                });
            });
        });

        return targetEndpoints;
    }

    private List<Endpoint> nullHttpTargetDetector(List<Endpoint> targetEndpoints, String sourceAppId, String targetName,
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
                    //Endpoint nullTargetEndpoint = new NullEndpoint(targetName, targetMethod, targetPath);
                    Endpoint nullTargetEndpoint = endpointRepository.findByNullEndpointAndAppId(targetMethod + ":" + targetPath, targetService.getAppId());
                    if (nullTargetEndpoint == null) {
                        nullTargetEndpoint = new NullEndpoint(sourceAppId.split(":")[0], targetName, targetMethod, targetPath);
                        nullTargetEndpoint.ownBy(targetService);
                        endpointRepository.save(nullTargetEndpoint);
                        nullTargetEndpoint = endpointRepository.findByNullEndpointAndAppId(nullTargetEndpoint.getEndpointId(), targetService.getAppId());
                    }
                    nullEndpoints.add(nullTargetEndpoint);
                    logger.info("Found null endpoint: " + nullTargetEndpoint.getEndpointId());
                }
            } else {
                // Found null target service and endpoint.
                Service nullTargetService = serviceRepository.findNullByAppId(sourceAppId.split(":")[0] + ":" + targetName + ":" + null);
                if (nullTargetService == null) {
                    nullTargetService = new NullService(
                            sourceAppId.split(":")[0], targetName, null, 0);
                    Endpoint nullTargetEndpoint = new NullEndpoint(sourceAppId.split(":")[0], targetName, targetMethod, targetPath);
                    nullTargetEndpoint.ownBy(nullTargetService);
                    endpointRepository.save(nullTargetEndpoint);
                    nullTargetEndpoint = endpointRepository.findByNullEndpointAndAppId(nullTargetEndpoint.getEndpointId(), nullTargetService.getAppId());
                    nullEndpoints.add(nullTargetEndpoint);
                    logger.info("Found null service and endpoint: " + nullTargetService.getAppId() + " " + nullTargetEndpoint.getEndpointId());
                } else {
                    Endpoint nullTargetEndpoint = endpointRepository.findByNullEndpointAndAppId(targetMethod + ":" + targetPath, nullTargetService.getAppId());
                    if (nullTargetEndpoint == null) {
                        nullTargetEndpoint = new NullEndpoint(sourceAppId.split(":")[0], targetName, targetMethod, targetPath);
                        nullTargetEndpoint.ownBy(nullTargetService);
                        endpointRepository.save(nullTargetEndpoint);
                        nullTargetEndpoint = endpointRepository.findByNullEndpointAndAppId(nullTargetEndpoint.getEndpointId(), nullTargetService.getAppId());
                    }
                    nullEndpoints.add(nullTargetEndpoint);
                    logger.info("Found null service and endpoint: " + nullTargetService.getAppId() + " " + nullTargetEndpoint.getEndpointId());
                }
            }
            return nullEndpoints;
        }
    }

    private Endpoint nullHttpTargetDetector(Endpoint targetEndpoint, String sourceAppId, String targetName,
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
                    nullTargetEndpoint = new NullEndpoint(sourceAppId.split(":")[0], targetName, targetMethod, targetPath);
                    nullTargetEndpoint.ownBy(targetService);
                    endpointRepository.save(nullTargetEndpoint);
                    nullTargetEndpoint = endpointRepository.findByNullEndpointAndAppId(targetEndpointId, targetService.getAppId());
                }
                logger.info("Found null endpoint: " + nullTargetEndpoint.getEndpointId());
            } else {
                // Found null target service and endpoint.
                Service nullTargetService = new NullService(
                        sourceAppId.split(":")[0], targetName, targetVersion, 0
                );
                // Find newer patch version service.
                Service newerPatchService = newerPatchVersionDetector
                        (new MgpApplication(sourceAppId.split(":")[0], targetName, targetVersion, null));
                if (newerPatchService != null) {
                    nullTargetService.addLabel("OutdatedVersion");
                    nullTargetService.foundNewPatchVersion(newerPatchService);
                }
                nullTargetEndpoint = new NullEndpoint(sourceAppId.split(":")[0], targetName, targetMethod, targetPath);
                nullTargetEndpoint.ownBy(nullTargetService);
                endpointRepository.save(nullTargetEndpoint);
                logger.info("Found null service and endpoint: " + nullTargetService.getAppId() + " " + nullTargetEndpoint.getEndpointId());
            }
            return nullTargetEndpoint;
        }
    }

    private void addAmqpPublishQueue(Service sourceService, List<String> targets) {
        List<Queue> queues = new ArrayList<>();
        for (String queueName : targets) {
            String queueId = sourceService.getSystemName() + ":" + queueName;
            Queue queue = queueRepository.findByQueueId(queueId);
            if (queue != null) {
                queues.add(queue);
            } else {
                queues.add(new Queue(sourceService.getSystemName(), queueName));
            }
        }
        sourceService.amqpPublishToQueue(queues);
        serviceRepository.save(sourceService);
    }

    private void addAmqpPublishQueue(String sysName, Endpoint endpoint, List<String> targets) {
        List<Queue> queues = new ArrayList<>();
        for (String queueName : targets) {
            String queueId = sysName + ":" + queueName;
            Queue queue = queueRepository.findByQueueId(queueId);
            if (queue != null) {
                queues.add(queue);
            } else {
                queues.add(new Queue(sysName, queueName));
            }
        }
        endpoint.amqpPublishToQueue(queues);
        endpointRepository.save(endpoint);
    }

    private void addAmqpSubscribeQueue(Service sourceService, List<String> targets) {
        List<Queue> queues = new ArrayList<>();
        for (String queueName : targets) {
            String queueId = sourceService.getSystemName() + ":" + queueName;
            Queue queue = queueRepository.findByQueueId(queueId);
            if (queue != null) {
                queues.add(queue);
            } else {
                queues.add(new Queue(sourceService.getSystemName(), queueName));
            }
        }
        sourceService.amqpSubscribeToQueue(queues);
        serviceRepository.save(sourceService);
    }

    private void addAmqpSubscribeQueue(String sysName, Endpoint endpoint, List<String> targets) {
        List<Queue> queues = new ArrayList<>();
        for (String queueName : targets) {
            String queueId = sysName + ":" + queueName;
            Queue queue = queueRepository.findByQueueId(queueId);
            if (queue != null) {
                queues.add(queue);
            } else {
                queues.add(new Queue(sysName, queueName));
            }
        }
        endpoint.amqpSubscribeToQueue(queues);
        endpointRepository.save(endpoint);
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
                serviceRepository.setNumToZeroAndAddNullLabelWithEndpointsByAppId(appId);
            } else {
                serviceRepository.deleteWithEndpointsByAppId(appId);
            }
            logger.info("Remove service: " + appId);
        }
        if (removeAppsSet.size() > 0) {
            endpointRepository.deleteUselessNullEndpoint();
            serviceRepository.deleteUselessNullService();
            queueRepository.deleteUselessQueues();
            serviceRepository.removeUselessOutdatedVersionLabel();
        }
    }

}
