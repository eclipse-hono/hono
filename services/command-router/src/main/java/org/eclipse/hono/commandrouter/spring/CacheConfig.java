/*******************************************************************************
 * Copyright (c) 2020, 2021 Contributors to the Eclipse Foundation
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.hono.deviceconnection.infinispan.client.BasicCache;
import org.eclipse.hono.deviceconnection.infinispan.client.CommonCacheConfig;
import org.eclipse.hono.deviceconnection.infinispan.client.EmbeddedCache;
import org.eclipse.hono.deviceconnection.infinispan.client.HotrodCache;
import org.eclipse.hono.deviceconnection.infinispan.client.InfinispanRemoteConfigurationProperties;
import org.eclipse.hono.util.Strings;
import org.infinispan.configuration.parsing.ConfigurationBuilderHolder;
import org.infinispan.configuration.parsing.ParserRegistry;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.manager.EmbeddedCacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.vertx.core.Vertx;

/**
 * Spring Boot configuration for the cache of the Command Router service.
 *
 */
@Configuration
public class CacheConfig {

    private static final Logger LOG = LoggerFactory.getLogger(CacheConfig.class);

    @Value("${hono.command-router.cache.embedded.configuration-file:/etc/hono/cache-config.xml}")
    private Path configuration;

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
     * Exposes a cache for accessing command routing information.
     *
     * @param vertx The vert.x instance to run on.
     * @param commonCacheConfig Common cache configuration options.
     * @return The cache.
     */
    @Bean
    public BasicCache<String, String> cache(final Vertx vertx, final CommonCacheConfig commonCacheConfig) {

        LOG.info("Common Cache Config: {}", commonCacheConfig);

        if (Strings.isNullOrEmpty(remoteCacheProperties().getServerList())) {
            LOG.info("configuring embedded cache");
            return new EmbeddedCache<>(
                    vertx,
                    embeddedCacheManager(commonCacheConfig),
                    commonCacheConfig.getCacheName());
        } else {
            LOG.info("configuring remote cache");
            return HotrodCache.from(
                    vertx,
                    remoteCacheProperties(),
                    commonCacheConfig);
        }
    }

    /**
     * Exposes an embedded cache manager as a Spring bean.
     *
     * @param cacheConfig Common cache configuration options.
     * @return The newly created cache manager. The manager will not be started.
     */
    @Bean
    public EmbeddedCacheManager embeddedCacheManager(final CommonCacheConfig cacheConfig) {
        return new DefaultCacheManager(configuration(cacheConfig), false);
    }

    private ConfigurationBuilderHolder configuration(final CommonCacheConfig cacheConfig) {
        if (this.configuration != null && Files.exists(configuration)) {
            try {
                final ConfigurationBuilderHolder holder = new ParserRegistry().parseFile(configuration.toFile());
                LOG.info("successfully configured embedded cache from file [{}]", configuration);
                return holder;
            } catch (final IOException e) {
                LOG.error("failed to read configuration file [{}]", configuration, e);
                throw new IllegalStateException("failed to configure embedded cache", e);
            }
        } else {
            LOG.info("using default embedded cache configuration");
            final var builderHolder = new ConfigurationBuilderHolder();
            builderHolder.newConfigurationBuilder(cacheConfig.getCacheName());
            return builderHolder;
        }
    }
}
