package org.evochora.datapipeline;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValue;
import org.evochora.datapipeline.api.resources.IResource;
import org.evochora.datapipeline.api.services.IService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ServiceManager {

    private static final Logger log = LoggerFactory.getLogger(ServiceManager.class);
    private final Map<String, IResource> resources = new HashMap<>();
    private final Map<String, IService> services = new HashMap<>();

    public ServiceManager(Config config) {
        if (config.hasPath("resources")) {
            instantiateResources(config.getConfig("resources"));
        }
        if (config.hasPath("services")) {
            instantiateServices(config.getConfig("services"));
        }
    }

    private void instantiateResources(Config resourcesConfig) {
        for (String resourceName : resourcesConfig.root().keySet()) {
            try {
                Config resourceConfig = resourcesConfig.getConfig(resourceName);
                String className = resourceConfig.getString("class");
                Class<?> clazz = Class.forName(className);
                Constructor<?> constructor = clazz.getConstructor(Config.class);
                IResource resource = (IResource) constructor.newInstance(resourceConfig.hasPath("options") ? resourceConfig.getConfig("options") : ConfigFactory.empty());
                resources.put(resourceName, resource);
                log.info("Instantiated resource '{}' of type {}", resourceName, className);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to instantiate resource: " + resourceName, e);
            }
        }
    }

    private void instantiateServices(Config servicesConfig) {
        for (String serviceName : servicesConfig.root().keySet()) {
            try {
                Config serviceConfig = servicesConfig.getConfig(serviceName);
                String className = serviceConfig.getString("class");
                Map<String, List<IResource>> serviceResources = resolveServiceResources(serviceConfig);

                Class<?> clazz = Class.forName(className);
                Constructor<?> constructor = clazz.getConstructor(Config.class, Map.class);
                IService service = (IService) constructor.newInstance(
                        serviceConfig.hasPath("options") ? serviceConfig.getConfig("options") : ConfigFactory.empty(),
                        serviceResources
                );
                services.put(serviceName, service);
                log.info("Instantiated service '{}' of type {}", serviceName, className);
            } catch (IllegalArgumentException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException("Failed to instantiate service: " + serviceName, e);
            }
        }
    }

    private Map<String, List<IResource>> resolveServiceResources(Config serviceConfig) {
        if (!serviceConfig.hasPath("resources")) {
            return Collections.emptyMap();
        }

        Map<String, List<IResource>> resolvedResources = new HashMap<>();
        Config resourcesMap = serviceConfig.getConfig("resources");

        for (Map.Entry<String, ConfigValue> entry : resourcesMap.root().entrySet()) {
            String portName = entry.getKey();
            List<String> uris = resourcesMap.getStringList(portName);
            List<IResource> resourceList = new ArrayList<>();

            for (String uriString : uris) {
                try {
                    URI uri = new URI(uriString);
                    if (!"resource".equals(uri.getScheme())) {
                        throw new IllegalArgumentException("Invalid URI scheme for resource: " + uriString);
                    }
                    String resourceName = uri.getHost();
                    IResource resource = resources.get(resourceName);
                    if (resource == null) {
                        throw new IllegalStateException("Resource not found for URI: " + uriString);
                    }
                    resourceList.add(resource);
                } catch (java.net.URISyntaxException e) {
                    throw new IllegalArgumentException("Invalid resource URI: " + uriString, e);
                }
            }
            resolvedResources.put(portName, resourceList);
        }
        return resolvedResources;
    }

    public void startAll() {
        services.values().forEach(IService::start);
    }

    public void stopAll() {
        services.values().forEach(IService::stop);
    }

    public void pauseAll() {
        services.values().forEach(IService::pause);
    }

    public void resumeAll() {
        services.values().forEach(IService::resume);
    }

    public void restartAll() {
        services.values().forEach(IService::restart);
    }

    public IService getService(String name) {
        return services.get(name);
    }

    public IResource getResource(String name) {
        return resources.get(name);
    }

    public Map<String, IService> getServices() {
        return Collections.unmodifiableMap(services);
    }

    public Map<String, IResource> getResources() {
        return Collections.unmodifiableMap(resources);
    }
}