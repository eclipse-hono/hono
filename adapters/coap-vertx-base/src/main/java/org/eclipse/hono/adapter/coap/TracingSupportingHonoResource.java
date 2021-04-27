/**
 * Copyright (c) 2019, 2021 Contributors to the Eclipse Foundation
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
import java.security.Principal;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.coap.OptionSet;
import org.eclipse.californium.core.coap.Response;
import org.eclipse.californium.core.network.Exchange;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.californium.core.server.resources.Resource;
import org.eclipse.californium.elements.auth.ExtensiblePrincipal;
import org.eclipse.hono.auth.Device;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.ServerErrorException;
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
import io.vertx.core.Promise;


/**
 * A CoAP resource that supports the tracking of request processing using <em>OpenTracing</em>.
 * <p>
 * This resource supports processing of {@code POST} and {@code PUT} requests only.
 */
public abstract class TracingSupportingHonoResource extends CoapResource {

    private static final Logger LOG = LoggerFactory.getLogger(TracingSupportingHonoResource.class);

    private final Tracer tracer;
    private final CoapProtocolAdapter adapter;

    /**
     * Creates a new resource that supports tracing of request processing.
     *
     * @param adapter The protocol adapter that this resource is part of.
     * @param tracer The OpenTracing tracer.
     * @param resourceName The resource name.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public TracingSupportingHonoResource(
            final CoapProtocolAdapter adapter,
            final Tracer tracer,
            final String resourceName) {
        super(resourceName);
        this.adapter = Objects.requireNonNull(adapter);
        this.tracer = Objects.requireNonNull(tracer);
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

    /**
     * Gets the tracer to use for tracking the processing of requests.
     *
     * @return The tracer.
     */
    protected Tracer getTracer() {
        return tracer;
    }

    /**
     * Gets the protocol adapter that this resource is part of.
     *
     * @return The adapter.
     */
    protected CoapProtocolAdapter getAdapter() {
        return adapter;
    }

    /**
     * Gets an authenticated device's identity for a CoAP request.
     *
     * @param exchange The CoAP exchange with the authenticated device's principal.
     * @return A future indicating the outcome of the operation.
     *         The future will be succeeded if the authenticated device can be determined from the CoAP exchange,
     *         otherwise the future will be failed with a {@link ClientErrorException}.
     */
    public static Future<Device> getAuthenticatedDevice(final CoapExchange exchange) {

        final Promise<Device> result = Promise.promise();
        final Principal peerIdentity = exchange.advanced().getRequest().getSourceContext().getPeerIdentity();
        if (peerIdentity instanceof ExtensiblePrincipal) {
            final ExtensiblePrincipal<?> extPrincipal = (ExtensiblePrincipal<?>) peerIdentity;
            final Device authenticatedDevice = extPrincipal.getExtendedInfo()
                    .get(DefaultDeviceResolver.EXT_INFO_KEY_HONO_DEVICE, Device.class);
            if (authenticatedDevice != null) {
                result.complete(authenticatedDevice);
            } else {
                result.fail(new ClientErrorException(
                        HttpURLConnection.HTTP_UNAUTHORIZED,
                        "DTLS session does not contain authenticated Device"));
            }
        } else {
            result.fail(new ClientErrorException(
                    HttpURLConnection.HTTP_UNAUTHORIZED,
                    "DTLS session does not contain ExtensiblePrincipal"));

        }
        return result.future();
    }

    /**
     * Gets the authentication identifier of a CoAP request.
     *
     * @param exchange The CoAP exchange with the authenticated device's principal.
     * @return The authentication identifier or {@code null} if it could not be determined.
     */
    public static String getAuthId(final CoapExchange exchange) {
        final Principal peerIdentity = exchange.advanced().getRequest().getSourceContext().getPeerIdentity();
        if (!(peerIdentity instanceof ExtensiblePrincipal)) {
            return null;
        }
        final ExtensiblePrincipal<?> extPrincipal = (ExtensiblePrincipal<?>) peerIdentity;
        return extPrincipal.getExtendedInfo().get(DefaultDeviceResolver.EXT_INFO_KEY_HONO_AUTH_ID, String.class);
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
                adapter.getTypeName())
            .withTag(Tags.HTTP_METHOD, exchange.getRequest().getCode().name())
            .withTag(Tags.HTTP_URL.getKey(), exchange.getRequest().getOptions().getUriString())
            .withTag(CoapConstants.TAG_COAP_MESSAGE_TYPE, exchange.getRequest().getType().name())
            .start();
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation handles POST and PUT requests only.
     * All other request codes result in a 4.05 response code.
     * <p>
     * For each request, a new OpenTracing {@code Span} is created. The {@code Span} context is
     * associated with the {@link CoapContext} that is created by the <em>createCoapContextForXXX</em>
     * method matching the request code. The {@link CoapContext} is then passed in to the corresponding
     * <em>handleXXXX</em> method.
     * <p>
     * If the request contains the {@link CoapOptionInjectExtractAdapter#OPTION_TRACE_CONTEXT} option, its value
     * is expected to be a binary encoded trace context and the {@link CoapOptionInjectExtractAdapter}
     * is used to extract a {@code SpanContext} which is then used as the parent of the newly created
     * {@code Span}.
     */
    @Override
    public void handleRequest(final Exchange exchange) {

        final Span currentSpan = newSpan(exchange);
        final AtomicReference<ResponseCode> responseCode = new AtomicReference<>(null);
        final CoapExchange coapExchange = new CoapExchange(exchange, this) {
            /**
             * {@inheritDoc}
             */
            @Override
            public void respond(final Response response) {
                super.respond(response);
                responseCode.set(response.getCode());
            }
        };

        final Future<?> result;
        switch (exchange.getRequest().getCode()) {
        case POST:
            result = createCoapContextForPost(coapExchange, currentSpan)
                .compose(coapContext -> applyTraceSamplingPriority(coapContext, currentSpan))
                .compose(this::handlePost);
            break;
        case PUT:
            result = createCoapContextForPut(coapExchange, currentSpan)
                .compose(coapContext -> applyTraceSamplingPriority(coapContext, currentSpan))
                .compose(this::handlePut);
            break;
        default:
            result = Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_METHOD));
            break;
        }
        result.map(ok -> {
            if (responseCode.get() == null) {
                 throw new ServerErrorException(
                         HttpURLConnection.HTTP_INTERNAL_ERROR,
                         "no CoAP response sent");
            } else {
                return responseCode.get();
            }
        })
        .otherwise(t -> {
            LOG.debug("error handling request", t);
            TracingHelper.logError(currentSpan, t);
            final Response response = CoapErrorResponse.newResponse(t, ResponseCode.INTERNAL_SERVER_ERROR);
            coapExchange.respond(response);
            return response.getCode();
        })
        .onSuccess(code -> {
            LOG.debug("finished processing of request [response code: {}]", code);
            CoapConstants.TAG_COAP_RESPONSE_CODE.set(currentSpan, code.toString());
        })
        .onComplete(r -> {
            currentSpan.finish();
        });
    }

    /**
     * Creates a CoAP context for an incoming POST request.
     * <p>
     * This default implementation always returns a future that is failed with a
     * {@link ServerErrorException} with status code {@value HttpURLConnection#HTTP_NOT_IMPLEMENTED}.
     *
     * @param coapExchange The CoAP exchange to process.
     * @param span The <em>OpenTracing</em> root span that is used to track the processing of the created context.
     * @return A future indicating the outcome of processing the request.
     *         The future will be succeeded with the created CoAP context,
     *         otherwise the future will be failed with a {@link org.eclipse.hono.client.ServiceInvocationException}.
     */
    protected Future<CoapContext> createCoapContextForPost(final CoapExchange coapExchange, final Span span) {
        return Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_NOT_IMPLEMENTED));
    }

    /**
     * Creates a CoAP context for an incoming PUT request.
     * <p>
     * This default implementation always returns a future that is failed with a
     * {@link ServerErrorException} with status code {@value HttpURLConnection#HTTP_NOT_IMPLEMENTED}.
     *
     * @param coapExchange The CoAP exchange to process.
     * @param span The <em>OpenTracing</em> root span that is used to track the processing of the created context.
     * @return A future indicating the outcome of processing the request.
     *         The future will be succeeded with the created CoAP context,
     *         otherwise the future will be failed with a {@link org.eclipse.hono.client.ServiceInvocationException}.
     */
    protected Future<CoapContext> createCoapContextForPut(final CoapExchange coapExchange, final Span span) {
        return Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_NOT_IMPLEMENTED));
    }

    /**
     * Applies the trace sampling priority configured for the tenant associated with the
     * given CoAP context to the given span.
     *
     * @param ctx The CoAP context.
     * @param span The OpenTracing span.
     * @return A succeeded future with the given CoAP context.
     */
    protected final Future<CoapContext> applyTraceSamplingPriority(final CoapContext ctx, final Span span) {
        return adapter.getTenantClient().get(ctx.getTenantId(), span.context())
                .map(tenantObject -> {
                    TracingHelper.setDeviceTags(span, tenantObject.getTenantId(), null, ctx.getAuthId());
                    TenantTraceSamplingHelper.applyTraceSamplingPriority(tenantObject, ctx.getAuthId(), span);
                    return ctx;
                })
                .recover(t -> {
                    // do not propagate error here
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
     *         The future will be succeeded if the request has been processed successfully
     *         and a CoAP response has been sent back to the client.
     *         Otherwise the future will be failed with a {@link org.eclipse.hono.client.ServiceInvocationException}.
     */
    protected Future<?> handlePost(final CoapContext coapContext) {
        return Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_NOT_IMPLEMENTED));
    }

    /**
     * Invoked for an incoming PUT request.
     * <p>
     * This default implementation sends a response back to the client
     * with response code {@link ResponseCode#NOT_IMPLEMENTED}.
     *
     * @param coapContext The CoAP context of the current request.
     * @return A future indicating the outcome of processing the request.
     *         The future will be succeeded if the request has been processed successfully
     *         and a CoAP response has been sent back to the client.
     *         Otherwise the future will be failed with a {@link org.eclipse.hono.client.ServiceInvocationException}.
     */
    protected Future<?> handlePut(final CoapContext coapContext) {
        return Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_NOT_IMPLEMENTED));
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
    public final void handleGET(final CoapExchange exchange) {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * {@inheritDoc}
     * <p>
     * Always throws {@link UnsupportedOperationException}.
     */
    @Override
    public final void handleIPATCH(final CoapExchange exchange) {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * {@inheritDoc}
     * <p>
     * Always throws {@link UnsupportedOperationException}.
     */
    @Override
    public final void handlePATCH(final CoapExchange exchange) {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * {@inheritDoc}
     * <p>
     * Always throws {@link UnsupportedOperationException}.
     */
    @Override
    public final void handlePOST(final CoapExchange exchange) {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * {@inheritDoc}
     * <p>
     * Always throws {@link UnsupportedOperationException}.
     */
    @Override
    public final void handlePUT(final CoapExchange exchange) {
        throw new UnsupportedOperationException("not implemented");
    }
}
