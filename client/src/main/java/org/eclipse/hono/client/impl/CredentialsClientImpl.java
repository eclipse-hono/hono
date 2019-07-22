/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client.impl;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Objects;
import java.util.UUID;

import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.ProtonSender;
import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.eclipse.hono.cache.CacheProvider;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.CredentialsClient;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.StatusCodeMapper;
import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsObject;
import org.eclipse.hono.util.CredentialsResult;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RequestResponseApiConstants;
import org.eclipse.hono.util.TriTuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;

/**
 * A Vertx-Proton based client for Hono's Credentials API.
 *
 */
public class CredentialsClientImpl extends AbstractRequestResponseClient<CredentialsResult<CredentialsObject>> implements CredentialsClient {

    private static Logger LOG = LoggerFactory.getLogger(CredentialsClientImpl.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String TAG_AUTH_ID = "auth_id";
    private static final String TAG_CREDENTIALS_TYPE = "credentials_type";

    /**
     * Creates a new client for accessing the Credentials service.
     * <p>
     * The client will be ready to use after invoking {@link #createLinks()} or
     * {@link #createLinks(Handler, Handler)} only.
     * 
     * @param connection The connection to Hono.
     * @param tenantId The identifier of the tenant for which the client should be created.
     */
    CredentialsClientImpl(final HonoConnection connection, final String tenantId) {
        super(connection, tenantId);
    }

    /**
     * Creates a new client for accessing the Credentials service.
     *
     * @param connection The connection to Hono.
     * @param tenantId The identifier of the tenant for which the client should be created.
     * @param sender The AMQP link to use for sending requests to the service.
     * @param receiver The AMQP link to use for receiving responses from the service.
     */
    CredentialsClientImpl(final HonoConnection connection, final String tenantId, final ProtonSender sender,
            final ProtonReceiver receiver) {
        super(connection, tenantId, sender, receiver);
    }

    @Override
    protected final String getName() {

        return CredentialsConstants.CREDENTIALS_ENDPOINT;
    }

    @Override
    protected final String createMessageId() {

        return String.format("cred-client-%s", UUID.randomUUID());
    }

    @Override
    protected final CredentialsResult<CredentialsObject> getResult(
            final int status,
            final String contentType,
            final Buffer payload,
            final CacheDirective cacheDirective,
            final ApplicationProperties applicationProperties) {

        if (isSuccessResponse(status, contentType, payload)) {
            try {
                return CredentialsResult.from(
                        status,
                        OBJECT_MAPPER.readValue(payload.getBytes(), CredentialsObject.class),
                        cacheDirective,
                        applicationProperties);
            } catch (final IOException e) {
                LOG.warn("received malformed payload from Credentials service", e);
                return CredentialsResult.from(HttpURLConnection.HTTP_INTERNAL_ERROR, null, null, applicationProperties);
            }
        } else {
            return CredentialsResult.from(status, null, null, applicationProperties);
        }
    }

    /**
     * Gets the AMQP <em>target</em> address to use for sending requests to Hono's Credentials API endpoint.
     * 
     * @param tenantId The tenant to upload data for.
     * @return The target address.
     * @throws NullPointerException if tenant is {@code null}.
     */
    public static final String getTargetAddress(final String tenantId) {
        return String.format("%s/%s", CredentialsConstants.CREDENTIALS_ENDPOINT, Objects.requireNonNull(tenantId));
    }

    /**
     * Creates a new credentials client for a tenant.
     *
     * @param cacheProvider The cache provider to use for creating caches for credential objects
     *                      or {@code null} if credential objects should not be cached.
     * @param con The connection to the server.
     * @param tenantId The tenant for which credentials are handled.
     * @param senderCloseHook A handler to invoke if the peer closes the sender link unexpectedly.
     * @param receiverCloseHook A handler to invoke if the peer closes the receiver link unexpectedly.
     * @return A future indicating the outcome of the creation attempt.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public static final Future<CredentialsClient> create(
            final CacheProvider cacheProvider,
            final HonoConnection con,
            final String tenantId,
            final Handler<String> senderCloseHook,
            final Handler<String> receiverCloseHook) {

        LOG.debug("creating new credentials client for [{}]", tenantId);
        final CredentialsClientImpl client = new CredentialsClientImpl(con, tenantId);
        if (cacheProvider != null) {
            client.setResponseCache(cacheProvider.getCache(CredentialsClientImpl.getTargetAddress(tenantId)));
        }
        return client.createLinks(senderCloseHook, receiverCloseHook)
                .map(ok -> {
                    LOG.debug("successfully created credentials client for [{}]", tenantId);
                    return (CredentialsClient) client;
                }).recover(t -> {
                    LOG.debug("failed to create credentials client for [{}]", tenantId, t);
                    return Future.failedFuture(t);
                });
    }

    /**
     * Invokes the <em>Get Credentials</em> operation of Hono's
     * <a href="https://www.eclipse.org/hono/docs/api/credentials-api/">Credentials API</a>
     * on the service represented by the <em>sender</em> and <em>receiver</em> links.
     */
    @Override
    public Future<CredentialsObject> get(final String type, final String authId) {
        return get(type, authId, new JsonObject());
    }

    /**
     * Invokes the <em>Get Credentials</em> operation of Hono's
     * <a href="https://www.eclipse.org/hono/docs/api/credentials-api/">Credentials API</a>
     * on the service represented by the <em>sender</em> and <em>receiver</em> links.
     */
    @Override
    public Future<CredentialsObject> get(final String type, final String authId, final JsonObject clientContext) {
        return get(type, authId, clientContext, null);
    }

    /**
     * Invokes the <em>Get Credentials</em> operation of Hono's
     * <a href="https://www.eclipse.org/hono/docs/api/credentials-api/">Credentials API</a>
     * on the service represented by the <em>sender</em> and <em>receiver</em> links.
     */
    @Override
    public final Future<CredentialsObject> get(final String type, final String authId, final JsonObject clientContext,
            final SpanContext spanContext) {

        Objects.requireNonNull(type);
        Objects.requireNonNull(authId);
        Objects.requireNonNull(clientContext);

        final Future<CredentialsResult<CredentialsObject>> responseTracker = Future.future();
        final JsonObject specification = CredentialsConstants
                .getSearchCriteria(type, authId)
                .mergeIn(clientContext);
        final TriTuple<CredentialsConstants.CredentialsAction, String, Integer> key = TriTuple
                .of(CredentialsConstants.CredentialsAction.get, String.format("%s-%s", type, authId), clientContext.hashCode());

        final Span span = newChildSpan(spanContext, "get Credentials");
        span.setTag(MessageHelper.APP_PROPERTY_TENANT_ID, getTenantId());
        span.setTag(TAG_CREDENTIALS_TYPE, type);
        span.setTag(TAG_AUTH_ID, authId);

        final Future<CredentialsResult<CredentialsObject>> resultTracker = getResponseFromCache(key, span)
                .recover(cacheMiss -> {
                    createAndSendRequest(
                            CredentialsConstants.CredentialsAction.get.toString(),
                            null,
                            specification.toBuffer(),
                            RequestResponseApiConstants.CONTENT_TYPE_APPLICATION_JSON,
                            responseTracker,
                            key,
                            span);
                    return responseTracker;
                });
        return mapResultAndFinishSpan(resultTracker, result -> {
            switch (result.getStatus()) {
            case HttpURLConnection.HTTP_OK:
                return result.getPayload();
            case HttpURLConnection.HTTP_NOT_FOUND:
                throw new ClientErrorException(result.getStatus(), "no such credentials");
            default:
                throw StatusCodeMapper.from(result);
            }
        }, span);
    }
}
