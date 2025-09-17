package org.evochora.datapipeline.engine;

import org.evochora.compiler.Compiler;
import org.evochora.compiler.api.ProgramArtifact;
import org.evochora.runtime.model.EnvironmentProperties;
import org.evochora.datapipeline.queue.InMemoryTickQueue;
import org.evochora.datapipeline.queue.ITickMessageQueue;
import org.evochora.datapipeline.contracts.IQueueMessage;
import org.evochora.datapipeline.channel.inmemory.InMemoryChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.AfterEach;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.Map;
import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
class WorldSeedingTest {

    private InMemoryChannel<IQueueMessage> channel;
    private SimulationEngine engine;
    private final EnvironmentProperties testEnvProps = new EnvironmentProperties(new int[]{10, 10}, true);
    private Thread drainThread;

    @BeforeEach
    void setUp() {
        org.evochora.runtime.isa.Instruction.init();
        Map<String, Object> channelOptions = new HashMap<>();
        channelOptions.put("capacity", 100);
        channel = new InMemoryChannel<>(channelOptions);
        
        // A default engine is created here. Tests can re-assign this.engine if they need a custom one.
        engine = new SimulationEngine(channel, testEnvProps, new ArrayList<>(), new ArrayList<>());

        drainThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    channel.take(); // Use take() for aggressive draining
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        drainThread.setDaemon(true);
        drainThread.start();
    }

    @AfterEach
    void tearDown() {
        if (engine != null && engine.isRunning()) {
            engine.shutdown();
            try {
                waitForShutdown(2000); // Wait for the engine to stop
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        // Stop the consumer thread AFTER the producer is stopped
        if (drainThread != null) {
            drainThread.interrupt();
        }
    }

    private boolean waitForCondition(BooleanSupplier condition, long timeoutMs, String description) {
        long startTime = System.currentTimeMillis();
        long checkInterval = 10;

        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() - startTime > timeoutMs) {
                return false;
            }
            try {
                Thread.sleep(checkInterval);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return true;
    }

    private void waitForShutdown(long timeoutMillis) throws InterruptedException {
        // This method now correctly waits for the specific engine instance that the test was using.
        final SimulationEngine engineToWaitFor = this.engine;
        assertThat(waitForCondition(
            () -> !engineToWaitFor.isRunning(),
            timeoutMillis,
            "simulation engine to shutdown"
        )).isTrue();
    }

    private SimulationEngine createAndStartEngine(List<OrganismPlacement> placements) {
        SimulationEngine testEngine = new SimulationEngine(channel, testEnvProps, placements, new ArrayList<>());
        this.engine = testEngine; // Assign to class member so tearDown can shut it down
        testEngine.start();
        assertThat(waitForCondition(
            testEngine::isRunning,
            5000,
            "simulation engine to start"
        )).isTrue();
        return testEngine;
    }

    @Test
    void testWorldSeedingWithOrganisms() throws Exception {
        Compiler compiler = new Compiler();
        List<String> lines = List.of(
            "NOP",
            "NOP",
            "NOP"
        );
        ProgramArtifact artifact = compiler.compile(lines, "test_program", testEnvProps);
        
        OrganismPlacement placement = OrganismPlacement.of(artifact, 1000, new int[]{0, 0});
        
        SimulationEngine testEngine = createAndStartEngine(List.of(placement));
        
        assertThat(testEngine.isRunning()).isTrue();
        
        testEngine.shutdown();
        
        // Explicitly wait for the specific engine used in this test to shut down.
        final SimulationEngine finalEngine = testEngine;
        assertThat(waitForCondition(() -> !finalEngine.isRunning(), 5000, "test engine shutdown")).isTrue();
    }

    @Test
    void testMultipleOrganismSeeding() throws Exception {
        Compiler compiler = new Compiler();
        List<String> lines = List.of("NOP", "NOP", "NOP");
        
        ProgramArtifact artifact1 = compiler.compile(lines, "test_program_1", testEnvProps);
        ProgramArtifact artifact2 = compiler.compile(lines, "test_program_2", testEnvProps);
        
        OrganismPlacement placement1 = OrganismPlacement.of(artifact1, 1000, new int[]{0, 0});
        OrganismPlacement placement2 = OrganismPlacement.of(artifact2, 1000, new int[]{5, 5});
        
        SimulationEngine testEngine = createAndStartEngine(List.of(placement1, placement2));
        
        assertThat(testEngine.isRunning()).isTrue();
        
        testEngine.shutdown();
        
        // Explicitly wait for the specific engine used in this test to shut down.
        final SimulationEngine finalEngine = testEngine;
        assertThat(waitForCondition(() -> !finalEngine.isRunning(), 5000, "test engine shutdown")).isTrue();
    }

    @Test
    void testEmptyWorldSeeding() throws Exception {
        engine.start();
        
        assertThat(waitForCondition(
            engine::isRunning,
            5000,
            "simulation engine to start"
        )).isTrue();
        
        assertThat(engine.isRunning()).isTrue();
        
        engine.shutdown();
        
        waitForShutdown(5000);
    }
}
