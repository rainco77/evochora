package org.evochora.runtime.worldgen;

import org.evochora.runtime.Config;
import org.evochora.runtime.model.Environment;
import org.evochora.runtime.model.Molecule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.evochora.runtime.internal.services.IRandomProvider;

/**
 * A geyser-based energy distribution strategy. It creates geysers that erupt
 * at regular intervals, distributing energy to nearby cells.
 */
public class GeyserCreator implements IEnergyDistributionCreator {

    private final int geyserCount;
    private final int tickInterval;
    private final int energyAmount;
    /** Radius around a candidate cell that must be free of organism-owned cells. */
    private final int safetyRadius;
    private final Random random;
    private List<int[]> geyserLocations = null; // Initialized on first call

    /**
     * Creates a geyser-based energy distributor.
     *
     * @param randomProvider Source of randomness.
     * @param count Number of geysers to spawn.
     * @param interval Tick interval for eruptions.
     * @param amount Energy amount placed per eruption.
     * @param safetyRadius Radius around placement that must be unowned.
     */
    public GeyserCreator(IRandomProvider randomProvider, int count, int interval, int amount, int safetyRadius) {
        this.random = randomProvider.asJavaRandom();
        this.geyserCount = count;
        this.tickInterval = interval;
        this.energyAmount = amount;
        this.safetyRadius = Math.max(0, safetyRadius);
    }

    /**
     * Backward-compatible constructor for existing callers.
     * @param randomProvider Source of randomness.
     * @param count Number of geysers to spawn.
     * @param interval Tick interval for eruptions.
     * @param amount Energy amount placed per eruption.
     */
    public GeyserCreator(IRandomProvider randomProvider, int count, int interval, int amount) {
        this(randomProvider, count, interval, amount, 2);
    }

    /**
     * Backward-compatible constructor for legacy code/tests.
     * @param count Number of geysers to spawn.
     * @param interval Tick interval for eruptions.
     * @param amount Energy amount placed per eruption.
     */
    public GeyserCreator(int count, int interval, int amount) {
        this(new org.evochora.runtime.internal.services.SeededRandomProvider(0L), count, interval, amount, 2);
    }

    @Override
    public void distributeEnergy(Environment environment, long currentTick) {
        if (geyserLocations == null) {
            initializeGeysers(environment);
        }

        if (currentTick > 0 && currentTick % tickInterval == 0) {
            for (int[] geyserPos : geyserLocations) {
                // Find all valid neighbor cells (empty and safe distance from owned cells) in N dimensions
                List<int[]> validTargets = new ArrayList<>();
                int dims = environment.getShape().length;
                for (int axis = 0; axis < dims; axis++) {
                    for (int delta : new int[]{-1, 1}) {
                        int[] checkPos = java.util.Arrays.copyOf(geyserPos, dims);
                        checkPos[axis] = checkPos[axis] + delta;
                        if (environment.getMolecule(checkPos).isEmpty() && environment.isAreaUnowned(checkPos, this.safetyRadius)) {
                            validTargets.add(checkPos);
                        }
                    }
                }

                // Choose a random valid target and place the energy
                if (!validTargets.isEmpty()) {
                    Collections.shuffle(validTargets, random);
                    int[] targetCell = validTargets.get(0);
                    environment.setMolecule(new Molecule(Config.TYPE_ENERGY, energyAmount), targetCell);
                }
            }
        }
    }

    private void initializeGeysers(Environment environment) {
        geyserLocations = new ArrayList<>();
        int[] shape = environment.getShape();
        for (int i = 0; i < geyserCount; i++) {
            int[] coord = null;
            // Try to find a safe source: cell is empty and safety radius is unowned
            for (int attempt = 0; attempt < 1000; attempt++) {
                int[] c = new int[shape.length];
                for (int d = 0; d < shape.length; d++) {
                    c[d] = random.nextInt(shape[d]);
                }
                if (environment.getMolecule(c).isEmpty() && environment.isAreaUnowned(c, this.safetyRadius)) {
                    coord = c;
                    break;
                }
            }
            if (coord == null) {
                // Fallback: no safe position found, skip
                continue;
            }
            geyserLocations.add(coord);
            // Mark the source itself as indestructible to avoid conflicts
            environment.setMolecule(new Molecule(Config.TYPE_STRUCTURE, -1), coord);
        }
    }
}