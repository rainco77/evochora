package org.evochora.datapipeline.services;

import com.typesafe.config.Config;
import org.evochora.datapipeline.api.resources.channels.IInputChannel;
import org.evochora.datapipeline.api.resources.channels.IOutputChannel;
import org.evochora.datapipeline.api.resources.IMonitorableResource;
import org.evochora.datapipeline.api.services.*;
import org.evochora.datapipeline.core.AbstractResourceBinding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * An abstract base class for services, providing common lifecycle management and resource binding.
 * This class handles the state transitions (RUNNING, PAUSED, STOPPED) and provides
 * a thread-safe mechanism for pausing and resuming the service's execution.
 * Updated for Universal DI with resource-based injection pattern.
 */
public abstract class AbstractService implements IService {

    protected final Logger log = LoggerFactory.getLogger(this.getClass());
    protected final AtomicReference<State> currentState = new AtomicReference<>(State.STOPPED);
    protected final Object pauseLock = new Object();
    protected volatile boolean paused = false;
    protected Thread serviceThread;
    protected final Config options;
    protected final Map<String, List<Object>> resources;

    /**
     * Constructs an AbstractService with its dependencies using Universal DI pattern.
     *
     * @param options   The service-specific configuration.
     * @param resources A map of all injected resource objects, keyed by port name.
     */
    public AbstractService(Config options, Map<String, List<Object>> resources) {
        this.options = options;
        this.resources = resources;
    }

    /**
     * Resets error counts for all resource bindings when the service starts.
     * The ServiceManager handles resetting error counts for resource bindings it manages.
     */
    protected void resetResourceBindingErrorCounts() {
        // Default implementation - ServiceManager handles this for managed bindings
    }

    @Override
    public void start() {
        if (currentState.compareAndSet(State.STOPPED, State.RUNNING)) {
            // Reset error counts for all resource bindings when service starts
            resetResourceBindingErrorCounts();

            // Start service in separate thread
            this.serviceThread = new Thread(this::run, this.getClass().getSimpleName().toLowerCase());
            this.serviceThread.start();
        }
    }

    @Override
    public void stop() {
        if (currentState.compareAndSet(State.RUNNING, State.STOPPED) || 
            currentState.compareAndSet(State.PAUSED, State.STOPPED)) {
            // Wake up any waiting threads
            synchronized (pauseLock) {
                pauseLock.notifyAll();
            }
            if (serviceThread != null && serviceThread.isAlive()) {
                serviceThread.interrupt();
            }
            log.info("Stopped service: {}", this.getClass().getSimpleName());
        }
    }

    @Override
    public void pause() {
        if (currentState.compareAndSet(State.RUNNING, State.PAUSED)) {
            paused = true;
            log.info("Paused service: {}", this.getClass().getSimpleName());
        }
    }

    @Override
    public void resume() {
        if (currentState.compareAndSet(State.PAUSED, State.RUNNING)) {
            synchronized (pauseLock) {
                paused = false;
                pauseLock.notifyAll();
            }
            log.info("Resumed service: {}", this.getClass().getSimpleName());
        }
    }

    @Override
    public ServiceStatus getServiceStatus() {
        List<ResourceBindingStatus> resourceBindingStatuses = new ArrayList<>();
        resources.forEach((portName, resourceList) -> {
            resourceList.forEach(resource -> {
                String usageType = determineUsageType(resource);
                String resourceName = determineResourceName(portName, resource);

                resourceBindingStatuses.add(new ResourceBindingStatus(
                    resourceName,
                    usageType,
                    determineBindingState(resource, usageType),
                    0.0
                ));
            });
        });
        return new ServiceStatus(currentState.get(), resourceBindingStatuses);
    }

    private String determineUsageType(Object resource) {
        if (resource instanceof AbstractResourceBinding<?>) {
            return ((AbstractResourceBinding<?>) resource).getUsageType();
        } else if (resource instanceof IInputChannel) {
            return "input";
        } else if (resource instanceof IOutputChannel) {
            return "output";
        }
        return "default";
    }

    private String determineResourceName(String portName, Object resource) {
        if (resource instanceof AbstractResourceBinding<?>) {
            return ((AbstractResourceBinding<?>) resource).getResourceName();
        }
        return portName;
    }

    private BindingState determineBindingState(Object resource, String usageType) {
        Object underlyingResource = resource;

        // If it's a resource binding, get the underlying resource
        if (resource instanceof AbstractResourceBinding<?>) {
            underlyingResource = ((AbstractResourceBinding<?>) resource).getResource();
        }

        if (underlyingResource instanceof IMonitorableResource) {
            IMonitorableResource monitorableResource = (IMonitorableResource) underlyingResource;

            long backlogSize = monitorableResource.getBacklogSize();
            long capacity = monitorableResource.getCapacity();

            if ("input".equals(usageType) || "channel-in".equals(usageType)) {
                return backlogSize == 0 ? BindingState.WAITING : BindingState.ACTIVE;
            } else if ("output".equals(usageType) || "channel-out".equals(usageType)) {
                boolean isFull = (capacity >= 0 && backlogSize >= capacity);
                return isFull ? BindingState.WAITING : BindingState.ACTIVE;
            }
        }
        return BindingState.ACTIVE;
    }

    @Override
    public String getActivityInfo() {
        // Default implementation - subclasses can override
        return "";
    }

    /**
     * The main logic of the service, to be implemented by subclasses.
     * This method is called by the {@link #start()} method.
     */
    protected abstract void run();

    /**
     * Gets a single, required resource for a specific port.
     * This is a convenience method for services that expect exactly one resource on a given port.
     *
     * @param portName The logical port name.
     * @param <T>      The expected type of the resource.
     * @return The resource instance.
     * @throws IllegalStateException if no resource or more than one resource is configured for the port.
     */
    @SuppressWarnings("unchecked")
    protected <T> T getRequiredResource(String portName) {
        List<Object> resolved = resources.get(portName);
        if (resolved == null || resolved.isEmpty()) {
            throw new IllegalStateException("Required resource port '" + portName + "' has no configured resource.");
        }
        if (resolved.size() > 1) {
            throw new IllegalStateException("Required resource port '" + portName + "' expects a single resource, but " + resolved.size() + " were configured.");
        }
        return (T) resolved.get(0);
    }

    /**
     * Gets all resources for a specific port.
     * This is a convenience method for services that can handle multiple resources on a single logical port.
     *
     * @param portName The logical port name.
     * @param <T>      The expected type of the resources.
     * @return A stream of resource instances. Returns an empty stream if the port is not configured.
     */
    @SuppressWarnings("unchecked")
    protected <T> Stream<T> getResources(String portName) {
        return resources.getOrDefault(portName, Collections.emptyList())
                .stream()
                .map(resource -> (T) resource);
    }

    /**
     * Checks if the service should pause execution and waits if necessary.
     * This method should be called periodically in the service's main loop.
     */
    protected void checkPausePoint() {
        synchronized (pauseLock) {
            while (paused && currentState.get() == State.PAUSED) {
                try {
                    pauseLock.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }
}
