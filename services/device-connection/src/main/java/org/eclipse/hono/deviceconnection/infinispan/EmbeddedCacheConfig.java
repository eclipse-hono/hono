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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.hono.deviceconnection.infinispan.client.BasicCache;
import org.eclipse.hono.deviceconnection.infinispan.client.EmbeddedCache;
import org.eclipse.hono.util.DeviceConnectionConstants;
import org.infinispan.configuration.parsing.ConfigurationBuilderHolder;
import org.infinispan.configuration.parsing.ParserRegistry;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.manager.EmbeddedCacheManager;
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

    @Value("hono.device-connection.embedded.configuration-file")
    private Path configuration;

    /**
     * Exposes an embedded cache that contains device connection information.
     *
     * @param vertx The vert.x instance to run on.
     * @return The cache.
     */
    @Bean
    public BasicCache<String, String> embeddedCache(final Vertx vertx) {
        return new EmbeddedCache<>(
                vertx,
                embeddedCacheManager(),
                DeviceConnectionConstants.CACHE_NAME,
                "KEY_CONNECTION_CHECK",
                "VALUE_CONNECTION_CHECK");
    }

    /**
     * Create a new configuration, either from a configured file, or with some reasonable defaults.
     *
     * @return A new configuration.
     * @throws RuntimeException in case a configuration file is configured and loading fails.
     */
    @Bean
    public ConfigurationBuilderHolder configuration() {
        if (this.configuration != null) {
            try (InputStream in = Files.newInputStream(configuration)) {
                return new ParserRegistry().parse(in);
            } catch (IOException e) {
                throw new RuntimeException("Failed to read configuration", e);
            }
        } else {
            final var builderHolder = new ConfigurationBuilderHolder();
            builderHolder.newConfigurationBuilder(DeviceConnectionConstants.CACHE_NAME);
            return builderHolder;
        }
    }

    /**
     * Exposes an embedded cache manager as a Spring bean.
     *
     * @return The newly created cache manager. The manager will not be started.
     */
    @Bean
    public EmbeddedCacheManager embeddedCacheManager() {
        return new DefaultCacheManager(configuration(), false);
    }

}
