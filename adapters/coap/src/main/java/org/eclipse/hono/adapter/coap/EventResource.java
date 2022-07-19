/**
 * Copyright (c) 2021, 2022 Contributors to the Eclipse Foundation
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

import java.net.HttpURLConnection;
import java.util.Objects;

import org.eclipse.californium.core.network.Exchange;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.service.metric.MetricsTags;
import org.eclipse.hono.util.EventConstants;

import io.opentracing.Tracer;
import io.vertx.core.Future;
import io.vertx.core.Vertx;

/**
 * A CoAP resource for uploading events.
 *
 */
public class EventResource extends AbstractHonoResource {

    private static final String SPAN_NAME_DEFAULT = "/%s/*".formatted(EventConstants.EVENT_ENDPOINT);
    private static final String SPAN_NAME_POST = "/%s".formatted(EventConstants.EVENT_ENDPOINT);
    private static final String SPAN_NAME_PUT = "/%s/:tenant_id/:device_id".formatted(EventConstants.EVENT_ENDPOINT);

    /**
     * Creates a new resource.
     * <p>
     * Delegates to {@link #EventResource(String, CoapProtocolAdapter, Tracer, Vertx)} using
     * {@value EventConstants#EVENT_ENDPOINT} as the resource name.
     *
     * @param adapter The protocol adapter that this resource is part of.
     * @param tracer Open Tracing tracer to use for tracking the processing of requests.
     * @param vertx The vert.x instance to run on.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public EventResource(
            final CoapProtocolAdapter adapter,
            final Tracer tracer,
            final Vertx vertx) {
        this(EventConstants.EVENT_ENDPOINT, adapter, tracer, vertx);
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
    public EventResource(
            final String resourceName,
            final CoapProtocolAdapter adapter,
            final Tracer tracer,
            final Vertx vertx) {
        super(resourceName, adapter, tracer, vertx);
    }

    @Override
    protected String getSpanName(final Exchange exchange) {
        switch (exchange.getRequest().getCode()) {
        case POST:
            return SPAN_NAME_POST;
        case PUT:
            return SPAN_NAME_PUT;
        default:
            return SPAN_NAME_DEFAULT;
        }
    }

    @Override
    public Future<Void> handlePostRequest(final CoapContext ctx) {
        return uploadEventMessage(ctx);
    }

    @Override
    public Future<Void> handlePutRequest(final CoapContext ctx) {
        return uploadEventMessage(ctx);
    }

    /**
     * Forwards an event to a downstream consumer.
     *
     * @param context The context representing the event to be forwarded.
     * @return A future indicating the outcome of the operation.
     *         The future will be succeeded if the event has been forwarded successfully.
     *         In this case one of the context's <em>respond</em> methods will have been invoked to send a CoAP response
     *         back to the device.
     *         Otherwise the future will be failed with a {@link org.eclipse.hono.client.ServiceInvocationException}.
     * @throws NullPointerException if context is {@code null}.
     */
    private Future<Void> uploadEventMessage(final CoapContext context) {

        Objects.requireNonNull(context);
        if (context.isConfirmable()) {
            return doUploadMessage(context, MetricsTags.EndpointType.EVENT);
        } else {
            return Future.failedFuture(new ClientErrorException(
                    HttpURLConnection.HTTP_BAD_REQUEST,
                    "event endpoint supports confirmable request messages only"));
        }
    }
}
