/**
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 1.0 which is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * SPDX-License-Identifier: EPL-1.0
 */

package org.eclipse.hono.client.impl;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import io.vertx.core.AsyncResult;
import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.ProtonSender;
import org.eclipse.hono.client.StatusCodeMapper;
import org.eclipse.hono.client.TenantClient;
import org.eclipse.hono.config.ClientConfigProperties;

import org.eclipse.hono.util.TenantConstants;
import org.eclipse.hono.util.TenantObject;
import org.eclipse.hono.util.TenantResult;
import org.eclipse.hono.util.MessageHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.proton.ProtonConnection;

/**
 * A Vertx-Proton based client for Hono's Tenant API.
 *
 */
public class TenantClientImpl extends AbstractRequestResponseClient<TenantResult<TenantObject>>
        implements TenantClient {

    private static final Logger LOG = LoggerFactory.getLogger(TenantClientImpl.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();


    /**
     * Creates a tenant API client.
     *
     * @param context The Vert.x context to run message exchanges with the peer on.
     * @param config The configuration properties to use.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    protected TenantClientImpl(final Context context, final ClientConfigProperties config) {
        super(context, config, null);
    }

    /**
     * Creates a tenant API client.
     *
     * @param context The Vert.x context to run message exchanges with the peer on.
     * @param config The configuration properties to use.
     * @param sender The AMQP 1.0 link to use for sending requests to the peer.
     * @param receiver The AMQP 1.0 link to use for receiving responses from the peer.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    protected TenantClientImpl(final Context context, final ClientConfigProperties config,
                           final ProtonSender sender, final ProtonReceiver receiver) {

        super(context, config, null, sender, receiver);
    }

    @Override
    protected final String getName() {

        return TenantConstants.TENANT_ENDPOINT;
    }

    @Override
    protected final String createMessageId() {

        return String.format("%s-%s", TenantConstants.MESSAGE_ID_PREFIX, UUID.randomUUID());
    }

    @Override
    protected final TenantResult<TenantObject> getResult(final int status, final String payload) {
        try {
            if (status == HttpURLConnection.HTTP_OK) {
                return TenantResult.from(status, objectMapper.readValue(payload, TenantObject.class));
            } else {
                return TenantResult.from(status);
            }
        } catch (final IOException e) {
            return TenantResult.from(HttpURLConnection.HTTP_INTERNAL_ERROR);
        }
    }

    /**
     * Gets the AMQP <em>target</em> address to use for sending requests to Hono's Tenant API endpoint.
     *
     * @return The target address.
     * @throws NullPointerException if tenant is {@code null}.
     */
    public final static String getTargetAddress() {
        return TenantConstants.TENANT_ENDPOINT;
    }

    /**
     * Creates a new tenant API client.
     *
     * @param context The vert.x context to run all interactions with the server on.
     * @param clientConfig The configuration properties to use.
     * @param con The AMQP connection to the server.
     * @param senderCloseHook A handler to invoke if the peer closes the sender link unexpectedly.
     * @param receiverCloseHook A handler to invoke if the peer closes the receiver link unexpectedly.
     * @param creationHandler The handler to invoke with the outcome of the creation attempt.
     * @throws NullPointerException if any of the parameters, except for senderCloseHook and receiverCloseHook, is {@code null}.
     */
    public final static void create(
            final Context context,
            final ClientConfigProperties clientConfig,
            final ProtonConnection con,
            final Handler<String> senderCloseHook,
            final Handler<String> receiverCloseHook,
            final Handler<AsyncResult<TenantClient>> creationHandler) {

        LOG.debug("creating new tenant API client.");
        final TenantClientImpl client = new TenantClientImpl(context, clientConfig);
        client.createLinks(con, senderCloseHook, receiverCloseHook).setHandler(s -> {
            if (s.succeeded()) {
                LOG.debug("successfully created tenant API client");
                creationHandler.handle(Future.succeededFuture(client));
            } else {
                LOG.debug("failed to create tenant API client", s.cause());
                creationHandler.handle(Future.failedFuture(s.cause()));
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Future<TenantObject> get(final String tenantId) {

        final Future<TenantResult<TenantObject>> responseTracker = Future.future();
        createAndSendRequest(TenantConstants.StandardAction.ACTION_GET.toString(), createTenantProperties(tenantId), null,
                responseTracker.completer());
        return responseTracker.map(response -> {
            switch (response.getStatus()) {
            case HttpURLConnection.HTTP_OK:
                return response.getPayload();
            default:
                throw StatusCodeMapper.from(response);
            }
        });
    }

    private Map<String, Object> createTenantProperties(final String tenantId) {
        final Map<String, Object> properties = new HashMap<>();
        properties.put(MessageHelper.APP_PROPERTY_TENANT_ID, tenantId);
        return properties;
    }
}
