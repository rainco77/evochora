package org.evochora.datapipeline;

import org.evochora.datapipeline.contracts.IQueueMessage;
import org.evochora.datapipeline.contracts.raw.RawTickState;
import org.evochora.datapipeline.engine.SimulationEngine;
import org.evochora.runtime.model.EnvironmentProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Map;
import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.evochora.datapipeline.channel.inmemory.InMemoryChannel;

/**
 * Contains integration tests for the lifecycle control of the {@link SimulationEngine}.
 * These tests verify that the engine thread can be correctly started, paused, resumed, and shut down.
 * These are integration tests as they involve managing a live, threaded service.
 */
class SimulationEngineControlTest {

    private SimulationEngine sim;
    private InMemoryChannel<IQueueMessage> channel;
    private Thread drainThread; // Thread to drain the channel

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        Map<String, Object> channelOptions = new HashMap<>();
        channelOptions.put("capacity", 100); // Small capacity for testing
        channel = new InMemoryChannel<>(channelOptions);

        // This sim instance is now consistently used by all tests and managed by tearDown
        sim = new SimulationEngine(channel, new EnvironmentProperties(new int[]{10, 10}, true),
                new ArrayList<>(), new ArrayList<>());

        drainThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    // Use take() to aggressively drain the queue and respond to interrupts
                    channel.take();
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
        if (sim != null && sim.isRunning()) {
            sim.shutdown();
            try {
                waitForShutdown(2000); // Wait for engine to stop
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        // Stop the consumer thread AFTER the producer is stopped
        if (drainThread != null) {
            drainThread.interrupt();
        }
    }

    /**
     * Wait for a condition to be true, checking every 10ms
     * @param condition The condition to wait for
     * @param timeoutMs Maximum time to wait in milliseconds
     * @param description Description of what we're waiting for
     * @return true if condition was met, false if timeout occurred
     */
    private boolean waitForCondition(BooleanSupplier condition, long timeoutMs, String description) {
        long startTime = System.currentTimeMillis();
        long checkInterval = 10; // Check every 10ms for faster response

        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() - startTime > timeoutMs) {
                System.out.println("Timeout waiting for: " + description);
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

    /**
     * Helper method to wait until the simulation has reached a specific tick.
     * @param tick The target tick number.
     * @param timeoutMillis The maximum time to wait.
     * @throws InterruptedException if the thread is interrupted while waiting.
     */
    private void waitForTick(long tick, long timeoutMillis) throws InterruptedException {
        assertTrue(waitForCondition(
            () -> sim.getCurrentTick() >= tick,
            timeoutMillis,
            "tick to reach " + tick + " (current: " + sim.getCurrentTick() + ")"
        ));
    }

    /**
     * Helper method to wait until the simulation engine thread has shut down.
     * @param timeoutMillis The maximum time to wait.
     * @throws InterruptedException if the thread is interrupted while waiting.
     */
    private void waitForShutdown(long timeoutMillis) throws InterruptedException {
        // Use the class member 'sim' which is managed by tearDown
        assertTrue(waitForCondition(
            () -> !sim.isRunning(),
            timeoutMillis,
            "simulation engine to shutdown"
        ));
    }

    /**
     * A simple test to verify that the simulation engine can be started and then gracefully shut down.
     * This is an integration test of the service lifecycle.
     * @throws Exception if the test fails.
     */
    @Test
    @Tag("integration")
    void simple_start_shutdown_test() throws Exception {
        // This test can use the default engine from setUp
        sim.start();

        // Wait for simulation to start and produce some ticks
        waitForTick(0, 5000);
        assertThat(sim.getCurrentTick()).isGreaterThanOrEqualTo(0L);

        // Shutdown
        sim.shutdown();
        
        // Wait for shutdown to complete
        waitForShutdown(5000);
        
        assertThat(sim.isRunning()).isFalse();
    }

    /**
     * A comprehensive test that verifies the full lifecycle of the simulation engine:
     * start, run, pause, resume, and shutdown. It checks that ticks are produced
     * only when the service is in a running state.
     * This is an integration test of the service lifecycle.
     * @throws Exception if the test fails.
     */
    @Test
    @Tag("integration")
    void start_pause_resume_shutdown_cycle_advances_ticks() throws Exception {
        // This test can use the default engine from setUp
        sim.start();

        // 1. Wait for the simulation to initialize and produce the first tick.
        waitForTick(0, 5000);
        IQueueMessage initialMessage = channel.take();
        assertThat(initialMessage).isNotNull();
        long initialTick = ((RawTickState) initialMessage).tickNumber();
        assertThat(initialTick).isGreaterThanOrEqualTo(0L);

        // 2. Let the simulation run briefly.
        waitForTick(initialTick + 1, 5000);
        long t1 = sim.getCurrentTick();
        assertThat(t1).isGreaterThan(initialTick);

        // 3. Pause and wait for it to take effect.
        sim.pause();
        assertTrue(waitForCondition(
            sim::isPaused,
            1000,
            "simulation to pause"
        ));
        long tickAfterPause = sim.getCurrentTick();

        // 4. (MODIFIED) Drain any potential in-flight message after pausing.
        // This part of the test can only run if the channel supports polling.
        while(channel.poll().isPresent()) {
             // draining...
        }

        // 5. Assert that no NEW ticks arrive while paused.
        Optional<IQueueMessage> messageDuringPause = channel.poll(200, TimeUnit.MILLISECONDS);
        assertThat(messageDuringPause).isEmpty();
        assertThat(sim.getCurrentTick()).isEqualTo(tickAfterPause);

        // 6. Resume and wait for the next message.
        sim.resume();
        IQueueMessage nextMessage = channel.take(); // take() is fine here, test will timeout if it fails
        assertThat(nextMessage).isNotNull();
        assertThat(((RawTickState) nextMessage).tickNumber()).isGreaterThan(tickAfterPause);

        // 7. Shutdown.
        sim.shutdown();
        waitForShutdown(5000);
        assertThat(sim.isRunning()).isFalse();
    }

    /**
     * A minimal test to verify the shutdown functionality after a short run.
     * This is an integration test of the service lifecycle.
     * @throws Exception if the test fails.
     */
    @Test
    @Tag("integration")
    void minimal_shutdown_test() throws Exception {
        // This test can use the default engine from setUp
        sim.start();

        // Wait for simulation to start
        waitForTick(0, 5000);
        assertThat(sim.getCurrentTick()).isGreaterThanOrEqualTo(0L);

        // Shutdown
        sim.shutdown();
        
        // Wait for shutdown to complete
        waitForShutdown(5000);
        
        assertThat(sim.isRunning()).isFalse();
    }

    /**
     * Verifies that the engine can be shut down immediately after starting.
     * This is an integration test of the service lifecycle robustness.
     * @throws Exception if the test fails.
     */
    @Test
    @Tag("integration")
    void immediate_shutdown_test() throws Exception {
        // This test can use the default engine from setUp
        System.out.println("[Test] Starting simulation engine...");
        sim.start();

        // Shutdown immediately
        System.out.println("[Test] Shutting down simulation engine...");
        sim.shutdown();
        
        // Wait for shutdown to complete
        System.out.println("[Test] Waiting for shutdown to complete...");
        waitForShutdown(5000);
        
        assertThat(sim.isRunning()).isFalse();
        System.out.println("[Test] Shutdown verified.");
    }
}