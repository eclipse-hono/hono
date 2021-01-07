/*******************************************************************************
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.deviceregistry.jdbc.impl;

import java.net.HttpURLConnection;
import java.util.List;
import java.util.Optional;

import org.eclipse.hono.auth.HonoPasswordEncoder;
import org.eclipse.hono.deviceregistry.jdbc.config.DeviceServiceProperties;
import org.eclipse.hono.deviceregistry.service.credentials.AbstractCredentialsManagementService;
import org.eclipse.hono.deviceregistry.service.device.DeviceKey;
import org.eclipse.hono.service.base.jdbc.store.OptimisticLockingException;
import org.eclipse.hono.service.base.jdbc.store.SQL;
import org.eclipse.hono.service.base.jdbc.store.device.TableManagementStore;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.credentials.CommonCredential;
import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.CredentialsConstants;

import io.opentracing.Span;
import io.vertx.core.Future;
import io.vertx.core.Vertx;

/**
 * Implementation of the <em>credentials management</em> service.
 */
public class CredentialsManagementServiceImpl extends AbstractCredentialsManagementService {

    private final TableManagementStore store;
    private final Optional<CacheDirective> ttl;

    /**
     * Create a new instance.
     *
     * @param vertx The vert.x instance to use.
     * @param passwordEncoder The password encoder to use.
     * @param store The backing store.
     * @param properties The service properties.
     */
    public CredentialsManagementServiceImpl(final Vertx vertx, final HonoPasswordEncoder passwordEncoder, final TableManagementStore store, final DeviceServiceProperties properties) {
        super(vertx, passwordEncoder, properties.getMaxBcryptCostfactor(), properties.getHashAlgorithmsAllowList());
        this.store = store;
        this.ttl = Optional.of(CacheDirective.maxAgeDirective(properties.getCredentialsTtl()));
    }

    @Override
    protected Future<OperationResult<Void>> processUpdateCredentials(final DeviceKey key, final Optional<String> resourceVersion,
            final List<CommonCredential> credentials, final Span span) {

        return this.store

                .setCredentials(key, credentials, resourceVersion, span.context())

                .<OperationResult<Void>>map(r -> {
                    if (Boolean.TRUE.equals(r.getValue())) {
                        return OperationResult
                                .ok(
                                        HttpURLConnection.HTTP_NO_CONTENT,
                                        null,
                                        Optional.empty(),
                                        Optional.of(r.getVersion())
                                );
                    } else {
                        return OperationResult.empty(HttpURLConnection.HTTP_NOT_FOUND);
                    }
                })

                .recover( e -> {

                    if (SQL.hasCauseOf(e, OptimisticLockingException.class) ) {
                        return Future.succeededFuture(OperationResult.empty(HttpURLConnection.HTTP_PRECON_FAILED));
                    }

                    return Future.failedFuture(e);

                });

    }

    @Override
    protected Future<OperationResult<List<CommonCredential>>> processReadCredentials(final DeviceKey key, final Span span) {

        return this.store.getCredentials(key, span.context())
                .map(r -> r.map(result -> OperationResult.ok(
                        HttpURLConnection.HTTP_OK,
                        result.getCredentials(),
                        Optional.of(resolveCacheDirective(result.getCredentials())),
                        result.getResourceVersion()))
                        .orElseGet(() -> OperationResult.empty(HttpURLConnection.HTTP_NOT_FOUND)))

                .otherwise(err -> OperationResult.empty(HttpURLConnection.HTTP_INTERNAL_ERROR));

    }

    @Override
    protected CacheDirective getCacheDirective(final String type) {
        if (ttl.isPresent()) {
            switch (type) {
                case CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD:
                case CredentialsConstants.SECRETS_TYPE_X509_CERT:
                    return ttl.get();
                default:
                    return CacheDirective.noCacheDirective();
            }
        } else {
            return CacheDirective.noCacheDirective();
        }
    }

}
