package org.evochora.node.processes.http;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigValue;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import org.evochora.node.processes.AbstractProcess;
import org.evochora.node.spi.IController;
import org.evochora.node.spi.ServiceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A manageable process that runs a Javalin HTTP server.
 * This process dynamically configures its routes, controllers, and static file handlers
 * based on the provided configuration.
 */
public class HttpServerProcess extends AbstractProcess {
    private static final Logger LOG = LoggerFactory.getLogger(HttpServerProcess.class);

    private final List<RouteDefinition> routeDefinitions;
    private final String host;
    private final int port;
    private Javalin javalin;

    // Sealed interface for route definitions to ensure type safety
    private sealed interface RouteDefinition permits StaticRoute, ControllerRoute {
        String basePath();
    }

    private record StaticRoute(String basePath, String resourcePath) implements RouteDefinition {}

    private record ControllerRoute(String basePath, String className, Config controllerOptions) implements RouteDefinition {}

    /**
     * Initializes the HTTP server process.
     * Parses the 'routes' configuration to build a list of controllers and static file handlers.
     *
     * @param registry The service registry for dependency injection.
     * @param options  The configuration for this process, including network settings and routes.
     */
    public HttpServerProcess(final ServiceRegistry registry, final Config options) {
        super(registry, options);
        this.host = options.getString("network.host");
        this.port = options.getInt("network.port");
        this.routeDefinitions = new ArrayList<>();

        if (options.hasPath("routes")) {
            LOG.info("Parsing HTTP route configuration...");
            parseRoutes(this.routeDefinitions, "", options.getObject("routes"));
        } else {
            LOG.warn("No 'routes' configuration found for HttpServerProcess.");
        }
    }

    /**
     * Recursively parses the route configuration object to build a flat list of route definitions.
     * This method iterates through the config entry set to avoid path parsing issues with special keys like '$'.
     *
     * @param routeDefs The list to add definitions to.
     * @param basePath  The current URL path prefix being built.
     * @param config    The current configuration object to parse.
     */
    private void parseRoutes(final List<RouteDefinition> routeDefs, final String basePath, final ConfigObject config) {
        for (final Map.Entry<String, ConfigValue> entry : config.entrySet()) {
            final String key = entry.getKey();
            final ConfigValue value = entry.getValue();

            switch (key) {
                case "$controller":
                    if (value instanceof ConfigObject) {
                        final Config controllerConfig = ((ConfigObject) value).toConfig();
                        final String className = controllerConfig.getString("className");
                        final Config controllerOptions = controllerConfig.hasPath("options") ? controllerConfig.getConfig("options") : ConfigFactory.empty();
                        routeDefs.add(new ControllerRoute(basePath.isEmpty() ? "/" : basePath, className, controllerOptions));
                    }
                    break;
                case "$static":
                    final String resourcePath = (String) value.unwrapped();
                    routeDefs.add(new StaticRoute(basePath.isEmpty() ? "/" : basePath, resourcePath));
                    break;
                default:
                    if (value instanceof ConfigObject) {
                        // It's a nested path segment
                        final String nextPath = basePath.isEmpty() ? "/" + key : basePath + "/" + key;
                        parseRoutes(routeDefs, nextPath, (ConfigObject) value);
                    }
                    break;
            }
        }
    }


    @Override
    public void start() {
        LOG.info("Starting HTTP server on {}:{}", host, port);
        this.javalin = Javalin.create(config -> {
            config.showJavalinBanner = false;
            config.requestLogger.http((ctx, ms) -> {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Request: {} {} -> {}", ctx.method(), ctx.path(), ctx.status());
                }
            });

            // Configure static file handlers first
            for (final RouteDefinition def : routeDefinitions) {
                if (def instanceof final StaticRoute staticRoute) {
                    LOG.info("Serving static files from classpath:{} at URL path: {}", staticRoute.resourcePath(), staticRoute.basePath());
                    config.staticFiles.add(staticFiles -> {
                        staticFiles.hostedPath = staticRoute.basePath();
                        staticFiles.directory = staticRoute.resourcePath();
                        staticFiles.location = Location.CLASSPATH;
                    });
                }
            }
        });

        // Register controllers after creating the app instance
        for (final RouteDefinition def : routeDefinitions) {
            if (def instanceof final ControllerRoute controllerRoute) {
                try {
                    LOG.info("Registering controller {} at base path: {}", controllerRoute.className(), controllerRoute.basePath());
                    final Class<?> clazz = Class.forName(controllerRoute.className());
                    final Constructor<?> ctor = clazz.getConstructor(ServiceRegistry.class, Config.class);
                    final IController controller = (IController) ctor.newInstance(registry, controllerRoute.controllerOptions());
                    controller.registerRoutes(this.javalin, controllerRoute.basePath());
                } catch (final Exception e) {
                    LOG.error("Failed to instantiate or register controller {}", controllerRoute.className(), e);
                    throw new RuntimeException("Failed to initialize controller: " + controllerRoute.className(), e);
                }
            }
        }

        this.javalin.start(host, port);
    }

    @Override
    public void stop() {
        if (this.javalin != null) {
            LOG.info("Stopping HTTP server...");
            this.javalin.stop();
            LOG.info("HTTP server stopped.");
        }
    }
}