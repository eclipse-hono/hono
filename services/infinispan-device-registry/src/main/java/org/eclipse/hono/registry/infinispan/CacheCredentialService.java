/*******************************************************************************
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.registry.infinispan;

import io.opentracing.Span;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.eclipse.hono.auth.HonoPasswordEncoder;
import org.eclipse.hono.service.credentials.CompleteBaseCredentialsService;
import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsObject;
import org.eclipse.hono.util.CredentialsResult;

import org.infinispan.client.hotrod.Flag;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.Search;
import org.infinispan.query.dsl.Query;
import org.infinispan.query.dsl.QueryFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A credentials service that use an Infinispan as a backend service.
 * Infinispan is an open source project providing a distributed in-memory key/value data store
 *
 * <p>
 *@see <a href="https://infinspan.org">https://infinspan.org</a>
 *
 */
@Repository
@Primary
public class CacheCredentialService extends CompleteBaseCredentialsService<CacheCredentialConfigProperties> {

    private final RemoteCache<CredentialsKey, RegistryCredentialObject> credentialsCache;

    /**
     * Creates a new service instance for a password encoder.
     *
     * @param pwdEncoder The encoder to use for hashing clear text passwords.
     * @throws NullPointerException if encoder is {@code null}.
     */
    @Autowired
    protected CacheCredentialService(final RemoteCache cache, final HonoPasswordEncoder pwdEncoder) {
        super(pwdEncoder);
        this.credentialsCache = cache;
    }

    @Override
    public void setConfig(final CacheCredentialConfigProperties configuration) {
    }

    @Override
    public void add(final String tenantId, final JsonObject credentialsJson, final Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {
        final CredentialsObject credentials = Optional.ofNullable(credentialsJson)
                .map(json -> json.mapTo(CredentialsObject.class)).orElse(null);

        final CredentialsKey key = new CredentialsKey(tenantId, credentials.getAuthId(), credentials.getType());
        final RegistryCredentialObject registryCredential = new RegistryCredentialObject(credentials, tenantId, credentialsJson);

        credentialsCache.withFlags(Flag.FORCE_RETURN_VALUE).putIfAbsentAsync(key, registryCredential).thenAccept(result -> {
            if (result == null) {
                log.debug("Created credentials [tenant-id: {}, auth-id: {}, type: {}]", tenantId, credentials.getAuthId(), credentials.getType());
                resultHandler.handle(Future.succeededFuture(CredentialsResult.from(HttpURLConnection.HTTP_CREATED)));
            } else {
                log.debug("Conflict, cannot create credentials [tenant-id: {}, auth-id: {}, type: {}]", tenantId, credentials.getAuthId(), credentials.getType());
                resultHandler.handle(Future.succeededFuture(CredentialsResult.from(HttpURLConnection.HTTP_CONFLICT)));
            }
        });
    }

    /**
     * {@inheritDoc}
     * <p>
     * The result object will include a <em>no-cache</em> directive.
     */
    @Override
    public void get(final String tenantId, final String type, final String authId, final Span span,
            final Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {
        get(tenantId, type, authId, null, span, resultHandler);
    }

    /**
     * {@inheritDoc}
     * <p>
     * The result object will include a <em>no-cache</em> directive.
     */
    @Override
    public void get(
            final String tenantId,
            final String type,
            final String authId,
            final JsonObject clientContext,
            final Span span,
            final Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(type);
        Objects.requireNonNull(authId);
        Objects.requireNonNull(resultHandler);

        final CredentialsKey key = new CredentialsKey(tenantId, authId, type);

        credentialsCache.getAsync(key).thenAccept(credential -> {
            if (credential == null) {
                log.debug("Credential not found [tenant-id: {}, auth-id: {}, type: {}]", tenantId, authId, type);
                resultHandler.handle(Future.succeededFuture(CredentialsResult.from(HttpURLConnection.HTTP_NOT_FOUND)));
            } else if (clientContext != null && !clientContext.isEmpty()) {
                if (contextMatches(clientContext, new JsonObject(credential.getOriginalJson()))) {
                    log.debug("Retrieve credential, context matches [tenant-id: {}, auth-id: {}, type: {}]", tenantId, authId, type);
                    resultHandler.handle(Future.succeededFuture(
                            CredentialsResult.from(HttpURLConnection.HTTP_OK,
                                    JsonObject.mapFrom(credential.getOriginalJson()), CacheDirective.noCacheDirective())));
                } else {
                    log.debug("Context mismatch [tenant-id: {}, auth-id: {}, type: {}]", tenantId, authId, type);
                    resultHandler.handle(Future.succeededFuture(CredentialsResult.from(HttpURLConnection.HTTP_NOT_FOUND)));
                }
            } else {
                log.debug("Retrieve credential !![tenant-id: {}, auth-id: {}, type: {}]", tenantId, authId, type);
                resultHandler.handle(Future.succeededFuture(
                        CredentialsResult.from(HttpURLConnection.HTTP_OK,
                                JsonObject.mapFrom(credential.getOriginalJson()), CacheDirective.noCacheDirective())));
            }
        });
    }

    @Override
    public void update(final String tenantId, final JsonObject otherKeys, final Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {

        final CredentialsObject credentials = Optional.ofNullable(otherKeys)
                .map(json -> json.mapTo(CredentialsObject.class)).orElse(null);

        final CredentialsKey key = new CredentialsKey(tenantId, credentials.getAuthId(), credentials.getType());
        final RegistryCredentialObject registryCredential = new RegistryCredentialObject(credentials, tenantId, otherKeys);

        credentialsCache.replaceAsync(key, registryCredential).thenAccept(result -> {
            if (result == null){
                log.debug("Cannot update credential : not found [tenant-id: {}, auth-id: {}, type: {}]", tenantId, credentials.getAuthId(), credentials.getType());
                resultHandler.handle(Future.succeededFuture(CredentialsResult.from(HttpURLConnection.HTTP_NOT_FOUND)));
            } else {
                log.debug("Credential updated [tenant-id: {}, auth-id: {}, type: {}]", tenantId, credentials.getAuthId(), credentials.getType());
                resultHandler.handle(Future.succeededFuture(CredentialsResult.from(HttpURLConnection.HTTP_NO_CONTENT)));
            }
        });
    }

    @Override
    public void remove(final String tenantId, final String type, final String authId, final Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {

        final CredentialsKey key = new CredentialsKey(tenantId, authId, type);
        credentialsCache.withFlags(Flag.FORCE_RETURN_VALUE).removeAsync(key).thenAccept(result -> {
                    if (result == null){
                        log.debug("Cannot remove credential : not found [tenant-id: {}, auth-id: {}, type: {}]", tenantId, authId, type);
                        resultHandler.handle(Future.succeededFuture(CredentialsResult.from(HttpURLConnection.HTTP_NOT_FOUND)));
                    } else {
                        log.debug("removed credential : not found [tenant-id: {}, auth-id: {}, type: {}]", tenantId, authId, type);
                        resultHandler.handle(Future.succeededFuture(CredentialsResult.from(HttpURLConnection.HTTP_NO_CONTENT)));
                    }
                }
        );
    }

    @Override
    public void removeAll(final String tenantId, final String deviceId, final Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {

        final List<RegistryCredentialObject>  matches = queryAllCredentialsForDevice(tenantId, deviceId);
        if (matches.isEmpty()){
            log.debug("Cannot remove credentials for device : not found [tenant-id: {}, deviceID {}]", tenantId, deviceId);
            resultHandler.handle(Future.succeededFuture(CredentialsResult.from(HttpURLConnection.HTTP_NOT_FOUND)));
        } else {
            final List<CompletableFuture<RegistryCredentialObject>> futureResultList = new ArrayList();
            matches.forEach(registryCredential -> {
                final CredentialsKey key = new CredentialsKey(
                        tenantId,
                        new JsonObject(registryCredential.getOriginalJson()).getString(CredentialsConstants.FIELD_AUTH_ID),
                        new JsonObject(registryCredential.getOriginalJson()).getString(CredentialsConstants.FIELD_TYPE));
                futureResultList.add( credentialsCache.removeAsync(key));
            });
            CompletableFuture.allOf(futureResultList.toArray(new CompletableFuture[futureResultList.size()])).thenAccept( r-> {
                log.debug("Removed {} credentials for device [tenant-id: {}, deviceID {}]", matches.size(), tenantId, deviceId);
                resultHandler.handle(Future.succeededFuture(CredentialsResult.from(HttpURLConnection.HTTP_NO_CONTENT)));
            });
        }
    }

    @Override
    public void getAll(final String tenantId, final String deviceId, final Span span, final Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {

        final JsonArray creds = new JsonArray();

        queryAllCredentialsForDevice(tenantId, deviceId).forEach(result -> {
            creds.add(new JsonObject(result.getOriginalJson()));
        });

        if (creds.isEmpty()) {
            log.debug("Cannot retrieve credentials for device : not found [tenant-id: {}, deviceID {}]", tenantId, deviceId);
            resultHandler.handle(Future.succeededFuture(CredentialsResult.from(HttpURLConnection.HTTP_NOT_FOUND)));
        } else {

            log.debug("Retrieved {} credentials for device [tenant-id: {}, deviceID {}]", creds.size(), tenantId, deviceId);
            final JsonObject result = new JsonObject()
                    .put(CredentialsConstants.FIELD_CREDENTIALS_TOTAL, creds.size())
                    .put(CredentialsConstants.CREDENTIALS_ENDPOINT, creds);
            resultHandler.handle(Future.succeededFuture(
                    CredentialsResult.from(HttpURLConnection.HTTP_OK, result, CacheDirective.noCacheDirective())));
        }
    }

    // TODO : async request
    private List<RegistryCredentialObject> queryAllCredentialsForDevice(final String tenantId, final String deviceId){
        // Obtain a query factory for the cache
        final QueryFactory queryFactory = Search.getQueryFactory(credentialsCache);
        final Query query = queryFactory.from(RegistryCredentialObject.class)
                .having("deviceId").eq(deviceId)
                .and().having("tenantId").eq(tenantId)
                .build();
        // Execute the query
        return query.list();
    }

    private boolean contextMatches(final JsonObject clientContext, final JsonObject storedCredential) {
        final AtomicBoolean match = new AtomicBoolean(true);
        clientContext.forEach(field -> {
            if (storedCredential.containsKey(field.getKey())) {
                if (!storedCredential.getString(field.getKey()).equals(field.getValue())) {
                    match.set(false);
                }
            } else {
                match.set(false);
            }
        });
        return match.get();
    }
}
