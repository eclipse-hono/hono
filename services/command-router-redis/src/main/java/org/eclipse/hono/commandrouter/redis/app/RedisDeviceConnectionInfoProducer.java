/**
 * Copyright (c) 2021, 2022 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.commandrouter.redis.app;

import java.util.Optional;

import org.eclipse.hono.commandrouter.AdapterInstanceStatusService;
import org.eclipse.hono.commandrouter.CommandRouterServiceOptions;
import org.eclipse.hono.commandrouter.impl.KubernetesBasedAdapterInstanceStatusService;
import org.eclipse.hono.commandrouter.impl.UnknownStatusProvidingService;
import org.eclipse.hono.deviceconnection.common.Cache;
import org.eclipse.hono.deviceconnection.common.CacheBasedDeviceConnectionInfo;
import org.eclipse.hono.deviceconnection.common.DeviceConnectionInfo;
import org.eclipse.hono.deviceconnection.redis.client.RedisCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Tracer;
import io.vertx.redis.client.RedisAPI;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;


/**
 * A producer of an application scoped {@link DeviceConnectionInfo} instance.
 * <p>
 * The underlying cache implementation will store data in-memory or in a remote cache, depending
 * on whether a remote cache config with a non-empty server list is used or not.
 */
@ApplicationScoped
public class RedisDeviceConnectionInfoProducer {

    private static final Logger LOG = LoggerFactory.getLogger(RedisDeviceConnectionInfoProducer.class);

    @Produces
    DeviceConnectionInfo deviceConnectionInfo(
            final Cache<String, String> cache,
            final Tracer tracer,
            final AdapterInstanceStatusService adapterInstanceStatusService) {
        return new CacheBasedDeviceConnectionInfo(cache, tracer, adapterInstanceStatusService);
    }

    @Produces
    Cache<String, String> cache(final RedisAPI redis) {
        LOG.info("configuring redis cache");
        return RedisCache.from(redis);
    }

    @Produces
    @Singleton
    AdapterInstanceStatusService adapterInstanceStatusService(
            final CommandRouterServiceOptions commandRouterServiceOptions) {
        final AdapterInstanceStatusService service = commandRouterServiceOptions
                .kubernetesBasedAdapterInstanceStatusServiceEnabled()
                        ? KubernetesBasedAdapterInstanceStatusService.create()
                        : null;
        return Optional.ofNullable(service)
                .orElseGet(UnknownStatusProvidingService::new);
    }
}
