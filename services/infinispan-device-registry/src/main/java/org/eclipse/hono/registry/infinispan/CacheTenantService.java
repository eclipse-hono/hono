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
import io.vertx.core.json.JsonObject;
import org.eclipse.hono.service.tenant.CompleteBaseTenantService;
import org.eclipse.hono.util.TenantObject;
import org.eclipse.hono.util.TenantResult;
import org.infinispan.Cache;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.query.Search;
import org.infinispan.query.dsl.Query;
import org.infinispan.query.dsl.QueryFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import javax.security.auth.x500.X500Principal;
import java.net.HttpURLConnection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A tenant service that use an Infinispan as a backend service.
 * Infinispan is an open source project providing a distributed in-memory key/value data store
 *
 * <p>
 *@see <a href="https://infinspan.org">https://infinspan.org</a>
 *
 */
@Repository
@Primary
public class CacheTenantService extends CompleteBaseTenantService<CacheTenantConfigProperties> {

    Cache<String, RegistryTenantObject> tenantsCache;

    @Autowired
    protected CacheTenantService(final EmbeddedCacheManager cacheManager) {
        this.tenantsCache = cacheManager.createCache("tenants", new ConfigurationBuilder().build());
    }

    @Override
    public void setConfig(final CacheTenantConfigProperties configuration) {
    }

    @Override
    public void add(final String tenantId, final JsonObject tenantObj, final Handler<AsyncResult<TenantResult<JsonObject>>> resultHandler) {

        final TenantObject tenantDetails = Optional.ofNullable(tenantObj)
                .map(json -> json.mapTo(TenantObject.class)).orElse(null);
        tenantDetails.setTenantId(tenantId);

        //verify if a duplicate cert exists
        if (tenantDetails.getTrustedCaSubjectDn() != null) {
            final RegistryTenantObject tenant = searchByCert(tenantDetails.getTrustedCaSubjectDn().getName());
            if (tenant != null) {
                resultHandler.handle(Future.succeededFuture(TenantResult.from(HttpURLConnection.HTTP_CONFLICT)));
                return;
            }
        }

        tenantsCache.putIfAbsentAsync(tenantId, new RegistryTenantObject(tenantDetails)).thenAccept(result -> {
            if (result == null) {
                resultHandler.handle(Future.succeededFuture(TenantResult.from(HttpURLConnection.HTTP_CREATED)));
            } else {
                resultHandler.handle(Future.succeededFuture(TenantResult.from(HttpURLConnection.HTTP_CONFLICT)));
            }
        });

    }

    @Override
    public void update(final String tenantId, final JsonObject tenantObj, final Handler<AsyncResult<TenantResult<JsonObject>>> resultHandler) {

        final TenantObject tenantDetails = Optional.ofNullable(tenantObj)
                .map(json -> json.mapTo(TenantObject.class)).orElse(null);

        //verify if a duplicate cert exists
        if (tenantDetails.getTrustedCaSubjectDn() != null) {
            final RegistryTenantObject tenant = searchByCert(tenantDetails.getTrustedCaSubjectDn().getName());
            if (tenant != null) {
                resultHandler.handle(Future.succeededFuture(TenantResult.from(HttpURLConnection.HTTP_CONFLICT)));
                return;
            }
        }

        tenantsCache.containsKeyAsync(tenantId).thenAccept(containsKey -> {
            if (containsKey) {
                tenantsCache.replaceAsync(tenantId, new RegistryTenantObject(tenantDetails)).thenAccept(result -> {
                    resultHandler.handle(Future.succeededFuture(TenantResult.from(HttpURLConnection.HTTP_NO_CONTENT)));
                });
            } else {
                resultHandler.handle(Future.succeededFuture(TenantResult.from(HttpURLConnection.HTTP_NOT_FOUND)));
            }
        });
    }

    @Override
    public void remove(final String tenantId, final Handler<AsyncResult<TenantResult<JsonObject>>> resultHandler) {

        tenantsCache.removeAsync(tenantId).thenAccept(result -> {
            if (result != null) {
                resultHandler.handle(Future.succeededFuture(TenantResult.from(HttpURLConnection.HTTP_NO_CONTENT)));
            } else {
                resultHandler.handle(Future.succeededFuture(TenantResult.from(HttpURLConnection.HTTP_NOT_FOUND)));
            }
        });
    }

    @Override
    public void get(final String tenantId, final Span span, final Handler<AsyncResult<TenantResult<JsonObject>>> resultHandler) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(resultHandler);

        tenantsCache.getAsync(tenantId).thenAccept(registryTenantObject -> {
            if (registryTenantObject == null) {
                resultHandler.handle(Future.succeededFuture(TenantResult.from(HttpURLConnection.HTTP_NOT_FOUND)));
            } else {
                resultHandler.handle(Future.succeededFuture(
                        TenantResult.from(HttpURLConnection.HTTP_OK, JsonObject.mapFrom(registryTenantObject.getTenantObject()))));
            }
        });
    }

    @Override
    public void get(final X500Principal subjectDn, final Span span, final Handler<AsyncResult<TenantResult<JsonObject>>> resultHandler) {

        if (subjectDn == null) {
            resultHandler.handle(Future.succeededFuture(TenantResult.from(HttpURLConnection.HTTP_BAD_REQUEST)));
        } else {

            final RegistryTenantObject searchResult = searchByCert(subjectDn.getName());

            if (searchResult == null) {
                resultHandler.handle(Future.succeededFuture(TenantResult.from(HttpURLConnection.HTTP_NOT_FOUND)));
            } else {
                resultHandler.handle(Future.succeededFuture(TenantResult.from(HttpURLConnection.HTTP_OK,
                        JsonObject.mapFrom(searchResult.getTenantObject()))));
            }
        }
    }

    // infinispan async querying coming soon™
    private RegistryTenantObject searchByCert(final String subjectDnName){

        final QueryFactory queryFactory = Search.getQueryFactory(tenantsCache);
        final Query query = queryFactory
                .from(RegistryTenantObject.class)
                .having("trustedCa")
                .contains(subjectDnName).build();

        final List<RegistryTenantObject> matches = query.list();

        // TODO make a difference between not found and conflict?
        if (matches.size() != 1){
            return null;
        } else {
            return matches.get(0);
        }
    }
}
