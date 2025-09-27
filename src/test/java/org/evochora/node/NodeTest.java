package org.evochora.node;

import com.typesafe.config.Config;
import org.evochora.node.spi.IProcess;
import org.evochora.node.spi.ServiceRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("unit")
class NodeTest {

    @TempDir
    Path tempDir;

    private String originalUserDir;

    // A simple mock process for testing
    public static class MockProcess implements IProcess {
        public static final AtomicBoolean started = new AtomicBoolean(false);
        public static final AtomicBoolean stopped = new AtomicBoolean(false);

        public MockProcess(final ServiceRegistry registry, final Config options) {
            // Constructor for reflection
        }

        @Override
        public void start() {
            started.set(true);
        }

        @Override
        public void stop() {
            stopped.set(true);
        }

        public static void reset() {
            started.set(false);
            stopped.set(false);
        }
    }

    @BeforeEach
    void setUp() {
        originalUserDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.toString());
        MockProcess.reset();
    }

    @AfterEach
    void tearDown() {
        System.setProperty("user.dir", originalUserDir);
    }

    private void writeConf(String content) {
        // Ensure a minimal pipeline block exists to prevent ServiceManager from failing
        String fullContent = "pipeline {}\n" + content;
        try {
            Files.writeString(tempDir.resolve("evochora.conf"), fullContent);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void shouldLoadAndStartProcessFromConfig() {
        final String conf = """
            node {
              processes {
                mockProcess {
                  className = "org.evochora.node.NodeTest$MockProcess"
                }
              }
            }
            """;
        writeConf(conf);

        final Node node = new Node(new String[]{});

        node.start();

        await().untilTrue(MockProcess.started);

        node.stop();
        await().untilTrue(MockProcess.stopped);
    }

    @Test
    void shouldFailFastIfProcessClassIsNotFound() {
        final String conf = """
            node.processes.invalid {
              className = "org.nonexistent.Process"
            }
            """;
        writeConf(conf);

        final Node node = new Node(new String[]{});
        final RuntimeException e = assertThrows(RuntimeException.class, node::start);

        assertThat(e.getMessage()).contains("Failed to initialize process: invalid");
        assertThat(e.getCause()).isInstanceOf(ClassNotFoundException.class);
    }

    @Test
    void shouldFailFastIfProcessConstructorIsInvalid() {
        final String conf = """
            node.processes.invalid {
              className = "java.lang.String"
            }
            """;
        writeConf(conf);

        final Node node = new Node(new String[]{});
        final RuntimeException e = assertThrows(RuntimeException.class, node::start);

        assertThat(e.getMessage()).contains("Failed to initialize process: invalid");
        assertThat(e.getCause()).isInstanceOf(NoSuchMethodException.class);
    }

    @Test
    void shouldNotFailIfNoProcessesAreDefined() {
        writeConf("node {}"); // No processes block

        final Node node = new Node(new String[]{});
        assertDoesNotThrow(() -> {
            node.start();
            node.stop();
        });
    }
}