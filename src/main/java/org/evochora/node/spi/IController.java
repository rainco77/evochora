package org.evochora.node.spi;

import io.javalin.Javalin;

/**
 * A marker interface for all API controllers.
 * Controllers are responsible for defining and handling a set of related HTTP routes.
 */
public interface IController {

    /**
     * Registers the controller's routes with the Javalin application instance.
     *
     * @param app      The Javalin application to register routes with.
     * @param basePath The base path under which the routes should be registered.
     */
    void registerRoutes(Javalin app, String basePath);
}