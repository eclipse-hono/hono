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

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;
import io.vertx.core.Future;
import io.vertx.core.Verticle;
import io.vertx.core.json.JsonObject;

/**
 * Adapter to bind {@link CredentialsService} to the vertx event bus.
 * <p>
 * This base class provides support for receiving <em>Get</em> request messages via vert.x' event bus and routing them
 * to specific methods accepting the query parameters contained in the request message.
 */
public abstract class EventBusCredentialsAdapter extends EventBusService implements Verticle {

    private static final String SPAN_NAME_GET_CREDENTIALS = "get Credentials";

    /**
     * The service to forward requests to.
     * 
     * @return The service to bind to, must never return {@code null}.
     */
    protected abstract CredentialsService getService();

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

    /**
     * Processes a <em>get Credentials by Device ID</em> request message.
     * 
     * @param request The request message.
     * @param tenantId The tenant id.
     * @param type The secret type.
     * @param deviceId The device Id.
     * @param span The tracing span.
     * @return The response to send to the client via the event bus.
     */
    protected Future<EventBusMessage> processGetByDeviceIdRequest(final EventBusMessage request, final String tenantId,
            final String type, final String deviceId, final Span span) {
        return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_NOT_IMPLEMENTED));
    }

    private Future<EventBusMessage> processGetByAuthIdRequest(final EventBusMessage request, final String tenantId,
            final JsonObject payload, final String type, final String authId, final Span span) {
        log.debug("getting credentials [tenant: {}, type: {}, auth-id: {}]", tenantId, type, authId);
        TracingHelper.TAG_CREDENTIALS_TYPE.set(span, type);
        TracingHelper.TAG_AUTH_ID.set(span, authId);
        final Future<CredentialsResult<JsonObject>> result = Future.future();
        getService().get(tenantId, type, authId, payload, span, result);
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

    /**
     * Creates a new <em>OpenTracing</em> span for tracing the execution of a credentials service operation.
     * <p>
     * The returned span will already contain a tag for the given tenant (if it is not {@code null}).
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
        final Tracer.SpanBuilder spanBuilder = TracingHelper.buildChildSpan(tracer, spanContext, operationName)
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

}
