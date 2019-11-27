/**
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
 */


package org.eclipse.hono.deviceconnection.infinispan.client;

import java.util.Objects;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.eclipse.hono.client.BasicDeviceConnectionClientFactory;
import org.eclipse.hono.client.DeviceConnectionClient;
import org.infinispan.client.hotrod.RemoteCacheContainer;
import org.infinispan.commons.api.BasicCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.vertx.core.Future;


/**
 * A factory for creating Device Connection service clients that connect directly to
 * an Infinispan cluster for reading and writing device connection information.
 *
 */
public final class HotrodBasedDeviceConnectionClientFactory implements BasicDeviceConnectionClientFactory {

    private static final Logger LOG = LoggerFactory.getLogger(HotrodBasedDeviceConnectionClientFactory.class);

    private final Cache<String, HotrodBasedDeviceConnectionClient> clients = Caffeine.newBuilder()
            .maximumSize(100)
            .build();
    private RemoteCacheContainer cacheManager;
    private BasicCache<String, String> cache;

    /**
     * Sets the cache manager to use for retrieving a cache.
     * 
     * @param cacheManager The cache manager.
     * @throws NullPointerException if cache manager is {@code null}.
     */
    @Autowired
    public void setCacheManager(final RemoteCacheContainer cacheManager) {
        this.cacheManager = Objects.requireNonNull(cacheManager);
        LOG.info("using cache manager [{}]", cacheManager.getClass().getName());
    }

    void setCache(final BasicCache<String, String> cache) {
        this.cache = cache;
    }

    /**
     * Starts up the factory.
     */
    @PostConstruct
    public void start() {
        cacheManager.start();
        cache = cacheManager.getCache("device-connection");
        cache.start();
        LOG.info("successfully connected to remote cache");
    }

    /**
     * Shuts down the factory and releases all resources.
     */
    @PreDestroy
    public void stop() {
        clients.invalidateAll();
        if (cacheManager != null) {
            cacheManager.stop();
            LOG.info("connection(s) to remote cache stopped successfully");
        }
    }

    /**
     * {@inheritDoc}
     * 
     * @throws IllegalStateException if the factory is not connected to the data grid.
     */
    @Override
    public Future<DeviceConnectionClient> getOrCreateDeviceConnectionClient(final String tenantId) {
        if (cache == null) {
            throw new IllegalStateException("not connected to remote cache");
        } else {
            final DeviceConnectionClient result = clients.get(tenantId, key -> {
                final DeviceConnectionInfoCache infoCache = new HotrodBasedDeviceConnectionInfoCache(cache);
                return new HotrodBasedDeviceConnectionClient(key, infoCache);
            });
            return Future.succeededFuture(result);
        }
    }
}
