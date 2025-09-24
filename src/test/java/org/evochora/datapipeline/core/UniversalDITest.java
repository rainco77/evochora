package org.evochora.datapipeline.core;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.evochora.datapipeline.api.services.IService;
import org.evochora.datapipeline.api.services.ServiceStatus;
import org.evochora.datapipeline.api.services.State;
import org.evochora.datapipeline.services.DummyConsumerService;
import org.evochora.datapipeline.services.DummyProducerService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for the Universal DI system.
 * Tests the complete pipeline with producer/consumer services using resource URIs.
 */
@Tag("integration")
public class UniversalDITest {

    private ServiceManager serviceManager;
    private Config testConfig;

    @BeforeEach
    void setUp() {
        // Create test configuration using Universal DI pattern
        testConfig = ConfigFactory.parseString("""
            pipeline {
              resources {
                test-channel {
                  className = "org.evochora.datapipeline.resources.InMemoryChannel"
                  options {
                    capacity = 100
                  }
                }
              }

              services {
                dummy-producer {
                  className = "org.evochora.datapipeline.services.DummyProducerService"
                  resources {
                    messages = "channel-out:test-channel"
                  }
                  options {
                    messageCount = 5
                    executionDelay = 50
                  }
                }

                dummy-consumer {
                  className = "org.evochora.datapipeline.services.DummyConsumerService"
                  resources {
                    messages = "channel-in:test-channel"
                  }
                }
              }

              autoStart = false
              startupSequence = ["dummy-producer", "dummy-consumer"]

              metrics {
                enableMetrics = true
                updateIntervalSeconds = 1
              }
            }

            logging {
              default-level = "WARN"
              levels {
                "org.evochora.datapipeline" = "DEBUG"
              }
            }
            """);

        serviceManager = new ServiceManager(testConfig);
    }

    @AfterEach
    void tearDown() {
        if (serviceManager != null) {
            serviceManager.stopAll();
        }
    }

    @Test
    void testUniversalDIResourceInstantiation() {
        // Test that resources were created
        assertEquals(1, serviceManager.getResources().size());
        assertTrue(serviceManager.getResources().containsKey("test-channel"));

        // Test that services were created with injected resources
        assertEquals(2, serviceManager.getServices().size());
        assertTrue(serviceManager.getServices().containsKey("dummy-producer"));
        assertTrue(serviceManager.getServices().containsKey("dummy-consumer"));
    }

    @Test
    void testServiceLifecycle() throws InterruptedException {
        // Start services
        serviceManager.startAll();

        // Wait for services to start
        Thread.sleep(100);

        // Check that services are running
        IService producer = serviceManager.getServices().get("dummy-producer");
        IService consumer = serviceManager.getServices().get("dummy-consumer");

        assertNotNull(producer);
        assertNotNull(consumer);

        ServiceStatus producerStatus = producer.getServiceStatus();
        ServiceStatus consumerStatus = consumer.getServiceStatus();

        assertEquals(State.RUNNING, producerStatus.state());
        assertEquals(State.RUNNING, consumerStatus.state());

        // Test resource bindings are present
        assertFalse(producerStatus.resourceBindings().isEmpty());
        assertFalse(consumerStatus.resourceBindings().isEmpty());

        // Check resource binding details
        assertEquals("test-channel", producerStatus.resourceBindings().get(0).resourceName());
        assertEquals("channel-out", producerStatus.resourceBindings().get(0).usageType());

        assertEquals("test-channel", consumerStatus.resourceBindings().get(0).resourceName());
        assertEquals("channel-in", consumerStatus.resourceBindings().get(0).usageType());
    }

    @Test
    void testMessageFlow() throws InterruptedException {
        // Start services
        serviceManager.startAll();

        // Give producer time to produce messages and consumer time to consume them
        Thread.sleep(1000);

        // Stop services
        serviceManager.stopAll();

        // Wait for services to stop
        Thread.sleep(200);

        // Check that messages were processed
        DummyProducerService producer = (DummyProducerService) serviceManager.getServices().get("dummy-producer");
        DummyConsumerService consumer = (DummyConsumerService) serviceManager.getServices().get("dummy-consumer");

        // Producer should have finished all messages
        assertTrue(producer.getActivityInfo().contains("Messages produced:"));

        // Consumer should have received some/all messages
        assertTrue(consumer.getReceivedMessageCount() > 0);
        assertTrue(consumer.getReceivedMessageCount() <= 5); // At most 5 messages
    }

    @Test
    void testResourceURIParsing() {
        // Test that resource URIs were parsed correctly
        // This is verified by checking that the services can access their resources
        // and that the resource bindings have the correct usage types

        IService producer = serviceManager.getServices().get("dummy-producer");
        IService consumer = serviceManager.getServices().get("dummy-consumer");

        ServiceStatus producerStatus = producer.getServiceStatus();
        ServiceStatus consumerStatus = consumer.getServiceStatus();

        // Check usage types from URI parsing
        assertEquals("channel-out", producerStatus.resourceBindings().get(0).usageType());
        assertEquals("channel-in", consumerStatus.resourceBindings().get(0).usageType());

        // Check resource names
        assertEquals("test-channel", producerStatus.resourceBindings().get(0).resourceName());
        assertEquals("test-channel", consumerStatus.resourceBindings().get(0).resourceName());
    }

    @Test
    void testMetricsCollection() throws InterruptedException {
        // Start services with metrics enabled
        serviceManager.startAll();

        // Give time for metrics collection
        Thread.sleep(2000);

        // Check status output includes metrics
        String status = serviceManager.getStatus();
        assertNotNull(status);
        assertTrue(status.contains("SERVICE / RESOURCE"));
        assertTrue(status.contains("USAGE"));
        assertTrue(status.contains("dummy-producer"));
        assertTrue(status.contains("dummy-consumer"));
        assertTrue(status.contains("test-channel"));
    }
}
