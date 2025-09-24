package org.evochora.datapipeline.api.resources;

/**
 * Optional interface that resources can implement to provide context-aware injection.
 * When a resource implements this interface, the ServiceManager will call getInjectedObject()
 * instead of injecting the resource directly, allowing the resource to return an appropriate
 * wrapper or adapter based on the injection context.
 */
public interface IContextualResource {

    /**
     * Returns the object that should be injected into the service.
     * This allows resources to inspect the context (service name, port name, usage type)
     * and return an appropriate object, such as a metric-collecting wrapper.
     *
     * @param context The injection context containing service name, port name, usage type, etc.
     * @return The object to be injected into the service
     */
    Object getInjectedObject(ResourceContext context);
}
