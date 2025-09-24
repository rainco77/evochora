package org.evochora.datapipeline.api.resources.storage.indexer.model;

import java.util.Map;
import java.util.List;

/**
 * Represents the complete state of the simulation environment at a specific tick.
 * Part of the Universal DI resources system for storage data models.
 */
public record EnvironmentState(
    long tick,
    Map<Position, List<Object>> molecules,
    Map<Position, Object> organisms,
    long timestamp
) {

    /**
     * Creates an EnvironmentState with the current timestamp.
     *
     * @param tick The simulation tick
     * @param molecules Map of molecules at each position
     * @param organisms Map of organisms at each position
     * @return EnvironmentState with current timestamp
     */
    public static EnvironmentState withCurrentTimestamp(
            long tick,
            Map<Position, List<Object>> molecules,
            Map<Position, Object> organisms) {
        return new EnvironmentState(tick, molecules, organisms, System.currentTimeMillis());
    }
}
