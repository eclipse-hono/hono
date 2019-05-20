/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.service.tenant;

import java.net.HttpURLConnection;
import java.util.Objects;

import javax.security.auth.x500.X500Principal;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.service.EventBusService;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.EventBusMessage;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.TenantConstants;
import org.eclipse.hono.util.TenantResult;

import io.opentracing.References;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.noop.NoopSpan;
import io.opentracing.tag.Tags;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;

/**
 * a Base class for implementing {@link TenantService}.
 * <p>
 * In particular, this base class provides support for receiving service invocation request messages
 * via vert.x' event bus and routing them to specific methods accepting the
 * query parameters contained in the request message.
 *
 * @param <T> The type of configuration properties this service requires.
 */
public abstract class BaseTenantService<T> extends EventBusService<T> implements TenantService {

    private static final String SPAN_NAME_GET_TENANT = "get Tenant";

    private static final String TAG_SUBJECT_DN_NAME = "subject_dn_name";

    @Override
    protected final String getEventBusAddress() {
        return TenantConstants.EVENT_BUS_ADDRESS_TENANT_IN;
    }

    /**
     * Processes a Tenant API request message received via the vert.x event bus.
     * <p>
     * This method validates the request payload against the Tenant API specification
     * before invoking the corresponding {@code TenantService} methods.
     * 
     * @param request The request message.
     * @return A future indicating the outcome of the service invocation.
     * @throws NullPointerException If the request message is {@code null}.
     */
    @Override
    public Future<EventBusMessage> processRequest(final EventBusMessage request) {

        Objects.requireNonNull(request);

        switch (TenantConstants.TenantAction.from(request.getOperation())) {
        case get:
            return processGetRequest(request);
        default:
            return processCustomTenantMessage(request);
        }
    }

    Future<EventBusMessage> processGetRequest(final EventBusMessage request) {

        final String tenantId = request.getTenant();
        final JsonObject payload = request.getJsonPayload();

        final Span span = newChildSpan(SPAN_NAME_GET_TENANT, request.getSpanContext(), tenantId);
        final Future<EventBusMessage> resultFuture;
        if (tenantId == null && payload == null) {
            TracingHelper.logError(span, "request does not contain any query parameters");
            log.debug("request does not contain any query parameters");
            resultFuture = Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));

        } else if (tenantId != null) {

            // deprecated API
            log.debug("retrieving tenant [{}] using deprecated variant of get tenant request", tenantId);
            span.log("using deprecated variant of get tenant request");
            // span will be finished in processGetByIdRequest
            resultFuture = processGetByIdRequest(request, tenantId, span);

        } else {

            final String tenantIdFromPayload = getTypesafeValueForField(String.class, payload,
                    TenantConstants.FIELD_PAYLOAD_TENANT_ID);
            final String subjectDn = getTypesafeValueForField(String.class, payload,
                    TenantConstants.FIELD_PAYLOAD_SUBJECT_DN);

            if (tenantIdFromPayload == null && subjectDn == null) {
                TracingHelper.logError(span, "request does not contain any query parameters");
                log.debug("payload does not contain any query parameters");
                resultFuture = Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
            } else if (tenantIdFromPayload != null) {
                log.debug("retrieving tenant [id: {}]", tenantIdFromPayload);
                span.setTag(MessageHelper.APP_PROPERTY_TENANT_ID, tenantIdFromPayload);
                resultFuture = processGetByIdRequest(request, tenantIdFromPayload, span);
            } else {
                span.setTag(TAG_SUBJECT_DN_NAME, subjectDn);
                resultFuture = processGetByCaRequest(request, subjectDn, span);
            }
        }
        return finishSpanOnFutureCompletion(span, resultFuture);
    }

    private Future<EventBusMessage> processGetByIdRequest(final EventBusMessage request, final String tenantId,
            final Span span) {

        final Future<TenantResult<JsonObject>> getResult = Future.future();
        get(tenantId, span, getResult);
        return getResult.map(tr -> {
            return request.getResponse(tr.getStatus())
                    .setJsonPayload(tr.getPayload())
                    .setTenant(tenantId)
                    .setCacheDirective(tr.getCacheDirective());
        });
    }

    private Future<EventBusMessage> processGetByCaRequest(final EventBusMessage request, final String subjectDn,
            final Span span) {

        try {
            final X500Principal dn = new X500Principal(subjectDn);
            log.debug("retrieving tenant [subject DN: {}]", subjectDn);
            final Future<TenantResult<JsonObject>> getResult = Future.future();
            get(dn, span, getResult);
            return getResult.map(tr -> {
                final EventBusMessage response = request.getResponse(tr.getStatus())
                        .setJsonPayload(tr.getPayload())
                        .setCacheDirective(tr.getCacheDirective());
                if (tr.isOk() && tr.getPayload() != null) {
                    final String tenantId = getTypesafeValueForField(String.class, tr.getPayload(),
                            TenantConstants.FIELD_PAYLOAD_TENANT_ID);
                    span.setTag(MessageHelper.APP_PROPERTY_TENANT_ID, tenantId);
                    response.setTenant(tenantId);
                }
                return response;
            });
        } catch (final IllegalArgumentException e) {
            TracingHelper.logError(span, "illegal subject DN provided by client: " + subjectDn);
            // the given subject DN is invalid
            log.debug("cannot parse subject DN [{}] provided by client", subjectDn);
            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
        }
    }

    /**
     * Creates a new <em>OpenTracing</em> span for tracing the execution of a tenant service operation.
     * <p>
     * The returned span will already contain a tag for the given tenant (if it is not {code null}).
     *
     * @param operationName The operation name that the span should be created for.
     * @param spanContext Existing span context.
     * @param tenantId The tenant id.
     * @return The new {@code Span}.
     * @throws NullPointerException if operationName is {@code null}.
     */
    protected final Span newChildSpan(final String operationName, final SpanContext spanContext, final String tenantId) {
        Objects.requireNonNull(operationName);
        // we set the component tag to the class name because we have no access to
        // the name of the enclosing component we are running in
        final Tracer.SpanBuilder spanBuilder = tracer.buildSpan(operationName)
                .addReference(References.CHILD_OF, spanContext)
                .ignoreActiveSpan()
                .withTag(Tags.COMPONENT.getKey(), getClass().getSimpleName())
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER);
        if (tenantId != null) {
            spanBuilder.withTag(MessageHelper.APP_PROPERTY_TENANT_ID, tenantId);
        }
        return spanBuilder.start();
    }

    /**
     * Processes a request for a non-standard operation.
     * <p>
     * Subclasses should override this method in order to support additional, custom
     * operations that are not defined by Hono's Tenant API.
     * <p>
     * This default implementation simply returns a future that is failed with a
     * {@link ClientErrorException} with an error code <em>400 Bad Request</em>.
     *
     * @param request The request to process.
     * @return A future indicating the outcome of the service invocation.
     */
    protected Future<EventBusMessage> processCustomTenantMessage(final EventBusMessage request) {
        log.debug("invalid operation in request message [{}]", request.getOperation());
        return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
    }

    @Override
    public final void get(final String tenantId, final Handler<AsyncResult<TenantResult<JsonObject>>> resultHandler) {
        get(tenantId, NoopSpan.INSTANCE, resultHandler);
    }

    /**
     * {@inheritDoc}
     *
     * This default implementation simply returns an empty result with status code 501 (Not Implemented).
     * Subclasses should override this method in order to provide a reasonable implementation.
     */
    @Override
    public void get(final String tenantId, final Span span,
            final Handler<AsyncResult<TenantResult<JsonObject>>> resultHandler) {
        handleUnimplementedOperation(resultHandler);
    }

    @Override
    public final void get(final X500Principal subjectDn,
            final Handler<AsyncResult<TenantResult<JsonObject>>> resultHandler) {
        get(subjectDn, NoopSpan.INSTANCE, resultHandler);
    }

    /**
     * {@inheritDoc}
     *
     * This default implementation simply returns an empty result with status code 501 (Not Implemented).
     * Subclasses should override this method in order to provide a reasonable implementation.
     */
    @Override
    public void get(final X500Principal subjectDn, final Span span,
            final Handler<AsyncResult<TenantResult<JsonObject>>> resultHandler) {
        handleUnimplementedOperation(resultHandler);
    }

    /**
     * Handles an unimplemented operation by failing the given handler
     * with a {@link ClientErrorException} having a <em>501 Not Implemented</em> status code.
     *
     * @param resultHandler The handler.
     */
    protected void handleUnimplementedOperation(final Handler<AsyncResult<TenantResult<JsonObject>>> resultHandler) {
        resultHandler.handle(Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_NOT_IMPLEMENTED)));
    }
}
