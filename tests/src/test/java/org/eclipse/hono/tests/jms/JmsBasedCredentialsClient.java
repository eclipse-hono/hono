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
import java.util.Map;
import java.util.Objects;

import javax.jms.JMSException;
import javax.jms.Message;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.registry.CredentialsClient;
import org.eclipse.hono.client.util.StatusCodeMapper;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsConstants.CredentialsAction;
import org.eclipse.hono.util.CredentialsObject;
import org.eclipse.hono.util.CredentialsResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.SpanContext;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;


/**
 * A client for interacting with a Credentials service implementation.
 *
 */
public class JmsBasedCredentialsClient extends JmsBasedRequestResponseServiceClient<CredentialsObject, CredentialsResult<CredentialsObject>> implements CredentialsClient {

    private static final Logger LOG = LoggerFactory.getLogger(JmsBasedCredentialsClient.class);

    /**
     * Creates a new client.
     *
     * @param connection The JMS connection to use.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public JmsBasedCredentialsClient(final JmsBasedHonoConnection connection) {

        super(connection);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<CredentialsObject> get(
            final String tenantId,
            final String type,
            final String authId,
            final SpanContext context) {

        return get(tenantId, type, authId, new JsonObject(), context);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<CredentialsObject> get(
            final String tenantId,
            final String type,
            final String authId,
            final JsonObject clientContext,
            final SpanContext context) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(type);
        Objects.requireNonNull(authId);
        Objects.requireNonNull(clientContext);

        final JsonObject searchCriteria = CredentialsConstants.getSearchCriteria(type, authId);
        searchCriteria.mergeIn(clientContext);
        return sendRequest(tenantId, CredentialsAction.get.toString(), null, searchCriteria.toBuffer());
    }

    /**
     * Sends a request for an operation.
     *
     * @param tenantId The tenant to send the message for.
     * @param operation The name of the operation to invoke or {@code null} if the message
     *                  should not have a subject.
     * @param applicationProperties Application properties to set on the request message or
     *                              {@code null} if no properties should be set.
     * @param payload Payload to include or {@code null} if the message should have no body.
     * @return A future indicating the outcome of the operation.
     */
    public Future<CredentialsObject> sendRequest(
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

            if (applicationProperties != null) {
                for (Map.Entry<String, Object> entry : applicationProperties.entrySet()) {
                    if (entry.getValue() instanceof String) {
                        request.setStringProperty(entry.getKey(), (String) entry.getValue());
                    } else {
                        request.setObjectProperty(entry.getKey(), entry.getValue());
                    }
                }
            }
        } catch (final JMSException e) {
            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST, e));
        }

        final Future<JmsBasedRequestResponseClient<CredentialsResult<CredentialsObject>>> client = JmsBasedRequestResponseClient.forEndpoint(
                connection, CredentialsConstants.CREDENTIALS_ENDPOINT, tenantId);

        return client.compose(c -> c.send(request, this::getResult))
                .compose(credentialsResult -> {
                    final Promise<CredentialsObject> result = Promise.promise();
                    switch (credentialsResult.getStatus()) {
                    case HttpURLConnection.HTTP_OK:
                    case HttpURLConnection.HTTP_CREATED:
                        result.complete(credentialsResult.getPayload());
                        break;
                    case HttpURLConnection.HTTP_NOT_FOUND:
                        result.fail(new ClientErrorException(credentialsResult.getStatus(), "no such credentials"));
                        break;
                    default:
                        result.fail(StatusCodeMapper.from(credentialsResult));
                    }
                    return result.future();
                });
    }

    private Future<CredentialsResult<CredentialsObject>> getResult(final Message message) {

        return getPayload(message)
                .map(payload -> {
                    if (payload == null) {
                        return CredentialsResult.from(getStatus(message));
                    } else {
                        try {
                            final JsonObject json = payload.toJsonObject();
                            final CredentialsObject credentials = json.mapTo(CredentialsObject.class);
                            return CredentialsResult.from(getStatus(message), credentials, getCacheDirective(message));
                        } catch (DecodeException e) {
                            LOG.warn("Credentials service returned malformed payload", e);
                            throw StatusCodeMapper.from(
                                    HttpURLConnection.HTTP_INTERNAL_ERROR,
                                    "Credentials service returned malformed payload");
                        }
                    }
                });
    }
}
