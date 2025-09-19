package org.evochora.datapipeline.services.debugindexer;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.evochora.datapipeline.api.contracts.RawTickData;
import org.evochora.datapipeline.api.contracts.SimulationContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.*;

@Tag("unit")
class DebugIndexerStateUnitTest {

    private Config createTestConfig(String dbPath) {
        Map<String, Object> options = new HashMap<>();
        options.put("batchSize", 1);
        options.put("debugDbPath", dbPath);
        return ConfigFactory.parseMap(options);
    }

    @Test
    @Tag("unit")
    void testProcessTickDirectly() throws Exception {
        Config config = createTestConfig("dummy_path");
        DatabaseManager mockDbManager = Mockito.mock(DatabaseManager.class);
        DebugIndexer indexer = new DebugIndexer(config, mockDbManager);

        SimulationContext context = new SimulationContext();
        context.setSimulationRunId("test-run-direct");
        context.setEnvironment(new org.evochora.datapipeline.api.contracts.EnvironmentProperties(new int[]{10,10}, org.evochora.datapipeline.api.contracts.WorldTopology.TORUS));
        context.setArtifacts(new java.util.ArrayList<>());

        RawTickData tick1 = new RawTickData();
        tick1.setTickNumber(1);
        tick1.setOrganisms(new java.util.ArrayList<>());
        tick1.setCells(new java.util.ArrayList<>());

        // Call methods directly
        indexer.initializeFromContext(context);
        indexer.processTick(tick1);

        // Verify interactions
        verify(mockDbManager, times(1)).setupDebugDatabase();
        verify(mockDbManager, times(1)).writeSimulationMetadata(eq("worldShape"), any(byte[].class));
        verify(mockDbManager, times(1)).writeSimulationMetadata(eq("isToroidal"), any(byte[].class));
        verify(mockDbManager, times(1)).writePreparedTick(eq(1L), any(byte[].class));
    }
}
