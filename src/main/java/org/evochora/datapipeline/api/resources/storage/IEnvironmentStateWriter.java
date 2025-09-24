package org.evochora.datapipeline.api.resources.storage;

import org.evochora.datapipeline.api.resources.storage.indexer.model.EnvironmentState;

/**
 * Interface for writing environment state data to storage.
 * This is part of the Universal DI resources system for storage providers.
 */
public interface IEnvironmentStateWriter {

    /**
     * Writes environment state data to the storage backend.
     *
     * @param environmentState The environment state to write
     * @throws Exception if writing fails
     */
    void writeEnvironmentState(EnvironmentState environmentState) throws Exception;

    /**
     * Flushes any pending writes to ensure data persistence.
     *
     * @throws Exception if flush fails
     */
    void flush() throws Exception;

    /**
     * Closes the writer and releases any resources.
     *
     * @throws Exception if closing fails
     */
    void close() throws Exception;
}
