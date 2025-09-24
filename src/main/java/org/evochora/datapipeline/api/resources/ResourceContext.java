package org.evochora.datapipeline.api.resources;

import org.evochora.datapipeline.core.ServiceManager;

/**
 * Provides context information for resource injection in the Universal DI framework.
 * This context allows resources to adapt their behavior based on how they are being used.
 *
 * @param serviceName The name of the service requesting the resource
 * @param portName The logical port name within the service  
 * @param usageType The optional usage type hint (e.g., "channel-in", "channel-out", "storage-readonly")
 * @param serviceManager Reference to the ServiceManager for accessing global components
 */
public record ResourceContext(
    String serviceName,
    String portName, 
    String usageType,
    ServiceManager serviceManager
) {

    /**
     * Creates a ResourceContext with a default usage type.
     */
    public static ResourceContext withDefaults(String serviceName, String portName, ServiceManager serviceManager) {
        return new ResourceContext(serviceName, portName, "default", serviceManager);
    }
}
