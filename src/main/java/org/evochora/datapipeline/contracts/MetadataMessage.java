package org.evochora.datapipeline.contracts;

import org.evochora.runtime.model.EnvironmentProperties;

/**
 * A message carrying simulation metadata, such as environment properties.
 * This is typically one of the first messages sent in the pipeline to configure downstream services.
 *
 * @param environmentProperties The environment properties of the simulation.
 */
public record MetadataMessage(EnvironmentProperties environmentProperties) implements IQueueMessage {
}
