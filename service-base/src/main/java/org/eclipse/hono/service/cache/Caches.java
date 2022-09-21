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


package org.eclipse.hono.service.cache;

import java.time.Duration;
import java.util.Optional;

import org.eclipse.hono.client.amqp.config.RequestResponseClientConfigProperties;
import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.RequestResponseResult;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * A helper class for creating Caffeine based cache instances.
 *
 */
@RegisterForReflection(
        classNames = {
            "com.github.benmanes.caffeine.cache.SSMSA",
            "com.github.benmanes.caffeine.cache.SSMSWR",
            "com.github.benmanes.caffeine.cache.PSWMS",
            "com.github.benmanes.caffeine.cache.PSWRMW"
        })
public final class Caches {

    private Caches() {
        // prevent instantiation
    }

    /**
     * Creates a new Caffeine based cache.
     * <p>
     * The created cache will automatically expire values based on the maximum age
     * set in the value's {@linkplain org.eclipse.hono.util.CacheDirective#getMaxAge() cache directive}.
     * The cache size will be according to the given configuration's minimum and maximum
     * response cache size properties.
     *
     * @param <V> The type of values that the cache supports.
     * @param config The configuration to use for the cache.
     * @return A new cache or {@code null} if the configured max cache size is &lt;= 0.
     */
    public static <V extends RequestResponseResult<?>> Cache<Object, V> newCaffeineCache(
            final RequestResponseClientConfigProperties config) {

        if (config.getResponseCacheMaxSize() <= 0) {
            return null;
        }

        final long defaultTimeoutNanos = Duration.ofSeconds(config.getResponseCacheDefaultTimeout()).toNanos();

        return Caffeine.newBuilder()
                .initialCapacity(config.getResponseCacheMinSize())
                .maximumSize(Math.max(config.getResponseCacheMinSize(), config.getResponseCacheMaxSize()))
                .expireAfter(new Expiry<Object, V>() {

                    private long getMaxAge(final CacheDirective directive) {
                        return Optional.ofNullable(directive)
                            .map(d -> Duration.ofSeconds(Math.min(d.getMaxAge(), config.getResponseCacheDefaultTimeout())).toNanos())
                            .orElse(defaultTimeoutNanos);
                    }

                    @Override
                    public long expireAfterCreate(
                            final Object key,
                            final V value,
                            final long currentTime) {

                        return getMaxAge(value.getCacheDirective());
                    }

                    @Override
                    public long expireAfterUpdate(
                            final Object key,
                            final V value,
                            final long currentTime,
                            final long currentDuration) {

                        return getMaxAge(value.getCacheDirective());
                    }

                    @Override
                    public long expireAfterRead(
                            final Object key,
                            final V value,
                            final long currentTime,
                            final long currentDuration) {

                        return currentDuration;
                    }
                })
                .build();
    }
}
