package org.evochora.datapipeline;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.evochora.datapipeline.api.services.IService;
import org.evochora.datapipeline.resources.queues.InMemoryBlockingQueue;
import org.evochora.junit.extensions.logging.AllowLog;
import org.evochora.junit.extensions.logging.AllowLogs;
import org.evochora.junit.extensions.logging.LogLevel;
import org.evochora.junit.extensions.logging.LogWatchExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;

@Tag("integration")
@ExtendWith(LogWatchExtension.class)
public class ServiceManagerIntegrationTest {

    private ServiceManager serviceManager;
    private Config config;

    @BeforeEach
    void setUp() {
        config = ConfigFactory.parseString("""
            resources {
              testQueue {
                class = "org.evochora.datapipeline.resources.queues.InMemoryBlockingQueue"
                options {
                  capacity = 10
                }
              }
            }
            services {
              producer {
                class = "org.evochora.datapipeline.services.DummyProducerService"
                options {
                  maxMessages = 10
                  intervalMs = 10
                }
                resources {
                  output = ["resource://testQueue"]
                }
              }
              consumer {
                class = "org.evochora.datapipeline.services.DummyConsumerService"
                options {
                  maxMessages = 10
                }
                resources {
                  input = ["resource://testQueue"]
                }
              }
            }
        """);
        serviceManager = new ServiceManager(config);
    }

    @AfterEach
    void tearDown() {
        if (serviceManager != null) {
            serviceManager.stopAll();
        }
    }

    @Test
    @AllowLogs({
        @AllowLog(level = LogLevel.WARN, loggerPattern = ".*DummyProducerService", messagePattern = ".* is not running or paused. Stop command ignored.*"),
        @AllowLog(level = LogLevel.WARN, loggerPattern = ".*DummyConsumerService", messagePattern = ".* is not running or paused. Stop command ignored.*")
    })
    void startAll_shouldStartAllServices() {
        serviceManager.startAll();

        await().atMost(Duration.ofSeconds(5)).until(() -> {
            boolean allRunning = serviceManager.getServices().values().stream()
                .allMatch(s -> s.getCurrentState() == IService.State.RUNNING);
            return allRunning;
        });
    }

    @Test
    @AllowLogs({
        @AllowLog(level = LogLevel.WARN, loggerPattern = ".*DummyProducerService", messagePattern = ".* is not running or paused. Stop command ignored.*"),
        @AllowLog(level = LogLevel.WARN, loggerPattern = ".*DummyConsumerService", messagePattern = ".* is not running or paused. Stop command ignored.*")
    })
    void stopAll_shouldStopAllServices() {
        serviceManager.startAll();
        // Give services a moment to start before stopping them.
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        serviceManager.stopAll();

        await().atMost(Duration.ofSeconds(5)).until(() ->
            serviceManager.getServices().values().stream()
                .allMatch(s -> s.getCurrentState() == IService.State.STOPPED)
        );
    }

    @Test
    @AllowLogs({
        @AllowLog(level = LogLevel.WARN, loggerPattern = ".*DummyProducerService", messagePattern = ".* is not running or paused. Stop command ignored.*"),
        @AllowLog(level = LogLevel.WARN, loggerPattern = ".*DummyConsumerService", messagePattern = ".* is not running or paused. Stop command ignored.*")
    })
    void endToEnd_producerShouldSendAndConsumerShouldReceive() {
        // Start all services
        serviceManager.startAll();

        // Wait for both services to run to completion and stop
        await().atMost(Duration.ofSeconds(5)).until(() ->
            serviceManager.getServices().values().stream()
                .allMatch(s -> s.getCurrentState() == IService.State.STOPPED)
        );

        // Verify the final state
        IService producer = serviceManager.getService("producer");
        IService consumer = serviceManager.getService("consumer");

        assertEquals(IService.State.STOPPED, producer.getCurrentState(), "Producer should have stopped.");
        assertEquals(IService.State.STOPPED, consumer.getCurrentState(), "Consumer should have stopped.");

        // Check metrics to ensure all messages were processed
        if (producer instanceof org.evochora.datapipeline.api.resources.IMonitorable) {
            var producerMetrics = ((org.evochora.datapipeline.api.resources.IMonitorable) producer).getMetrics();
            assertEquals(10L, producerMetrics.get("messages_sent").longValue());
        }

        if (consumer instanceof org.evochora.datapipeline.api.resources.IMonitorable) {
            var consumerMetrics = ((org.evochora.datapipeline.api.resources.IMonitorable) consumer).getMetrics();
            assertEquals(10L, consumerMetrics.get("messages_received").longValue());
        }
    }
}