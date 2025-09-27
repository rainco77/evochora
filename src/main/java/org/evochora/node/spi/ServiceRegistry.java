package org.evochora.node.spi;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A simple, type-safe service locator for managing and providing access to shared, application-wide services.
 * This registry acts as a centralized dependency injection mechanism.
 */
public class ServiceRegistry {

    private final Map<Class<?>, Object> serviceMap = new ConcurrentHashMap<>();

    /**
     * Registers a service instance with the registry.
     * If a service of the same type is already registered, it will be replaced.
     *
     * @param type     The class type of the service to register. Must not be null.
     * @param instance The service instance. Must not be null.
     * @param <T>      The type of the service.
     */
    public <T> void register(final Class<T> type, final T instance) {
        if (type == null) {
            throw new IllegalArgumentException("Service type cannot be null.");
        }
        if (instance == null) {
            throw new IllegalArgumentException("Service instance cannot be null.");
        }
        serviceMap.put(type, instance);
    }

    /**
     * Retrieves a service instance of the specified type.
     *
     * @param type The class type of the service to retrieve. Must not be null.
     * @param <T>  The type of the service.
     * @return The registered service instance.
     * @throws IllegalArgumentException if no service of the specified type is found.
     */
    @SuppressWarnings("unchecked")
    public <T> T get(final Class<T> type) {
        if (type == null) {
            throw new IllegalArgumentException("Service type cannot be null.");
        }
        final T instance = (T) serviceMap.get(type);
        if (instance == null) {
            throw new IllegalArgumentException("No service registered for type: " + type.getName());
        }
        return instance;
    }
}