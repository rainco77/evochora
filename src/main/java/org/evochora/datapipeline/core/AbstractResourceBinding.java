package org.evochora.datapipeline.core;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Base class for resource bindings that provides common functionality for metrics collection.
 * This replaces the old AbstractChannelBinding with a more general approach.
 *
 * @param <T> The type of resource being bound
 */
public abstract class AbstractResourceBinding<T> {
    private final String serviceName;
    private final String portName;
    private final String resourceName;
    private final String usageType;
    private final T resource;
    private final AtomicLong messageCount = new AtomicLong(0);
    private final AtomicLong errorCount = new AtomicLong(0);

    public AbstractResourceBinding(String serviceName, String portName, String resourceName, String usageType, T resource) {
        this.serviceName = serviceName;
        this.portName = portName;
        this.resourceName = resourceName;
        this.usageType = usageType;
        this.resource = resource;
    }

    public String getServiceName() { return serviceName; }
    public String getPortName() { return portName; }
    public String getResourceName() { return resourceName; }
    public String getUsageType() { return usageType; }
    public T getResource() { return resource; }

    public long getAndResetCount() {
        return messageCount.getAndSet(0);
    }

    public long getErrorCount() {
        return errorCount.get();
    }

    public void resetErrorCount() {
        errorCount.set(0);
    }

    protected void incrementCount() {
        messageCount.incrementAndGet();
    }

    protected void incrementErrorCount() {
        errorCount.incrementAndGet();
    }

    @Override
    public String toString() {
        return String.format("%s:%s:%s:%s", serviceName, portName, resourceName, usageType);
    }
}
