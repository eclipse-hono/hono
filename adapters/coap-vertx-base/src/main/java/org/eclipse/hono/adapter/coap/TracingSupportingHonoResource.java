/**
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

import java.util.Objects;

import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.network.Exchange;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.californium.core.server.resources.Resource;
import org.eclipse.hono.client.ServiceInvocationException;

import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;
import io.vertx.core.Future;


/**
 * A CoAP resource that supports the tracking of request processing using OpenTracing.
 * <p>
 * The resource only supports processing of POST and PUT requests.
 */
public abstract class TracingSupportingHonoResource extends CoapResource {

    final Tracer tracer;
    final String adapterName;

    /**
     * Creates a new resource that supports tracing of request processing.
     * 
     * @param tracer The OpenTracing tracer.
     * @param resourceName The resource name.
     * @param adapterName The name of the protocol adapter that this resource is exposed on.
     */
    public TracingSupportingHonoResource(final Tracer tracer, final String resourceName, final String adapterName) {
        super(resourceName);
        this.tracer = Objects.requireNonNull(tracer);
        this.adapterName = Objects.requireNonNull(adapterName);
    }

    /**
     * {@inheritDoc}
     * <p>
     * This default implementation always returns {@code this}.
     */
    @Override
    public Resource getChild(final String name) {
        return this;
    }

    private Span newSpan(final Exchange exchange) {
        return tracer.buildSpan(exchange.getRequest().getCode().toString())
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER)
                .withTag(Tags.COMPONENT.getKey(), adapterName)
                .withTag("coap.type", exchange.getRequest().getType().name())
                .withTag(Tags.HTTP_URL.getKey(), exchange.getRequest().getOptions().getUriString())
                .start();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void handleRequest(final Exchange exchange) {

        final Span currentSpan = newSpan(exchange);
        final CoapExchange coapExchange = new CoapExchange(exchange, this);
        final Future<ResponseCode> responseCode;
        switch (exchange.getRequest().getCode()) {
            case POST:
                responseCode = handlePost(coapExchange, currentSpan);
                break;
            case PUT:
                responseCode = handlePut(coapExchange, currentSpan);
                break;
            default:
                CoapErrorResponse.respond(coapExchange, "unsupported method", ResponseCode.METHOD_NOT_ALLOWED);
                responseCode = Future.succeededFuture(ResponseCode.METHOD_NOT_ALLOWED);
                break;
        }
        responseCode.otherwise(t -> {
            return CoapErrorResponse.respond(coapExchange, t);
        })
        .setHandler(r -> {
            currentSpan.setTag("coap.response_code", r.result().toString());
            currentSpan.finish();
        });
    }

    /**
     * Invoked for an incoming POST request.
     * <p>
     * This default implementation sends a response back to the client
     * with response code {@link ResponseCode#NOT_IMPLEMENTED}.
     * 
     * @param exchange The CoAP exchange to process.
     * @param currentSpan The OpenTracing span used to track the processing of the request.
     * @return A future indicating the outcome of processing the request.
     *         The future will be succeeded with the CoAP response code sent back to the client,
     *         otherwise the future will be failed with a {@link ServiceInvocationException}.
     */
    protected Future<ResponseCode> handlePost(final CoapExchange exchange, final Span currentSpan) {
        CoapErrorResponse.respond(exchange, "not impleented", ResponseCode.NOT_IMPLEMENTED);
        return Future.succeededFuture(ResponseCode.NOT_IMPLEMENTED);
    }

    /**
     * Invoked for an incoming PUT request.
     * <p>
     * This default implementation sends a response back to the client
     * with response code {@link ResponseCode#NOT_IMPLEMENTED}.
     * 
     * @param exchange The CoAP exchange to process.
     * @param currentSpan The OpenTracing span used to track the processing of the request.
     * @return A future indicating the outcome of processing the request.
     *         The future will be succeeded with the CoAP response code sent back to the client,
     *         otherwise the future will be failed with a {@link ServiceInvocationException}.
     */
    protected Future<ResponseCode> handlePut(final CoapExchange exchange, final Span currentSpan) {
        CoapErrorResponse.respond(exchange, "not impleented", ResponseCode.NOT_IMPLEMENTED);
        return Future.succeededFuture(ResponseCode.NOT_IMPLEMENTED);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Always throws {@link UnsupportedOperationException}.
     */
    @Override
    public final void handleDELETE(final CoapExchange exchange) {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * {@inheritDoc}
     * <p>
     * Always throws {@link UnsupportedOperationException}.
     */
    @Override
    public final void handleFETCH(final CoapExchange exchange) {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * {@inheritDoc}
     * <p>
     * Always throws {@link UnsupportedOperationException}.
     */
    @Override
    public void handleGET(final CoapExchange exchange) {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * {@inheritDoc}
     * <p>
     * Always throws {@link UnsupportedOperationException}.
     */
    @Override
    public void handleIPATCH(final CoapExchange exchange) {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * {@inheritDoc}
     * <p>
     * Always throws {@link UnsupportedOperationException}.
     */
    @Override
    public void handlePATCH(final CoapExchange exchange) {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * {@inheritDoc}
     * <p>
     * Always throws {@link UnsupportedOperationException}.
     */
    @Override
    public void handlePOST(final CoapExchange exchange) {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * {@inheritDoc}
     * <p>
     * Always throws {@link UnsupportedOperationException}.
     */
    @Override
    public void handlePUT(final CoapExchange exchange) {
        throw new UnsupportedOperationException("not implemented");
    }
}
