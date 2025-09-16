// Create the ChannelFactory class
package org.evochora.datapipeline.channel;

import org.evochora.datapipeline.config.SimulationConfiguration;
import org.evochora.datapipeline.channel.inmemory.InMemoryChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.lang.reflect.Constructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChannelFactory {
    private static final Logger log = LoggerFactory.getLogger(ChannelFactory.class);
    private final Map<String, Object> channels = new HashMap<>();
    private final SimulationConfiguration.PipelineConfig config;

    public ChannelFactory(SimulationConfiguration.PipelineConfig config) {
        this.config = config;
    }

    public <T> Optional<Object> getOrCreateChannel(String name) {
        if (channels.containsKey(name)) {
            return Optional.of(channels.get(name));
        }

        if (config == null || config.channels == null || !config.channels.containsKey(name)) {
            log.error("Channel configuration for '{}' not found.", name);
            return Optional.empty();
        }

        SimulationConfiguration.ChannelConfig channelConfig = config.channels.get(name);
        String className = channelConfig.className;
        if (className == null || className.isBlank()) {
            log.error("className is not configured for channel '{}'.", name);
            return Optional.empty();
        }

        try {
            log.debug("Creating channel '{}' with class '{}'", name, className);
            Class<?> channelClass = Class.forName(className);
            
            // Look for a constructor that takes a Map
            Constructor<?> constructor = channelClass.getConstructor(Map.class);
            Object channelInstance = constructor.newInstance(channelConfig.options);
            
            channels.put(name, channelInstance);
            return Optional.of(channelInstance);
        } catch (Exception e) {
            log.error("Failed to create channel instance for class '{}': {}", className, e.getMessage(), e);
            return Optional.empty();
        }
    }
}
