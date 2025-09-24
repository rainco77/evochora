package org.evochora.datapipeline.api.resources.storage.indexer.model;

/**
 * Represents a position in the simulation environment.
 * Part of the Universal DI resources system for storage data models.
 */
public record Position(
    int x,
    int y
) {

    /**
     * Creates a Position from an array.
     *
     * @param coordinates Array with [x, y] coordinates
     * @return Position instance
     */
    public static Position from(int[] coordinates) {
        if (coordinates.length != 2) {
            throw new IllegalArgumentException("Position requires exactly 2 coordinates");
        }
        return new Position(coordinates[0], coordinates[1]);
    }

    /**
     * Converts this position to an array.
     *
     * @return Array with [x, y] coordinates
     */
    public int[] toArray() {
        return new int[]{x, y};
    }
}
