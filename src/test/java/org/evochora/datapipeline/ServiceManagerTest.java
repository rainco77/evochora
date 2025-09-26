package org.evochora.datapipeline;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.evochora.datapipeline.api.resources.IResource;
import org.evochora.datapipeline.api.services.IService;
import org.evochora.datapipeline.resources.queues.InMemoryBlockingQueue;
import org.evochora.datapipeline.services.DummyConsumerService;
import org.evochora.datapipeline.services.DummyProducerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@Tag("unit")
public class ServiceManagerTest {

    private Config config;

    @BeforeEach
    void setUp() {
        // Basic configuration for testing
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
              testProducer {
                class = "org.evochora.datapipeline.services.DummyProducerService"
                resources {
                  output = ["resource://testQueue"]
                }
              }
              testConsumer {
                class = "org.evochora.datapipeline.services.DummyConsumerService"
                resources {
                  input = ["resource://testQueue"]
                }
              }
            }
        """);
    }

    @Test
    void constructor_shouldInstantiateResourcesAndServices() {
        ServiceManager manager = new ServiceManager(config);

        assertNotNull(manager.getResource("testQueue"), "Resource should be instantiated");
        assertTrue(manager.getResource("testQueue") instanceof InMemoryBlockingQueue, "Resource should be of type InMemoryBlockingQueue");

        assertNotNull(manager.getService("testProducer"), "Producer service should be instantiated");
        assertTrue(manager.getService("testProducer") instanceof DummyProducerService, "Service should be of type DummyProducerService");

        assertNotNull(manager.getService("testConsumer"), "Consumer service should be instantiated");
        assertTrue(manager.getService("testConsumer") instanceof DummyConsumerService, "Service should be of type DummyConsumerService");

        assertEquals(1, manager.getResources().size(), "Should have one resource");
        assertEquals(2, manager.getServices().size(), "Should have two services");
    }

    @Test
    void constructor_shouldThrow_whenResourceClassNotFound() {
        Config badConfig = ConfigFactory.parseString("resources.test.class = \"not.a.real.class\"");
        assertThrows(IllegalStateException.class, () -> new ServiceManager(badConfig));
    }

    @Test
    void constructor_shouldThrow_whenServiceClassNotFound() {
        Config badConfig = ConfigFactory.parseString("services.test.class = \"not.a.real.class\"");
        assertThrows(IllegalStateException.class, () -> new ServiceManager(badConfig));
    }

    @Test
    void constructor_shouldThrow_whenResourceUriIsInvalid() {
        Config badConfig = ConfigFactory.parseString("""
            services.test.class = "org.evochora.datapipeline.services.DummyProducerService"
            services.test.resources.output = ["invalid-uri"]
        """);
        assertThrows(IllegalArgumentException.class, () -> new ServiceManager(badConfig));
    }

    @Test
    void constructor_shouldThrow_whenResourceIsNotFound() {
        Config badConfig = ConfigFactory.parseString("""
            services.test.class = "org.evochora.datapipeline.services.DummyProducerService"
            services.test.resources.output = ["resource://nonExistent"]
        """);
        assertThrows(IllegalStateException.class, () -> new ServiceManager(badConfig));
    }

    @Test
    void lifecycleMethods_shouldCallMethodsOnAllServices() throws Exception {
        // Using mocks to verify interactions
        IService producer = mock(IService.class);
        IService consumer = mock(IService.class);

        // A bit of a hack to inject mocks, but fine for a unit test
        ServiceManager manager = new ServiceManager(ConfigFactory.empty());

        // Use reflection to access the private services map
        java.lang.reflect.Field servicesField = ServiceManager.class.getDeclaredField("services");
        servicesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<String, IService> servicesMap = (java.util.Map<String, IService>) servicesField.get(manager);

        servicesMap.put("producer", producer);
        servicesMap.put("consumer", consumer);

        manager.startAll();
        verify(producer).start();
        verify(consumer).start();

        manager.stopAll();
        verify(producer).stop();
        verify(consumer).stop();

        manager.pauseAll();
        verify(producer).pause();
        verify(consumer).pause();

        manager.resumeAll();
        verify(producer).resume();
        verify(consumer).resume();

        manager.restartAll();
        verify(producer).restart();
        verify(consumer).restart();
    }
}