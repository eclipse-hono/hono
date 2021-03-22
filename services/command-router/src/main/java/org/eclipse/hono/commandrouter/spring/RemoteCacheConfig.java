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

package org.eclipse.hono.commandrouter.spring;

import org.eclipse.hono.deviceconnection.infinispan.client.BasicCache;
import org.eclipse.hono.deviceconnection.infinispan.client.CommonCacheConfig;
import org.eclipse.hono.deviceconnection.infinispan.client.HotrodCache;
import org.eclipse.hono.deviceconnection.infinispan.client.InfinispanRemoteConfigurationProperties;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import io.vertx.core.Vertx;

/**
 * Spring Boot configuration for the remote cache of the Device Connection service.
 */
@Configuration
@Profile("!" + ApplicationConfig.PROFILE_EMBEDDED_CACHE)
public class RemoteCacheConfig {

    private static final Logger log = LoggerFactory.getLogger(RemoteCacheConfig.class);

    /**
     * Gets properties for configuring the connection to the remote cache.
     *
     * @return The properties.
     */
    @Bean
    @ConfigurationProperties("hono.command-router.cache.remote")
    public InfinispanRemoteConfigurationProperties remoteCacheProperties() {
        return new InfinispanRemoteConfigurationProperties();
    }

    /**
     * Exposes a remote cache manager as a Spring bean.
     *
     * @return The newly created cache manager. The manager will not be started.
     */
    @Bean
    public RemoteCacheManager remoteCacheManager() {
        final InfinispanRemoteConfigurationProperties properties = remoteCacheProperties();
        return new RemoteCacheManager(properties.getConfigurationBuilder().build(), false);
    }

    /**
     * Exposes a remote cache for accessing the Infinispan data grid that contains device
     * connection information.
     *
     * @param vertx The vert.x instance to run on.
     * @param cacheConfig Common cache configuration options.
     * @return The cache.
     */
    @Bean
    public BasicCache<String, String> remoteCache(final Vertx vertx, final CommonCacheConfig cacheConfig) {
        log.info("Common Config: {}", cacheConfig);
        return new HotrodCache<>(
                vertx,
                remoteCacheManager(),
                cacheConfig.getCacheName(),
                cacheConfig.getCheckKey(),
                cacheConfig.getCheckValue());
    }

}
