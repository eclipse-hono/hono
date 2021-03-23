/**
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
 */


package org.eclipse.hono.deviceconnection.infinispan.client;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.TimeUnit;

import org.infinispan.Cache;
import org.infinispan.lifecycle.ComponentStatus;
import org.infinispan.manager.EmbeddedCacheManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;


/**
 * Tests verifying behavior of the {@link EmbeddedCache}.
 *
 */
@ExtendWith(VertxExtension.class)
@Timeout(timeUnit = TimeUnit.SECONDS, value = 5)
class EmbeddedCacheTest extends AbstractBasicCacheTest {

    private static final String CACHE_NAME = "cache";

    private EmbeddedCacheManager cacheManager;
    private EmbeddedCache<String , String> cache;

    /**
     * Sets up the fixture.
     */
    @BeforeEach
    void setUpCache() {
        cacheManager = mock(EmbeddedCacheManager.class);
        when(cacheManager.isRunning(eq(CACHE_NAME))).thenReturn(Boolean.TRUE);
        cache = new EmbeddedCache<>(vertx, cacheManager, CACHE_NAME);
    }

    @Override
    protected org.infinispan.commons.api.BasicCache<Object, Object> givenAConnectedInfinispanCache() {
        @SuppressWarnings("unchecked")
        final Cache<Object, Object> result = mock(Cache.class);
        when(cacheManager.getCache(anyString())).thenReturn(result);
        when(cacheManager.getStatus()).thenReturn(ComponentStatus.RUNNING);
        return result;
    }

    @Override
    protected BasicCache<String, String> getCache() {
        return cache;
    }
}
