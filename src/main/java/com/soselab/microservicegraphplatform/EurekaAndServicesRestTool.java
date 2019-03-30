package com.soselab.microservicegraphplatform;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.soselab.microservicegraphplatform.bean.actuators.Info;
import com.soselab.microservicegraphplatform.bean.eureka.AppInstance;
import com.soselab.microservicegraphplatform.bean.eureka.AppList;
import com.soselab.microservicegraphplatform.bean.eureka.Application;
import com.soselab.microservicegraphplatform.bean.eureka.AppsList;
import com.soselab.microservicegraphplatform.bean.mgp.MgpApplication;
import com.soselab.microservicegraphplatform.bean.mgp.MgpInstance;
import com.soselab.microservicegraphplatform.bean.neo4j.Instance;
import com.soselab.microservicegraphplatform.bean.neo4j.ServiceRegistry;
import com.soselab.microservicegraphplatform.repositories.InstanceRepository;
import com.soselab.microservicegraphplatform.repositories.ServiceRegistryRepository;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
public class EurekaAndServicesRestTool {
    private static final Logger logger = LoggerFactory.getLogger(EurekaAndServicesRestTool.class);

    @Autowired
    private ServiceRegistryRepository serviceRegistryRepository;
    @Autowired
    private InstanceRepository instanceRepository;
    @Autowired
    private ObjectMapper mapper;
    private RestTemplate restTemplate = new RestTemplate();

    public MgpApplication getAppFromEureka(String systemName, String appName, String version) {
        ServiceRegistry serviceRegistry = serviceRegistryRepository.findBySystemName(systemName);
        List<Instance> registryInstance = instanceRepository.findByServiceRegistryAppId(serviceRegistry.getAppId());
        String registryUrl = "http://" + registryInstance.get(0).getIpAddr() + ":" + registryInstance.get(0).getPort() + "/eureka/apps/" + appName;
        AppList eurekaApp = restTemplate.getForObject(registryUrl, AppList.class);
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

    public MgpApplication getAppFromEureka(ServiceRegistry serviceRegistry, String appName, String version) {
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

    // Map<appId, Pair<appInfo, number of apps>>
    public Map<String, Pair<MgpApplication, Integer>> getAppsInfoAndNumFromEurekaAppList(String systemName, AppsList appsList) {
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

    public String getVersionFromRemoteApp(String serviceUrl) {
        try {
            Info appInfo = restTemplate.getForObject( serviceUrl + "/info", Info.class);
            if (appInfo != null) {
                return appInfo.getVersion();
            } else {
                return null;
            }
        } catch (ResourceAccessException e) {
            logger.error(e.getMessage(), e);
            return null;
        }
    }

    public String getSwaggerFromRemoteApp(String systemName, String appName, String version) {
        MgpApplication mgpApplication = getAppFromEureka(systemName, appName, version);
        String url = "http://" + mgpApplication.getInstances().get(0).getIpAddr() + ":" + mgpApplication.getInstances().get(0).getPort();
        return restTemplate.getForObject(url + "/v2/api-docs", String.class);
    }

    public Map<String, Object> getSwaggerFromRemoteApp(String serviceUrl) {
        String swagger = restTemplate.getForObject(serviceUrl + "/v2/api-docs", String.class);
        Map<String, Object> swaggerMap = null;
        try {
            swaggerMap = mapper.readValue(swagger, new TypeReference<Map<String, Object>>(){});
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
        return swaggerMap;
    }

}