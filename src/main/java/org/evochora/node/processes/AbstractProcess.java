package org.evochora.node.processes;

import com.typesafe.config.Config;
import org.evochora.node.spi.IProcess;
import org.evochora.node.spi.ServiceRegistry;

/**
 * An abstract base class for {@link IProcess} implementations.
 * It provides a consistent constructor for injecting the {@link ServiceRegistry} and process-specific configuration,
 * ensuring that all concrete process implementations have access to core services and their own options.
 */
public abstract class AbstractProcess implements IProcess {

    protected final ServiceRegistry registry;
    protected final Config options;

    /**
     * Initializes the process with its dependencies.
     *
     * @param registry The central service registry for accessing shared services.
     * @param options  The configuration specific to this process instance, extracted from the main config file.
     */
    public AbstractProcess(final ServiceRegistry registry, final Config options) {
        this.registry = registry;
        this.options = options;
    }
}