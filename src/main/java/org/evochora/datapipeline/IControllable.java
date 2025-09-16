package org.evochora.datapipeline;

/**
 * Basic lifecycle controls for long-running services.
 */
public interface IControllable {
    void start();
    void pause();
    void resume();
    void shutdown();
    boolean isRunning();
    boolean isPaused();
    boolean isAutoPaused();
    
    /**
     * Flushes any in-memory buffers to the underlying storage.
     * This method is intended for ensuring data visibility between services during tests
     * without requiring a full shutdown. Implementations for services without buffers
     * can be a no-op.
     */
    void flush();
}


