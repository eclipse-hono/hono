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

import javax.jms.BytesMessage;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.security.auth.x500.X500Principal;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.registry.TenantClient;
import org.eclipse.hono.client.util.StatusCodeMapper;
import org.eclipse.hono.util.TenantConstants;
import org.eclipse.hono.util.TenantObject;
import org.eclipse.hono.util.TenantResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.SpanContext;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;


/**
 * A {@link TenantClient} implementation that uses the JMS API to interact
 * with a Tenant service.
 */
public class JmsBasedTenantClient extends JmsBasedRequestResponseServiceClient<TenantObject, TenantResult<TenantObject>> implements TenantClient {

    private static final Logger LOG = LoggerFactory.getLogger(JmsBasedTenantClient.class);

    /**
     * Creates a new client.
     *
     * @param connection The JMS connection to use.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public JmsBasedTenantClient(final JmsBasedHonoConnection connection) {

        super(connection);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<TenantObject> get(final String tenantId, final SpanContext context) {

        return get(new JsonObject().put(TenantConstants.FIELD_PAYLOAD_TENANT_ID, tenantId).toBuffer());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<TenantObject> get(final X500Principal subjectDn, final SpanContext context) {

        final String subjectDnRfc2253 = subjectDn.getName(X500Principal.RFC2253);
        return get(new JsonObject().put(TenantConstants.FIELD_PAYLOAD_SUBJECT_DN, subjectDnRfc2253).toBuffer());
    }

    /**
     * Sends a request for retrieving tenant information using arbitrary
     * search criteria.
     *
     * @param searchCriteria The search criteria.
     * @return A future indicating the outcome of the operation.
     */
    public Future<TenantObject> get(final Buffer searchCriteria) {
        return sendRequest(TenantConstants.TenantAction.get.name(), searchCriteria);
    }

    /**
     * Sends a request for an operation.
     *
     * @param operation The name of the operation to invoke or {@code null} if the message
     *                  should not have a subject.
     * @param searchCriteria The search criteria to include in the body.
     * @return A future indicating the outcome of the operation.
     */
    public Future<TenantObject> sendRequest(final String operation, final Buffer searchCriteria) {

        final BytesMessage request;
        try {
            request = createMessage(searchCriteria);
            if  (operation != null) {
                request.setJMSType(operation);
            }
        } catch (JMSException e) {
            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
        }

        final Future<JmsBasedRequestResponseClient<TenantResult<TenantObject>>> client = JmsBasedRequestResponseClient.forEndpoint(
                connection, TenantConstants.TENANT_ENDPOINT, null);

        return client.compose(c -> c.send(request, this::getResult))
                .compose(tenantResult -> {
                    final Promise<TenantObject> result = Promise.promise();
                    switch (tenantResult.getStatus()) {
                    case HttpURLConnection.HTTP_OK:
                        result.complete(tenantResult.getPayload());
                        break;
                    case HttpURLConnection.HTTP_NOT_FOUND:
                        result.fail(new ClientErrorException(tenantResult.getStatus(), "no such tenant"));
                        break;
                    default:
                        result.fail(StatusCodeMapper.from(tenantResult));
                    }
                    return result.future();
                });
    }

    private Future<TenantResult<TenantObject>> getResult(final Message message) {

        return getPayload(message)
            .map(payload -> {
                if (payload == null) {
                    return TenantResult.from(getStatus(message));
                } else {
                    try {
                        final JsonObject json = payload.toJsonObject();
                        final TenantObject tenant = json.mapTo(TenantObject.class);
                        return TenantResult.from(getStatus(message), tenant, getCacheDirective(message));
                    } catch (DecodeException e) {
                        LOG.warn("Tenant service returned malformed payload", e);
                        throw StatusCodeMapper.from(
                                HttpURLConnection.HTTP_INTERNAL_ERROR,
                                "Tenant service returned malformed payload");
                    }
                }
            });
    }
}
