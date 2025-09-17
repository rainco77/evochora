package org.evochora.datapipeline.engine;

import org.evochora.compiler.api.ProgramArtifact;
import org.evochora.runtime.Simulation;
import org.evochora.runtime.internal.services.IRandomProvider;
import org.evochora.runtime.model.Environment;
import org.evochora.runtime.model.Organism;
import org.evochora.runtime.worldgen.IEnergyDistributionCreator;
import org.evochora.runtime.worldgen.EnergyStrategyFactory;
import org.evochora.datapipeline.IControllable;
import org.evochora.datapipeline.channel.IOutputChannel;
import org.evochora.datapipeline.contracts.IQueueMessage;
import org.evochora.datapipeline.contracts.ProgramArtifactMessage;
import org.evochora.datapipeline.contracts.raw.RawCellState;
import org.evochora.datapipeline.contracts.raw.RawOrganismState;
import org.evochora.datapipeline.contracts.raw.RawTickState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class SimulationEngine implements IControllable, Runnable {

    private static final Logger log = LoggerFactory.getLogger(SimulationEngine.class);
    
    @FunctionalInterface
    public interface CheckpointPauseCallback {
        void onCheckpointPause(int pausedAtTick, int[] remainingTicks);
    }

    private final IOutputChannel<IQueueMessage> outputChannel;
    private final Thread thread;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private final AtomicBoolean autoPaused = new AtomicBoolean(false);
    private final AtomicBoolean manuallyPaused = new AtomicBoolean(false);
    private final org.evochora.runtime.model.EnvironmentProperties environmentProperties;
    private Simulation simulation;
    private List<OrganismPlacement> organismPlacements;
    private List<IEnergyDistributionCreator> energyStrategies;
    private IRandomProvider randomProvider;
    private long startTime = 0;
    private int[] checkpointPauseTicks = null; // Configuration for checkpoint-pause ticks
    private int nextCheckpointPauseIndex = 0; // Index of next checkpoint-pause tick to check
    private Long maxTicks = null; // Maximum number of ticks to run before stopping (null = no limit)
    private CheckpointPauseCallback checkpointPauseCallback = null; // Callback for checkpoint-pause events
    
    // Simple TPS calculation - no complex timer tracking needed
    
    // TEST FLAG: ProgramArtifact-Funktionalität ein-/ausschalten
    // true = normal (mit ProgramArtifact), false = nur Code platzieren (ohne ProgramArtifact)
    private boolean enableProgramArtifactFeatures = true; // Default-Wert

    /**
     * Creates a new SimulationEngine with the specified configuration.
     * 
     * @param outputChannel The output channel for communication with other services
     * @param environmentProperties The environment configuration (world shape, toroidal setting)
     * @param organismPlacements The list of organisms to place in the simulation
     * @param energyStrategies The energy distribution strategies
     * @param skipProgramArtefact Whether to skip ProgramArtifact features (default: false)
     */
    public SimulationEngine(IOutputChannel<IQueueMessage> outputChannel, 
                           org.evochora.runtime.model.EnvironmentProperties environmentProperties,
                           List<OrganismPlacement> organismPlacements, 
                           List<IEnergyDistributionCreator> energyStrategies, 
                           boolean skipProgramArtefact) {
        this.outputChannel = outputChannel;
        this.environmentProperties = environmentProperties;
        this.organismPlacements = organismPlacements != null ? new java.util.ArrayList<>(organismPlacements) : new java.util.ArrayList<>();
        this.energyStrategies = energyStrategies != null ? new java.util.ArrayList<>(energyStrategies) : new java.util.ArrayList<>();
        this.randomProvider = null;
        this.thread = new Thread(this, "SimulationEngine");
        this.thread.setDaemon(true);
        
        // Setze ProgramArtifact-Konfiguration direkt
        this.enableProgramArtifactFeatures = !skipProgramArtefact;
    }
    
    /**
     * Creates a new SimulationEngine with ProgramArtifact features enabled by default.
     */
    public SimulationEngine(IOutputChannel<IQueueMessage> outputChannel, 
                           org.evochora.runtime.model.EnvironmentProperties environmentProperties,
                           List<OrganismPlacement> organismPlacements, 
                           List<IEnergyDistributionCreator> energyStrategies) {
        this(outputChannel, environmentProperties, organismPlacements, energyStrategies, false);
    }
    
    /**
     * Simple constructor for tests with default environment.
     */
    public SimulationEngine(IOutputChannel<IQueueMessage> outputChannel) {
        this(outputChannel, new org.evochora.runtime.model.EnvironmentProperties(new int[]{120, 80}, true), 
             new java.util.ArrayList<>(), new java.util.ArrayList<>());
    }
    






    public void setEnergyStrategies(java.util.List<org.evochora.datapipeline.config.SimulationConfiguration.EnergyStrategyConfig> configs) {
        if (configs == null || configs.isEmpty()) {
            this.energyStrategies = java.util.Collections.emptyList();
            return;
        }
        java.util.List<IEnergyDistributionCreator> built = new java.util.ArrayList<>();
        for (org.evochora.datapipeline.config.SimulationConfiguration.EnergyStrategyConfig cfg : configs) {
            if (cfg == null || cfg.type == null || cfg.type.isBlank()) continue;
            try {
                org.evochora.runtime.internal.services.IRandomProvider prov = this.randomProvider != null ? this.randomProvider : new org.evochora.runtime.internal.services.SeededRandomProvider(0L);
                built.add(EnergyStrategyFactory.create(cfg.type, cfg.params, prov));
            } catch (IllegalArgumentException ex) {
                log.warn("Ignoring unknown energy strategy type '{}': {}", cfg.type, ex.getMessage());
            } catch (Exception ex) {
                log.warn("Failed to build energy strategy '{}': {}", cfg.type, ex.getMessage());
            }
        }
        this.energyStrategies = java.util.Collections.unmodifiableList(built);
    }
    


    public void setSeed(java.lang.Long seed) {
        if (seed == null) {
            this.randomProvider = null;
        } else {
            this.randomProvider = new org.evochora.runtime.internal.services.SeededRandomProvider(seed);
        }
    }

    /**
     * Sets the checkpoint-pause ticks configuration.
     * @param checkpointPauseTicks Array of tick values where simulation should checkpoint-pause, or null to disable
     */
    public void setCheckpointPauseTicks(int[] checkpointPauseTicks) {
        this.checkpointPauseTicks = checkpointPauseTicks != null ? java.util.Arrays.copyOf(checkpointPauseTicks, checkpointPauseTicks.length) : null;
        this.nextCheckpointPauseIndex = 0;
        if (this.checkpointPauseTicks != null) {
            java.util.Arrays.sort(this.checkpointPauseTicks); // Ensure ticks are in ascending order
        }
    }

    /**
     * Gets the current checkpoint-pause ticks configuration.
     * @return Array of tick values where simulation should checkpoint-pause, or null if disabled
     */
    public int[] getCheckpointPauseTicks() {
        return this.checkpointPauseTicks != null ? java.util.Arrays.copyOf(this.checkpointPauseTicks, this.checkpointPauseTicks.length) : null;
    }

    /**
     * Sets the maximum number of ticks to run before stopping.
     * @param maxTicks Maximum number of ticks, or null to disable limit (run indefinitely)
     */
    public void setMaxTicks(Long maxTicks) {
        this.maxTicks = maxTicks;
        if (maxTicks != null) {
            log.info("Maximum ticks configured: {}", maxTicks);
        } else {
            log.info("Maximum ticks disabled - simulation will run indefinitely");
        }
    }
    
    /**
     * Sets the checkpoint-pause callback for logging purposes.
     * @param callback Callback to invoke when checkpoint-pause occurs
     */
    public void setCheckpointPauseCallback(CheckpointPauseCallback callback) {
        this.checkpointPauseCallback = callback;
    }

    @Override
    public void start() {
        if (running.compareAndSet(false, true)) {
            startTime = System.currentTimeMillis();
            Environment env = new Environment(environmentProperties);
            simulation = new Simulation(env);
            simulation.setRandomProvider(randomProvider);
            
            thread.start();
        }
    }

    @Override
    public void pause() { 
        manuallyPaused.set(true);
        paused.set(true);
    }

    @Override
    public void resume() { 
        manuallyPaused.set(false);
        paused.set(false); 
        autoPaused.set(false);
        // Don't start processing timer here - it will start when actual tick processing begins
    }

    @Override
    public void shutdown() {
        if (running.compareAndSet(true, false)) {
            log.debug("Shutdown initiated for SimulationEngine.");
            thread.interrupt();
            try {
                // Wait for the service thread to die to ensure clean termination.
                thread.join(2000); // Wait up to 2 seconds
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted while waiting for SimulationEngine to shut down.");
            }
            if (thread.isAlive()) {
                log.warn("SimulationEngine thread did not terminate gracefully.");
            }
        }
    }
    
    public void forceShutdown() {
        if (running.get()) {
            running.set(false);
            thread.interrupt();
        }
    }

    @Override
    public boolean isRunning() { return running.get(); }

    @Override
    public boolean isPaused() { return paused.get(); }
    
    @Override
    public boolean isAutoPaused() { return autoPaused.get(); }

    @Override
    public void flush() {
        // No-op for SimulationEngine, as it writes directly to the output channel and has no internal buffers to flush.
    }

    private double calculateTPS() {
        long currentTime = System.currentTimeMillis();
        long currentTick = getCurrentTick();
        
        if (currentTick <= 0 || startTime <= 0) {
            return 0.0;
        }
        
        long totalTime = currentTime - startTime;
        if (totalTime <= 0) {
            return 0.0;
        }
        
        // Simple calculation: ticks / time since start
        return (double) currentTick / (totalTime / 1000.0);
    }
    
    public long getCurrentTick() {
        return simulation != null ? simulation.getCurrentTick() : 0L;
    }

    public Simulation getSimulation() { return simulation; }

    /**
     * Checks if the simulation should checkpoint-pause at the current tick.
     * @return true if checkpoint-pause should occur, false otherwise
     */
    private boolean shouldCheckpointPause() {
        if (checkpointPauseTicks == null || checkpointPauseTicks.length == 0 || nextCheckpointPauseIndex >= checkpointPauseTicks.length) {
            return false;
        }
        
        long currentTick = simulation.getCurrentTick();
        // Check if we've reached or passed the next checkpoint-pause tick
        if (currentTick >= checkpointPauseTicks[nextCheckpointPauseIndex]) {
            int pausedAtTick = checkpointPauseTicks[nextCheckpointPauseIndex];
            nextCheckpointPauseIndex++;
            
            // Calculate remaining ticks and invoke callback
            if (checkpointPauseCallback != null) {
                int[] remainingTicks = new int[checkpointPauseTicks.length - nextCheckpointPauseIndex];
                if (remainingTicks.length > 0) {
                    System.arraycopy(checkpointPauseTicks, nextCheckpointPauseIndex, remainingTicks, 0, remainingTicks.length);
                }
                checkpointPauseCallback.onCheckpointPause(pausedAtTick, remainingTicks);
            }
            
            return true;
        }
        
        return false;
    }

    public int[] getOrganismCounts() {
        if (simulation == null) return new int[]{0, 0};
        int living = 0, dead = 0;
        for (var o : simulation.getOrganisms()) {
            if (o.isDead()) dead++; else living++;
        }
        return new int[]{living, dead};
    }
    

    public String getStatus() {
        if (simulation == null) {
            return "NOT_STARTED";
        }
        
        long currentTick = simulation.getCurrentTick();
        int[] counts = getOrganismCounts();
        double tps = calculateTPS();
        
        String status = running.get() ? "started" : "stopped";
        if (paused.get()) {
            String pauseType;
            if (manuallyPaused.get()) {
                pauseType = "paused";
            } else if (autoPaused.get()) {
                pauseType = "auto-paused";
            } else {
                pauseType = "paused";
            }
            return String.format("%-12s %-8d %-8.2f %s",
                    pauseType, currentTick, tps,
                    String.format("organisms:[%d,%d]", counts[0], counts[1]));
        }
        
        return String.format("%-12s %-8d %-8.2f",
                status, currentTick, tps);
    }

    @Override
    public void run() {
        running.set(true); // Signal that the thread is running
        try {
            var env = new org.evochora.runtime.model.Environment(this.environmentProperties);
            simulation = new Simulation(env); // Always run in debug mode
            if (this.randomProvider == null) {
                this.randomProvider = new org.evochora.runtime.internal.services.SeededRandomProvider(0L);
            }
            simulation.setRandomProvider(this.randomProvider);

            // Place organisms from OrganismPlacement list
            if (organismPlacements != null && !organismPlacements.isEmpty()) {
                java.util.Map<String, ProgramArtifact> artifactsById = new java.util.HashMap<>();
                
                for (OrganismPlacement placement : organismPlacements) {
                    try {
                        ProgramArtifact artifact = placement.programArtifact();
                        artifactsById.put(artifact.programId(), artifact);
                        
                        // Create organism with IP offset
                        int[] initialIpPosition = placement.getInitialIpPosition();
                        org.evochora.runtime.model.Organism organism = org.evochora.runtime.model.Organism.create(
                            simulation, 
                            initialIpPosition, 
                            placement.initialEnergy(), 
                            simulation.getLogger()
                        );
                        
                        if (enableProgramArtifactFeatures) {
                            organism.setProgramId(artifact.programId());
                        }
                        
                        
                        simulation.addOrganism(organism);
                        
                        // Place code in environment with organism ID as owner
                        for (Map.Entry<int[], Integer> e : artifact.machineCodeLayout().entrySet()) {
                            int[] rel = e.getKey();
                            int[] abs = new int[placement.startPosition().length];
                            for (int i = 0; i < placement.startPosition().length; i++) {
                                abs[i] = placement.startPosition()[i] + rel[i];
                            }
                            simulation.getEnvironment().setMolecule(
                                org.evochora.runtime.model.Molecule.fromInt(e.getValue()), 
                                organism.getId(), 
                                abs
                            );
                        }
                        
                        // Place initial world objects with organism ID as owner
                        for (Map.Entry<int[], org.evochora.compiler.api.PlacedMolecule> e : artifact.initialWorldObjects().entrySet()) {
                            int[] rel = e.getKey();
                            int[] abs = new int[placement.startPosition().length];
                            for (int i = 0; i < placement.startPosition().length; i++) {
                                abs[i] = placement.startPosition()[i] + rel[i];
                            }
                            org.evochora.compiler.api.PlacedMolecule pm = e.getValue();
                            simulation.getEnvironment().setMolecule(
                                new org.evochora.runtime.model.Molecule(pm.type(), pm.value()), 
                                organism.getId(), 
                                abs
                            );
                        }
                        
                        // Register call-site bindings in CallBindingRegistry
                        if (enableProgramArtifactFeatures) {
                            org.evochora.runtime.internal.services.CallBindingRegistry registry = 
                                org.evochora.runtime.internal.services.CallBindingRegistry.getInstance();
                            for (Map.Entry<Integer, int[]> binding : artifact.callSiteBindings().entrySet()) {
                                int linearAddress = binding.getKey();
                                int[] relativeCoord = artifact.linearAddressToCoord().get(linearAddress);
                                if (relativeCoord != null) {
                                    int[] absoluteCoord = new int[placement.startPosition().length];
                                    for (int i = 0; i < placement.startPosition().length; i++) {
                                        absoluteCoord[i] = placement.startPosition()[i] + relativeCoord[i];
                                    }
                                    registry.registerBindingForAbsoluteCoord(absoluteCoord, binding.getValue());
                                }
                            }
                        }
                        
                        log.info("Created organism {} at position {} with program {}", 
                            organism.getId(), 
                            java.util.Arrays.toString(placement.startPosition()), 
                            artifact.programId());
                            
                    } catch (Exception e) {
                        log.warn("Failed to create organism at position {}: {}", 
                            java.util.Arrays.toString(placement.startPosition()), e.getMessage());
                    }
                }
                
                // Set program artifacts in simulation
                if (enableProgramArtifactFeatures) {
                    simulation.setProgramArtifacts(artifactsById);
                }
                
                // Send ProgramArtifacts to persistence service
                if (enableProgramArtifactFeatures) {
                    for (Map.Entry<String, ProgramArtifact> entry : artifactsById.entrySet()) {
                        try {
                            ProgramArtifactMessage artifactMsg = new ProgramArtifactMessage(entry.getKey(), entry.getValue());
                            outputChannel.send(artifactMsg);
                            log.debug("Sent ProgramArtifact {} to persistence queue", entry.getKey());
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            log.warn("[Engine] Interrupted while sending ProgramArtifact. Terminating.");
                            return; // <-- EXIT THREAD
                        } catch (Exception e) {
                            log.warn("Failed to send ProgramArtifact {} to persistence queue: {}", entry.getKey(), e.getMessage());
                        }
                    }
                }
            }


            // Send initial tick state (tick 0) before starting the simulation loop
            try {
                RawTickState initialTickMsg = toRawState(simulation);
                outputChannel.send(initialTickMsg);
                log.debug("Sent initial tick state (tick 0) to queue");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("[Engine] Interrupted while sending initial tick. Terminating.");
                return; // <-- EXIT THREAD
            }
            
            // Start simulation loop
            while (running.get()) {
                if (paused.get()) {
                    // REMOVED: All auto-resume logic based on canAcceptMessage()
                    try {
                        Thread.sleep(100); 
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    continue;
                }

                try {
                    // Check if we should pause at this tick FIRST (has priority over queue-full)
                    if (shouldCheckpointPause()) {
                        manuallyPaused.set(true);
                        paused.set(true);
                        continue;
                    }
                    
                    // REMOVED: All pre-emptive checks using canAcceptMessage()
                    simulation.tick();
                    
                    // Check if we've reached the maximum tick limit BEFORE writing the tick
                    if (maxTicks != null && simulation.getCurrentTick() >= maxTicks) {
                        log.info("Reached maximum tick limit ({}), stopping simulation", maxTicks);
                        running.set(false);
                        break;
                    }
                    
                    // Apply energy distribution strategies AFTER the tick
                    if (energyStrategies != null && !energyStrategies.isEmpty()) {
                        for (IEnergyDistributionCreator strategy : energyStrategies) {
                            try {
                                strategy.distributeEnergy(simulation.getEnvironment(), simulation.getCurrentTick());
                            } catch (Exception ex) {
                                log.warn("Energy strategy execution failed: {}", ex.getMessage());
                            }
                        }
                    }
                    
                    // Always create raw tick state
                    RawTickState tickMsg = toRawState(simulation);
                    
                    // Try to put message - if it fails, go to auto-pause
                    outputChannel.send(tickMsg);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    // InterruptedException is expected during shutdown, not an error
                    log.info("[Engine] Interrupted in main loop. Terminating.");
                    return; // <-- EXIT THREAD
                } catch (Exception e) {
                    if (running.get()) {
                        log.error("Simulation tick failed, terminating service: {}", e.getMessage());
                        break;
                    }
                }
            }
        } catch (Exception e) {
            if (running.get()) {
                log.error("SimulationEngine fatal error, terminating service: {}", e.getMessage());
            }
        } finally {
            // Log graceful termination from the service thread
            if (Thread.currentThread().getName().equals("SimulationEngine")) {
                long currentTick = getCurrentTick();
                double tps = calculateTPS();
                log.info("SimulationEngine: graceful termination tick:{} TPS:{}", currentTick, String.format("%.2f", tps));
            }
        }
    }

    private RawTickState toRawState(Simulation simulation) {
        final var env = simulation.getEnvironment();
        final int[] shape = env.getShape();
        final int dims = shape.length;

        // Use sparse cell tracking if enabled, otherwise fall back to full iteration
        List<RawCellState> cells;
        if (org.evochora.runtime.Config.ENABLE_SPARSE_CELL_TRACKING) {
            // Sparse: Only get occupied cells (much faster for large worlds)
            cells = env.getOccupiedCells();
            if (cells == null) {
                // Fallback to full iteration if tracking is disabled
                cells = new ArrayList<>();
                int[] coord = new int[dims];
                Arrays.fill(coord, 0);
                final List<RawCellState> finalCells = cells;
                iterateOptimized(shape, 0, coord, () -> {
                    var m = env.getMolecule(coord);
                    int ownerId = env.getOwnerId(coord);
                    if (m.toInt() != 0 || ownerId != 0) {
                        finalCells.add(new RawCellState(coord.clone(), m.toInt(), ownerId));
                    }
                });
            }
        } else {
            // Full iteration (current behavior)
            cells = new ArrayList<>();
            int[] coord = new int[dims];
            Arrays.fill(coord, 0);
            final List<RawCellState> finalCells = cells;
            iterateOptimized(shape, 0, coord, () -> {
                var m = env.getMolecule(coord);
                int ownerId = env.getOwnerId(coord);
                if (m.toInt() != 0 || ownerId != 0) {
                    finalCells.add(new RawCellState(coord.clone(), m.toInt(), ownerId));
                }
            });
        }

        // Optimize organism processing - only process living organisms
        List<RawOrganismState> organisms = new ArrayList<>();
        for (Organism o : simulation.getOrganisms()) {
            if (o.isDead()) continue; // Skip dead organisms for performance
            
            // Use the optimized createRawOrganismState method with lazy loading and caching
            RawOrganismState rawState = o.createRawOrganismState();
            
            // Add the remaining fields that are not handled by createRawOrganismState
            organisms.add(new RawOrganismState(
                    rawState.id(), rawState.parentId(), rawState.birthTick(), rawState.programId(), rawState.initialPosition(),
                    rawState.ip(), rawState.dv(), rawState.dps(), rawState.activeDpIndex(), rawState.er(),
                    rawState.drs(), rawState.prs(), rawState.fprs(), rawState.lrs(),
                    rawState.dataStack(), rawState.locationStack(), rawState.callStack(),
                    o.isDead(), o.isInstructionFailed(), o.getFailureReason(),
                    o.shouldSkipIpAdvance(), o.getIpBeforeFetch(), o.getDvBeforeFetch()
            ));
        }

        return new RawTickState(simulation.getCurrentTick(), organisms, cells);
    }

    private static void iterateOptimized(int[] shape, int dim, int[] coord, Runnable visitor) {
        if (dim == shape.length) {
            visitor.run();
            return;
        }
        for (int i = 0; i < shape[dim]; i++) {
            coord[dim] = i;
            iterateOptimized(shape, dim + 1, coord, visitor);
        }
    }
}