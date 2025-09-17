package org.evochora.datapipeline.channel;

/**
 * A channel for sending messages.
 * @param <T> The type of message to send.
 */
public interface IOutputChannel<T> {
    void send(T message) throws InterruptedException;
}
