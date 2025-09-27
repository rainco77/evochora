package org.evochora.node.http.api.pipeline.dto;

import org.evochora.datapipeline.api.resources.OperationalError;
import org.evochora.datapipeline.api.services.IService;
import org.evochora.datapipeline.api.services.ServiceStatus;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A Data Transfer Object representing the status of a single service.
 * This is an immutable, serializable representation suitable for API responses.
 *
 * @param name             The name of the service.
 * @param state            The current state of the service (e.g., RUNNING, STOPPED).
 * @param metrics          A map of metrics for the service.
 * @param errors           A list of errors reported by the service.
 * @param resourceBindings A list of resource bindings for the service.
 */
public record ServiceStatusDto(
    String name,
    IService.State state,
    Map<String, Number> metrics,
    List<String> errors,
    List<ResourceBindingDto> resourceBindings
) {
    /**
     * Creates a {@link ServiceStatusDto} from a domain {@link ServiceStatus}.
     *
     * @param name   The name of the service.
     * @param status The status object from the core domain.
     * @return A new DTO instance.
     */
    public static ServiceStatusDto from(final String name, final ServiceStatus status) {
        final List<ResourceBindingDto> bindings = status.resourceBindings().stream()
            .map(ResourceBindingDto::from)
            .collect(Collectors.toList());

        // Map OperationalError to a simple string for the DTO
        final List<String> errorStrings = status.errors().stream()
            .map(OperationalError::message)
            .collect(Collectors.toList());

        return new ServiceStatusDto(
            name,
            status.state(),
            status.metrics(),
            errorStrings,
            bindings
        );
    }
}