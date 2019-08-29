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
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

import javax.security.auth.x500.X500Principal;

import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.eclipse.hono.cache.CacheProvider;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.StatusCodeMapper;
import org.eclipse.hono.client.TenantClient;
import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.TenantConstants;
import org.eclipse.hono.util.TenantConstants.TenantAction;
import org.eclipse.hono.util.TenantObject;
import org.eclipse.hono.util.TenantResult;
import org.eclipse.hono.util.TriTuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.tag.StringTag;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.ProtonSender;

/**
 * A Vertx-Proton based client for Hono's Tenant API.
 *
 */
public class TenantClientImpl extends AbstractRequestResponseClient<TenantResult<TenantObject>>
        implements TenantClient {

    private static final Logger LOG = LoggerFactory.getLogger(TenantClientImpl.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final StringTag TAG_SUBJECT_DN = new StringTag("subject_dn");

    /**
     * Creates a client for invoking operations of the Tenant API.
     * <p>
     * The client will be ready to use after invoking {@link #createLinks()} or
     * {@link #createLinks(Handler, Handler)} only.
     *
     * @param connection The connection to Hono.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    protected TenantClientImpl(final HonoConnection connection) {
        super(connection, null);
    }

    /**
     * Creates a client for invoking operations of the Tenant API.
     *
     * @param connection The connection to Hono.
     * @param sender The AMQP 1.0 link to use for sending requests to the peer.
     * @param receiver The AMQP 1.0 link to use for receiving responses from the peer.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    protected TenantClientImpl(
            final HonoConnection connection,
            final ProtonSender sender,
            final ProtonReceiver receiver) {

        super(connection, null, sender, receiver);
    }

    @Override
    protected final String getName() {

        return TenantConstants.TENANT_ENDPOINT;
    }

    @Override
    protected final String createMessageId() {

        return String.format("tenant-client-%s", UUID.randomUUID());
    }

    @Override
    protected final TenantResult<TenantObject> getResult(
            final int status,
            final String contentType,
            final Buffer payload,
            final CacheDirective cacheDirective,
            final ApplicationProperties applicationProperties) {

        if (isSuccessResponse(status, contentType, payload)) {
            try {
                return TenantResult.from(
                        status,
                        OBJECT_MAPPER.readValue(payload.getBytes(), TenantObject.class),
                        cacheDirective,
                        applicationProperties);
            } catch (final IOException e) {
                LOG.warn("received malformed payload from Tenant service", e);
                return TenantResult.from(HttpURLConnection.HTTP_INTERNAL_ERROR, null, null, applicationProperties);
            }
        } else {
            return TenantResult.from(status, null, null, applicationProperties);
        }
    }

    /**
     * Gets the AMQP <em>target</em> address to use for sending requests to Hono's Tenant API endpoint.
     *
     * @return The target address.
     */
    public static final String getTargetAddress() {
        return TenantConstants.TENANT_ENDPOINT;
    }

    /**
     * Creates a new tenant client.
     *
     * @param cacheProvider A factory for cache instances for tenant configuration results. If {@code null}
     *                     the client will not cache any results from the Tenant service.
     * @param con The connection to the server.
     * @param senderCloseHook A handler to invoke if the peer closes the sender link unexpectedly.
     * @param receiverCloseHook A handler to invoke if the peer closes the receiver link unexpectedly.
     * @return A future indicating the outcome of the creation attempt.
     * @throws NullPointerException if any of the parameters, except for senderCloseHook and receiverCloseHook are {@code null}.
     */
    public static final Future<TenantClient> create(
            final CacheProvider cacheProvider,
            final HonoConnection con,
            final Handler<String> senderCloseHook,
            final Handler<String> receiverCloseHook) {

        LOG.debug("creating new tenant client");
        final TenantClientImpl client = new TenantClientImpl(con);
        if (cacheProvider != null) {
            client.setResponseCache(cacheProvider.getCache(TenantClientImpl.getTargetAddress()));
        }
        return client.createLinks(senderCloseHook, receiverCloseHook)
            .map(ok -> {
                LOG.debug("successfully created tenant client");
                return (TenantClient) client;
            }).recover(t -> {
                LOG.debug("failed to create tenant client", t);
                return Future.failedFuture(t);
            });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Future<TenantObject> get(final String tenantId) {
        return get(tenantId, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Future<TenantObject> get(final String tenantId, final SpanContext parent) {

        Objects.requireNonNull(tenantId);

        final TriTuple<TenantAction, String, Object> key = TriTuple.of(TenantAction.get, tenantId, null);
        final Span span = newChildSpan(parent, "get Tenant by ID");
        span.setTag(MessageHelper.APP_PROPERTY_TENANT_ID, tenantId);
        return get(
                key,
                () -> new JsonObject().put(TenantConstants.FIELD_PAYLOAD_TENANT_ID, tenantId),
                span);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public final Future<TenantObject> get(final X500Principal subjectDn) {
        return get(subjectDn, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Future<TenantObject> get(final X500Principal subjectDn, final SpanContext parent) {

        Objects.requireNonNull(subjectDn);

        final String subjectDnRfc2253 = subjectDn.getName(X500Principal.RFC2253);
        final TriTuple<TenantAction, X500Principal, Object> key = TriTuple.of(TenantAction.get, subjectDn, null);
        final Span span = newChildSpan(parent, "get Tenant by subject DN");
        TAG_SUBJECT_DN.set(span, subjectDnRfc2253);
        return get(
                key,
                () -> new JsonObject().put(TenantConstants.FIELD_PAYLOAD_SUBJECT_DN, subjectDnRfc2253),
                span);
    }

    private <T> Future<TenantObject> get(
            final TriTuple<TenantAction, T, Object> key,
            final Supplier<JsonObject> payloadSupplier,
            final Span currentSpan) {

        final Future<TenantResult<TenantObject>> resultTracker = getResponseFromCache(key, currentSpan)
                .recover(cacheMiss -> {
                    final Future<TenantResult<TenantObject>> tenantResult = Future.future();
                    createAndSendRequest(
                            TenantAction.get.toString(),
                            customizeRequestApplicationProperties(),
                            payloadSupplier.get().toBuffer(),
                            RegistrationConstants.CONTENT_TYPE_APPLICATION_JSON,
                            tenantResult,
                            key,
                            currentSpan);
                    return tenantResult;
                });
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

    /**
     * Customize AMQP application properties of the request by overwriting this method.
     * @return The map that holds the properties to include in the AMQP 1.0 message, or null (if nothing is customized).
     */
    protected Map<String, Object> customizeRequestApplicationProperties() {
        return null;
    }
}
