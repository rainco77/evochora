package org.evochora.runtime.worldgen;

import org.evochora.runtime.Config;
import org.evochora.runtime.model.Environment;
import org.evochora.runtime.model.Molecule;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contains unit tests for the {@link GeyserCreator} world generation strategy.
 * These tests verify the logic of geyser placement and energy eruption.
 * They operate on an in-memory environment and do not require external resources.
 */
public class GeyserStrategyTest {

    /**
     * Verifies that the GeyserCreator correctly initializes a geyser source and subsequently
     * places energy in the environment when it erupts. The test checks a 3D environment
     * to ensure the logic is dimension-agnostic.
     * This is a unit test for the geyser world generation logic.
     */
    @Test
    @Tag("unit")
    void initializesGeysersAndPlacesEnergyOnAxisAdjacentCells_inND() {
        // 3D world 3x3x3 to allow neighbors around random geyser locations
        Environment env = new Environment(new int[]{3, 3, 3}, true);
        // count=1 for determinism, interval=1 to trigger on tick 1, amount=77
        GeyserCreator strat = new GeyserCreator(1, 1, 77);

        // First call initializes geysers but does not place energy until tick%interval==0 and >0
        strat.distributeEnergy(env, 0);
        strat.distributeEnergy(env, 1);

        // Verify at least one ENERGY cell exists with the correct amount
        boolean found = false;
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
                for (int z = 0; z < 3; z++) {
                    Molecule m = env.getMolecule(x, y, z);
                    if (m.type() == Config.TYPE_ENERGY && m.toScalarValue() == 77) {
                        found = true;
                        break;
                    }
                }
                if (found) break;
            }
            if (found) break;
        }
        assertThat(found).isTrue();
    }
}
