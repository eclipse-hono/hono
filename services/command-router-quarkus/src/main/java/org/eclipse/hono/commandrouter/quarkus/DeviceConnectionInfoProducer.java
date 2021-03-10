/**
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.commandrouter.quarkus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;

import org.eclipse.hono.deviceconnection.infinispan.client.BasicCache;
import org.eclipse.hono.deviceconnection.infinispan.client.CacheBasedDeviceConnectionInfo;
import org.eclipse.hono.deviceconnection.infinispan.client.CommonCacheConfig;
import org.eclipse.hono.deviceconnection.infinispan.client.DeviceConnectionInfo;
import org.eclipse.hono.deviceconnection.infinispan.client.EmbeddedCache;
import org.eclipse.hono.deviceconnection.infinispan.client.HotrodCache;
import org.eclipse.hono.deviceconnection.infinispan.client.quarkus.InfinispanRemoteConfigurationProperties;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.configuration.parsing.ConfigurationBuilderHolder;
import org.infinispan.configuration.parsing.ParserRegistry;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.manager.EmbeddedCacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Tracer;
import io.quarkus.arc.config.ConfigPrefix;
import io.quarkus.runtime.configuration.ProfileManager;
import io.vertx.core.Vertx;

/**
 * A producer of an application scoped {@link DeviceConnectionInfo} instance.
 * <p>
 * The underlying cache implementation will store data in-memory or in a remote cache, depending
 * on whether the active Quarkus profile is <em>embedded-cache</em> or not.
 */
@ApplicationScoped
public class DeviceConnectionInfoProducer {

    private static final Logger LOG = LoggerFactory.getLogger(DeviceConnectionInfoProducer.class);
    private static final String PROFILE_EMBEDDED_CACHE = "embedded-cache";

    @ConfigProperty(name = "hono.commandRouter.cache.embedded.configurationFile", defaultValue = "/etc/hono/cache-config.xml")
    String configFile;

    @ConfigPrefix("hono.commandRouter.cache.remote")
    InfinispanRemoteConfigurationProperties infinispanCacheConfig;

    @Produces
    DeviceConnectionInfo deviceConnectionInfo(
            final BasicCache<String, String> cache,
            final Tracer tracer) {
        return new CacheBasedDeviceConnectionInfo(cache, tracer);
    }

    @Produces
    BasicCache<String, String> cache(
            final Vertx vertx,
            final CommonCacheConfig commonCacheConfig) {

        if (PROFILE_EMBEDDED_CACHE.equals(ProfileManager.getActiveProfile())) {
            LOG.info("configuring embedded cache");
            return new EmbeddedCache<>(
                    vertx,
                    embeddedCacheManager(commonCacheConfig),
                    commonCacheConfig.getCacheName());
        } else {
            LOG.info("configuring remote cache");
            return new HotrodCache<>(
                    vertx,
                    remoteCacheManager(),
                    commonCacheConfig.getCacheName(),
                    commonCacheConfig.getCheckKey(),
                    commonCacheConfig.getCheckValue());
        }
    }

    private ConfigurationBuilderHolder configuration(final CommonCacheConfig cacheConfig) {

        final var configuration = Path.of(configFile);
        if (configuration != null && Files.exists(configuration)) {
            try {
                final ConfigurationBuilderHolder holder = new ParserRegistry().parseFile(configuration.toFile());
                LOG.info("successfully configured embedded cache from file [{}]", configuration);
                return holder;
            } catch (final IOException e) {
                LOG.error("failed to read configuration file [{}]", configuration, e);
                throw new IllegalStateException("failed to configure embedded cache", e);
            }
        } else {
            final var builderHolder = new ConfigurationBuilderHolder();
            final var builder = builderHolder.newConfigurationBuilder(cacheConfig.getCacheName());
            LOG.info("using default embedded cache configuration:{}{}", System.lineSeparator(), builder.toString());
            return builderHolder;
        }
    }

    private EmbeddedCacheManager embeddedCacheManager(final CommonCacheConfig cacheConfig) {
        return new DefaultCacheManager(configuration(cacheConfig), false);
    }

    private RemoteCacheManager remoteCacheManager() {
        return new RemoteCacheManager(infinispanCacheConfig.getConfigurationBuilder().build(), false);
    }
}
