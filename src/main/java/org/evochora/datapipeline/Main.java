package org.evochora.datapipeline;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.evochora.datapipeline.core.CommandLineInterface;
import picocli.CommandLine;

import java.io.File;

/**
 * The main entry point for the Evochora Data Pipeline application.
 * <p>
 * This class serves as the main entry point for the executable JAR. Its sole
 * responsibility is to pre-load the configuration to set up logging, and then
 * delegate control to the {@link CommandLineInterface}, which handles all
 * command-line parsing and application logic.
 * </p>
 */
public final class Main {

    /**
     * Private constructor to prevent instantiation.
     */
    private Main() {
        // This class should not be instantiated.
    }

    /**
     * The main method that launches the application.
     *
     * @param args The command-line arguments passed to the application.
     */
    public static void main(final String[] args) {
        // Pre-parse args to find config file for logging setup
        String configPath = null;
        for (int i = 0; i < args.length; i++) {
            if (("--config".equals(args[i]) || "-c".equals(args[i])) && i + 1 < args.length) {
                configPath = args[i + 1];
                break;
            }
        }

        Config config = loadConfiguration(configPath);
        setupLogging(config);

        // Now, properly execute the CLI with picocli
        int exitCode = new CommandLine(new CommandLineInterface(config)).execute(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    private static Config loadConfiguration(String configPath) {
        Config defaultConfig = ConfigFactory.parseResources("reference.conf");
        Config fileConfig = configPath != null
                ? ConfigFactory.parseFile(new File(configPath))
                : ConfigFactory.empty();

        return fileConfig.withFallback(defaultConfig).resolve();
    }

    private static void setupLogging(Config config) {
        String logFormat = "JSON"; // Default
        if (config.hasPath("logging.format")) {
            logFormat = config.getString("logging.format");
        }

        String logbackFile = "logback-json.xml";
        if ("PLAIN".equalsIgnoreCase(logFormat)) {
            logbackFile = "logback-plain.xml";
        }

        System.setProperty("logback.configurationFile", logbackFile);
    }
}
