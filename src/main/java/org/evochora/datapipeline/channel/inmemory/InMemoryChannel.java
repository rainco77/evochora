package org.evochora.datapipeline.channel.inmemory;

import org.evochora.datapipeline.channel.IInputChannel;
import org.evochora.datapipeline.channel.IOutputChannel;
import org.evochora.datapipeline.channel.IMonitorableChannel;
import org.evochora.datapipeline.contracts.IQueueMessage;
import org.evochora.datapipeline.queue.InMemoryTickQueue;
import org.evochora.datapipeline.queue.ITickMessageQueue;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * An in-memory channel implementation that uses a blocking queue.
 * It implements both input and output interfaces, allowing it to connect two services in memory.
 * This class is a wrapper around the existing {@link InMemoryTickQueue}.
 *
 * @param <T> The type of message to be handled by the channel.
 */
public class InMemoryChannel<T extends IQueueMessage> implements IInputChannel<T>, IOutputChannel<T>, IMonitorableChannel {

    private final ITickMessageQueue queue; // This will internally handle IQueueMessage

    /**
     * Creates a new in-memory channel with options from the configuration.
     *
     * @param options A map containing configuration, expecting a "capacity" key.
     */
    public InMemoryChannel(Map<String, Object> options) {
        // Extract capacity from options with a default value
        int capacity = ((Number) options.getOrDefault("capacity", 10000)).intValue();
        this.queue = new InMemoryTickQueue(capacity);
    }

    @Override
    public T take() throws InterruptedException {
        // The cast is now safe due to the class-level type parameter
        @SuppressWarnings("unchecked")
        T message = (T) queue.take();
        return message;
    }

    @Override
    public void send(T message) throws InterruptedException {
        queue.put(message);
    }

    @Override
    public Optional<T> poll() {
        @SuppressWarnings("unchecked")
        T message = (T) queue.poll();
        return Optional.ofNullable(message);
    }

    @Override
    public Optional<T> poll(long timeout, TimeUnit unit) throws InterruptedException {
        @SuppressWarnings("unchecked")
        T message = (T) queue.poll(timeout, unit);
        return Optional.ofNullable(message);
    }

    @Override
    public int size() {
        return queue.size();
    }

    @Override
    public int getCapacity() {
        return queue.getCapacity();
    }
}
