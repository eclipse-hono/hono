/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.deviceregistry;

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
import org.infinispan.Cache;

import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.query.Search;
import org.infinispan.query.dsl.Query;
import org.infinispan.query.dsl.QueryFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
@ConditionalOnProperty(name = "hono.app.type", havingValue = "infinispan", matchIfMissing = true)
public class CacheCredentialService extends CompleteBaseCredentialsService<CacheCredentialConfigProperties> {

     private final Cache<CredentialsKey, RegistryCredentialObject> credentialsCache;

    /**
     * Creates a new service instance for a password encoder.
     *
     * @param pwdEncoder The encoder to use for hashing clear text passwords.
     * @throws NullPointerException if encoder is {@code null}.
     */
    @Autowired
    protected CacheCredentialService(final EmbeddedCacheManager cacheManager, final HonoPasswordEncoder pwdEncoder) {
        super(pwdEncoder);
        this.credentialsCache = cacheManager.createCache("credentials", new ConfigurationBuilder().build());
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

        credentialsCache.putIfAbsentAsync(key, registryCredential).thenAccept(result -> {
            if (result == null) {
                resultHandler.handle(Future.succeededFuture(CredentialsResult.from(HttpURLConnection.HTTP_CREATED)));
            } else {
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
                resultHandler.handle(Future.succeededFuture(CredentialsResult.from(HttpURLConnection.HTTP_NOT_FOUND)));
            } else if (clientContext != null && !clientContext.isEmpty()) {
                if (contextMatches(clientContext, credential.getOriginalJson())) {
                    resultHandler.handle(Future.succeededFuture(
                            CredentialsResult.from(HttpURLConnection.HTTP_OK,
                                    JsonObject.mapFrom(credential.getOriginalJson()), CacheDirective.noCacheDirective())));
                } else {
                    resultHandler.handle(Future.succeededFuture(CredentialsResult.from(HttpURLConnection.HTTP_NOT_FOUND)));
                }
            } else {
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
                resultHandler.handle(Future.succeededFuture(CredentialsResult.from(HttpURLConnection.HTTP_NOT_FOUND)));
            } else {
                resultHandler.handle(Future.succeededFuture(CredentialsResult.from(HttpURLConnection.HTTP_NO_CONTENT)));
            }
        });
    }

    @Override
    public void remove(final String tenantId, final String type, final String authId, final Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {

        final CredentialsKey key = new CredentialsKey(tenantId, authId, type);
        credentialsCache.removeAsync(key).thenAccept(result -> {
                    if (result == null){
                        resultHandler.handle(Future.succeededFuture(CredentialsResult.from(HttpURLConnection.HTTP_NOT_FOUND)));
                    } else {
                        resultHandler.handle(Future.succeededFuture(CredentialsResult.from(HttpURLConnection.HTTP_NO_CONTENT)));
                    }
                }
        );
    }

    //TODO : use futures and composeAll to be Async.
    @Override
    public void removeAll(final String tenantId, final String deviceId, final Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {

        final List<RegistryCredentialObject>  matches = queryAllCredentialsForDevice(tenantId, deviceId);
        if (matches.isEmpty()){
            resultHandler.handle(Future.succeededFuture(CredentialsResult.from(HttpURLConnection.HTTP_NOT_FOUND)));
        } else {
            final List<CompletableFuture<RegistryCredentialObject>> futureResultList = new ArrayList();
            matches.forEach(registryCredential -> {
                final CredentialsKey key = new CredentialsKey(
                        tenantId,
                        registryCredential.getOriginalJson().getString(CredentialsConstants.FIELD_AUTH_ID),
                        registryCredential.getOriginalJson().getString(CredentialsConstants.FIELD_TYPE));
                futureResultList.add( credentialsCache.removeAsync(key));
            });
            CompletableFuture.allOf(futureResultList.toArray(new CompletableFuture[futureResultList.size()]))
                    .thenAccept( r->
                        resultHandler.handle(Future.succeededFuture(CredentialsResult.from(HttpURLConnection.HTTP_NO_CONTENT))));
        }
    }

    @Override
    public void getAll(final String tenantId, final String deviceId, final Span span, final Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {

        final JsonArray creds = new JsonArray();

        queryAllCredentialsForDevice(tenantId, deviceId).forEach(result -> {
            creds.add(JsonObject.mapFrom(result.getOriginalJson()));
        });

        if (creds.isEmpty()) {
            resultHandler.handle(Future.succeededFuture(CredentialsResult.from(HttpURLConnection.HTTP_NOT_FOUND)));
        } else {

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
