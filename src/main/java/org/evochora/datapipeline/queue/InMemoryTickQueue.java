package org.evochora.datapipeline.queue;

import org.evochora.datapipeline.contracts.IQueueMessage;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * In-memory implementation of the tick message queue.
 * Uses a LinkedBlockingQueue with configurable element capacity.
 */
public final class InMemoryTickQueue implements ITickMessageQueue {

    private final LinkedBlockingQueue<IQueueMessage> queue;
    private final int maxMessageCount;

    /**
     * Creates a new in-memory tick queue with the specified element capacity.
     *
     * @param maxMessageCount the maximum number of messages the queue can hold
     */
    public InMemoryTickQueue(int maxMessageCount) {
        this.maxMessageCount = maxMessageCount;
        this.queue = new LinkedBlockingQueue<>(maxMessageCount);
    }


    @Override
    public void put(IQueueMessage message) throws InterruptedException {
        queue.put(message);
    }

    @Override
    public IQueueMessage take() throws InterruptedException {
        return queue.take();
    }

    @Override
    public IQueueMessage poll(long timeout, TimeUnit unit) throws InterruptedException {
        return queue.poll(timeout, unit);
    }

    @Override
    public IQueueMessage poll() {
        return queue.poll();
    }

    @Override
    public int size() {
        return queue.size();
    }

    @Override
    public int getCapacity() {
        return queue.remainingCapacity() + queue.size();
    }

    @Override
    public void clear() {
        queue.clear();
    }


    /**
     * Checks if the queue can accept another message.
     *
     * @return true if there is space for another message
     */
    public boolean canAcceptMessage() {
        return queue.remainingCapacity() > 0;
    }

    /**
     * Attempts to add a message to the queue with a short timeout.
     * Used by SimulationEngine to detect if the queue is full without blocking.
     *
     * @param message the message to add
     * @return true if the message was added, false if the queue is full
     */
    public boolean tryPut(IQueueMessage message) {
        try {
            return queue.offer(message, 1, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Gets the current number of messages in the queue.
     *
     * @return the current message count
     */
    public int getCurrentMessageCount() {
        return queue.size();
    }

    /**
     * Gets the maximum number of messages the queue can hold.
     *
     * @return the maximum message count
     */
    public int getMaxMessageCount() {
        return maxMessageCount;
    }


}


