package org.evochora.node.http.api.pipeline;

import com.typesafe.config.ConfigFactory;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import org.evochora.datapipeline.ServiceManager;
import org.evochora.datapipeline.api.resources.IMonitorable;
import org.evochora.datapipeline.api.resources.IResource;
import org.evochora.datapipeline.api.resources.ResourceContext;
import org.evochora.datapipeline.api.services.IService;
import org.evochora.datapipeline.api.services.ResourceBinding;
import org.evochora.datapipeline.api.services.ServiceStatus;
import org.evochora.node.http.api.pipeline.dto.PipelineStatusDto;
import org.evochora.node.http.api.pipeline.dto.ServiceStatusDto;
import org.evochora.node.spi.ServiceRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PipelineControllerTest {

    interface MonitorableResource extends IResource, IMonitorable {}

    @Mock
    private ServiceManager mockServiceManager;
    @Mock
    private ServiceRegistry mockRegistry;
    @Mock
    private Context mockCtx;
    @Mock
    private IService mockService;
    @Mock
    private MonitorableResource mockResource;

    @InjectMocks
    private PipelineController controller;

    @Captor
    private ArgumentCaptor<Object> jsonCaptor;

    @BeforeEach
    void setUp() {
        when(mockRegistry.get(ServiceManager.class)).thenReturn(mockServiceManager);
        controller = new PipelineController(mockRegistry, ConfigFactory.empty());
        when(mockResource.getMetrics()).thenReturn(Map.of("metric", 1));
    }

    private ServiceStatus createDummyStatus(final IService.State state) {
        final ResourceContext resourceContext = new ResourceContext("svc", "port1", "queue-in", "res1", Collections.emptyMap());
        final ResourceBinding binding = new ResourceBinding(resourceContext, mockService, mockResource);
        return new ServiceStatus(state, Map.of("svcMetric", 2), Collections.emptyList(), List.of(binding));
    }

    @Test
    void getPipelineStatus_shouldReturnCorrectDtoAnd200() {
        final ServiceStatus runningStatus = createDummyStatus(IService.State.RUNNING);
        final ServiceStatus stoppedStatus = createDummyStatus(IService.State.STOPPED);
        when(mockServiceManager.getAllServiceStatus()).thenReturn(Map.of("serviceA", runningStatus, "serviceB", stoppedStatus));

        controller.getPipelineStatus(mockCtx);

        verify(mockCtx).status(HttpStatus.OK);
        verify(mockCtx).json(jsonCaptor.capture());
        final PipelineStatusDto result = (PipelineStatusDto) jsonCaptor.getValue();
        assertThat(result).isNotNull();
        assertThat(result.nodeId()).isNotBlank();
        assertThat(result.status()).isEqualTo("DEGRADED");
        assertThat(result.services()).hasSize(2);
        assertThat(result.services()).anyMatch(s -> s.name().equals("serviceA") && s.state() == IService.State.RUNNING);
        assertThat(result.services()).anyMatch(s -> s.name().equals("serviceB") && s.state() == IService.State.STOPPED);
    }

    @Test
    void getPipelineStatus_shouldShowRunningWhenAllServicesAreRunning() {
        final ServiceStatus runningStatus = createDummyStatus(IService.State.RUNNING);
        when(mockServiceManager.getAllServiceStatus()).thenReturn(Map.of("serviceA", runningStatus));

        controller.getPipelineStatus(mockCtx);

        verify(mockCtx).json(jsonCaptor.capture());
        final PipelineStatusDto result = (PipelineStatusDto) jsonCaptor.getValue();
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo("RUNNING");
    }

    @Test
    void startAll_shouldCallServiceManagerAndReturn202() {
        controller.startAll(mockCtx);
        verify(mockServiceManager).startAll();
        verify(mockCtx).status(HttpStatus.ACCEPTED);
    }

    @Test
    void stopAll_shouldCallServiceManagerAndReturn202() {
        controller.stopAll(mockCtx);
        verify(mockServiceManager).stopAll();
        verify(mockCtx).status(HttpStatus.ACCEPTED);
    }

    @Test
    void getServiceStatus_shouldReturnDtoForSpecificService() {
        final String serviceName = "my-service";
        final ServiceStatus status = createDummyStatus(IService.State.PAUSED);
        when(mockCtx.pathParam("serviceName")).thenReturn(serviceName);
        when(mockServiceManager.getServiceStatus(serviceName)).thenReturn(status);

        controller.getServiceStatus(mockCtx);

        verify(mockCtx).status(HttpStatus.OK);
        verify(mockCtx).json(jsonCaptor.capture());
        final ServiceStatusDto result = (ServiceStatusDto) jsonCaptor.getValue();
        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo(serviceName);
        assertThat(result.state()).isEqualTo(IService.State.PAUSED);
        assertThat(result.resourceBindings()).hasSize(1);
        assertThat(result.resourceBindings().get(0).portName()).isEqualTo("port1");
    }

    @Test
    void startService_shouldCallServiceManagerWithCorrectName() {
        final String serviceName = "service-to-start";
        when(mockCtx.pathParam("serviceName")).thenReturn(serviceName);
        controller.startService(mockCtx);
        verify(mockServiceManager).startService(serviceName);
        verify(mockCtx).status(HttpStatus.ACCEPTED);
    }

    @Test
    void stopService_shouldCallServiceManagerWithCorrectName() {
        final String serviceName = "service-to-stop";
        when(mockCtx.pathParam("serviceName")).thenReturn(serviceName);
        controller.stopService(mockCtx);
        verify(mockServiceManager).stopService(serviceName);
        verify(mockCtx).status(HttpStatus.ACCEPTED);
    }

    @Test
    void getServiceStatus_shouldThrowIllegalArgumentExceptionForUnknownService() {
        final String serviceName = "unknown-service";
        when(mockCtx.pathParam("serviceName")).thenReturn(serviceName);
        when(mockServiceManager.getServiceStatus(serviceName)).thenThrow(new IllegalArgumentException("Service not found"));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> controller.getServiceStatus(mockCtx));
    }

    @Test
    void startService_shouldThrowIllegalStateExceptionForInvalidTransition() {
        final String serviceName = "running-service";
        when(mockCtx.pathParam("serviceName")).thenReturn(serviceName);
        doThrow(new IllegalStateException("Service is already running")).when(mockServiceManager).startService(serviceName);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> controller.startService(mockCtx));
    }
}