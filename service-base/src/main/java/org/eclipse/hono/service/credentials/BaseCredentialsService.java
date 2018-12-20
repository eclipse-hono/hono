/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.service.credentials;

import java.net.HttpURLConnection;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.service.EventBusService;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsResult;
import org.eclipse.hono.util.EventBusMessage;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.TenantConstants;

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
 * A base class for implementing {@link CredentialsService}s.
 * <p>
 * This base class provides support for receiving <em>Get</em> request messages
 * via vert.x' event bus and routing them to specific methods accepting the
 * query parameters contained in the request message.
 *
 * @param <T> The type of configuration class this service supports.
 */
public abstract class BaseCredentialsService<T> extends EventBusService<T> implements CredentialsService {

    private static final String SPAN_NAME_GET_CREDENTIALS = "get Credentials";

    private static final String TAG_AUTH_ID = "auth_id";
    private static final String TAG_CREDENTIALS_TYPE = "credentials_type";

    @Override
    protected String getEventBusAddress() {
        return CredentialsConstants.EVENT_BUS_ADDRESS_CREDENTIALS_IN;
    }

    /**
     * Processes a Credentials API request received via the vert.x event bus.
     * <p>
     * This method validates the request parameters against the Credentials API
     * specification before invoking the corresponding {@code CredentialsService} methods.
     *
     * @param request The request message.
     * @return A future indicating the outcome of the service invocation.
     * @throws NullPointerException If the request message is {@code null}.
     */
    @Override
    public Future<EventBusMessage> processRequest(final EventBusMessage request) {

        Objects.requireNonNull(request);

        final String operation = request.getOperation();

        switch (CredentialsConstants.CredentialsAction.from(operation)) {
            case get:
                return processGetRequest(request);
            default:
                return processCustomCredentialsMessage(request);
        }
    }

    /**
     * Processes a <em>get Credentials</em> request message.
     * 
     * @param request The request message.
     * @return The response to send to the client via the event bus.
     */
    protected Future<EventBusMessage> processGetRequest(final EventBusMessage request) {

        final String tenantId = request.getTenant();
        final JsonObject payload = request.getJsonPayload();

        final Span span = newChildSpan(SPAN_NAME_GET_CREDENTIALS, request.getSpanContext(), tenantId);
        final Future<EventBusMessage> resultFuture;
        if (tenantId == null || payload == null) {
            TracingHelper.logError(span, "missing tenant and/or payload");
            resultFuture = Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
        } else {
            final String type = removeTypesafeValueForField(String.class, payload, CredentialsConstants.FIELD_TYPE);
            final String authId = removeTypesafeValueForField(String.class, payload,
                    CredentialsConstants.FIELD_AUTH_ID);
            final String deviceId = removeTypesafeValueForField(String.class, payload,
                    CredentialsConstants.FIELD_PAYLOAD_DEVICE_ID);

            if (type == null) {
                TracingHelper.logError(span, "missing type");
                resultFuture = Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
            } else if (authId != null && deviceId == null) {
                resultFuture = processGetByAuthIdRequest(request, tenantId, payload, type, authId, span);
            } else if (deviceId != null && authId == null) {
                resultFuture = processGetByDeviceIdRequest(request, tenantId, type, deviceId, span);
            } else {
                TracingHelper.logError(span, String.format(
                        "invalid search criteria [type: %s, device-id: %s, auth-id: %s]", type, deviceId, authId));
                log.debug("get credentials request contains invalid search criteria [type: {}, device-id: {}, auth-id: {}]",
                        type, deviceId, authId);
                resultFuture = Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
            }
        }
        return finishSpanOnFutureCompletion(span, resultFuture);
    }

    private Future<EventBusMessage> processGetByAuthIdRequest(final EventBusMessage request, final String tenantId,
            final JsonObject payload, final String type, final String authId, final Span span) {
        log.debug("getting credentials [tenant: {}, type: {}, auth-id: {}]", tenantId, type, authId);
        span.setTag(TAG_CREDENTIALS_TYPE, type);
        span.setTag(TAG_AUTH_ID, authId);
        final Future<CredentialsResult<JsonObject>> result = Future.future();
        get(tenantId, type, authId, payload, span, result.completer());
        return result.map(res -> {
            final String deviceIdFromPayload = Optional.ofNullable(res.getPayload())
                    .map(p -> getTypesafeValueForField(String.class, p,
                            TenantConstants.FIELD_PAYLOAD_DEVICE_ID))
                    .orElse(null);
            if (deviceIdFromPayload != null) {
                span.setTag(MessageHelper.APP_PROPERTY_DEVICE_ID, deviceIdFromPayload);
            }
            return request.getResponse(res.getStatus())
                    .setDeviceId(deviceIdFromPayload)
                    .setJsonPayload(res.getPayload())
                    .setCacheDirective(res.getCacheDirective());
        });
    }

    private Future<EventBusMessage> processGetByDeviceIdRequest(final EventBusMessage request, final String tenantId,
            final String type, final String deviceId, final Span span) {
        log.debug("getting credentials for device [tenant: {}, device-id: {}]", tenantId, deviceId);
        span.setTag(TAG_CREDENTIALS_TYPE, type);
        span.setTag(MessageHelper.APP_PROPERTY_DEVICE_ID, deviceId);
        final Future<CredentialsResult<JsonObject>> result = Future.future();
        getAll(tenantId, deviceId, span, result.completer());
        return result.map(res -> {
            return request.getResponse(res.getStatus())
                    .setDeviceId(deviceId)
                    .setJsonPayload(res.getPayload())
                    .setCacheDirective(res.getCacheDirective());
        });
    }

    /**
     * Creates a new <em>OpenTracing</em> span for tracing the execution of a credentials service operation.
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
     * operations that are not defined by Hono's Credentials API.
     * <p>
     * This default implementation simply returns a future that is failed with a
     * {@link ClientErrorException} with an error code <em>400 Bad Request</em>.
     *
     * @param request The request to process.
     * @return A future indicating the outcome of the service invocation.
     */
    protected Future<EventBusMessage> processCustomCredentialsMessage(final EventBusMessage request) {
        log.debug("invalid operation in request message [{}]", request.getOperation());
        return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
    }

    @Override
    public final void get(final String tenantId, final String type, final String authId,
                    final Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {
        get(tenantId, type, authId, NoopSpan.INSTANCE, resultHandler);
    }

    /**
     * {@inheritDoc}
     *
     * This default implementation simply returns an empty result with status code 501 (Not Implemented).
     * Subclasses should override this method in order to provide a reasonable implementation.
     */
    @Override
    public void get(final String tenantId, final String type, final String authId, final Span span,
            final Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {
        handleUnimplementedOperation(resultHandler);
    }

    @Override
    public final void get(final String tenantId, final String type, final String authId, final JsonObject clientContext,
                    final Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {
        get(tenantId, type, authId, clientContext, NoopSpan.INSTANCE, resultHandler);
    }

    /**
     * {@inheritDoc}
     *
     * This default implementation simply returns an empty result with status code 501 (Not Implemented).
     * Subclasses should override this method in order to provide a reasonable implementation.
     */
    @Override
    public void get(final String tenantId, final String type, final String authId, final JsonObject clientContext,
            final Span span, final Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {
        handleUnimplementedOperation(resultHandler);
    }

    /**
     * Gets all credentials on record for a device.
     * 
     * @param tenantId The tenant the device belongs to.
     * @param deviceId The ID of the device.
     * @param span The active OpenTracing span for this operation. It is not to be closed in this method!
     *            An implementation should log (error) events on this span and it may set tags and use this span as the
     *            parent for any spans created in this method.
     * @param resultHandler The handler to invoke with the result of the operation.
     */
    public abstract void getAll(String tenantId, String deviceId, Span span,
            Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler);

    /**
     * Handles an unimplemented operation by failing the given handler
     * with a {@link ClientErrorException} having a <em>501 Not Implemented</em> status code.
     *
     * @param resultHandler The handler.
     */
    protected void handleUnimplementedOperation(final Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {
        resultHandler.handle(Future.succeededFuture(CredentialsResult.from(HttpURLConnection.HTTP_NOT_IMPLEMENTED)));
    }
}
