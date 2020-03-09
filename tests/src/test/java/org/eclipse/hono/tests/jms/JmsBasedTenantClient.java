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

import javax.jms.BytesMessage;
import javax.jms.JMSException;
import javax.security.auth.x500.X500Principal;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.client.StatusCodeMapper;
import org.eclipse.hono.client.TenantClient;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.TenantConstants;
import org.eclipse.hono.util.TenantObject;
import org.eclipse.hono.util.TenantResult;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;


/**
 * A {@link TenantClient} implementation that uses the JMS API to interact
 * with a Tenant service.
 */
public class JmsBasedTenantClient extends JmsBasedRequestResponseClient<TenantResult<TenantObject>> implements TenantClient {


    private JmsBasedTenantClient(final JmsBasedHonoConnection connection, final ClientConfigProperties clientConfig) {

        super(connection, TenantConstants.TENANT_ENDPOINT, clientConfig);
    }

    /**
     * Creates a new client for a connection.
     * 
     * @param connection The connection to the Tenant service.
     * @param clientConfig The configuration properties for the connection to the
     *                     Tenant service.
     * @return A future indicating the outcome of the operation.
     */
    public static Future<JmsBasedTenantClient> create(final JmsBasedHonoConnection connection, final ClientConfigProperties clientConfig) {

        try {
            final JmsBasedTenantClient client = new JmsBasedTenantClient(connection, clientConfig);
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
    public Future<TenantObject> get(final String tenantId) {

        return get(new JsonObject().put(TenantConstants.FIELD_PAYLOAD_TENANT_ID, tenantId).toBuffer());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<TenantObject> get(final X500Principal subjectDn) {

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
     * Sends a request for retrieving tenant information using arbitrary
     * search criteria.
     * 
     * @param operation The name of the operation to invoke.
     * @param searchCriteria The search criteria.
     * @return A future indicating the outcome of the operation.
     */
    public Future<TenantObject> sendRequest(final String operation, final Buffer searchCriteria) {

        try {
            final BytesMessage request = createMessage(searchCriteria);
            if  (operation != null) {
                request.setJMSType(operation);
            }

            return send(request)
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
        } catch (JMSException e) {
            return Future.failedFuture(getServiceInvocationException(e));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected TenantResult<TenantObject> getResult(final int status, final Buffer payload, final CacheDirective cacheDirective) {

        if (payload == null) {
            return TenantResult.from(status);
        } else {
            try {
                final JsonObject json = payload.toJsonObject();
                final TenantObject tenant = json.mapTo(TenantObject.class);
                return TenantResult.from(status, tenant, cacheDirective);
            } catch (DecodeException e) {
                LOGGER.warn("Tenant service returned malformed payload", e);
                throw new ServiceInvocationException(
                        HttpURLConnection.HTTP_INTERNAL_ERROR,
                        "Tenant service returned malformed payload");
            }
        }
    }
}
