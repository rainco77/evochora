package org.evochora.node.config;

import com.typesafe.config.Config;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class ConfigLoaderTest {

    @TempDir
    Path tempDir;

    private File configFile;
    private String originalUserDir;

    @BeforeEach
    void setUp() throws IOException {
        originalUserDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.toString());
        configFile = tempDir.resolve("evochora.conf").toFile();
    }

    @AfterEach
    void tearDown() {
        System.setProperty("user.dir", originalUserDir);
        System.clearProperty("file.value");
        System.clearProperty("cli.value");
        System.clearProperty("a");
        System.clearProperty("b");
    }

    private void writeConf(String content) {
        // Ensure a minimal pipeline block exists to prevent ServiceManager from failing
        String fullContent = "pipeline {}\n" + content;
        try {
            Files.writeString(configFile.toPath(), fullContent);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void shouldLoadDefaultConfigWhenNoOtherConfigExists() {
        // No custom config file written, will use reference.conf which now has a pipeline block
        final Config config = ConfigLoader.load(new String[]{});
        assertThat(config.getString("default.value")).isEqualTo("default");
    }

    @Test
    void shouldLoadFileConfigOverDefault() {
        writeConf("file.value = \"file_val\" \n default.value = \"overridden_by_file\"");
        final Config config = ConfigLoader.load(new String[]{});
        assertThat(config.getString("file.value")).isEqualTo("file_val");
        assertThat(config.getString("default.value")).isEqualTo("overridden_by_file");
    }

    @Test
    void shouldLoadCliConfigOverFileAndDefault() {
        writeConf("file.value = \"file_val\" \n cli.value = \"from_file\"");
        System.setProperty("cli.value", "from_cli");
        final Config config = ConfigLoader.load(new String[]{});
        assertThat(config.getString("cli.value")).isEqualTo("from_cli");
        assertThat(config.getString("file.value")).isEqualTo("file_val");
    }

    @Test
    void shouldLoadConfigWithCorrectPrecedence() {
        writeConf("a = \"from_file\"\n b = \"from_file\"");
        System.setProperty("b", "from_cli");
        final Config config = ConfigLoader.load(new String[]{});
        assertThat(config.getString("default.value")).isEqualTo("default");
        assertThat(config.getString("a")).isEqualTo("from_file");
        assertThat(config.getString("b")).isEqualTo("from_cli");
    }
}