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

package org.eclipse.hono.deviceregistry.mongodb.app;

import org.eclipse.hono.deviceregistry.metrics.DeviceRegistryMetricsAdapter;
import org.eclipse.hono.deviceregistry.mongodb.model.TenantDao;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Metrics reported by the registry.
 */
@ApplicationScoped
public class MongoDbBasedDeviceRegistryMetrics extends DeviceRegistryMetricsAdapter {

    @Inject
    TenantDao tenantDao;

    @Override
    protected Future<Integer> determineCurrentNumberOfTenants() {
        return tenantDao.count(new JsonObject(), null);
    }
}
