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


package org.eclipse.hono.tests.jms;

import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import javax.jms.JMSException;
import javax.jms.Message;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.RegistrationClient;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.client.StatusCodeMapper;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.RegistrationResult;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;


/**
 * A JmsBasedRegistrationClient.
 *
 */
public class JmsBasedRegistrationClient extends JmsBasedRequestResponseClient<RegistrationResult> implements RegistrationClient {

    private JmsBasedRegistrationClient(
            final JmsBasedHonoConnection connection,
            final ClientConfigProperties clientConfig,
            final String tenant) {

        super(connection, RegistrationConstants.REGISTRATION_ENDPOINT, tenant, clientConfig);
    }

    /**
     * Creates a new client for a connection.
     * 
     * @param connection The connection to the Device Registration service.
     * @param clientConfig The configuration properties for the connection to the
     *                     Device Registration service.
     * @param tenant The tenant to create the client for.
     * @return A future indicating the outcome of the operation.
     */
    public static Future<JmsBasedRegistrationClient> create(
            final JmsBasedHonoConnection connection,
            final ClientConfigProperties clientConfig,
            final String tenant) {

        try {
            final JmsBasedRegistrationClient client = new JmsBasedRegistrationClient(connection, clientConfig, tenant);
            client.createLinks();
            return Future.succeededFuture(client);
        } catch (JMSException e) {
            return Future.failedFuture(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<JsonObject> assertRegistration(final String deviceId) {

        return assertRegistration(deviceId, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<JsonObject> assertRegistration(final String deviceId, final String gatewayId) {

        Objects.requireNonNull(deviceId);

        final Map<String, Object> props = new HashMap<>();
        props.put(MessageHelper.APP_PROPERTY_DEVICE_ID, deviceId);
        if (gatewayId != null) {
            props.put(MessageHelper.APP_PROPERTY_GATEWAY_ID, gatewayId);
        }
        return sendRequest(RegistrationConstants.ACTION_ASSERT, props, null);
    }

    /**
     * Sends a request for an operation.
     * 
     * @param operation The name of the operation to invoke or {@code null} if the message
     *                  should not have a subject.
     * @param applicationProperties Application properties to set on the request message or
     *                              {@code null} if no properties should be set.
     * @param payload Payload to include or {@code null} if the message should have no body.
     * @return A future indicating the outcome of the operation.
     */
    public Future<JsonObject> sendRequest(
            final String operation,
            final Map<String, Object> applicationProperties,
            final Buffer payload) {

        try {
            final Message request = createMessage(payload);

            if  (operation != null) {
                request.setJMSType(operation);
            }

            if (applicationProperties != null) {
                for (Map.Entry<String, Object> entry : applicationProperties.entrySet()) {
                    if (entry.getValue() instanceof String) {
                        request.setStringProperty(entry.getKey(), (String) entry.getValue());
                    } else {
                        request.setObjectProperty(entry.getKey(), entry.getValue());
                    }
                }
            }

            return send(request)
                    .compose(registrationResult -> {
                        final Promise<JsonObject> result = Promise.promise();
                        switch (registrationResult.getStatus()) {
                        case HttpURLConnection.HTTP_OK:
                            result.complete(registrationResult.getPayload());
                            break;
                        case HttpURLConnection.HTTP_NOT_FOUND:
                            result.fail(new ClientErrorException(registrationResult.getStatus(), "no such device"));
                            break;
                        default:
                            result.fail(StatusCodeMapper.from(registrationResult));
                        }
                        return result.future();
                    });
        } catch (JMSException e) {
            return Future.failedFuture(getServiceInvocationException(e));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected RegistrationResult getResult(final int status, final Buffer payload, final CacheDirective cacheDirective) {

        if (payload == null) {
            return RegistrationResult.from(status);
        } else {
            try {
                final JsonObject json = payload.toJsonObject();
                return RegistrationResult.from(status, json, cacheDirective);
            } catch (DecodeException e) {
                LOGGER.warn("Device Registration service returned malformed payload", e);
                throw new ServiceInvocationException(
                        HttpURLConnection.HTTP_INTERNAL_ERROR,
                        "Device Registration service returned malformed payload");
            }
        }
    }
}
