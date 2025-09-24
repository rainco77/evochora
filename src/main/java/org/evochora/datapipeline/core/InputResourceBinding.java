package org.evochora.datapipeline.core;

import org.evochora.datapipeline.api.resources.channels.IInputChannel;

/**
 * A wrapper around input resources that tracks metrics and provides monitoring capabilities.
 */
public class InputResourceBinding<T> extends AbstractResourceBinding<IInputChannel<T>> implements IInputChannel<T> {

    public InputResourceBinding(String serviceName, String portName, String resourceName, String usageType, IInputChannel<T> resource) {
        super(serviceName, portName, resourceName, usageType, resource);
    }

    @Override
    public T read() throws InterruptedException {
        try {
            T message = getResource().read();
            incrementCount();
            return message;
        } catch (Exception e) {
            incrementErrorCount();
            throw e;
        }
    }
}
