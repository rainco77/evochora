package org.evochora.datapipeline.api.services;

/**
 * An immutable data object describing the status of a single connection between a service and a resource.
 *
 * @param resourceName      The name of the resource.
 * @param usageType         The usage type (e.g., "channel-in", "channel-out", "storage-readonly").
 * @param state             The current state of the binding (e.g., ACTIVE, WAITING).
 * @param messagesPerSecond The recent throughput of the binding, in messages per second.
 */
public record ResourceBindingStatus(
    String resourceName,
    String usageType,
    BindingState state,
    double messagesPerSecond
) {
}
