package org.evochora.datapipeline.resources;

import com.typesafe.config.Config;
import org.evochora.datapipeline.api.resources.channels.IInputChannel;
import org.evochora.datapipeline.api.resources.channels.IOutputChannel;
import org.evochora.datapipeline.api.resources.IContextualResource;
import org.evochora.datapipeline.api.resources.IMonitorableResource;
import org.evochora.datapipeline.api.resources.ResourceContext;
import org.evochora.datapipeline.core.InputResourceBinding;
import org.evochora.datapipeline.core.OutputResourceBinding;
import org.evochora.datapipeline.core.ServiceManager;

import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * A thread-safe, in-memory message channel with a fixed capacity.
 * <p>
 * This channel is implemented using a {@link java.util.concurrent.ArrayBlockingQueue}
 * and is designed for high-performance, in-process communication between services.
 * Its capacity is configurable via a {@link com.typesafe.config.Config} object.
 *
 * @param <T> The type of message this channel will hold.
 */
public class InMemoryChannel<T> implements IInputChannel<T>, IOutputChannel<T>, IMonitorableResource, IContextualResource {

    private final BlockingQueue<T> queue;
    private final int capacity;

    /**
     * Constructs an InMemoryChannel based on the provided configuration.
     * <p>
     * The configuration is expected to contain a "capacity" key. If the key is not present,
     * a default capacity of 1000 will be used.
     *
     * @param options A Config object containing the channel's settings, e.g., 'capacity'.
     */
    public InMemoryChannel(Config options) {
        this.capacity = options.hasPath("capacity") ? options.getInt("capacity") : 1000;
        this.queue = new ArrayBlockingQueue<>(this.capacity);
    }

    @Override
    public T read() throws InterruptedException {
        return queue.take();
    }

    @Override
    public void write(T message) throws InterruptedException {
        queue.put(message);
    }

    @Override
    public long getBacklogSize() {
        return queue.size();
    }

    @Override
    public long getCapacity() {
        return this.capacity;
    }

    @Override
    public Object getInjectedObject(ResourceContext context) {
        // Return appropriate wrapper based on usage type
        String usageType = context.usageType();
        if (usageType == null) {
            usageType = "default";
        }

        // Find the resource name by searching through the ServiceManager's resources
        String resourceName = findResourceName(context.serviceManager());
        if (resourceName == null) {
            resourceName = context.portName(); // fallback to port name
        }

        switch (usageType.toLowerCase()) {
            case "channel-in":
            case "input":
                return new InputResourceBinding<>(
                    context.serviceName(),
                    context.portName(), 
                    resourceName,
                    usageType,
                    this
                );
            case "channel-out":
            case "output":
                return new OutputResourceBinding<>(
                    context.serviceName(),
                    context.portName(),
                    resourceName,
                    usageType,
                    this
                );
            default:
                // For unknown usage types, return the channel directly
                return this;
        }
    }

    private String findResourceName(ServiceManager serviceManager) {
        // Find this resource instance in the ServiceManager's resources map
        for (Map.Entry<String, Object> entry : serviceManager.getResources().entrySet()) {
            if (entry.getValue() == this) {
                return entry.getKey();
            }
        }
        return null;
    }
}
