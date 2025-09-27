package org.evochora.node.http;

import com.typesafe.config.Config;
import org.evochora.node.spi.IController;
import org.evochora.node.spi.ServiceRegistry;

/**
 * An abstract base class for {@link IController} implementations.
 * It provides a consistent constructor for injecting the {@link ServiceRegistry} and controller-specific configuration,
 * ensuring that all concrete controller implementations have access to core services and their own options.
 */
public abstract class AbstractController implements IController {

    protected final ServiceRegistry registry;
    protected final Config options;

    /**
     * Initializes the controller with its dependencies.
     *
     * @param registry The central service registry for accessing shared services.
     * @param options  The configuration specific to this controller instance.
     */
    public AbstractController(final ServiceRegistry registry, final Config options) {
        this.registry = registry;
        this.options = options;
    }
}