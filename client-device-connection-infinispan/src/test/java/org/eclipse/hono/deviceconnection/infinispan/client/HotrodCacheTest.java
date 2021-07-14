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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.infinispan.client.hotrod.Flag;
import org.infinispan.client.hotrod.RemoteCacheContainer;
import org.infinispan.client.hotrod.configuration.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;


/**
 * Tests verifying behavior of the {@link HotrodCache}.
 *
 */
@ExtendWith(VertxExtension.class)
@Timeout(timeUnit = TimeUnit.SECONDS, value = 5)
class HotrodCacheTest extends AbstractBasicCacheTest {

    private RemoteCacheContainer remoteCacheManager;
    private HotrodCache<String, String> cache;

    /**
     * Sets up the fixture.
     */
    @BeforeEach
    void setUpCache() {
        remoteCacheManager = mock(RemoteCacheContainer.class);
        cache = new HotrodCache<>(vertx, remoteCacheManager, "cache", "testKey", "testValue");
    }

    @Override
    protected org.infinispan.client.hotrod.RemoteCache<Object, Object> givenAConnectedInfinispanCache() {
        final Configuration configuration = mock(Configuration.class);
        when(configuration.forceReturnValues()).thenReturn(false);
        @SuppressWarnings("unchecked")
        final org.infinispan.client.hotrod.RemoteCache<Object, Object> result = mock(org.infinispan.client.hotrod.RemoteCache.class);
        when(remoteCacheManager.getCache(anyString())).thenReturn(result);
        when(remoteCacheManager.getConfiguration()).thenReturn(configuration);
        when(remoteCacheManager.isStarted()).thenReturn(true);
        when(result.withFlags(Flag.FORCE_RETURN_VALUE)).thenReturn(result);
        return result;
    }

    @Override
    protected org.eclipse.hono.deviceconnection.infinispan.client.BasicCache<String, String> getCache() {
        return cache;
    }

    /**
     * Verifies that the <em>checkForCacheAvailability</em> check uses a cached connection
     * check result value if invoked shortly after a previous invocation.
     *
     * @param ctx The vert.x text context.
     */
    @Test
    void testCheckForCacheAvailabilityUsesCachedResult(final VertxTestContext ctx) {
        final org.infinispan.commons.api.BasicCache<Object, Object> grid = givenAConnectedInfinispanCache();
        when(grid.putAsync(anyString(), anyString())).thenReturn(CompletableFuture.completedFuture("oldValue"));
        getCache().start()
                .compose(c -> getCache().checkForCacheAvailability())
                // 2nd invocation is supposed to use cached value
                .compose(c -> getCache().checkForCacheAvailability())
                .onComplete(ctx.succeeding(v -> {
                    ctx.verify(() -> {
                        // putAsync() must have only been called once
                        verify(grid).putAsync(anyString(), anyString());
                    });
                    ctx.completeNow();
                }));
    }
}
