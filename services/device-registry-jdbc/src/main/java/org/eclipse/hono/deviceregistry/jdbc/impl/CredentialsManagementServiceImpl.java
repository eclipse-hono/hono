/*******************************************************************************
 * Copyright (c) 2020, 2022 Contributors to the Eclipse Foundation
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
import java.util.Set;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.deviceregistry.jdbc.config.DeviceServiceOptions;
import org.eclipse.hono.deviceregistry.service.credentials.AbstractCredentialsManagementService;
import org.eclipse.hono.deviceregistry.service.device.DeviceKey;
import org.eclipse.hono.service.auth.HonoPasswordEncoder;
import org.eclipse.hono.service.base.jdbc.store.OptimisticLockingException;
import org.eclipse.hono.service.base.jdbc.store.SQL;
import org.eclipse.hono.service.base.jdbc.store.device.TableManagementStore;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.credentials.CommonCredential;
import org.eclipse.hono.util.CacheDirective;

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
    public CredentialsManagementServiceImpl(
            final Vertx vertx,
            final HonoPasswordEncoder passwordEncoder,
            final TableManagementStore store,
            final DeviceServiceOptions properties) {

        super(vertx, passwordEncoder, properties.maxBcryptCostFactor(),
                properties.hashAlgorithmsAllowList().orElseGet(Set::of));
        this.store = store;
        this.ttl = Optional.of(CacheDirective.maxAgeDirective(properties.credentialsTtl()));
    }

    @Override
    protected Future<OperationResult<Void>> processUpdateCredentials(
            final DeviceKey key,
            final List<CommonCredential> credentials,
            final Optional<String> resourceVersion,
            final Span span) {

        return this.store.setCredentials(key, credentials, resourceVersion, span.context())

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
                        throw new ClientErrorException(key.getTenantId(), HttpURLConnection.HTTP_NOT_FOUND);
                    }
                })

                .recover( e -> {

                    if (SQL.hasCauseOf(e, OptimisticLockingException.class) ) {
                        return Future.failedFuture(new ClientErrorException(key.getTenantId(), HttpURLConnection.HTTP_PRECON_FAILED));
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
                        this.ttl,
                        result.getResourceVersion()))
                        .orElseThrow(() -> new ClientErrorException(key.getTenantId(), HttpURLConnection.HTTP_NOT_FOUND)));

    }

}
