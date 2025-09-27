package org.evochora.node.http.api.pipeline;

import com.typesafe.config.Config;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import org.evochora.datapipeline.ServiceManager;
import org.evochora.datapipeline.api.services.IService;
import org.evochora.node.http.AbstractController;
import org.evochora.node.http.api.pipeline.dto.PipelineStatusDto;
import org.evochora.node.http.api.pipeline.dto.ServiceStatusDto;
import org.evochora.node.spi.ServiceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controller responsible for exposing the Data Pipeline's {@link ServiceManager} via a REST API.
 * It provides endpoints for controlling the lifecycle of the entire pipeline and individual services,
 * as well as for monitoring their status.
 */
public class PipelineController extends AbstractController {
    private static final Logger LOG = LoggerFactory.getLogger(PipelineController.class);

    private final ServiceManager serviceManager;
    private final String nodeId;

    /**
     * A simple, immutable record for serializing consistent JSON error responses.
     */
    public record ErrorResponse(String timestamp, int status, String error, String message) {
    }

    /**
     * Initializes the controller and retrieves the {@link ServiceManager} from the registry.
     *
     * @param registry The service registry for dependency injection.
     * @param options  The configuration specific to this controller.
     */
    public PipelineController(final ServiceRegistry registry, final Config options) {
        super(registry, options);
        this.serviceManager = registry.get(ServiceManager.class);
        this.nodeId = getHostname();
    }

    @Override
    public void registerRoutes(final Javalin app, final String basePath) {
        // Centralized exception handling for this controller's routes
        app.exception(IllegalArgumentException.class, (e, ctx) -> {
            final var response = new ErrorResponse(Instant.now().toString(), 404, "Not Found", e.getMessage());
            ctx.status(HttpStatus.NOT_FOUND).json(response);
        }).exception(IllegalStateException.class, (e, ctx) -> {
            final var response = new ErrorResponse(Instant.now().toString(), 409, "Conflict", e.getMessage());
            ctx.status(HttpStatus.CONFLICT).json(response);
        }).exception(Exception.class, (e, ctx) -> {
            LOG.error("Unhandled exception processing request: {} {}", ctx.method(), ctx.path(), e);
            final var response = new ErrorResponse(Instant.now().toString(), 500, "Internal Server Error", "An unexpected error occurred.");
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(response);
        });

        // Define all routes directly on the app instance with the full path
        // Global pipeline controls
        app.get(basePath + "/status", this::getPipelineStatus);
        app.post(basePath + "/start", this::startAll);
        app.post(basePath + "/stop", this::stopAll);
        app.post(basePath + "/restart", this::restartAll);
        app.post(basePath + "/pause", this::pauseAll);
        app.post(basePath + "/resume", this::resumeAll);

        // Individual service controls
        app.get(basePath + "/service/{serviceName}/status", this::getServiceStatus);
        app.post(basePath + "/service/{serviceName}/start", this::startService);
        app.post(basePath + "/service/{serviceName}/stop", this::stopService);
        app.post(basePath + "/service/{serviceName}/restart", this::restartService);
        app.post(basePath + "/service/{serviceName}/pause", this::pauseService);
        app.post(basePath + "/service/{serviceName}/resume", this::resumeService);
    }

    // --- Handler Implementations (package-private for testing) ---

    void getPipelineStatus(final Context ctx) {
        final Map<String, org.evochora.datapipeline.api.services.ServiceStatus> allStatuses = serviceManager.getAllServiceStatus();
        final List<ServiceStatusDto> serviceDtos = allStatuses.entrySet().stream()
            .map(entry -> ServiceStatusDto.from(entry.getKey(), entry.getValue()))
            .collect(Collectors.toList());

        final String overallStatus = determineOverallStatus(serviceDtos);
        final var pipelineStatus = new PipelineStatusDto(this.nodeId, overallStatus, serviceDtos);
        ctx.status(HttpStatus.OK).json(pipelineStatus);
    }

    void startAll(final Context ctx) {
        serviceManager.startAll();
        ctx.status(HttpStatus.ACCEPTED);
    }

    void stopAll(final Context ctx) {
        serviceManager.stopAll();
        ctx.status(HttpStatus.ACCEPTED);
    }

    void restartAll(final Context ctx) {
        serviceManager.restartAll();
        ctx.status(HttpStatus.ACCEPTED);
    }

    void pauseAll(final Context ctx) {
        serviceManager.pauseAll();
        ctx.status(HttpStatus.ACCEPTED);
    }

    void resumeAll(final Context ctx) {
        serviceManager.resumeAll();
        ctx.status(HttpStatus.ACCEPTED);
    }

    void getServiceStatus(final Context ctx) {
        final String serviceName = ctx.pathParam("serviceName");
        final org.evochora.datapipeline.api.services.ServiceStatus status = serviceManager.getServiceStatus(serviceName);
        ctx.status(HttpStatus.OK).json(ServiceStatusDto.from(serviceName, status));
    }

    void startService(final Context ctx) {
        serviceManager.startService(ctx.pathParam("serviceName"));
        ctx.status(HttpStatus.ACCEPTED);
    }

    void stopService(final Context ctx) {
        serviceManager.stopService(ctx.pathParam("serviceName"));
        ctx.status(HttpStatus.ACCEPTED);
    }

    void restartService(final Context ctx) {
        serviceManager.restartService(ctx.pathParam("serviceName"));
        ctx.status(HttpStatus.ACCEPTED);
    }

    void pauseService(final Context ctx) {
        serviceManager.pauseService(ctx.pathParam("serviceName"));
        ctx.status(HttpStatus.ACCEPTED);
    }

    void resumeService(final Context ctx) {
        serviceManager.resumeService(ctx.pathParam("serviceName"));
        ctx.status(HttpStatus.ACCEPTED);
    }

    // --- Private Helper Methods ---

    /**
     * Determines the overall status of the pipeline based on the states of its services.
     */
    private String determineOverallStatus(final List<ServiceStatusDto> services) {
        if (services.isEmpty()) {
            return "STOPPED";
        }
        final boolean allRunning = services.stream().allMatch(s -> s.state() == IService.State.RUNNING);
        if (allRunning) {
            return "RUNNING";
        }
        final boolean allStopped = services.stream().allMatch(s -> s.state() == IService.State.STOPPED);
        if (allStopped) {
            return "STOPPED";
        }
        return "DEGRADED";
    }

    /**
     * Retrieves the hostname of the local machine to use as the node ID.
     */
    private String getHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (final UnknownHostException e) {
            LOG.warn("Could not determine hostname, using 'unknown' as node ID.", e);
            return "unknown";
        }
    }
}