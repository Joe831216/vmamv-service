package com.soselab.microservicegraphplatform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.soselab.microservicegraphplatform.bean.actuators.Info;
import com.soselab.microservicegraphplatform.bean.eureka.AppInstance;
import com.soselab.microservicegraphplatform.bean.eureka.AppList;
import com.soselab.microservicegraphplatform.bean.mgp.MgpApplication;
import com.soselab.microservicegraphplatform.bean.mgp.MgpInstance;
import com.soselab.microservicegraphplatform.bean.neo4j.Instance;
import com.soselab.microservicegraphplatform.bean.neo4j.ServiceRegistry;
import com.soselab.microservicegraphplatform.repositories.InstanceRepository;
import com.soselab.microservicegraphplatform.repositories.ServiceRegistryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

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

    public String getVersionFromRemoteApp(String serviceUrl) {
        try {
            return restTemplate.getForObject( serviceUrl + "/info", Info.class).getVersion();
        } catch (ResourceAccessException e) {
            logger.error(e.getMessage(), e);
            return null;
        }
    }

    public String getSwaggerFromRemoteApp(String url) {
        return restTemplate.getForObject(url + "/v2/api-docs", String.class);
    }

    public String getSwaggerFromRemoteApp(String systemName, String appName, String version) {
        MgpApplication mgpApplication = getAppFromEureka(systemName, appName, version);
        String url = "http://" + mgpApplication.getInstances().get(0).getIpAddr() + ":" + mgpApplication.getInstances().get(0).getPort();
        return restTemplate.getForObject(url + "/v2/api-docs", String.class);
    }

}
