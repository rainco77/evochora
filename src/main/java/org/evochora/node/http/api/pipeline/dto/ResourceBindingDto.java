package org.evochora.node.http.api.pipeline.dto;

import org.evochora.datapipeline.api.resources.IMonitorable;
import org.evochora.datapipeline.api.resources.IResource;
import org.evochora.datapipeline.api.services.ResourceBinding;

import java.util.Collections;
import java.util.Map;

/**
 * A Data Transfer Object representing a resource binding for a service.
 * This is an immutable, serializable representation suitable for API responses.
 *
 * @param portName      The name of the service port the resource is bound to.
 * @param resourceName  The name of the bound resource.
 * @param usageType     The type of usage (e.g., "queue-in", "storage-readonly").
 * @param metrics       A map of metrics associated with the resource.
 */
public record ResourceBindingDto(
    String portName,
    String resourceName,
    String usageType,
    Map<String, Number> metrics
) {
    /**
     * Creates a {@link ResourceBindingDto} from a domain {@link ResourceBinding}.
     * It safely extracts metrics by checking if the resource is an instance of {@link IMonitorable}.
     *
     * @param binding The resource binding from the core domain.
     * @return A new DTO instance.
     */
    public static ResourceBindingDto from(final ResourceBinding binding) {
        final IResource resource = binding.resource();
        final Map<String, Number> resourceMetrics;

        // The base resource itself should be monitorable.
        if (resource instanceof IMonitorable) {
            resourceMetrics = ((IMonitorable) resource).getMetrics();
        } else {
            resourceMetrics = Collections.emptyMap();
        }

        return new ResourceBindingDto(
            binding.context().portName(),
            binding.context().resourceName(),
            binding.context().usageType(),
            resourceMetrics
        );
    }
}