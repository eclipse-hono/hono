/**
 * Copyright (c) 2019, 2020 Contributors to the Eclipse Foundation
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
import org.eclipse.californium.core.coap.OptionSet;
import org.eclipse.californium.core.network.Exchange;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.californium.core.server.resources.Resource;
import org.eclipse.hono.tracing.TracingHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.tag.Tags;
import io.vertx.core.Future;


/**
 * A CoAP resource that supports the tracking of request processing using <em>OpenTracing</em>.
 * <p>
 * This resource supports processing of {@code POST} and {@code PUT} requests only.
 * For each request, a new OpenTracing {@code Span} is created which is passed in to
 * the {@link #handlePost(CoapExchange, Span)} or {@link #handlePut(CoapExchange, Span)}
 * method.
 * <p>
 * If the request contains the {@link CoapOptionInjectExtractAdapter#OPTION_TRACE_CONTEXT} option, its value
 * is expected to be a binary encoded trace context and the {@link CoapOptionInjectExtractAdapter}
 * is used to extract a {@code SpanContext} which is then used as the parent for the newly created
 * {@code Span}.
 */
public abstract class TracingSupportingHonoResource extends CoapResource {

    protected final Logger log = LoggerFactory.getLogger(getClass());
    final Tracer tracer;
    final String adapterName;

    /**
     * Creates a new resource that supports tracing of request processing.
     * 
     * @param tracer The OpenTracing tracer.
     * @param resourceName The resource name.
     * @param adapterName The name of the protocol adapter that this resource is exposed on.
     * @throws NullPointerException if any of the parameters are {@code null}.
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

    private SpanContext extractSpanContextFromRequest(final OptionSet requestOptions) {
        return CoapOptionInjectExtractAdapter.forExtraction(requestOptions)
                .map(carrier -> tracer.extract(Format.Builtin.BINARY, carrier))
                .orElse(null);
    }

    private Span newSpan(final Exchange exchange) {
        return TracingHelper.buildServerChildSpan(
                tracer,
                extractSpanContextFromRequest(exchange.getRequest().getOptions()),
                exchange.getRequest().getCode().toString(),
                adapterName)
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
            log.debug("failed:", t);
            return CoapErrorResponse.respond(coapExchange, t);
        })
        .setHandler(r -> {
            log.debug("response: {}", r.result());
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
     *         otherwise the future will be failed with a {@link org.eclipse.hono.client.ServiceInvocationException}.
     */
    protected Future<ResponseCode> handlePost(final CoapExchange exchange, final Span currentSpan) {
        CoapErrorResponse.respond(exchange, "not implemented", ResponseCode.NOT_IMPLEMENTED);
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
     *         otherwise the future will be failed with a {@link org.eclipse.hono.client.ServiceInvocationException}.
     */
    protected Future<ResponseCode> handlePut(final CoapExchange exchange, final Span currentSpan) {
        CoapErrorResponse.respond(exchange, "not implemented", ResponseCode.NOT_IMPLEMENTED);
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
