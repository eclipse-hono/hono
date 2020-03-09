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
import java.util.Map;
import java.util.Objects;

import javax.jms.JMSException;
import javax.jms.Message;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.CredentialsClient;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.client.StatusCodeMapper;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsConstants.CredentialsAction;
import org.eclipse.hono.util.CredentialsObject;
import org.eclipse.hono.util.CredentialsResult;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;


/**
 * A client for interacting with a Credentials service implementation.
 *
 */
public class JmsBasedCredentialsClient extends JmsBasedRequestResponseClient<CredentialsResult<CredentialsObject>> implements CredentialsClient {

    private JmsBasedCredentialsClient(
            final JmsBasedHonoConnection connection,
            final ClientConfigProperties clientConfig,
            final String tenant) {

        super(connection, CredentialsConstants.CREDENTIALS_ENDPOINT, tenant, clientConfig);
    }

    /**
     * Creates a new client for a connection.
     * 
     * @param connection The connection to the Credentials service.
     * @param clientConfig The configuration properties for the connection to the
     *                     Credentials service.
     * @param tenant The tenant to create the client for.
     * @return A future indicating the outcome of the operation.
     */
    public static Future<JmsBasedCredentialsClient> create(
            final JmsBasedHonoConnection connection,
            final ClientConfigProperties clientConfig,
            final String tenant) {

        try {
            final JmsBasedCredentialsClient client = new JmsBasedCredentialsClient(connection, clientConfig, tenant);
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
    public Future<CredentialsObject> get(final String type, final String authId) {

        return get(type, authId, new JsonObject());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<CredentialsObject> get(final String type, final String authId, final JsonObject clientContext) {

        Objects.requireNonNull(type);
        Objects.requireNonNull(authId);
        Objects.requireNonNull(clientContext);

        final JsonObject searchCriteria = CredentialsConstants.getSearchCriteria(type, authId);
        searchCriteria.mergeIn(clientContext);
        return sendRequest(CredentialsAction.get.toString(), null, searchCriteria.toBuffer());
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
    public Future<CredentialsObject> sendRequest(
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
        } catch (JMSException e) {
            return Future.failedFuture(getServiceInvocationException(e));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected CredentialsResult<CredentialsObject> getResult(final int status, final Buffer payload, final CacheDirective cacheDirective) {

        if (payload == null) {
            return CredentialsResult.from(status);
        } else {
            try {
                final JsonObject json = payload.toJsonObject();
                final CredentialsObject credentials = json.mapTo(CredentialsObject.class);
                return CredentialsResult.from(status, credentials, cacheDirective);
            } catch (DecodeException e) {
                LOGGER.warn("Credentials service returned malformed payload", e);
                throw new ServiceInvocationException(
                        HttpURLConnection.HTTP_INTERNAL_ERROR,
                        "Credentials service returned malformed payload");
            }
        }
    }
}
