/*******************************************************************************
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.service.amqp.AbstractRequestResponseEndpoint;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.ResourceIdentifier;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;

/**
 * An {@code AmqpEndpoint} for managing device credential information.
 * <p>
 * This endpoint implements Hono's <a href="https://www.eclipse.org/hono/docs/api/credentials/">Credentials API</a>.
 * It receives AMQP 1.0 messages representing requests and forward them to the credential service implementation.
 * The outcome is then returned to the peer in a response message.
 */
public abstract class AbstractCredentialsAmqpEndpoint extends AbstractRequestResponseEndpoint<ServiceConfigProperties> {

    private static final String SPAN_NAME_GET_CREDENTIALS = "get Credentials";

    /**
     * Creates a new credentials endpoint for a vertx instance.
     *
     * @param vertx The vertx instance to use.
     */
    public AbstractCredentialsAmqpEndpoint(final Vertx vertx) {
        super(vertx);
    }

    @Override
    public final String getName() {
        return CredentialsConstants.CREDENTIALS_ENDPOINT;
    }

    /**
     * The service to forward requests to.
     *
     * @return The service to bind to, must never return {@code null}.
     */
    protected abstract CredentialsService getService();


    @Override
    protected Future<Message> handleRequestMessage(final Message requestMessage, final ResourceIdentifier targetAddress) {

        Objects.requireNonNull(requestMessage);

        switch (CredentialsConstants.CredentialsAction.from(requestMessage.getSubject())) {
        case get:
            return processGetRequest(requestMessage, targetAddress);
        default:
            return processCustomCredentialsMessage(requestMessage);
        }
    }

    /**
     * Processes a <em>get Credentials</em> request message.
     *
     * @param request The request message.
     * @param targetAddress The address the message is sent to.
     * @return The response to send to the client via the event bus.
     */
    protected Future<Message> processGetRequest(final Message request, final ResourceIdentifier targetAddress) {

        final String tenantId = targetAddress.getTenantId();
        final SpanContext spanContext = TracingHelper.extractSpanContext(tracer, request);
        final Span span = newChildSpan(SPAN_NAME_GET_CREDENTIALS, spanContext, tenantId);

        JsonObject payload = null;
        try {
            payload = MessageHelper.getJsonPayload(request);
        } catch (DecodeException e) {
            logger.debug("failed to decode AMQP request message", e);
            return Future.failedFuture(
                    new ClientErrorException(
                            HttpURLConnection.HTTP_BAD_REQUEST,
                            "request message body contains malformed JSON"));
        }
        final Future<Message> resultFuture;

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
    protected Future<Message> processGetByDeviceIdRequest(final Message request, final String tenantId,
            final String type, final String deviceId, final Span span) {
        return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_NOT_IMPLEMENTED));
    }

    private Future<Message> processGetByAuthIdRequest(final Message request, final String tenantId,
            final JsonObject payload, final String type, final String authId, final Span span) {

        log.debug("getting credentials [tenant: {}, type: {}, auth-id: {}]", tenantId, type, authId);
        TracingHelper.TAG_CREDENTIALS_TYPE.set(span, type);
        TracingHelper.TAG_AUTH_ID.set(span, authId);

        return getService().get(tenantId, type, authId, payload, span)
                .map(res -> {
                    final String deviceIdFromPayload = Optional.ofNullable(res.getPayload())
                            .map(p -> getTypesafeValueForField(String.class, p,
                                    CredentialsConstants.FIELD_PAYLOAD_DEVICE_ID))
                            .orElse(null);
                    if (deviceIdFromPayload != null) {
                        span.setTag(MessageHelper.APP_PROPERTY_DEVICE_ID, deviceIdFromPayload);
                    }
                    return CredentialsConstants.getAmqpReply(
                            CredentialsConstants.CREDENTIALS_ENDPOINT,
                            tenantId,
                            request,
                            res);
                });
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
    protected Future<Message> processCustomCredentialsMessage(final Message request) {
        log.debug("invalid operation in request message [{}]", request.getSubject());
        return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
    }

    @Override
    protected boolean passesFormalVerification(final ResourceIdentifier linkTarget, final Message msg) {
        return CredentialsMessageFilter.verify(linkTarget, msg);
    }
}
