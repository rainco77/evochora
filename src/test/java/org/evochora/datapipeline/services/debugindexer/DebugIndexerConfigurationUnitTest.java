package org.evochora.datapipeline.services.debugindexer;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("unit")
class DebugIndexerConfigurationUnitTest {

    private Config createTestConfig(int batchSize, String dbPath) {
        Map<String, Object> options = new HashMap<>();
        options.put("batchSize", batchSize);
        options.put("debugDbPath", dbPath);
        return ConfigFactory.parseMap(options);
    }

    @Test
    @Tag("unit")
    void testValidConfiguration() {
        Config config = createTestConfig(100, "build/valid_config.sqlite");
        DebugIndexer indexer = new DebugIndexer(config);
        assertThat(indexer).isNotNull();
    }

    @Test
    @Tag("unit")
    void testMissingBatchSize() {
        Map<String, Object> options = new HashMap<>();
        options.put("debugDbPath", "build/missing_batch.sqlite");
        Config config = ConfigFactory.parseMap(options);

        assertThrows(com.typesafe.config.ConfigException.Missing.class, () -> {
            new DebugIndexer(config);
        });
    }

    @Test
    @Tag("unit")
    void testMissingDbPath() {
        Map<String, Object> options = new HashMap<>();
        options.put("batchSize", 100);
        Config config = ConfigFactory.parseMap(options);

        assertThrows(com.typesafe.config.ConfigException.Missing.class, () -> {
            new DebugIndexer(config);
        });
    }

    @Test
    @Tag("unit")
    void testEmptyConfig() {
        Config config = ConfigFactory.empty();
        assertThrows(com.typesafe.config.ConfigException.Missing.class, () -> {
            new DebugIndexer(config);
        });
    }

    @Test
    @Tag("unit")
    void testDifferentValidConfigs() {
        Config config1 = createTestConfig(1, "build/db1.sqlite");
        DebugIndexer indexer1 = new DebugIndexer(config1);
        assertThat(indexer1).isNotNull();

        Config config2 = createTestConfig(9999, "build/db2.sqlite");
        DebugIndexer indexer2 = new DebugIndexer(config2);
        assertThat(indexer2).isNotNull();

        Config config3 = createTestConfig(500, "/tmp/db3.sqlite");
        DebugIndexer indexer3 = new DebugIndexer(config3);
        assertThat(indexer3).isNotNull();
    }
}
