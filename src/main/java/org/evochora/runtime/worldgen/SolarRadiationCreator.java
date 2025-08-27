package org.evochora.runtime.worldgen;

import org.evochora.runtime.Config;
import org.evochora.runtime.model.Environment;
import org.evochora.runtime.model.Molecule;
import java.util.Random;
import org.evochora.runtime.internal.services.IRandomProvider;

/**
 * A solar radiation-based energy distribution strategy. It randomly spawns
 * energy in free cells based on a given probability.
 */
public class SolarRadiationCreator implements IEnergyDistributionCreator {

    private final Random random;
    private final double spawnProbability;
    private final int spawnAmount;
    private final int safetyRadius;
    /**
     * Number of independent execution attempts per tick. Each attempt applies the
     * configured probability gate. Must be >= 1.
     */
    private final int executionsPerTick;

    /**
     * Creates a solar radiation distributor.
     *
     * @param randomProvider Source of randomness.
     * @param probability Probability per execution to spawn energy in a random free cell.
     * @param amount Energy amount placed when an execution succeeds.
     * @param safetyRadius Radius around placement that must be unowned.
     * @param executionsPerTick How many independent executions to run per tick (each with its own probability check).
     */
    public SolarRadiationCreator(IRandomProvider randomProvider, double probability, int amount, int safetyRadius, int executionsPerTick) {
        this.random = randomProvider.asJavaRandom();
        this.spawnProbability = probability;
        this.spawnAmount = amount;
        this.safetyRadius = safetyRadius;
        this.executionsPerTick = Math.max(1, executionsPerTick);
    }

    /**
     * Backward-compatible constructor (defaults executionsPerTick to 1).
     * @param randomProvider Source of randomness.
     * @param probability Probability per execution to spawn energy in a random free cell.
     * @param amount Energy amount placed when an execution succeeds.
     * @param safetyRadius Radius around placement that must be unowned.
     */
    public SolarRadiationCreator(IRandomProvider randomProvider, double probability, int amount, int safetyRadius) {
        this(randomProvider, probability, amount, safetyRadius, 1);
    }

    /**
     * Backward-compatible constructor for tests and legacy code.
     * @param probability Probability per execution to spawn energy in a random free cell.
     * @param amount Energy amount placed when an execution succeeds.
     * @param safetyRadius Radius around placement that must be unowned.
     */
    public SolarRadiationCreator(double probability, int amount, int safetyRadius) {
        this(new org.evochora.runtime.internal.services.SeededRandomProvider(0L), probability, amount, safetyRadius, 1);
    }

    /**
     * Convenience constructor for tests allowing executionsPerTick configuration.
     * @param probability Probability per execution to spawn energy in a random free cell.
     * @param amount Energy amount placed when an execution succeeds.
     * @param safetyRadius Radius around placement that must be unowned.
     * @param executionsPerTick How many independent executions to run per tick (each with its own probability check).
     */
    public SolarRadiationCreator(double probability, int amount, int safetyRadius, int executionsPerTick) {
        this(new org.evochora.runtime.internal.services.SeededRandomProvider(0L), probability, amount, safetyRadius, executionsPerTick);
    }

    @Override
    public void distributeEnergy(Environment environment, long currentTick) {
        for (int attempt = 0; attempt < this.executionsPerTick; attempt++) {
            if (random.nextDouble() < this.spawnProbability) {
                int[] shape = environment.getShape();
                int[] coord = new int[shape.length];
                for (int i = 0; i < shape.length; i++) {
                    coord[i] = random.nextInt(shape[i]);
                }

                // Area must be unowned (distance to organism cells)
                if (environment.getMolecule(coord).isEmpty() && environment.isAreaUnowned(coord, this.safetyRadius)) {
                    environment.setMolecule(new Molecule(Config.TYPE_ENERGY, spawnAmount), coord);
                }
            }
        }
    }
}