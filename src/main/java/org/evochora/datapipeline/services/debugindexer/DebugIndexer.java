package org.evochora.datapipeline.services.debugindexer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typesafe.config.Config;
import org.evochora.compiler.api.ProgramArtifact;
import org.evochora.datapipeline.api.contracts.RawTickData;
import org.evochora.datapipeline.api.contracts.SimulationContext;
import org.evochora.datapipeline.api.channels.IInputChannel;
import org.evochora.datapipeline.services.AbstractService;
import org.evochora.server.contracts.debug.PreparedTickState;
import org.evochora.server.contracts.raw.RawOrganismState;
import org.evochora.server.contracts.raw.RawTickState;
import org.evochora.runtime.model.EnvironmentProperties;
import org.evochora.datapipeline.services.debugindexer.ArtifactValidator.ArtifactValidity;

import java.util.HashMap;
import java.util.Map;

public class DebugIndexer extends AbstractService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private String debugDbPath;
    private final int batchSize;
    private final Config config;
    private EnvironmentProperties envProps;
    private Map<String, ProgramArtifact> artifacts = new HashMap<>();
    private final DatabaseManager databaseManager;
    private final SourceViewBuilder sourceViewBuilder;
    private final InstructionBuilder instructionBuilder;
    private final InternalStateBuilder internalStateBuilder;
    private final TickProcessor tickProcessor;

    private IInputChannel<SimulationContext> contextChannel;
    private IInputChannel<RawTickData> tickChannel;

    public DebugIndexer(Config options) {
        this(options, new DatabaseManager(options.getString("debugDbPath"), options.getInt("batchSize"), null));
    }

    // Constructor for testing with a mock DatabaseManager
    DebugIndexer(Config options, DatabaseManager databaseManager) {
        this.config = options;
        this.debugDbPath = options.getString("debugDbPath");
        this.batchSize = options.getInt("batchSize");
        this.databaseManager = databaseManager;
        this.sourceViewBuilder = new SourceViewBuilder();
        this.instructionBuilder = new InstructionBuilder();
        this.internalStateBuilder = new InternalStateBuilder();
        this.tickProcessor = new TickProcessor(objectMapper, new ArtifactValidator(), sourceViewBuilder, instructionBuilder, internalStateBuilder, null); // Config for memory optimization can be added
    }

    @Override
    public void addInputChannel(String name, IInputChannel<?> channel) {
        super.addInputChannel(name, channel);
        if (name.equals("simulation-context")) {
            this.contextChannel = (IInputChannel<SimulationContext>) channel;
        } else if (name.equals("raw-tick-stream")) {
            this.tickChannel = (IInputChannel<RawTickData>) channel;
        }
    }

    @Override
    protected void run() {
        try {
            log.info("DebugIndexer service thread started.");
            log.info("Waiting for simulation context...");
            SimulationContext context = contextChannel.read();
            log.info("Received simulation context for run ID: {}", context.getSimulationRunId());

            initializeFromContext(context);
            log.info("Initialization complete.");

            log.info("Starting tick processing loop...");
            while (currentState.get() == org.evochora.datapipeline.api.services.State.RUNNING) {
                if (paused) {
                    log.info("Service is paused. Waiting...");
                    synchronized (pauseLock) {
                        while (paused) {
                            pauseLock.wait();
                        }
                    }
                    log.info("Service resumed.");
                }
                log.debug("Waiting for raw tick data...");
                RawTickData rawTickData = tickChannel.read();
                if (rawTickData != null) {
                    log.debug("Processing tick {}.", rawTickData.getTickNumber());
                    processTick(rawTickData);
                } else {
                    log.debug("Read null from tick channel.");
                }
            }
            log.info("Tick processing loop finished. Current state: {}", currentState.get());
        } catch (InterruptedException e) {
            log.info("DebugIndexer service interrupted. Shutting down.");
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("A critical error occurred in the DebugIndexer service.", e);
            currentState.set(org.evochora.datapipeline.api.services.State.STOPPED);
        } finally {
            log.info("DebugIndexer service thread finished.");
        }
    }

    protected void initializeFromContext(SimulationContext context) {
        this.envProps = new EnvironmentProperties(context.getEnvironment().getWorldShape(), context.getEnvironment().getTopology() == org.evochora.datapipeline.api.contracts.WorldTopology.TORUS);
        for (org.evochora.datapipeline.api.contracts.ProgramArtifact apiArtifact : context.getArtifacts()) {
            artifacts.put(apiArtifact.getProgramId(), DataContractConverter.convertProgramArtifact(apiArtifact));
        }

        try {
            databaseManager.setupDebugDatabase();
            writeProgramArtifacts();
            writeSimulationMetadata();
        } catch (Exception e) {
            log.error("Failed to initialize debug database", e);
        }
    }

    protected void processTick(RawTickData rawTickData) {
        try {
            log.debug("Converting RawTickData to RawTickState for tick {}", rawTickData.getTickNumber());
            RawTickState rawTickState = DataContractConverter.convertToRawTickState(rawTickData);
            log.debug("Transforming RawTickState to PreparedTickState for tick {}", rawTickData.getTickNumber());
            PreparedTickState preparedTick = tickProcessor.transformRawToPrepared(rawTickState, artifacts, envProps);
            if (preparedTick == null) {
                log.error("transformRawToPrepared returned null for tick {}", rawTickData.getTickNumber());
                return;
            }
            log.debug("Writing prepared tick {} to database", preparedTick.tickNumber());
            writePreparedTick(preparedTick);
        } catch (Exception e) {
            log.error("Failed to process tick {}", rawTickData.getTickNumber(), e);
        }
    }

    private void writePreparedTick(PreparedTickState preparedTick) {
        try {
            byte[] jsonBytes = objectMapper.writeValueAsBytes(preparedTick);
            databaseManager.writePreparedTick(preparedTick.tickNumber(), jsonBytes);
        } catch (Exception e) {
            log.error("Failed to write prepared tick {} to database", preparedTick.tickNumber(), e);
        }
    }

    private void writeProgramArtifacts() {
        if (this.artifacts.isEmpty()) {
            return;
        }
        try {
            for (Map.Entry<String, ProgramArtifact> entry : this.artifacts.entrySet()) {
                byte[] jsonBytes = objectMapper.writeValueAsBytes(entry.getValue());
                databaseManager.writeProgramArtifact(entry.getKey(), jsonBytes);
            }
        } catch (Exception e) {
            log.error("Failed to write program artifacts", e);
        }
    }

    private void writeSimulationMetadata() {
        try {
            byte[] worldShapeBytes = objectMapper.writeValueAsBytes(envProps.getWorldShape());
            databaseManager.writeSimulationMetadata("worldShape", worldShapeBytes);
            byte[] isToroidalBytes = objectMapper.writeValueAsBytes(envProps.isToroidal());
            databaseManager.writeSimulationMetadata("isToroidal", isToroidalBytes);
        } catch (Exception e) {
            log.error("Failed to write simulation metadata", e);
        }
    }

    public PreparedTickState.SourceView buildSourceView(RawOrganismState organism, ProgramArtifact artifact, ArtifactValidator.ArtifactValidity validity) {
        return sourceViewBuilder.buildSourceView(organism, artifact, validity);
    }
}