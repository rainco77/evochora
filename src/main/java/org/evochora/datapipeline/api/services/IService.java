package org.evochora.datapipeline.api.services;

import org.evochora.datapipeline.api.resources.OperationalError;
import java.util.List;

/**
 * The core interface for all services in the data pipeline.
 * <p>
 * Services are the primary components of the pipeline, responsible for
 * processing data. Each service has a well-defined lifecycle (start, stop,
 * pause, resume) and provides status information. All services must have a
 * specific constructor signature for the ServiceManager to instantiate them.
 */
public interface IService {

    /**
     * The operational state of a service.
     */
    enum State {
        /**
         * The service is not running and must be started to become active.
         */
        STOPPED,
        /**
         * The service is actively processing data.
         */
        RUNNING,
        /**
         * The service is temporarily suspended but can resume its work.
         */
        PAUSED,
        /**
         * The service has encountered a fatal error and cannot continue.
         */
        ERROR
    }

    /**
     * Starts the service, transitioning it to the RUNNING state.
     * This method should be idempotent.
     */
    void start();

    /**
     * Stops the service, transitioning it to the STOPPED state.
     * This method should be idempotent.
     */
    void stop();

    /**
     * Pauses the service, transitioning it to the PAUSED state.
     * The service should be able to resume from where it left off.
     */
    void pause();

    /**
     * Resumes a paused service, transitioning it back to the RUNNING state.
     */
    void resume();

    /**
     * Returns the current state of the service.
     *
     * @return The current {@link State}.
     */
    State getCurrentState();

    /**
     * Restarts the service. This is typically implemented as a stop() followed by a start().
     */
    void restart();

    /**
     * Returns a list of operational errors that have occurred in the service.
     * @return A list of {@link OperationalError}s.
     */
    List<OperationalError> getErrors();

    /**
     * Clears the list of operational errors for the service.
     */
    void clearErrors();
}