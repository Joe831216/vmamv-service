package com.soselab.microservicegraphplatform.controllers;

import com.soselab.microservicegraphplatform.bean.mgp.Application;
import com.soselab.microservicegraphplatform.bean.mgp.MgpInstance;
import com.soselab.microservicegraphplatform.bean.neo4j.Instance;
import com.soselab.microservicegraphplatform.bean.mgp.RegisterInfo;
import com.soselab.microservicegraphplatform.bean.neo4j.ServiceRegistry;
import com.soselab.microservicegraphplatform.repositories.EndpointRepository;
import com.soselab.microservicegraphplatform.repositories.InstanceRepository;
import com.soselab.microservicegraphplatform.repositories.MicroserviceRepository;
import com.soselab.microservicegraphplatform.repositories.ServiceRegistryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import javax.servlet.http.HttpServletRequest;


@RestController
@RequestMapping("/registry")
public class RegistryController {

    private static final Logger logger = LoggerFactory.getLogger(RegistryController.class);

    @Autowired
    ServiceRegistryRepository serviceRegistryRepository;
    @Autowired
    InstanceRepository instanceRepository;
    @Autowired
    MicroserviceRepository microserviceRepository;
    @Autowired
    EndpointRepository endpointRepository;

    private RestTemplate restTemplate = new RestTemplate();

    @PostMapping("/register/eureka")
    @Transactional
    public String registerServiceRegistry(@RequestBody RegisterInfo registerInfo, HttpServletRequest request) {
        if (registerInfo == null) {
            return "fail(no data)";
        } else {
            logger.info("getRemoteHost: " + request.getRemoteHost());
            logger.info("getRemoteAddr: " + request.getRemoteAddr());
            logger.info("getRemotePort: " + request.getRemotePort());
            for (Application app : registerInfo.getApplications()) {
                // If this app (service tasks) is not in neo4j then save to neo4j DB
                String appId = app.getAppId();
                if (serviceRegistryRepository.findByAppId(appId) == null) {
                    String scsName = app.getScsName();
                    String appName = app.getAppName();
                    String version = app.getVersion();
                    ServiceRegistry serviceRegistry = new ServiceRegistry(scsName, appName, version);
                    serviceRegistryRepository.save(serviceRegistry);
                    logger.info("Add service tasks: " + appId);
                }
                // Loop the instances
                for (MgpInstance ins : app.getInstances()) {
                    // If this instance is not in neo4j then save to neo4j DB
                    String instanceId = ins.getInstanceId();
                    if (instanceRepository.findByInstanceId(instanceId) == null) {
                        String appName = ins.getAppName();
                        String hostName = ins.getHostName();
                        String ipAddr = ins.getIpAddr();
                        int port = ins.getPort();
                        Instance instance = new Instance(appName, hostName, ipAddr, port);
                        instance.ownBy(serviceRegistryRepository.findByAppId(appId));
                        instanceRepository.save(instance);
                        logger.info("Add service tasks instance: " + instanceId);
                    }
                }
            }
            return "success";
        }
    }

    @DeleteMapping("/unregister/{instanceId:.+}")
    @Transactional
    public String unregisterServiceRegistry(@PathVariable("instanceId") String id) {
        instanceRepository.deleteByInstanceId(id);
        logger.info("Unregister: " + id);
        /*
        if (instanceRepository.findByInstanceId(id) != null) {

        }
        */
        return "success";
    }

    /*
    @RequestMapping("/refreshAppList/{serviceRegistryAppId:.+}")
    public void refreshAppListrest(@PathVariable("serviceRegistryAppId") String serviceRegistryAppId) {
        ArrayList<Instance> instances = instanceRepository.findByServiceRegistryAppId(serviceRegistryAppId);
        Instance instance = instances.get(0);
        String url = "http://" + instance.getIpAddr() + ":" + instance.getPort() + "/eureka/apps/";
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(headers);
        ResponseEntity<String> responseEntity = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        logger.info("Refresh " + serviceRegistryAppId + "app list: " + responseEntity.getBody());
    }
    */

    /*
    @PostMapping("/register/swagger/{appId:.+}")
    public String registerSwagger(@PathVariable("appId") String appId, @RequestBody String swagger) {
        logger.info(appId + " register swagger: " + swagger);
        return "success";
    }
    */

}
