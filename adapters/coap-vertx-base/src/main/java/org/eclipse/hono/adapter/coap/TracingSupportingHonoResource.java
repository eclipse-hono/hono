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
import org.eclipse.californium.core.coap.Response;
import org.eclipse.californium.core.network.Exchange;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.californium.core.server.resources.Resource;
import org.eclipse.hono.tracing.TenantTraceSamplingHelper;
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
 * For each request, a new OpenTracing {@code Span} is created. The {@code Span} context is
 * associated with the {@link CoapContext} that is created via {@link #createCoapContextForPost(CoapExchange)}
 * or {@link #createCoapContextForPut(CoapExchange)}. The {@link CoapContext} is
 * then passed in to the {@link #handlePost(CoapContext)} or {@link #handlePut(CoapContext)}
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
    final CoapContextTenantAndAuthIdProvider tenantObjectWithAuthIdProvider;

    /**
     * Creates a new resource that supports tracing of request processing.
     *
     * @param tracer The OpenTracing tracer.
     * @param resourceName The resource name.
     * @param adapterName The name of the protocol adapter that this resource is exposed on.
     * @param tenantObjectWithAuthIdProvider The provider that determines the tenant and auth-id
     *                                       associated with a request.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public TracingSupportingHonoResource(
            final Tracer tracer,
            final String resourceName,
            final String adapterName,
            final CoapContextTenantAndAuthIdProvider tenantObjectWithAuthIdProvider) {
        super(resourceName);
        this.tracer = Objects.requireNonNull(tracer);
        this.adapterName = Objects.requireNonNull(adapterName);
        this.tenantObjectWithAuthIdProvider = Objects.requireNonNull(tenantObjectWithAuthIdProvider);
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
            responseCode = createCoapContextForPost(coapExchange)
                    .compose(coapContext -> applyTraceSamplingPriority(coapContext, currentSpan))
                    .compose(this::handlePost);
            break;
        case PUT:
            responseCode = createCoapContextForPut(coapExchange)
                    .compose(coapContext -> applyTraceSamplingPriority(coapContext, currentSpan))
                    .compose(this::handlePut);
            break;
        default:
            final Response response = CoapErrorResponse.respond("unsupported method", ResponseCode.METHOD_NOT_ALLOWED);
            coapExchange.respond(response);
            responseCode = Future.succeededFuture(response.getCode());
            break;
        }
        responseCode.otherwise(t -> {
            log.debug("error handling request", t);
            TracingHelper.logError(currentSpan, t);
            final Response response = CoapErrorResponse.respond(t, ResponseCode.INTERNAL_SERVER_ERROR);
            coapExchange.respond(response);
            return response.getCode();
        })
        .onComplete(r -> {
            log.debug("response: {}", r.result());
            currentSpan.setTag("coap.response_code", r.result().toString());
            currentSpan.finish();
        });
    }

    /**
     * Creates a CoAP context for an incoming POST request.
     *
     * @param coapExchange The CoAP exchange to process.
     * @return A future indicating the outcome of processing the request.
     *         The future will be succeeded with the created CoAP context,
     *         otherwise the future will be failed with a {@link org.eclipse.hono.client.ClientErrorException}.
     */
    protected abstract Future<CoapContext> createCoapContextForPost(CoapExchange coapExchange);

    /**
     * Creates a CoAP context for an incoming PUT request.
     *
     * @param coapExchange The CoAP exchange to process.
     * @return A future indicating the outcome of processing the request.
     *         The future will be succeeded with the created CoAP context,
     *         otherwise the future will be failed with a {@link org.eclipse.hono.client.ClientErrorException}.
     */
    protected abstract Future<CoapContext> createCoapContextForPut(CoapExchange coapExchange);

    /**
     * Applies the trace sampling priority configured for the tenant associated with the
     * given CoAP context to the given span.
     * <p>
     * Also ensures that the span context is set in the CoAP context.
     *
     * @param ctx The CoAP context.
     * @param span The OpenTracing span.
     * @return A succeeded future with the given CoAP context.
     */
    protected final Future<CoapContext> applyTraceSamplingPriority(final CoapContext ctx, final Span span) {
        return tenantObjectWithAuthIdProvider.get(ctx, span.context())
                .map(tenantObjectWithAuthId -> {
                    TenantTraceSamplingHelper.applyTraceSamplingPriority(tenantObjectWithAuthId, span);
                    ctx.setTracingContext(span.context());
                    return ctx;
                })
                .recover(t -> {
                    ctx.setTracingContext(span.context());
                    return Future.succeededFuture(ctx);
                });
    }

    /**
     * Invoked for an incoming POST request.
     * <p>
     * This default implementation sends a response back to the client
     * with response code {@link ResponseCode#NOT_IMPLEMENTED}.
     *
     * @param coapContext The CoAP context of the current request.
     * @return A future indicating the outcome of processing the request.
     *         The future will be succeeded with the CoAP response code sent back to the client,
     *         otherwise the future will be failed with a {@link org.eclipse.hono.client.ServiceInvocationException}.
     */
    protected Future<ResponseCode> handlePost(final CoapContext coapContext) {
        final Response response = CoapErrorResponse.respond("not implemented", ResponseCode.NOT_IMPLEMENTED);
        coapContext.respond(response);
        return Future.succeededFuture(response.getCode());
    }

    /**
     * Invoked for an incoming PUT request.
     * <p>
     * This default implementation sends a response back to the client
     * with response code {@link ResponseCode#NOT_IMPLEMENTED}.
     *
     * @param coapContext The CoAP context of the current request.
     * @return A future indicating the outcome of processing the request.
     *         The future will be succeeded with the CoAP response code sent back to the client,
     *         otherwise the future will be failed with a {@link org.eclipse.hono.client.ServiceInvocationException}.
     */
    protected Future<ResponseCode> handlePut(final CoapContext coapContext) {
        final Response response = CoapErrorResponse.respond("not implemented", ResponseCode.NOT_IMPLEMENTED);
        coapContext.respond(response);
        return Future.succeededFuture(response.getCode());
    }

    // --------------- unsupported operations ---------------

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
