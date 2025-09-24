package org.evochora.datapipeline.resources.storage;

import com.typesafe.config.Config;
import org.evochora.datapipeline.api.resources.IContextualResource;
import org.evochora.datapipeline.api.resources.ResourceContext;
import org.evochora.datapipeline.api.resources.storage.IEnvironmentStateWriter;
import org.evochora.datapipeline.api.resources.storage.indexer.model.EnvironmentState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Properties;

/**
 * H2 database-based simulation repository that stores environment states.
 * Implements Universal DI pattern as a contextual resource.
 */
public class H2SimulationRepository implements IEnvironmentStateWriter, IContextualResource {

    private static final Logger log = LoggerFactory.getLogger(H2SimulationRepository.class);

    private final String jdbcUrl;
    private final Properties connectionProps;
    private Connection connection;
    private PreparedStatement insertStatement;

    public H2SimulationRepository(Config config) {
        this.jdbcUrl = config.hasPath("jdbcUrl") 
            ? config.getString("jdbcUrl") 
            : "jdbc:h2:mem:simulation;DB_CLOSE_DELAY=-1";

        this.connectionProps = new Properties();
        if (config.hasPath("username")) {
            connectionProps.setProperty("user", config.getString("username"));
        }
        if (config.hasPath("password")) {
            connectionProps.setProperty("password", config.getString("password"));
        }

        initializeDatabase();
    }

    private void initializeDatabase() {
        try {
            connection = DriverManager.getConnection(jdbcUrl, connectionProps);

            // Create tables
            String createTableSql = """
                CREATE TABLE IF NOT EXISTS environment_states (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    tick BIGINT NOT NULL,
                    state_data CLOB NOT NULL,
                    timestamp BIGINT NOT NULL
                )
                """;

            connection.createStatement().execute(createTableSql);

            // Prepare insert statement
            String insertSql = "INSERT INTO environment_states (tick, state_data, timestamp) VALUES (?, ?, ?)";
            insertStatement = connection.prepareStatement(insertSql);

            log.debug("H2SimulationRepository initialized with URL: {}", jdbcUrl);

        } catch (SQLException e) {
            log.error("Failed to initialize H2 database", e);
            throw new RuntimeException("Database initialization failed", e);
        }
    }

    @Override
    public void writeEnvironmentState(EnvironmentState environmentState) throws Exception {
        if (insertStatement == null) {
            throw new IllegalStateException("Repository not properly initialized");
        }

        try {
            insertStatement.setLong(1, environmentState.tick());
            insertStatement.setString(2, serializeState(environmentState));
            insertStatement.setLong(3, environmentState.timestamp());

            insertStatement.executeUpdate();

        } catch (SQLException e) {
            log.error("Failed to write environment state for tick {}", environmentState.tick(), e);
            throw e;
        }
    }

    private String serializeState(EnvironmentState state) {
        // Simple JSON-like serialization for demo purposes
        // In production, you'd use a proper serialization library
        return String.format("{\"tick\":%d,\"molecules\":%d,\"organisms\":%d,\"timestamp\":%d}",
            state.tick(),
            state.molecules().size(),
            state.organisms().size(),
            state.timestamp());
    }

    @Override
    public void flush() throws Exception {
        if (connection != null) {
            connection.commit();
        }
    }

    @Override
    public void close() throws Exception {
        if (insertStatement != null) {
            insertStatement.close();
        }
        if (connection != null) {
            connection.close();
        }
        log.debug("H2SimulationRepository closed");
    }

    @Override
    public Object getInjectedObject(ResourceContext context) {
        // For storage resources, we can return different adapters based on usage type
        String usageType = context.usageType();

        switch (usageType != null ? usageType.toLowerCase() : "default") {
            case "storage-readonly":
                // Could return a read-only wrapper
                return new ReadOnlyStorageAdapter(this);
            case "storage-readwrite":
            case "storage":
            case "default":
                // Return this instance for full read-write access
                return this;
            default:
                log.warn("Unknown usage type '{}' for storage resource, returning default", usageType);
                return this;
        }
    }

    /**
     * Read-only adapter for storage resources.
     */
    public static class ReadOnlyStorageAdapter {
        private final H2SimulationRepository repository;

        public ReadOnlyStorageAdapter(H2SimulationRepository repository) {
            this.repository = repository;
        }

        // Could provide read-only methods here
        public Connection getReadOnlyConnection() throws SQLException {
            // Return a read-only connection
            Connection conn = DriverManager.getConnection(repository.jdbcUrl, repository.connectionProps);
            conn.setReadOnly(true);
            return conn;
        }
    }
}
