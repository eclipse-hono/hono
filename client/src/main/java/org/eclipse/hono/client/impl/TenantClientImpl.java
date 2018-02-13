/**
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 * <p>
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * <p>
 * Contributors:
 * Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.client.impl;

import static java.net.HttpURLConnection.HTTP_OK;
import static org.eclipse.hono.util.TenantConstants.ACTION_ADD;
import static org.eclipse.hono.util.TenantConstants.ACTION_GET;
import static org.eclipse.hono.util.TenantConstants.ACTION_UPDATE;


import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.eclipse.hono.client.TenantClient;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.TenantConstants;
import org.eclipse.hono.util.TenantResult;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.proton.ProtonConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Vertx-Proton based client for Hono's Tenant API.
 *
 */
public final class TenantClientImpl extends AbstractRequestResponseClient<TenantResult> implements TenantClient {

    private static final Logger LOG = LoggerFactory.getLogger(TenantClientImpl.class);

    private TenantClientImpl(final Context context, final ClientConfigProperties config) {
        super(context, config, "NO_TENANT");
    }

    @Override
    protected String getName() {

        return TenantConstants.TENANT_ENDPOINT;
    }

    @Override
    protected String createMessageId() {

        return String.format("tenant-client-%s", UUID.randomUUID());
    }

    @Override
    protected TenantResult getResult(final int status, final String payload) {
        if (status == HTTP_OK) {
            return TenantResult.from(status, new JsonObject(payload));
        } else {
            return TenantResult.from(status);
        }
    }

    /**
     * Creates a new tenant client.
     *
     * @param context                 The vert.x context to run all interactions with the server on.
     * @param con                     The AMQP connection to the server.
     *                                credits.
     * @param senderCloseHook         A handler to invoke if the peer closes the sender link
     *                                unexpectedly.
     * @param receiverCloseHook       A handler to invoke if the peer closes the receiver link
     *                                unexpectedly.
     * @param creationHandler         The handler to invoke with the outcome of the creation
     *                                attempt.
     * @throws NullPointerException     if any of the parameters is {@code null}.
     * @throws IllegalArgumentException if receiverPrefetchCredits is {@code < 0}.
     * @throws IllegalArgumentException if waitForInitialCredits is {@code < 1}.
     */
    public static void create(
            final Context context,
            final ClientConfigProperties clientConfig,
            final ProtonConnection con,
            final Handler<String> senderCloseHook,
            final Handler<String> receiverCloseHook,
            final Handler<AsyncResult<TenantClient>> creationHandler) {

        LOG.debug("creating new tenant client");
        final TenantClientImpl client = new TenantClientImpl(context, clientConfig);
        client.createLinks(con, senderCloseHook, receiverCloseHook)
                .setHandler(s -> {
                    if (s.succeeded()) {
                        LOG.debug("successfully created tenant client");
                        creationHandler.handle(Future.succeededFuture(client));
                    } else {
                        LOG.debug("failed to create tenant client", s.cause());
                        creationHandler.handle(Future.failedFuture(s.cause()));
                    }
                });


    }

    @Override
    public void add(final JsonObject data, final Handler<AsyncResult<TenantResult>> resultHandler) {
        Objects.requireNonNull(resultHandler);
        String tenantId = data.getString(TenantConstants.FIELD_TENANT_ID);
        createAndSendRequest(ACTION_ADD, createProperties(tenantId), data, resultHandler);
    }

    @Override
    public final void get(final String tenantId, final Handler<AsyncResult<TenantResult>> resultHandler) {
        final JsonObject specification = new JsonObject().put(TenantConstants.FIELD_TENANT_ID, tenantId);
        createAndSendRequest(ACTION_GET, createProperties(tenantId), specification, resultHandler);
    }

    @Override
    public void update(final String tenantId, final JsonObject data, final Handler<AsyncResult<TenantResult>> resultHandler) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(resultHandler);
        createAndSendRequest(ACTION_UPDATE, createProperties(tenantId), data, resultHandler);
    }

    private Map<String, Object> createProperties(final String tenantId) {
        final Map<String, Object> properties = new HashMap<>();
        properties.put(MessageHelper.APP_PROPERTY_TENANT_ID, tenantId);
        return properties;
    }

    /**
     * Gets the AMQP <em>target</em> address to use for sending requests to Hono's Device Registration API endpoint.
     *
     * @return The target address.
     * @throws NullPointerException if tenant is {@code null}.
     */
    public static String getTargetAddress() {
        return String.format("%s/%s", TenantConstants.TENANT_ENDPOINT, "NO_TENANT");
    }
}
