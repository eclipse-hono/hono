/*******************************************************************************
 * Copyright (c) 2016, 2022 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.hono.service;

import java.util.Objects;

import org.eclipse.hono.tracing.TracingHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.noop.NoopTracerFactory;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.ext.healthchecks.HealthCheckHandler;

/**
 * Base class for Hono endpoints.
 */
public abstract class AbstractEndpoint implements Endpoint {

    /**
     * The Vert.x instance this endpoint is running on.
     */
    protected final Vertx vertx;
    /**
     * A logger to be used by subclasses.
     */
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * The OpenTracing {@code Tracer} for tracking processing of requests.
     */
    protected Tracer tracer = NoopTracerFactory.create();

    /**
     * Creates an endpoint for a Vertx instance.
     *
     * @param vertx The Vertx instance to use.
     * @throws NullPointerException if vertx is {@code null};
     */
    protected AbstractEndpoint(final Vertx vertx) {
        this.vertx = Objects.requireNonNull(vertx);
    }

    /**
     * Sets the OpenTracing {@code Tracer} to use for tracking the processing
     * of requests.
     * <p>
     * If not set explicitly, the {@code NoopTracer} from OpenTracing will
     * be used.
     *
     * @param opentracingTracer The tracer.
     */
    public final void setTracer(final Tracer opentracingTracer) {
        this.tracer = Objects.requireNonNull(opentracingTracer);
    }

    @Override
    public final Future<Void> start() {
        final Promise<Void> result = Promise.promise();
        doStart(result);
        return result.future();
    }

    /**
     * Subclasses should override this method to create required resources
     * during startup.
     * <p>
     * This implementation always completes the start future.
     *
     * @param startPromise Completes if startup succeeded.
     */
    protected void doStart(final Promise<Void> startPromise) {
        startPromise.complete();
    }

    @Override
    public final Future<Void> stop() {
        final Promise<Void> result = Promise.promise();
        doStop(result);
        return result.future();
    }

    /**
     * Subclasses should override this method to release resources
     * during shutdown.
     * <p>
     * This implementation always completes the stop future.
     *
     * @param stopPromise Completes if shutdown succeeded.
     */
    protected void doStop(final Promise<Void> stopPromise) {
        stopPromise.complete();
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

    /**
     * Creates {@code DeliveryOptions} that contain the given {@code SpanContext}.
     *
     * @param sendTimeOutInMs The send timeout value in milliseconds.
     * @param spanContext The {@code SpanContext} (may be {@code null}).
     * @return The {@code DeliveryOptions}.
     */
    protected final DeliveryOptions createEventBusMessageDeliveryOptions(final long sendTimeOutInMs, final SpanContext spanContext) {
        final DeliveryOptions deliveryOptions = new DeliveryOptions();
        deliveryOptions.setSendTimeout(sendTimeOutInMs);
        TracingHelper.injectSpanContext(tracer, spanContext, deliveryOptions);
        return deliveryOptions;
    }
}
