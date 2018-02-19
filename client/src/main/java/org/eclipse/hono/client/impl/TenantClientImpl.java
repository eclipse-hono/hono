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
import java.util.Objects;
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
public final class TenantClientImpl extends AbstractRequestResponseClient<TenantResult<TenantObject>>
        implements TenantClient {

    private static final Logger LOG = LoggerFactory.getLogger(TenantClientImpl.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private TenantClientImpl(final Context context, final ClientConfigProperties config, final String tenantId) {
        super(context, config, tenantId);
    }

    TenantClientImpl(final Context context, final ClientConfigProperties config, final String tenantId,
                           final ProtonSender sender, final ProtonReceiver receiver) {

        super(context, config, tenantId, sender, receiver);
    }

    @Override
    protected String getName() {

        return TenantConstants.TENANT_ENDPOINT;
    }

    @Override
    protected String createMessageId() {

        return String.format("%s-%s", TenantConstants.MESSAGE_ID_PREFIX, UUID.randomUUID());
    }

    @Override
    protected TenantResult<TenantObject> getResult(final int status, final String payload) {
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
     * @param tenantId The tenant to upload data for.
     * @return The target address.
     * @throws NullPointerException if tenant is {@code null}.
     */
    public static String getTargetAddress(final String tenantId) {
        return String.format("%s/%s", TenantConstants.TENANT_ENDPOINT, Objects.requireNonNull(tenantId));
    }

    /**
     * Creates a new tenant API client for a tenant.
     *
     * @param context The vert.x context to run all interactions with the server on.
     * @param clientConfig The configuration properties to use.
     * @param con The AMQP connection to the server.
     * @param tenantId The tenant for which the API is accessed.
     * @param senderCloseHook A handler to invoke if the peer closes the sender link unexpectedly.
     * @param receiverCloseHook A handler to invoke if the peer closes the receiver link unexpectedly.
     * @param creationHandler The handler to invoke with the outcome of the creation attempt.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public static void create(
            final Context context,
            final ClientConfigProperties clientConfig,
            final ProtonConnection con,
            final String tenantId,
            final Handler<String> senderCloseHook,
            final Handler<String> receiverCloseHook,
            final Handler<AsyncResult<TenantClient>> creationHandler) {

        LOG.debug("creating new tenant API client for [{}]", tenantId);
        final TenantClientImpl client = new TenantClientImpl(context, clientConfig, tenantId);
        client.createLinks(con, senderCloseHook, receiverCloseHook).setHandler(s -> {
            if (s.succeeded()) {
                LOG.debug("successfully created tenant API client for [{}]", tenantId);
                creationHandler.handle(Future.succeededFuture(client));
            } else {
                LOG.debug("failed to create tenant API client for [{}]", tenantId, s.cause());
                creationHandler.handle(Future.failedFuture(s.cause()));
            }
        });
    }

    @Override
    public Future<TenantObject> get() {

        final Future<TenantResult<TenantObject>> responseTracker = Future.future();
        createAndSendRequest(TenantConstants.StandardAction.ACTION_GET.toString(), createTenantProperties(), null,
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

    private Map<String, Object> createTenantProperties() {
        final Map<String, Object> properties = new HashMap<>();
        properties.put(MessageHelper.APP_PROPERTY_TENANT_ID, getTenantId());
        return properties;
    }
}
