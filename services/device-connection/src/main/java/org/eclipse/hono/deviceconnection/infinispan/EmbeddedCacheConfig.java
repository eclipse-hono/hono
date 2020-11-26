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

package org.eclipse.hono.deviceconnection.infinispan;

import java.io.IOException;
import java.nio.file.Path;

import org.eclipse.hono.deviceconnection.infinispan.client.BasicCache;
import org.eclipse.hono.deviceconnection.infinispan.client.CommonCacheConfig;
import org.eclipse.hono.deviceconnection.infinispan.client.EmbeddedCache;
import org.infinispan.configuration.parsing.ConfigurationBuilderHolder;
import org.infinispan.configuration.parsing.ParserRegistry;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.manager.EmbeddedCacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import io.vertx.core.Vertx;

/**
 * Spring Boot configuration for the embedded cache of the Device Connection service.
 *
 */
@Configuration
@Profile(ApplicationConfig.PROFILE_EMBEDDED_CACHE)
public class EmbeddedCacheConfig {

    private static final Logger LOG = LoggerFactory.getLogger(RemoteCacheConfig.class);

    @Value("${hono.device-connection.embedded.configuration-file:/etc/hono/cache-config.xml}")
    private Path configuration;

    /**
     * Exposes an embedded cache that contains device connection information.
     *
     * @param vertx The vert.x instance to run on.
     * @param cacheConfig Common cache configuration options.
     * @return The cache.
     */
    @Bean
    public BasicCache<String, String> embeddedCache(final Vertx vertx, final CommonCacheConfig cacheConfig) {
        LOG.info("Common Config: {}", cacheConfig);
        return new EmbeddedCache<>(
                vertx,
                embeddedCacheManager(cacheConfig),
                cacheConfig.getCacheName());
    }

    /**
     * Creates a new configuration, either from a configured file, or with some reasonable defaults.
     *
     * @param cacheConfig Common cache configuration options.
     * @return A new configuration.
     * @throws IllegalStateException in case a configuration file is configured and cannot be loaded/parsed.
     */
    @Bean
    public ConfigurationBuilderHolder configuration(final CommonCacheConfig cacheConfig) {
        if (this.configuration != null) {
            try {
                final ConfigurationBuilderHolder holder = new ParserRegistry().parseFile(configuration.toFile());
                LOG.info("successfully configured embedded cache from file [{}]", configuration);
                return holder;
            } catch (final IOException e) {
                LOG.error("failed to read configuration file [{}], falling back to default configuration", configuration, e);
                throw new IllegalStateException("failed to configure embedded cache", e);
            }
        } else {
            final var builderHolder = new ConfigurationBuilderHolder();
            builderHolder.newConfigurationBuilder(cacheConfig.getCacheName());
            return builderHolder;
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

}
