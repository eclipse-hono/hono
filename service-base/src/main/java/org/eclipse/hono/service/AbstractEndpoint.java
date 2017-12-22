/**
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */
package org.eclipse.hono.service;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.healthchecks.HealthCheckHandler;

/**
 * Base class for Hono endpoints.
 */
public abstract class AbstractEndpoint implements Endpoint {

    /**
     * The Vert.x instance this endpoint is running on.
     */
    protected final Vertx  vertx;
    /**
     * A logger to be used by subclasses.
     */
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * Creates an endpoint for a Vertx instance.
     * 
     * @param vertx The Vertx instance to use.
     * @throws NullPointerException if vertx is {@code null};
     */
    protected AbstractEndpoint(final Vertx vertx) {
        this.vertx = Objects.requireNonNull(vertx);
    }

    @Override
    public final Future<Void> start() {
        Future<Void> result = Future.future();
        if (vertx == null) {
            result.fail(new IllegalStateException("Vert.x instance must be set"));
        } else {
            doStart(result);
        }
        return result;
    }

    /**
     * Subclasses should override this method to create required resources
     * during startup.
     * <p>
     * This implementation always completes the start future.
     * 
     * @param startFuture Completes if startup succeeded.
     */
    protected void doStart(final Future<Void> startFuture) {
        startFuture.complete();
    }

    @Override
    public final Future<Void> stop() {
        Future<Void> result = Future.future();
        doStop(result);
        return result;
    }

    /**
     * Subclasses should override this method to release resources
     * during shutdown.
     * <p>
     * This implementation always completes the stop future.
     * 
     * @param stopFuture Completes if shutdown succeeded.
     */
    protected void doStop(final Future<Void> stopFuture) {
        stopFuture.complete();
    }

    /**
     * Does not register any checks.
     * <p>
     * Subclasses may want to override this method in order to implement meaningful checks
     * specific to the particular endpoint.
     */
    @Override
    public void registerLivenessChecks(final HealthCheckHandler handler) {
        // empty default implementation
    }

    /**
     * Does not register any checks.
     * <p>
     * Subclasses may want to override this method in order to implement meaningful checks
     * specific to the particular endpoint.
     */
    @Override
    public void registerReadinessChecks(final HealthCheckHandler handler) {
        // empty default implementation
    }
}
