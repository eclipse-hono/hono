/*******************************************************************************
 * Copyright (c) 2020, 2022 Contributors to the Eclipse Foundation
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
import org.eclipse.hono.client.amqp.connection.AmqpUtils;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.service.amqp.AbstractDelegatingRequestResponseEndpoint;
import org.eclipse.hono.service.amqp.AbstractRequestResponseEndpoint;
import org.eclipse.hono.service.amqp.GenericRequestMessageFilter;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.CredentialsConstants;
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
 * It receives AMQP 1.0 messages representing requests and forwards them to the credential service implementation.
 * The outcome is then returned to the peer in a response message.
 *
 * @param <S> The type of service this endpoint delegates to.
 */
public class DelegatingCredentialsAmqpEndpoint<S extends CredentialsService> extends AbstractDelegatingRequestResponseEndpoint<S, ServiceConfigProperties> {

    private static final String SPAN_NAME_GET_CREDENTIALS = "get Credentials";

    /**
     * Creates a new credentials endpoint for a vertx instance.
     *
     * @param vertx The vert.x instance to use.
     * @param service The service to delegate to.
     * @throws NullPointerException if any of the parameters are {@code null};
     */
    public DelegatingCredentialsAmqpEndpoint(final Vertx vertx, final S service) {
        super(vertx, service);
    }

    @Override
    public final String getName() {
        return CredentialsConstants.CREDENTIALS_ENDPOINT;
    }

    @Override
    protected Future<Message> handleRequestMessage(final Message requestMessage, final ResourceIdentifier targetAddress,
            final SpanContext spanContext) {

        Objects.requireNonNull(requestMessage);
        Objects.requireNonNull(targetAddress);

        switch (CredentialsConstants.CredentialsAction.from(requestMessage.getSubject())) {
        case get:
            return processGetRequest(requestMessage, targetAddress, spanContext);
        default:
            return processCustomCredentialsMessage(requestMessage, spanContext);
        }
    }

    /**
     * Processes a <em>get Credentials</em> request message.
     *
     * @param request The request message.
     * @param targetAddress The address the message is sent to.
     * @param spanContext The span context representing the request to be processed.
     * @return The response to send to the client via the event bus.
     */
    protected Future<Message> processGetRequest(final Message request, final ResourceIdentifier targetAddress,
            final SpanContext spanContext) {

        final String tenantId = targetAddress.getTenantId();

        final Span span = TracingHelper.buildServerChildSpan(
                tracer,
                spanContext,
                SPAN_NAME_GET_CREDENTIALS,
                getClass().getSimpleName()
        ).start();

        final JsonObject payload;
        try {
            payload = AmqpUtils.getJsonPayload(request);
        } catch (final DecodeException e) {
            logger.debug("failed to decode AMQP request message", e);
            return finishSpanOnFutureCompletion(span, Future.failedFuture(
                    new ClientErrorException(
                            HttpURLConnection.HTTP_BAD_REQUEST,
                            "request message body contains malformed JSON")));
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

            TracingHelper.TAG_TENANT_ID.set(span, tenantId);

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
                logger.debug("get credentials request contains invalid search criteria [type: {}, device-id: {}, auth-id: {}]",
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

        logger.debug("getting credentials [tenant: {}, type: {}, auth-id: {}]", tenantId, type, authId);
        TracingHelper.TAG_CREDENTIALS_TYPE.set(span, type);
        TracingHelper.TAG_AUTH_ID.set(span, authId);

        return getService().get(tenantId, type, authId, payload, span)
                .map(res -> {
                    Optional.ofNullable(res.getPayload())
                            .map(p -> getTypesafeValueForField(String.class, p,
                                    CredentialsConstants.FIELD_PAYLOAD_DEVICE_ID))
                            .ifPresent(deviceIdFromPayload -> {
                                TracingHelper.TAG_DEVICE_ID.set(span, deviceIdFromPayload);
                            });
                    return AbstractRequestResponseEndpoint.getAmqpReply(
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
     * @param spanContext The span context representing the request to be processed.
     * @return A future indicating the outcome of the service invocation.
     */
    protected Future<Message> processCustomCredentialsMessage(final Message request, final SpanContext spanContext) {
        logger.debug("invalid operation in request message [{}]", request.getSubject());
        return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
    }

    @Override
    protected boolean passesFormalVerification(final ResourceIdentifier linkTarget, final Message msg) {
        return GenericRequestMessageFilter.isValidRequestMessage(msg);
    }
}
