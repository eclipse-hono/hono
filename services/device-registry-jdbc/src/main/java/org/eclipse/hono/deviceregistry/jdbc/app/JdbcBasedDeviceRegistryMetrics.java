/*******************************************************************************
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
 *******************************************************************************/

package org.eclipse.hono.deviceregistry.jdbc.app;

import org.eclipse.hono.deviceregistry.metrics.DeviceRegistryMetricsAdapter;
import org.eclipse.hono.service.base.jdbc.store.tenant.ManagementStore;

import io.vertx.core.Future;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Metrics reported by the registry.
 */
@ApplicationScoped
public class JdbcBasedDeviceRegistryMetrics extends DeviceRegistryMetricsAdapter {

    @Inject
    ManagementStore tenantStore;

    @Override
    protected Future<Integer> determineCurrentNumberOfTenants() {
        return tenantStore.getTenantCount();
    }
}
