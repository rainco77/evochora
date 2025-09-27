package org.evochora.node;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.evochora.datapipeline.ServiceManager;
import org.evochora.node.config.ConfigLoader;
import org.evochora.node.spi.IProcess;
import org.evochora.node.spi.ServiceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * The main entry point for the Evochora application node.
 * This class is responsible for initializing the configuration, managing the lifecycle of background processes,
 * and providing a graceful shutdown mechanism.
 */
public final class Node {

    private static final Logger LOG = LoggerFactory.getLogger(Node.class);

    private final ServiceRegistry serviceRegistry = new ServiceRegistry();
    private final List<IProcess> managedProcesses = new ArrayList<>();
    private final ExecutorService processExecutor = Executors.newCachedThreadPool();
    private final Config config;

    /**
     * Constructs a new Node, loading the application configuration.
     *
     * @param args Command-line arguments.
     */
    public Node(final String[] args) {
        this.config = ConfigLoader.load(args);
    }

    /**
     * The main method that starts the application.
     *
     * @param args Command-line arguments.
     */
    public static void main(final String[] args) {
        try {
            final Node node = new Node(args);
            // Register a shutdown hook to ensure graceful termination
            Runtime.getRuntime().addShutdownHook(new Thread(node::stop, "shutdown-hook"));
            node.start();
        } catch (final Exception e) {
            LOG.error("Fatal error during node startup. The application will exit.", e);
            System.exit(1);
        }
    }

    /**
     * Starts the node by initializing services and launching background processes.
     * Made package-private for testing.
     */
    void start() {
        LOG.info("Starting Evochora Node...");

        // 1. Initialize and register core services
        initializeCoreServices();

        // 2. Load and start all configured background processes
        startManagedProcesses();

        LOG.info("Evochora Node started successfully. The application is now running.");
    }

    /**
     * Initializes core services like the ServiceManager and registers them with the ServiceRegistry.
     */
    private void initializeCoreServices() {
        LOG.info("Initializing core services...");
        if (config.hasPath("pipeline")) {
            // The ServiceManager expects the root config object to find the 'pipeline' block within it.
            final ServiceManager serviceManager = new ServiceManager(config);
            serviceRegistry.register(ServiceManager.class, serviceManager);
            LOG.info("ServiceManager initialized and registered.");
        } else {
            LOG.warn("No 'pipeline' configuration found. ServiceManager will not be available.");
        }
    }

    /**
     * Parses the 'node.processes' configuration, instantiates, and starts each process.
     */
    private void startManagedProcesses() {
        if (!config.hasPath("node.processes")) {
            LOG.warn("No processes configured under 'node.processes'. The node will run without background processes.");
            return;
        }

        LOG.info("Loading and starting configured processes...");
        final Config processesConfig = config.getConfig("node.processes");
        for (final Map.Entry<String, Object> entry : processesConfig.root().unwrapped().entrySet()) {
            final String processName = entry.getKey();
            final Config processConfig = processesConfig.getConfig(processName);
            startProcess(processName, processConfig);
        }
    }

    /**
     * Instantiates and starts a single process using reflection.
     *
     * @param processName   The logical name of the process from the configuration.
     * @param processConfig The configuration for this specific process.
     */
    private void startProcess(final String processName, final Config processConfig) {
        try {
            final String className = processConfig.getString("className");
            LOG.info("Starting process '{}' with class {}", processName, className);

            final Class<?> clazz = Class.forName(className);
            final Constructor<?> ctor = clazz.getConstructor(ServiceRegistry.class, Config.class);
            final Config options = processConfig.hasPath("options") ? processConfig.getConfig("options") : ConfigFactory.empty();

            final IProcess process = (IProcess) ctor.newInstance(serviceRegistry, options);
            managedProcesses.add(process);

            // Start the process in a separate thread from the pool
            processExecutor.submit(() -> {
                Thread.currentThread().setName("process-" + processName);
                try {
                    process.start();
                } catch (final Exception e) {
                    LOG.error("Process '{}' terminated with an unhandled exception.", processName, e);
                }
            });

            LOG.info("Process '{}' has been launched.", processName);
        } catch (final Exception e) {
            LOG.error("Failed to start process '{}'. The node will terminate.", processName, e);
            throw new RuntimeException("Failed to initialize process: " + processName, e);
        }
    }

    /**
     * Gracefully stops all managed processes and shuts down the node.
     * This method is typically called by the shutdown hook.
     * Made package-private for testing.
     */
    void stop() {
        LOG.info("Shutting down Evochora Node...");

        // Stop processes in the reverse order of their start
        for (int i = managedProcesses.size() - 1; i >= 0; i--) {
            final IProcess process = managedProcesses.get(i);
            try {
                LOG.info("Stopping process {}...", process.getClass().getSimpleName());
                process.stop();
                LOG.info("Process {} stopped.", process.getClass().getSimpleName());
            } catch (final Exception e) {
                LOG.error("Error while stopping process {}", process.getClass().getSimpleName(), e);
            }
        }

        // Shut down the main process executor
        processExecutor.shutdown();
        try {
            if (!processExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                LOG.warn("Process executor did not terminate gracefully after 5 seconds. Forcing shutdown.");
                processExecutor.shutdownNow();
            }
        } catch (final InterruptedException e) {
            LOG.warn("Interrupted while waiting for process executor to terminate.");
            processExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        LOG.info("Evochora Node shutdown complete.");
    }
}