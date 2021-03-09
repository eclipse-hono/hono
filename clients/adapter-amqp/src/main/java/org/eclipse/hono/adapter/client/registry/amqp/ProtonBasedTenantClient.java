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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import javax.security.auth.x500.X500Principal;

import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.eclipse.hono.adapter.client.registry.TenantClient;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.SendMessageSampler;
import org.eclipse.hono.client.StatusCodeMapper;
import org.eclipse.hono.client.amqp.AbstractRequestResponseServiceClient;
import org.eclipse.hono.client.amqp.RequestResponseClient;
import org.eclipse.hono.client.impl.CachingClientFactory;
import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.Pair;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.TenantConstants;
import org.eclipse.hono.util.TenantConstants.TenantAction;
import org.eclipse.hono.util.TenantObject;
import org.eclipse.hono.util.TenantResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.benmanes.caffeine.cache.Cache;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.tag.StringTag;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;


/**
 * A vertx-proton based client of Hono's Tenant service.
 *
 */
public final class ProtonBasedTenantClient extends AbstractRequestResponseServiceClient<TenantObject, TenantResult<TenantObject>> implements TenantClient {

    private static final Logger LOG = LoggerFactory.getLogger(ProtonBasedTenantClient.class);
    private static final StringTag TAG_SUBJECT_DN = new StringTag("subject_dn");
    private final Map<Object, Future<TenantResult<TenantObject>>> pendingRequests = new HashMap<>();

    /**
     * Creates a new client for a connection.
     *
     * @param connection The connection to the service.
     * @param samplerFactory The factory for creating samplers for tracing AMQP messages being sent.
     * @param responseCache The cache to use for service responses or {@code null} if responses should not be cached.
     * @throws NullPointerException if any of the parameters other than the response cache are {@code null}.
     */
    public ProtonBasedTenantClient(
            final HonoConnection connection,
            final SendMessageSampler.Factory samplerFactory,
            final Cache<Object, TenantResult<TenantObject>> responseCache) {
        super(connection,
                samplerFactory,
                new CachingClientFactory<>(connection.getVertx(), RequestResponseClient::isOpen),
                responseCache);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getKey(final String tenantId) {
        // there is one client for all tenant IDs only
        return TenantConstants.TENANT_ENDPOINT;
    }

    private Future<RequestResponseClient<TenantResult<TenantObject>>> getOrCreateClient() {

        return connection.isConnected(getDefaultConnectionCheckTimeout())
                .compose(v -> connection.executeOnContext(result -> {
                    clientFactory.getOrCreateClient(
                            TenantConstants.TENANT_ENDPOINT,
                            () -> {
                                return RequestResponseClient.forEndpoint(
                                        connection,
                                        TenantConstants.TENANT_ENDPOINT,
                                        null,
                                        samplerFactory.create(TenantConstants.TENANT_ENDPOINT),
                                        this::removeClient,
                                        this::removeClient);
                            },
                            result);
                }));
    }

    @Override
    protected TenantResult<TenantObject> getResult(
            final int status,
            final String contentType,
            final Buffer payload,
            final CacheDirective cacheDirective,
            final ApplicationProperties applicationProperties) {

        if (isSuccessResponse(status, contentType, payload)) {
            try {
                return TenantResult.from(
                        status,
                        Json.decodeValue(payload, TenantObject.class),
                        cacheDirective,
                        applicationProperties);
            } catch (final DecodeException e) {
                LOG.warn("received malformed payload from Tenant service", e);
                return TenantResult.from(HttpURLConnection.HTTP_INTERNAL_ERROR, null, null, applicationProperties);
            }
        } else {
            return TenantResult.from(status, null, null, applicationProperties);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<TenantObject> get(final String tenantId, final SpanContext parent) {

        Objects.requireNonNull(tenantId);

        final var responseCacheKey = Pair.of(TenantAction.get, tenantId);
        final Span span = newChildSpan(parent, "get Tenant by ID");
        span.setTag(MessageHelper.APP_PROPERTY_TENANT_ID, tenantId);
        return get(
                responseCacheKey,
                () -> new JsonObject().put(TenantConstants.FIELD_PAYLOAD_TENANT_ID, tenantId),
                span);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<TenantObject> get(final X500Principal subjectDn, final SpanContext parent) {

        Objects.requireNonNull(subjectDn);

        final String subjectDnRfc2253 = subjectDn.getName(X500Principal.RFC2253);
        final var responseCacheKey = Pair.of(TenantAction.get, subjectDn);
        final Span span = newChildSpan(parent, "get Tenant by subject DN");
        TAG_SUBJECT_DN.set(span, subjectDnRfc2253);
        return get(
                responseCacheKey,
                () -> new JsonObject().put(TenantConstants.FIELD_PAYLOAD_SUBJECT_DN, subjectDnRfc2253),
                span);
    }

    private Future<TenantObject> get(
            final Pair<TenantAction, ?> responseCacheKey,
            final Supplier<JsonObject> payloadSupplier,
            final Span currentSpan) {

        final Future<TenantResult<TenantObject>> resultTracker = getResponseFromCache(responseCacheKey, currentSpan)
                .recover(cacheMiss -> addResultHandler(
                        responseCacheKey,
                        () -> getOrCreateClient().compose(client -> client.createAndSendRequest(
                                    TenantAction.get.toString(),
                                    null,
                                    payloadSupplier.get().toBuffer(),
                                    RegistrationConstants.CONTENT_TYPE_APPLICATION_JSON,
                                    this::getRequestResponseResult,
                                    currentSpan))));
        return mapResultAndFinishSpan(resultTracker, tenantResult -> {
            switch (tenantResult.getStatus()) {
            case HttpURLConnection.HTTP_OK:
                return tenantResult.getPayload();
            case HttpURLConnection.HTTP_NOT_FOUND:
                throw new ClientErrorException(tenantResult.getStatus(), "no such tenant");
            default:
                throw StatusCodeMapper.from(tenantResult);
            }
        }, currentSpan);
    }

    private Future<TenantResult<TenantObject>> addResultHandler(
            final Object responseCacheKey,
            final Supplier<Future<TenantResult<TenantObject>>> serviceRequest) {

        final Future<TenantResult<TenantObject>> response = Optional.ofNullable(pendingRequests.get(responseCacheKey))
                .map(request -> {
                    // create a new result handler
                    final Promise<TenantResult<TenantObject>> resultHandler = Promise.promise();
                    // which will be notified once the the pending request completes
                    request.onComplete(resultHandler);
                    return resultHandler.future();
                })
                // execute the request
                .orElseGet(() -> serviceRequest.get()
                        // make sure to put the response to the cache (if applicable)
                        .onSuccess(tenantResult -> addToCache(responseCacheKey, tenantResult))
                        // and remove the most recent result handler so that
                        // a new request will be executed again
                        .onComplete(r -> pendingRequests.remove(responseCacheKey)));

        // keep track of the most recent result handler
        pendingRequests.put(responseCacheKey, response);
        return response;
    }
}
