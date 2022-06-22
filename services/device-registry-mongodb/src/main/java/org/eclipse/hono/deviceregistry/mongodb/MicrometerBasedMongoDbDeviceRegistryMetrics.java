/*******************************************************************************
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.deviceregistry.mongodb;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.hono.deviceregistry.metrics.DeviceRegistryMetrics;
import org.eclipse.hono.deviceregistry.mongodb.model.TenantDao;
import org.eclipse.hono.service.metric.MicrometerBasedMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

/**
 * Metrics for Mongo DB based Device Registry service.
 */
public class MicrometerBasedMongoDbDeviceRegistryMetrics extends MicrometerBasedMetrics implements DeviceRegistryMetrics {

    /**
     * Metric key for total number of Tenants in the system.
     */
    public static final String TOTAL_TENANTS_METRIC_KEY = "hono.tenants.total";

    private static final Logger LOG = LoggerFactory.getLogger(MicrometerBasedMongoDbDeviceRegistryMetrics.class);

    private final TenantDao dao;
    private final AtomicInteger tenantsCount;

    /**
     * Create a new metrics instance for the Device Registry service.
     *
     * @param vertx The Vert.x instance to use.
     * @param registry The meter registry to use.
     * @param dao The data access object to use for accessing data in the MongoDB.
     *
     * @throws NullPointerException if either parameter is {@code null}.
     */
    public MicrometerBasedMongoDbDeviceRegistryMetrics(final Vertx vertx, final MeterRegistry registry, final TenantDao dao) {
        super(registry, vertx);
        Objects.requireNonNull(dao);
        this.dao = dao;
        this.tenantsCount = new AtomicInteger(-1);
        register();
    }

    private void register() {
        Gauge.builder(TOTAL_TENANTS_METRIC_KEY, () -> {
            dao.count(new JsonObject(), null)
                    .onSuccess(result -> tenantsCount.set(result.intValue()))
                    .onFailure(e -> LOG.warn("еrror while querying Tenants count from MongoDB", e));
            return tenantsCount.intValue();
        }).register(registry);
    }
}
