/**
 * Copyright (c) 2019, 2023 Contributors to the Eclipse Foundation
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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.coap.Response;
import org.eclipse.californium.core.network.Exchange;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.californium.core.server.resources.Resource;
import org.eclipse.californium.elements.EndpointContext;
import org.eclipse.californium.elements.auth.ExtensiblePrincipal;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.service.auth.DeviceUser;
import org.eclipse.hono.tracing.TenantTraceSamplingHelper;
import org.eclipse.hono.tracing.TracingHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;
import io.vertx.core.Future;


/**
 * A CoAP resource that supports the tracking of request processing using <em>OpenTracing</em>.
 * <p>
 * This resource supports processing of {@code POST} and {@code PUT} requests only.
 */
public abstract class TracingSupportingHonoResource extends CoapResource {

    private static final Logger LOG = LoggerFactory.getLogger(TracingSupportingHonoResource.class);
    private static final String MSG_NOT_IMPLEMENTED = "not implemented";

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
    protected TracingSupportingHonoResource(
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
     * @return The authenticated device or {@code null} if the request has not been authenticated.
     */
    protected static DeviceUser getAuthenticatedDevice(final CoapExchange exchange) {

        return Optional.ofNullable(exchange.advanced().getRequest().getSourceContext())
                .map(EndpointContext::getPeerIdentity)
                .filter(ExtensiblePrincipal.class::isInstance)
                .map(ExtensiblePrincipal.class::cast)
                .map(ExtensiblePrincipal::getExtendedInfo)
                .map(info -> info.get(DeviceInfoSupplier.EXT_INFO_KEY_HONO_DEVICE, DeviceUser.class))
                .orElse(null);
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
        return extPrincipal.getExtendedInfo().get(DeviceInfoSupplier.EXT_INFO_KEY_HONO_AUTH_ID, String.class);
    }

    private Span newSpan(final Exchange exchange) {
        return TracingHelper.buildServerChildSpan(
                tracer,
                null,
                getSpanName(exchange),
                adapter.getTypeName())
            .withTag(Tags.HTTP_METHOD, exchange.getRequest().getCode().name())
            .withTag(Tags.HTTP_URL.getKey(), exchange.getRequest().getOptions().getUriString())
            .withTag(CoapConstants.TAG_COAP_MESSAGE_TYPE, exchange.getRequest().getType().name())
            .start();
    }

    /**
     * Gets the name to use for the span to be created for tracing a given request.
     *
     * @param exchange The CoAP exchange representing the request from a client.
     * @return The name.
     */
    protected abstract String getSpanName(Exchange exchange);

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
     */
    @Override
    public void handleRequest(final Exchange exchange) {

        final Span currentSpan = newSpan(exchange);
        final AtomicReference<ResponseCode> responseCode = new AtomicReference<>(null);
        final CoapExchange coapExchange = new CoapExchange(exchange) {
            @Override
            public void respond(final Response response) {
                super.respond(response);
                responseCode.set(response.getCode());
            }
        };

        final Future<Void> result;
        switch (exchange.getRequest().getCode()) {
        case POST:
            result = createCoapContextForPost(coapExchange, currentSpan)
                .compose(coapContext -> applyTraceSamplingPriority(coapContext, currentSpan))
                .compose(this::handlePostRequest);
            break;
        case PUT:
            result = createCoapContextForPut(coapExchange, currentSpan)
                .compose(coapContext -> applyTraceSamplingPriority(coapContext, currentSpan))
                .compose(this::handlePutRequest);
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
    protected Future<Void> handlePostRequest(final CoapContext coapContext) {
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
    protected Future<Void> handlePutRequest(final CoapContext coapContext) {
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
        throw new UnsupportedOperationException(MSG_NOT_IMPLEMENTED);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Always throws {@link UnsupportedOperationException}.
     */
    @Override
    public final void handleFETCH(final CoapExchange exchange) {
        throw new UnsupportedOperationException(MSG_NOT_IMPLEMENTED);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Always throws {@link UnsupportedOperationException}.
     */
    @Override
    public final void handleGET(final CoapExchange exchange) {
        throw new UnsupportedOperationException(MSG_NOT_IMPLEMENTED);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Always throws {@link UnsupportedOperationException}.
     */
    @Override
    public final void handleIPATCH(final CoapExchange exchange) {
        throw new UnsupportedOperationException(MSG_NOT_IMPLEMENTED);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Always throws {@link UnsupportedOperationException}.
     */
    @Override
    public final void handlePATCH(final CoapExchange exchange) {
        throw new UnsupportedOperationException(MSG_NOT_IMPLEMENTED);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Always throws {@link UnsupportedOperationException}.
     */
    @Override
    public final void handlePOST(final CoapExchange exchange) {
        throw new UnsupportedOperationException(MSG_NOT_IMPLEMENTED);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Always throws {@link UnsupportedOperationException}.
     */
    @Override
    public final void handlePUT(final CoapExchange exchange) {
        throw new UnsupportedOperationException(MSG_NOT_IMPLEMENTED);
    }
}
