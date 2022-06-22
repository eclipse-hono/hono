/**
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
 */

package org.eclipse.hono.deviceregistry.mongodb.app;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Singleton;

import org.eclipse.hono.deviceregistry.mongodb.MicrometerBasedMongoDbDeviceRegistryMetrics;
import org.eclipse.hono.deviceregistry.mongodb.model.TenantDao;

import io.micrometer.core.instrument.MeterRegistry;
import io.vertx.core.Vertx;

/**
 * A factory class that creates Mongo DB based Device Registry specific metrics.
 */
@ApplicationScoped
public class MetricsFactory {

    @Singleton
    @Produces
    MicrometerBasedMongoDbDeviceRegistryMetrics metrics(
            final Vertx vertx,
            final MeterRegistry registry,
            final TenantDao dao) {
        return new MicrometerBasedMongoDbDeviceRegistryMetrics(vertx, registry, dao);
    }
}
