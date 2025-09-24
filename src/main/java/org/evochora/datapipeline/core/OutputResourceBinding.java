package org.evochora.datapipeline.core;

import org.evochora.datapipeline.api.resources.channels.IOutputChannel;

/**
 * A wrapper around output resources that tracks metrics and provides monitoring capabilities.
 */
public class OutputResourceBinding<T> extends AbstractResourceBinding<IOutputChannel<T>> implements IOutputChannel<T> {

    public OutputResourceBinding(String serviceName, String portName, String resourceName, String usageType, IOutputChannel<T> resource) {
        super(serviceName, portName, resourceName, usageType, resource);
    }

    @Override
    public void write(T message) throws InterruptedException {
        try {
            getResource().write(message);
            incrementCount();
        } catch (Exception e) {
            incrementErrorCount();
            throw e;
        }
    }
}
