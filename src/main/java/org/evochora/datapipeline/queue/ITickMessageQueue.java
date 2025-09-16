package org.evochora.datapipeline.queue;

import org.evochora.datapipeline.contracts.IQueueMessage;
import java.util.concurrent.TimeUnit;

/**
 * Blocking queue interface for exchanging tick messages between services.
 */
public interface ITickMessageQueue extends IQueue<IQueueMessage> {
    // Methods inherited from IQueue:
    // void put(IQueueMessage message) throws InterruptedException;
    // IQueueMessage take() throws InterruptedException;
    // ...
    int size();
    int getCapacity();
}


