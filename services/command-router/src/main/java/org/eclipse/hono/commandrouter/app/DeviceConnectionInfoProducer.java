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


package org.eclipse.hono.commandrouter.app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.eclipse.hono.commandrouter.AdapterInstanceStatusService;
import org.eclipse.hono.commandrouter.CommandRouterServiceOptions;
import org.eclipse.hono.commandrouter.impl.KubernetesBasedAdapterInstanceStatusService;
import org.eclipse.hono.commandrouter.impl.UnknownStatusProvidingService;
import org.eclipse.hono.deviceconnection.infinispan.client.BasicCache;
import org.eclipse.hono.deviceconnection.infinispan.client.CacheBasedDeviceConnectionInfo;
import org.eclipse.hono.deviceconnection.infinispan.client.CommonCacheConfig;
import org.eclipse.hono.deviceconnection.infinispan.client.CommonCacheOptions;
import org.eclipse.hono.deviceconnection.infinispan.client.DeviceConnectionInfo;
import org.eclipse.hono.deviceconnection.infinispan.client.EmbeddedCache;
import org.eclipse.hono.deviceconnection.infinispan.client.HotrodCache;
import org.eclipse.hono.deviceconnection.infinispan.client.InfinispanRemoteConfigurationOptions;
import org.eclipse.hono.deviceconnection.infinispan.client.InfinispanRemoteConfigurationProperties;
import org.eclipse.hono.util.Strings;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.infinispan.configuration.parsing.ConfigurationBuilderHolder;
import org.infinispan.configuration.parsing.ParserRegistry;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.manager.EmbeddedCacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Tracer;
import io.smallrye.config.ConfigMapping;
import io.vertx.core.Vertx;
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
public class DeviceConnectionInfoProducer {

    private static final Logger LOG = LoggerFactory.getLogger(DeviceConnectionInfoProducer.class);

    @ConfigProperty(name = "hono.commandRouter.cache.embedded.configurationFile", defaultValue = "/etc/hono/cache-config.xml")
    String configFile;

    @Produces
    DeviceConnectionInfo deviceConnectionInfo(
            final BasicCache<String, String> cache,
            final Tracer tracer,
            final AdapterInstanceStatusService adapterInstanceStatusService) {
        return new CacheBasedDeviceConnectionInfo(cache, tracer, adapterInstanceStatusService);
    }

    @Produces
    BasicCache<String, String> cache(
            final Vertx vertx,
            @ConfigMapping(prefix = "hono.commandRouter.cache.common")
            final CommonCacheOptions commonCacheOptions,
            @ConfigMapping(prefix = "hono.commandRouter.cache.remote")
            final InfinispanRemoteConfigurationOptions remoteCacheConfigurationOptions) {

        final var commonCacheConfig = new CommonCacheConfig(commonCacheOptions);
        final var infinispanCacheConfig = new InfinispanRemoteConfigurationProperties(remoteCacheConfigurationOptions);
        if (Strings.isNullOrEmpty(infinispanCacheConfig.getServerList())) {
            LOG.info("configuring embedded cache");
            return new EmbeddedCache<>(
                    vertx,
                    embeddedCacheManager(commonCacheConfig),
                    commonCacheConfig.getCacheName());
        } else {
            LOG.info("configuring remote cache");
            return HotrodCache.from(
                    vertx,
                    infinispanCacheConfig,
                    commonCacheConfig);
        }
    }

    private EmbeddedCacheManager embeddedCacheManager(final CommonCacheConfig cacheConfig) {
        return new DefaultCacheManager(configuration(cacheConfig), false);
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
