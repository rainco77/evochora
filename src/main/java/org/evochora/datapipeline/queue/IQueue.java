package org.evochora.datapipeline.queue;

import java.util.concurrent.TimeUnit;
import org.evochora.datapipeline.contracts.IQueueMessage;

public interface IQueue<T> {
    void put(T message) throws InterruptedException;
    T take() throws InterruptedException;
    T poll(long timeout, TimeUnit unit) throws InterruptedException;
    T poll();
}
