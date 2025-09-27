package org.evochora.node.integration;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.awaitility.Awaitility;
import org.evochora.datapipeline.api.services.IService;
import org.evochora.node.Node;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@Tag("integration")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class NodeIntegrationTest {

    private static int testPort;
    private static ExecutorService executor;
    private static Future<?> nodeFuture;
    private static String originalUserDir;
    @TempDir
    static Path tempDir;

    @BeforeAll
    static void startNode() throws IOException {
        // Find a free port
        try (final ServerSocket socket = new ServerSocket(0)) {
            testPort = socket.getLocalPort();
        }

        // The Node loads 'evochora.conf' from the current working directory.
        // We set the working directory to a temp dir and place our test config there.
        originalUserDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.toString());

        // Correctly load the test config from test resources and write it to the temp dir
        Path testConfSource = Path.of(originalUserDir, "src/test/resources/evochora.conf");
        String confTemplate = Files.readString(testConfSource);
        String testConfContent = confTemplate.replace("port = 8080", "port = " + testPort);
        Files.writeString(tempDir.resolve("evochora.conf"), testConfContent);

        // Start the node in a background thread
        executor = Executors.newSingleThreadExecutor();
        nodeFuture = executor.submit(() -> Node.main(new String[]{}));

        // Configure REST Assured
        RestAssured.port = testPort;
        RestAssured.baseURI = "http://localhost";

        // Wait until the server is ready by polling the status endpoint
        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            given().when().get("/pipeline/api/status").then().statusCode(200);
        });
    }

    @AfterAll
    static void stopNode() {
        if (nodeFuture != null) {
            nodeFuture.cancel(true);
        }
        if (executor != null) {
            executor.shutdownNow();
        }
        // Restore original working directory
        System.setProperty("user.dir", originalUserDir);
    }

    @Test
    @Order(1)
    void getStatus_shouldReturnInitialStoppedStatus() {
        given()
            .when()
            .get("/pipeline/api/status")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("nodeId", not(emptyOrNullString()))
            .body("status", is("STOPPED"))
            .body("services", hasSize(2))
            .body("services.find { it.name == 'consumer' }.state", is(IService.State.STOPPED.toString()))
            .body("services.find { it.name == 'producer' }.state", is(IService.State.STOPPED.toString()));
    }

    @Test
    @Order(2)
    void postStartAll_shouldStartAllServices() {
        given()
            .when()
            .post("/pipeline/api/start")
            .then()
            .statusCode(202);

        // Verify the status has changed to RUNNING
        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            given()
                .when()
                .get("/pipeline/api/status")
                .then()
                .statusCode(200)
                .body("status", is("RUNNING"))
                .body("services.find { it.name == 'consumer' }.state", is(IService.State.RUNNING.toString()))
                .body("services.find { it.name == 'producer' }.state", is(IService.State.RUNNING.toString()));
        });
    }

    @Test
    @Order(3)
    void getServiceStatus_shouldReturnStatusForOneService() {
        given()
            .when()
            .get("/pipeline/api/service/consumer/status")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("name", is("consumer"))
            .body("state", is(IService.State.RUNNING.toString()));
    }

    @Test
    @Order(4)
    void getServiceStatus_forUnknownService_shouldReturn404() {
        given()
            .when()
            .get("/pipeline/api/service/nonexistent/status")
            .then()
            .statusCode(404)
            .contentType(ContentType.JSON)
            .body("error", is("Not Found"))
            .body("message", containsString("Service 'nonexistent' not found"));
    }

    @Test
    @Order(5)
    void postStart_forAlreadyRunningService_shouldReturn409() {
        // Service is already running from previous test
        given()
            .when()
            .post("/pipeline/api/service/consumer/start")
            .then()
            .statusCode(409) // Conflict
            .contentType(ContentType.JSON)
            .body("error", is("Conflict"))
            .body("message", containsString("Cannot start service 'consumer' as it is already in state RUNNING"));
    }

    @Test
    @Order(6)
    void postStop_shouldStopService() {
        given()
            .when()
            .post("/pipeline/api/service/consumer/stop")
            .then()
            .statusCode(202);

        // Verify status is now STOPPED
        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            given()
                .when()
                .get("/pipeline/api/service/consumer/status")
                .then()
                .statusCode(200)
                .body("state", is(IService.State.STOPPED.toString()));
        });
    }
}