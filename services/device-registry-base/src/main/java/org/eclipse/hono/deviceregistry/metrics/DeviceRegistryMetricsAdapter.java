/**
 * Copyright (c) 2022, 2023 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.deviceregistry.metrics;

import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.runtime.StartupEvent;
import io.vertx.core.Future;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

/**
 * A base class for implementing device registry metrics.
 * <p>
 * Registers a meter holding the total number of registered tenants.
 *
 */
public abstract class DeviceRegistryMetricsAdapter implements DeviceRegistryMetrics {

    private static final Logger LOG = LoggerFactory.getLogger(DeviceRegistryMetricsAdapter.class);
    /**
     * The last known total number of registered tenants.
     */
    protected final AtomicInteger tenantCount = new AtomicInteger(-1);
    /**
     * The Micrometer registry to register meters with.
     */
    @Inject
    protected MeterRegistry registry;

    /**
     * Determines the current total number of tenants registered.
     * <p>
     * Subclasses need to implement this method by means of querying a database etc.
     *
     * @return A future containing the current number of registered tenants.
     */
    protected abstract Future<Integer> determineCurrentNumberOfTenants();

    private Number getTenantCount() {
        determineCurrentNumberOfTenants()
            .onSuccess(tenantCount::set)
            .onFailure(e -> LOG.warn("error determining tenant count", e));
        return tenantCount.get();
    }

    void setUpTotalTenantsMeter(final @Observes StartupEvent ev) {
        LOG.info("registering gauge [{}]", TOTAL_TENANTS_METRIC_KEY);
        Gauge.builder(TOTAL_TENANTS_METRIC_KEY, this::getTenantCount)
            .register(registry);
    }
}
