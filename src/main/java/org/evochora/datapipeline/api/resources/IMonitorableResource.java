package org.evochora.datapipeline.api.resources;

/**
 * An optional interface that can be implemented by resources to provide monitoring capabilities.
 * This replaces the old IMonitorableChannel interface with a more general approach.
 */
public interface IMonitorableResource {

    /**
     * Gets the current backlog or queue size for this resource.
     *
     * @return The number of items currently buffered or queued.
     */
    long getBacklogSize();

    /**
     * Gets the total capacity of this resource.
     *
     * @return The maximum capacity, or -1 if unlimited.
     */
    long getCapacity();
}
