package org.evochora.datapipeline.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.evochora.datapipeline.IControllable;
import org.evochora.datapipeline.channel.IInputChannel;
import org.evochora.datapipeline.contracts.IQueueMessage;
import org.evochora.datapipeline.contracts.ProgramArtifactMessage;
import org.evochora.datapipeline.contracts.raw.RawTickState; // NEU
import org.evochora.runtime.model.EnvironmentProperties;
import org.evochora.datapipeline.config.SimulationConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;
import org.evochora.compiler.internal.LinearizedProgramArtifact;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.Optional;

/**
 * Consumes raw messages from a queue and persists them to a per-run SQLite database.
 * This service runs in its own thread, decoupling the simulation engine from disk I/O.
 * It handles raw data for simulation resumption and program artifacts.
 */
public final class PersistenceService implements IControllable, Runnable {

    private static final Logger log = LoggerFactory.getLogger(PersistenceService.class);

    private final IInputChannel<IQueueMessage> inputChannel;
    private final Thread thread;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private final AtomicBoolean autoPaused = new AtomicBoolean(false);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Object connectionLock = new Object(); // Synchronization for database operations
    private final EnvironmentProperties envProps;
    private final SimulationConfiguration.PersistenceServiceConfig config; // Consolidated configuration

    private Connection connection;
    private Path dbFilePath;
    private final String jdbcUrlOverride;
    private String jdbcUrlInUse;
    private volatile long lastPersistedTick = -1L;
    private long startTime = System.currentTimeMillis(); // Added for TPS calculation
    
    // Simple TPS calculation - no complex timer tracking needed

    private PreparedStatement tickInsertStatement;
    private int batchCount = 0; // Count of ticks in current batch
    private int actualBatchCount = 0; // Count of actual batches executed
    
    // Memory optimization: ThreadLocal collections to reduce allocations
    private ThreadLocal<ArrayList<IQueueMessage>> tempMessageList;

    /**
     * Creates a new PersistenceService with consolidated configuration.
     * 
     * @param inputChannel The message queue to consume from
     * @param envProps Environment properties for the simulation
     * @param config Consolidated persistence service configuration
     */
    public PersistenceService(IInputChannel<IQueueMessage> inputChannel,
                              EnvironmentProperties envProps,
                              SimulationConfiguration.PersistenceServiceConfig config) {
        this.inputChannel = inputChannel;
        this.envProps = envProps;
        this.config = config != null ? config : new SimulationConfiguration.PersistenceServiceConfig();
        this.jdbcUrlOverride = this.config.jdbcUrl; // Read from PersistenceServiceConfig
        
        // If an override URL is provided, consider it "in use" immediately.
        if (this.jdbcUrlOverride != null) {
            this.jdbcUrlInUse = this.jdbcUrlOverride;
        }
        
        // Initialize ThreadLocal collections after config is set
        this.tempMessageList = ThreadLocal.withInitial(() -> 
            this.config.memoryOptimization.enabled ? 
                new ArrayList<>(this.config.batchSize) : new ArrayList<>());
        
        this.thread = new Thread(this, "PersistenceService");
        this.thread.setDaemon(true);
    }


    @Override
    public void start() {
        if (running.compareAndSet(false, true)) {
            setupDatabase();
            thread.start();
        }
    }

        @Override
    public void pause() {
        // Manual pause always sets autoPaused to false
        // Auto-pause will set autoPaused to true before calling pause()
        autoPaused.set(false);
        
        // Set the pause flag - the service will pause after finishing current batch
        paused.set(true);
        // Manual pause
        log.debug("Manual pause flag set, service will pause after finishing current batch and close database");
    }

    @Override
    public void resume() { 
        paused.set(false); 
        autoPaused.set(false);
        // Don't start processing timer here - it will start when actual tick processing begins
    }

    @Override
    public void shutdown() {
        if (running.compareAndSet(true, false)) {
            long currentTick = getLastPersistedTick();
            double tps = calculateTPS();
            // Only log if called from the service thread
            if (Thread.currentThread().getName().equals("PersistenceService")) {
                log.info("PersistenceService: graceful termination tick:{} TPS:{}", currentTick, String.format("%.2f", tps));
            }
            
            // Immediately perform database cleanup and WAL checkpointing to prevent WAL/SHM file leaks
            // This ensures cleanup happens even if called from external threads (like CLI exit)
            try {
                log.debug("Performing immediate database cleanup during shutdown");
                // Execute any remaining batch operations before closing
                if (tickInsertStatement != null && batchCount % config.batchSize != 0) {
                    tickInsertStatement.executeBatch();
                    actualBatchCount++; // Increment actual batch count
                    log.debug("Executed final batch of {} ticks during shutdown", batchCount % config.batchSize);
                }
                closeQuietly();
                log.debug("Database cleanup completed during shutdown");
            } catch (Exception e) {
                log.warn("Error during shutdown database cleanup: {}", e.getMessage());
            }
            
            thread.interrupt();
            // Wait a bit for the thread to finish processing current messages
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
    
    public void forceShutdown() {
        if (running.get()) {
            running.set(false);
            
            // Even for force shutdown, try to perform database cleanup to prevent WAL/SHM file leaks
            try {
                log.debug("Performing database cleanup during force shutdown");
                // Execute any remaining batch operations before closing
                if (tickInsertStatement != null && batchCount % config.batchSize != 0) {
                    tickInsertStatement.executeBatch();
                    actualBatchCount++; // Increment actual batch count
                    log.debug("Executed final batch of {} ticks during force shutdown", batchCount % config.batchSize);
                }
                closeQuietly();
                log.debug("Database cleanup completed during force shutdown");
            } catch (Exception e) {
                log.warn("Error during force shutdown database cleanup: {}", e.getMessage());
            }
            
            thread.interrupt();
        }
    }

    @Override
    public boolean isRunning() { return running.get(); }

    @Override
    public boolean isPaused() { return paused.get(); }
    
    @Override
    public boolean isAutoPaused() { return autoPaused.get(); }

    public Path getDbFilePath() { return dbFilePath; }
    public long getLastPersistedTick() { return lastPersistedTick; }
    public long getCurrentTick() { return lastPersistedTick; }
    public String getJdbcUrl() { return jdbcUrlInUse; }
    public int getBatchCount() { return actualBatchCount; }
    public int getBatchSize() { return config.batchSize; }

    private double calculateTPS() {
        long currentTime = System.currentTimeMillis();
        long currentTick = getLastPersistedTick();
        
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

    public String getStatus() {
        if (!running.get()) {
            return "stopped";
        }
        
        String dbInfo = jdbcUrlOverride != null ? jdbcUrlOverride : 
                       (dbFilePath != null ? dbFilePath.toString() : "unknown");
        long currentTick = getLastPersistedTick();
        double tps = calculateTPS();
        
        if (paused.get()) {
            String status = autoPaused.get() ? "auto-paused" : "paused";
            return String.format("%-12s %-8d %-8.2f %s", status, currentTick, tps, dbInfo);
        }
        
        return String.format("%-12s %-8d %-8.2f %s", "started", currentTick, tps, dbInfo);
    }
    
    /**
     * Creates an optimized database connection with PRAGMA settings.
     * This method is used both for initial setup and for reconnecting after connection loss.
     * 
     * @return A configured database connection with performance optimizations
     * @throws Exception if connection creation fails
     */
    private Connection createOptimizedConnection() throws Exception {
        String jdbcUrl;
        
        if (jdbcUrlOverride != null) {
            jdbcUrl = jdbcUrlOverride;
        } else {
            // Fallback to file-based DB in "runs" directory
            Path runsDir = Paths.get(config.outputPath); // Use configured output path
                Files.createDirectories(runsDir);
                String ts = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
                dbFilePath = runsDir.resolve("sim_run_" + ts + "_raw.sqlite");
            jdbcUrl = "jdbc:sqlite:" + dbFilePath.toAbsolutePath();
        }
        
        this.jdbcUrlInUse = jdbcUrl;
        // Create connection
        Connection conn = DriverManager.getConnection(jdbcUrl);
        
        // Apply performance optimizations using hardcoded defaults
        try (Statement st = conn.createStatement()) {
            // WAL mode is unreliable for shared in-memory databases; disable it in that case.
            if (!jdbcUrl.contains("mode=memory")) {
                st.execute("PRAGMA journal_mode=WAL");
            }
            st.execute("PRAGMA synchronous=NORMAL");
            st.execute("PRAGMA temp_store=MEMORY");
            st.execute("PRAGMA cache_size=" + config.database.cacheSize); // These are still in config
            st.execute("PRAGMA mmap_size=" + config.database.mmapSize);
            st.execute("PRAGMA page_size=" + config.database.pageSize);
        }
        
        return conn;
    }

    private void setupDatabase() {
        try {
            connection = createOptimizedConnection();
            
            try (Statement st = connection.createStatement()) {
                // NEUES SCHEMA
                st.execute("CREATE TABLE IF NOT EXISTS program_artifacts (program_id TEXT PRIMARY KEY, artifact_json BLOB)");
                st.execute("CREATE TABLE IF NOT EXISTS raw_ticks (tick_number INTEGER PRIMARY KEY, tick_data_json BLOB)");
                st.execute("CREATE TABLE IF NOT EXISTS simulation_metadata (key TEXT PRIMARY KEY, value BLOB)");
                try (PreparedStatement ps = connection.prepareStatement("INSERT OR REPLACE INTO simulation_metadata (key, value) VALUES (?, ?)")) {
                    if (envProps != null) {
                        ps.setString(1, "worldShape");
                        ps.setBytes(2, objectMapper.writeValueAsBytes(envProps.getWorldShape()));
                        ps.executeUpdate();
                    }
                    ps.setString(1, "runMode");
                    ps.setString(2, "debug"); // Always run in debug mode
                    ps.executeUpdate();
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to setup database", e);
        }
    }

    private void closeQuietly() {
        synchronized (connectionLock) {
            try {
                // Finalize any remaining batch operations
                if (tickInsertStatement != null) {
                    if (batchCount % config.batchSize != 0) { // Use config.batchSize here
                        tickInsertStatement.executeBatch();
                        actualBatchCount++; // Increment actual batch count
                    }
                    tickInsertStatement.close();
                    tickInsertStatement = null;
                }
                
                // Perform WAL checkpoint to ensure all data is written to main database
                // This prevents WAL and SHM files from being left behind
                if (connection != null && !connection.isClosed()) {
                    try (Statement st = connection.createStatement()) {
                        // First, ensure all pending changes are written to WAL
                        st.execute("PRAGMA wal_checkpoint(FULL)");
                        log.debug("WAL checkpoint completed before closing");
                        
                        // Force a final checkpoint to ensure all data is flushed to main database
                        st.execute("PRAGMA wal_checkpoint(TRUNCATE)");
                        log.debug("Final WAL truncate checkpoint completed");
                        
                        // Additional checkpoint to ensure all data is committed
                        st.execute("PRAGMA wal_checkpoint(FULL)");
                        log.debug("Final WAL checkpoint completed");
                        
                        // Force a synchronous write to ensure all data is on disk
                        st.execute("PRAGMA synchronous=FULL");
                        st.execute("PRAGMA wal_checkpoint(FULL)");
                        log.debug("Synchronous WAL checkpoint completed");
                        
                    } catch (Exception e) {
                        // Skip WAL checkpoint if database is busy - this is normal with concurrent access
                        if (e.getMessage().contains("SQLITE_BUSY") || e.getMessage().contains("database is locked")) {
                            log.info("Skipping WAL checkpoint before closing due to concurrent access: {}", e.getMessage());
                        } else if (e.getMessage().contains("stmt pointer is closed") || e.getMessage().contains("database has been closed")) {
                            // Statement or database already closed is a real problem - data might be incomplete
                            log.error("Database or statement was already closed before WAL checkpoint - data may be incomplete! {}", e.getMessage());
                        } else {
                            log.warn("Error during WAL checkpoint before closing: {}", e.getMessage());
                        }
                    }
                }
                
                // Close the connection - this will properly release WAL and SHM files
                if (connection != null) {
                    connection.close();
                    connection = null;
                    log.debug("Database connection closed - WAL and SHM files should be released");
                }
            } catch (Exception e) {
                log.warn("Error during database cleanup: {}", e.getMessage());
            }
        }
    }

    @Override
    public void run() {
        try {
            while (running.get()) {
                if (paused.get()) {
                    // Simple pause logic without auto-resume
                    while (paused.get() && running.get()) {
                        Thread.sleep(100);
                    }
                }

                List<IQueueMessage> messages = tempMessageList.get();
                messages.clear();

                // Wait for the first message
                messages.add(inputChannel.take());

                // Drain any additional messages that are immediately available
                while (messages.size() < config.batchSize) {
                    Optional<IQueueMessage> msg = inputChannel.poll(10, TimeUnit.MILLISECONDS);
                    if (msg.isEmpty()) {
                        break;
                    }
                    messages.add(msg.get());
                }

                // Process the collected batch of messages
                if (!messages.isEmpty()) {
                    try {
                        for (IQueueMessage message : messages) {
                            if (message instanceof RawTickState rts) {
                                handleRawTick(rts);
                            } else if (message instanceof ProgramArtifactMessage pam) {
                                // Flush any pending tick batches before handling artifacts
                                if (tickInsertStatement != null && batchCount > 0) {
                                    tickInsertStatement.executeBatch();
                                    tickInsertStatement.clearBatch();
                                    batchCount = 0;
                                }
                                handleProgramArtifact(pam);
                            }
                        }

                        // Execute the final batch if it exists
                        if (tickInsertStatement != null && batchCount > 0) {
                            tickInsertStatement.executeBatch();
                            tickInsertStatement.clearBatch();
                            batchCount = 0;
                        }
                    } catch (Exception e) {
                        log.error("Failed to process message batch", e);
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.info("PersistenceService thread interrupted.");
        } finally {
            // Log graceful termination from the service thread
            if (Thread.currentThread().getName().equals("PersistenceService")) {
                long currentTick = getLastPersistedTick();
                double tps = calculateTPS();
                log.info("PersistenceService: graceful termination tick:{} TPS:{}", currentTick, String.format("%.2f", tps));
            }
        }
    }

    @Override
    public void flush() {
        synchronized (connectionLock) {
            try {
                // Execute any remaining batch operations
                if (tickInsertStatement != null && batchCount > 0) {
                    log.debug("Flushing remaining batch of {} ticks.", batchCount);
                    tickInsertStatement.executeBatch();
                    
                    // After executing, we must clear the batch and reset the counter
                    // to prevent duplicate writes on subsequent operations.
                    tickInsertStatement.clearBatch();
                    batchCount = 0;
                    
                    // In WAL mode, a checkpoint makes the changes visible to other connections.
                    if (connection != null && !connection.isClosed()) {
                        try (Statement st = connection.createStatement()) {
                            st.execute("PRAGMA wal_checkpoint(FULL)");
                            log.debug("WAL checkpoint completed during flush.");
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Error during flush operation: {}", e.getMessage(), e);
            }
        }
    }

    private void handleProgramArtifact(ProgramArtifactMessage pam) throws Exception {
        // Konvertierung zu linearisiertem Format für Jackson-Serialisierung
        // Wir brauchen die EnvironmentProperties für die Linearisierung
        if (envProps == null) {
            throw new IllegalStateException(
                "Cannot serialize ProgramArtifact: EnvironmentProperties are required for coordinate linearization. " +
                "Ensure EnvironmentProperties are provided when creating PersistenceService."
            );
        }
        
        try {
            LinearizedProgramArtifact linearized = pam.programArtifact().toLinearized(envProps);
            byte[] jsonBytes = objectMapper.writeValueAsBytes(linearized);
            
            try (PreparedStatement ps = connection.prepareStatement("INSERT OR REPLACE INTO program_artifacts(program_id, artifact_json) VALUES (?, ?)")) {
                ps.setString(1, pam.programId());
                ps.setBytes(2, jsonBytes);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            log.warn("Failed to serialize program artifact {}: {}", pam.programId(), e.getMessage());
            throw e;
        }
    }

    private void handleRawTick(RawTickState rts) throws Exception {
        try {
            // Ensure database connection is available
            if (connection == null || connection.isClosed()) {
                log.debug("Database connection not available, reopening...");
                connection = createOptimizedConnection();
                // Reset the prepared statement since we have a new connection
                tickInsertStatement = null;
            }
            
            // Use batch insert for better performance
            byte[] jsonBytes = objectMapper.writeValueAsBytes(rts);
            
            // Use a single prepared statement for all ticks
            if (tickInsertStatement == null) {
                tickInsertStatement = connection.prepareStatement(
                    "INSERT OR REPLACE INTO raw_ticks(tick_number, tick_data_json) VALUES (?, ?)");
            }
            
            tickInsertStatement.setLong(1, rts.tickNumber());
            tickInsertStatement.setBytes(2, jsonBytes);
            tickInsertStatement.addBatch();
            
            // Execute batch every 1000 ticks for optimal performance
            if (++batchCount % config.batchSize == 0) {
                int[] result = tickInsertStatement.executeBatch();
                actualBatchCount++; // Increment actual batch count
                log.debug("Executed batch of {} ticks, result: {}", config.batchSize, java.util.Arrays.toString(result));
                // Only clear batch if statement still exists (avoid race condition during shutdown)
                if (tickInsertStatement != null) {
                    tickInsertStatement.clearBatch();
                }
            }
            
            lastPersistedTick = rts.tickNumber();
            
            // Tick persisted
        } catch (Exception e) {
            log.warn("Failed to serialize raw tick {}: {}", rts.tickNumber(), e.getMessage());
            throw e;
        }
    }
}
