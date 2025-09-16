package org.evochora.datapipeline.channel;

/**
 * Defines a generic output channel for sending messages of a specific type.
 *
 * @param <T> The type of the message to be sent.
 */
public interface IOutputChannel<T> {

    /**
     * Sends a message to the channel.
     * This method may block if the channel's capacity is reached.
     *
     * @param message The message to send.
     * @throws InterruptedException if the calling thread is interrupted while waiting for space to become available.
     */
    void send(T message) throws InterruptedException;
}
