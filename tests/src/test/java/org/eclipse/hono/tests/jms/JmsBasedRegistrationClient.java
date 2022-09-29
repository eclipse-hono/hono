/**
 * Copyright (c) 2019, 2022 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.tests.jms;

import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import javax.jms.JMSException;
import javax.jms.Message;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.registry.DeviceRegistrationClient;
import org.eclipse.hono.client.util.StatusCodeMapper;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RegistrationAssertion;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.RegistrationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.SpanContext;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;


/**
 * A JMS based client for the Device Registration service.
 *
 */
public class JmsBasedRegistrationClient extends JmsBasedRequestResponseServiceClient<JsonObject, RegistrationResult> implements DeviceRegistrationClient {

    private static final Logger LOG = LoggerFactory.getLogger(JmsBasedRegistrationClient.class);

    /**
     * Creates a new client.
     *
     * @param connection The JMS connection to use.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public JmsBasedRegistrationClient(final JmsBasedHonoConnection connection) {

        super(connection);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<RegistrationAssertion> assertRegistration(
            final String tenantId,
            final String deviceId,
            final String gatewayId,
            final SpanContext context) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);

        final Map<String, Object> applicationProperties = new HashMap<>();
        applicationProperties.put(MessageHelper.APP_PROPERTY_DEVICE_ID, deviceId);
        if (gatewayId != null) {
            applicationProperties.put(MessageHelper.APP_PROPERTY_GATEWAY_ID, gatewayId);
        }
        return sendRequest(
                tenantId,
                RegistrationConstants.ACTION_ASSERT,
                applicationProperties,
                null);
    }

    /**
     * Sends a request for an operation.
     *
     * @param tenantId The tenant to send the request for.
     * @param operation The name of the operation to invoke or {@code null} if the message
     *                  should not have a subject.
     * @param applicationProperties Application properties to set on the request message or
     *                              {@code null} if no properties should be set.
     * @param payload Payload to include or {@code null} if the message should have no body.
     * @return A future indicating the outcome of the operation.
     */
    public Future<RegistrationAssertion> sendRequest(
            final String tenantId,
            final String operation,
            final Map<String, Object> applicationProperties,
            final Buffer payload) {

        final Message request;

        try {
            request = createMessage(payload);
            addProperties(request, applicationProperties);
            if  (operation != null) {
                request.setJMSType(operation);
            }

        } catch (final JMSException e) {
            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
        }

        final Future<JmsBasedRequestResponseClient<RegistrationResult>> client = JmsBasedRequestResponseClient.forEndpoint(
                connection,
                RegistrationConstants.REGISTRATION_ENDPOINT,
                tenantId);

        return client
            .compose(c -> c.send(request, this::getResult))
            .compose(registrationResult -> {
                final Promise<RegistrationAssertion> result = Promise.promise();
                switch (registrationResult.getStatus()) {
                case HttpURLConnection.HTTP_OK:
                    try {
                        final var assertion = registrationResult.getPayload().mapTo(RegistrationAssertion.class);
                        result.complete(assertion);
                    } catch (final DecodeException e) {
                        result.fail(e);
                    }
                    break;
                case HttpURLConnection.HTTP_NOT_FOUND:
                    result.fail(new ClientErrorException(registrationResult.getStatus(), "no such device"));
                    break;
                default:
                    result.fail(StatusCodeMapper.from(registrationResult));
                }
                return result.future();
            });
    }

    private Future<RegistrationResult> getResult(final Message message) {

        return getPayload(message)
                .map(payload -> {
                    if (payload == null) {
                        return RegistrationResult.from(getStatus(message));
                    } else {
                        try {
                            final JsonObject json = payload.toJsonObject();
                            return RegistrationResult.from(getStatus(message), json, getCacheDirective(message));
                        } catch (DecodeException e) {
                            LOG.warn("Device Registration service returned malformed payload", e);
                            throw StatusCodeMapper.from(
                                    HttpURLConnection.HTTP_INTERNAL_ERROR,
                                    "Device Registration service returned malformed payload");
                        }
                    }
                });
    }
}
