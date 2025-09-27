package org.evochora.node.spi;

/**
 * Defines the contract for a long-running, manageable background process within the Node.
 * Processes have a simple lifecycle and are managed by the main Node application.
 */
public interface IProcess {

    /**
     * Starts the process. This method should be non-blocking or managed by a separate thread
     * if it contains a long-running loop.
     */
    void start();

    /**
     * Stops the process gracefully, releasing any resources it holds.
     */
    void stop();
}