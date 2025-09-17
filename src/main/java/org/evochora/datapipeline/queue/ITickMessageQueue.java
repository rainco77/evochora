package org.evochora.datapipeline.queue;

import org.evochora.datapipeline.contracts.IQueueMessage;
import java.util.concurrent.TimeUnit;

/**
 * Blocking queue interface for exchanging tick messages between services.
 */
public interface ITickMessageQueue {
    void put(IQueueMessage message) throws InterruptedException;
    IQueueMessage take() throws InterruptedException;
    IQueueMessage poll();
    IQueueMessage poll(long timeout, TimeUnit unit) throws InterruptedException;
    int size();
    int getCapacity();
    void clear();
}


