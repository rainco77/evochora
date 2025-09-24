package org.evochora.datapipeline.services;

import com.typesafe.config.Config;
import org.evochora.datapipeline.api.resources.channels.IInputChannel;
import org.evochora.datapipeline.api.resources.IMonitorableResource;
import org.evochora.datapipeline.api.services.State;
import org.evochora.datapipeline.core.AbstractResourceBinding;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A dummy consumer service for testing purposes. It continuously reads messages
 * from its input channel and counts them until it is stopped.
 * Accepts any type of message (generic).
 * Updated for Universal DI pattern.
 */
public class DummyConsumerService extends AbstractService {

    private final AtomicInteger receivedMessageCount = new AtomicInteger(0);
    private final IInputChannel<?> inputChannel;

    public DummyConsumerService(Config options, Map<String, List<Object>> resources) {
        super(options, resources);
        this.inputChannel = getRequiredResource("messages");
    }

    @Override
    protected void run() {
        try {
            while (currentState.get() == State.RUNNING && !Thread.currentThread().isInterrupted()) {
                checkPausePoint();

                // Reading is blocking but will be interrupted by stop()
                // Accept any type of message (generic)
                inputChannel.read();
                receivedMessageCount.incrementAndGet();
            }
        } catch (InterruptedException e) {
            // Interruption is a signal to stop, but we must drain the queue first.
            Thread.currentThread().interrupt();
        } finally {
            // After being stopped or interrupted, drain any remaining messages from the channel.
            // This requires checking if the underlying resource is monitorable
            Object underlyingResource = inputChannel;

            // If it's a resource binding, get the underlying resource
            if (inputChannel instanceof AbstractResourceBinding<?>) {
                underlyingResource = ((AbstractResourceBinding<?>) inputChannel).getResource();
            }

            if (underlyingResource instanceof IMonitorableResource) {
                IMonitorableResource monitorableResource = (IMonitorableResource) underlyingResource;
                while (monitorableResource.getBacklogSize() > 0) {
                    try {
                        // We can't use a non-blocking poll from the interface,
                        // but we can read and assume it won't block if size > 0.
                        inputChannel.read();
                        receivedMessageCount.incrementAndGet();
                    } catch (InterruptedException e) {
                        // Should not happen as we are not in a blocking read here, but if it does...
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
    }

    /**
     * @return The number of messages received by this service.
     */
    public int getReceivedMessageCount() {
        return receivedMessageCount.get();
    }

    @Override
    public String getActivityInfo() {
        return "Messages consumed: " + receivedMessageCount.get();
    }
}
