/**
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */


package org.eclipse.hono.adapter.coap;

import org.eclipse.hono.service.metric.MetricsTags;
import org.eclipse.hono.util.TelemetryConstants;

import io.opentracing.Tracer;
import io.vertx.core.Future;
import io.vertx.core.Vertx;

/**
 * A CoAP resource for uploading telemetry messages.
 *
 */
public class TelemetryResource extends AbstractHonoResource {

    /**
     * Creates a new resource.
     * <p>
     * Delegates to {@link #TelemetryResource(String, CoapProtocolAdapter, Tracer, Vertx)} using
     * {@value TelemetryConstants#TELEMETRY_ENDPOINT} as the resource name.
     *
     * @param adapter The protocol adapter that this resource is part of.
     * @param tracer Open Tracing tracer to use for tracking the processing of requests.
     * @param vertx The vert.x instance to run on.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public TelemetryResource(
            final CoapProtocolAdapter adapter,
            final Tracer tracer,
            final Vertx vertx) {
        this(TelemetryConstants.TELEMETRY_ENDPOINT, adapter, tracer, vertx);
    }

    /**
     * Creates a new resource.
     *
     * @param resourceName The name of this resource.
     * @param adapter The protocol adapter that this resource is part of.
     * @param tracer Open Tracing tracer to use for tracking the processing of requests.
     * @param vertx The vert.x instance to run on.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public TelemetryResource(
            final String resourceName,
            final CoapProtocolAdapter adapter,
            final Tracer tracer,
            final Vertx vertx) {
        super(resourceName, adapter, tracer, vertx);
    }

    @Override
    public Future<Void> handlePost(final CoapContext ctx) {
        return doUploadMessage(ctx, MetricsTags.EndpointType.TELEMETRY);
    }

    @Override
    public Future<Void> handlePut(final CoapContext ctx) {
        return handlePost(ctx);
    }
}
