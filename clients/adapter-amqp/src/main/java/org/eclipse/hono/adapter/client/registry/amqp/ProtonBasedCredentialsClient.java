/**
 * Copyright (c) 2020, 2021 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.adapter.client.registry.amqp;

import java.net.HttpURLConnection;
import java.util.Objects;

import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.eclipse.hono.adapter.client.registry.CredentialsClient;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.SendMessageSampler;
import org.eclipse.hono.client.StatusCodeMapper;
import org.eclipse.hono.client.amqp.AbstractRequestResponseServiceClient;
import org.eclipse.hono.client.amqp.RequestResponseClient;
import org.eclipse.hono.client.impl.CachingClientFactory;
import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsObject;
import org.eclipse.hono.util.CredentialsResult;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RequestResponseApiConstants;
import org.eclipse.hono.util.TriTuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.benmanes.caffeine.cache.Cache;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;


/**
 * A vertx-proton based client of Hono's Credentials service.
 *
 */
public class ProtonBasedCredentialsClient extends AbstractRequestResponseServiceClient<CredentialsObject, CredentialsResult<CredentialsObject>> implements CredentialsClient {

    private static final Logger LOG = LoggerFactory.getLogger(ProtonBasedCredentialsClient.class);
    private static final String TAG_AUTH_ID = "auth_id";
    private static final String TAG_CREDENTIALS_TYPE = "credentials_type";

    /**
     * Creates a new client for a connection.
     *
     * @param connection The connection to the Credentials service.
     * @param samplerFactory The factory for creating samplers for tracing AMQP messages being sent.
     * @param responseCache The cache to use for service responses or {@code null} if responses should not be cached.
     * @throws NullPointerException if any of the parameters other than the response cache are {@code null}.
     */
    public ProtonBasedCredentialsClient(
            final HonoConnection connection,
            final SendMessageSampler.Factory samplerFactory,
            final Cache<Object, CredentialsResult<CredentialsObject>> responseCache) {

        super(connection,
                samplerFactory,
                new CachingClientFactory<>(connection.getVertx(), RequestResponseClient::isOpen),
                responseCache);
        connection.getVertx().eventBus().consumer(
                Constants.EVENT_BUS_ADDRESS_TENANT_TIMED_OUT,
                this::handleTenantTimeout);
    }

    @Override
    protected String getKey(final String tenantId) {
        return String.format("%s-%s", CredentialsConstants.CREDENTIALS_ENDPOINT, tenantId);
    }

    private Future<RequestResponseClient<CredentialsResult<CredentialsObject>>> getOrCreateClient(final String tenantId) {

        return connection.isConnected(getDefaultConnectionCheckTimeout())
                .compose(v -> connection.executeOnContext(result -> {
                    clientFactory.getOrCreateClient(
                            getKey(tenantId),
                            () -> {
                                return RequestResponseClient.forEndpoint(
                                        connection,
                                        CredentialsConstants.CREDENTIALS_ENDPOINT,
                                        tenantId,
                                        samplerFactory.create(CredentialsConstants.CREDENTIALS_ENDPOINT),
                                        this::removeClient,
                                        this::removeClient);
                            },
                            result);
                }));
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
                        Json.decodeValue(payload, CredentialsObject.class),
                        cacheDirective,
                        applicationProperties);
            } catch (final DecodeException e) {
                LOG.warn("received malformed payload from Credentials service", e);
                return CredentialsResult.from(HttpURLConnection.HTTP_INTERNAL_ERROR, null, null, applicationProperties);
            }
        } else {
            return CredentialsResult.from(status, null, null, applicationProperties);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<CredentialsObject> get(
            final String tenantId,
            final String type,
            final String authId,
            final SpanContext spanContext) {

        return get(tenantId, type, authId, new JsonObject(), spanContext);
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
            final SpanContext spanContext) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(type);
        Objects.requireNonNull(authId);
        Objects.requireNonNull(clientContext);

        final var responseCacheKey = TriTuple.of(
                CredentialsConstants.CredentialsAction.get,
                String.format("%s-%s-%s", tenantId, type, authId),
                clientContext.hashCode());

        final Span span = newChildSpan(spanContext, "get Credentials");
        span.setTag(MessageHelper.APP_PROPERTY_TENANT_ID, tenantId);
        span.setTag(TAG_CREDENTIALS_TYPE, type);
        span.setTag(TAG_AUTH_ID, authId);

        final Future<CredentialsResult<CredentialsObject>> resultTracker = getResponseFromCache(responseCacheKey, span)
                .recover(cacheMiss -> getOrCreateClient(tenantId)
                        .compose(client -> {
                            final JsonObject specification = CredentialsConstants
                                    .getSearchCriteria(type, authId)
                                    .mergeIn(clientContext);
                            return client.createAndSendRequest(
                                    CredentialsConstants.CredentialsAction.get.toString(),
                                    null,
                                    specification.toBuffer(),
                                    RequestResponseApiConstants.CONTENT_TYPE_APPLICATION_JSON,
                                    this::getRequestResponseResult,
                                    span);
                        })
                        .map(credentialsResult -> {
                            addToCache(responseCacheKey, credentialsResult);
                            return credentialsResult;
                        }));

        return mapResultAndFinishSpan(resultTracker, result -> {
            switch (result.getStatus()) {
            case HttpURLConnection.HTTP_OK:
            case HttpURLConnection.HTTP_CREATED:
                return result.getPayload();
            case HttpURLConnection.HTTP_NOT_FOUND:
                throw new ClientErrorException(result.getStatus(), "no such credentials");
            default:
                throw StatusCodeMapper.from(result);
            }
        }, span);
    }
}
