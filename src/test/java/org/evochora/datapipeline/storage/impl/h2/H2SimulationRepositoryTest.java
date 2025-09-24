package org.evochora.datapipeline.resources.storage;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.evochora.datapipeline.api.resources.storage.indexer.model.EnvironmentState;
import org.evochora.datapipeline.api.resources.storage.indexer.model.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for H2SimulationRepository.
 * Updated for Universal DI resource structure.
 * 
 * <p>These tests use an in-memory H2 database and do not depend on evochora.conf.
 * All tests are tagged as "unit" since they run fast and use no I/O resources.</p>
 */
@Tag("unit")
class H2SimulationRepositoryTest {
// Test file content will be updated in the previous patch
    private H2SimulationRepository repository;
    private Config testConfig;

    @BeforeEach
    void setUp() {
        // Set logger levels to WARN for services - only WARN and ERROR should be shown
        System.setProperty("org.evochora.datapipeline.resources.storage.H2SimulationRepository", "WARN");

        // Create test configuration with unique in-memory H2 database for each test
        String uniqueDbName = "testdb_" + System.nanoTime() + "_" + java.lang.System.identityHashCode(new Object());
        testConfig = ConfigFactory.parseString(String.format("""
            jdbcUrl: "jdbc:h2:mem:%s;DB_CLOSE_DELAY=0"
            username: "sa"
            password: ""
            """, uniqueDbName));

        repository = new H2SimulationRepository(testConfig);
    }
package org.evochora.datapipeline.resources.storage;
package org.evochora.datapipeline.resources.storage;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.evochora.datapipeline.api.resources.storage.indexer.model.EnvironmentState;
import org.evochora.datapipeline.api.resources.storage.indexer.model.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for H2SimulationRepository.
 * Updated for Universal DI pattern.
 * 
 * <p>These tests use an in-memory H2 database and do not depend on evochora.conf.
 * All tests are tagged as "unit" since they run fast and use no I/O resources.</p>
 */
@Tag("unit")
class H2SimulationRepositoryTest {

    private H2SimulationRepository repository;
    private Config testConfig;

    @BeforeEach
    void setUp() {
        // Set logger levels to WARN for services - only WARN and ERROR should be shown
        System.setProperty("org.evochora.datapipeline.resources.storage.H2SimulationRepository", "WARN");

        // Create test configuration with unique in-memory H2 database for each test
        String uniqueDbName = "testdb_" + System.nanoTime() + "_" + System.identityHashCode(new Object());
        testConfig = ConfigFactory.parseString(String.format("""
            jdbcUrl: "jdbc:h2:mem:%s;DB_CLOSE_DELAY=0"
            username: "sa"
            password: ""
            """, uniqueDbName));

        repository = new H2SimulationRepository(testConfig);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (repository != null) {
            repository.close();
        }
    }

    @Test
    void testRepositoryInitialization() {
        assertNotNull(repository);
        // Repository should be initialized without throwing exceptions
    }

    @Test
    void testWriteEnvironmentState() throws Exception {
        // Create test data
        Position pos1 = new Position(1, 2);
        Position pos2 = new Position(3, 4);

        Map<Position, List<Object>> molecules = new HashMap<>();
        molecules.put(pos1, List.of("molecule1", "molecule2"));
        molecules.put(pos2, List.of("molecule3"));

        Map<Position, Object> organisms = new HashMap<>();
        organisms.put(pos1, "organism1");

        EnvironmentState state = EnvironmentState.withCurrentTimestamp(42L, molecules, organisms);

        // Write state
        assertDoesNotThrow(() -> repository.writeEnvironmentState(state));

        // Flush to ensure data is persisted
        assertDoesNotThrow(() -> repository.flush());
    }

    @Test
    void testMultipleWrites() throws Exception {
        // Write multiple states
        for (long tick = 1; tick <= 5; tick++) {
            Map<Position, List<Object>> molecules = new HashMap<>();
            molecules.put(new Position((int)tick, (int)tick), List.of("molecule" + tick));

            Map<Position, Object> organisms = new HashMap<>();
            organisms.put(new Position((int)tick + 10, (int)tick + 10), "organism" + tick);

            EnvironmentState state = EnvironmentState.withCurrentTimestamp(tick, molecules, organisms);
            repository.writeEnvironmentState(state);
        }

        repository.flush();

        // Verify data was written by checking the database directly
        String jdbcUrl = testConfig.getString("jdbcUrl");
        try (Connection conn = DriverManager.getConnection(jdbcUrl, "sa", "")) {
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM environment_states");
            assertTrue(rs.next());
            assertEquals(5, rs.getInt(1));
        }
    }

    @Test
    void testFlushAndClose() throws Exception {
        // Write some data
        Map<Position, List<Object>> molecules = new HashMap<>();
        Map<Position, Object> organisms = new HashMap<>();
        EnvironmentState state = EnvironmentState.withCurrentTimestamp(1L, molecules, organisms);

        repository.writeEnvironmentState(state);

        // Test flush
        assertDoesNotThrow(() -> repository.flush());

        // Test close
        assertDoesNotThrow(() -> repository.close());
    }

    @Test
    void testConfigurationHandling() {
        // Test with minimal config
        Config minimalConfig = ConfigFactory.parseString("""
            jdbcUrl: "jdbc:h2:mem:minimal;DB_CLOSE_DELAY=0"
            """);

        assertDoesNotThrow(() -> {
            H2SimulationRepository minimalRepo = new H2SimulationRepository(minimalConfig);
            minimalRepo.close();
        });

        // Test with full config
        Config fullConfig = ConfigFactory.parseString("""
            jdbcUrl: "jdbc:h2:mem:full;DB_CLOSE_DELAY=0"
            username: "testuser"
            password: "testpass"
            """);

        assertDoesNotThrow(() -> {
            H2SimulationRepository fullRepo = new H2SimulationRepository(fullConfig);
            fullRepo.close();
        });
    }
}
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.evochora.datapipeline.api.resources.storage.indexer.model.EnvironmentState;
import org.evochora.datapipeline.api.resources.storage.indexer.model.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;