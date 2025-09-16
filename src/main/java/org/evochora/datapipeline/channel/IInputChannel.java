package org.evochora.datapipeline.channel;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

public interface IInputChannel<T> {
    T take() throws InterruptedException;

    /**
     * Retrieves and removes the head of this channel, or returns an empty Optional if this channel is empty.
     *
     * @return an {@link Optional} containing the head of this channel, or an empty {@code Optional} if this channel is empty
     */
    Optional<T> poll();

    /**
     * Retrieves and removes the head of this channel, waiting up to the
     * specified wait time if necessary for an element to become available.
     *
     * @param timeout how long to wait before giving up, in units of {@code unit}
     * @param unit a {@code TimeUnit} determining how to interpret the {@code timeout} parameter
     * @return an {@code Optional} containing the head of this channel, or an empty {@code Optional} if the
     *         specified waiting time elapses before an element is available
     * @throws InterruptedException if interrupted while waiting
     */
    Optional<T> poll(long timeout, TimeUnit unit) throws InterruptedException;
}
