package org.evochora.node.config;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * Responsible for loading the application configuration from various sources.
 * The loader respects a specific precedence order to allow for flexible configuration.
 */
public final class ConfigLoader {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigLoader.class);
    private static final String CONFIG_FILE_NAME = "evochora.conf";

    private ConfigLoader() {
        // Private constructor to prevent instantiation
    }

    /**
     * Loads the application configuration, respecting the precedence order:
     * 1. Environment Variables
     * 2. CLI Arguments (as Java System Properties, e.g., -Dkey=value)
     * 3. Configuration File (evochora.conf in the working directory)
     * 4. Default values (from reference.conf on the classpath)
     *
     * @param args Command-line arguments (currently unused, reserved for future expansion).
     * @return A resolved {@link Config} object containing the merged configuration.
     */
    public static Config load(final String[] args) {
        // 1. Environment Variables (highest precedence).
        // The Typesafe library can map env vars like 'NODE_PROCESSES_HTTPSERVER_OPTIONS_NETWORK_PORT=8081'
        // to 'node.processes.httpServer.options.network.port'.
        final Config envConfig = ConfigFactory.systemEnvironment();

        // 2. CLI arguments passed as -Dkey=value system properties.
        final Config cliConfig = ConfigFactory.systemProperties();

        // 3. Configuration file from the filesystem.
        final File configFile = new File(CONFIG_FILE_NAME);
        final Config fileConfig;
        if (configFile.exists() && !configFile.isDirectory()) {
            LOG.info("Loading configuration from file: {}", configFile.getAbsolutePath());
            fileConfig = ConfigFactory.parseFile(configFile);
        } else {
            LOG.info("Configuration file '{}' not found or is a directory. Skipping file-based configuration.", configFile.getPath());
            fileConfig = ConfigFactory.empty();
        }

        // 4. Default values from reference.conf in the classpath (lowest precedence).
        final Config defaultConfig = ConfigFactory.parseResources("reference.conf");

        // Chain the configs together. The one provided first wins.
        final Config combinedConfig = envConfig
            .withFallback(cliConfig)
            .withFallback(fileConfig)
            .withFallback(defaultConfig);

        // Resolve all substitutions (e.g., ${?some_value}) within the configuration.
        return combinedConfig.resolve();
    }
}