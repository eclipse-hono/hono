/**
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
 */


package org.eclipse.hono.deviceconnection.infinispan.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.TimeUnit;

import org.infinispan.client.hotrod.RemoteCacheContainer;
import org.infinispan.commons.api.BasicCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;


/**
 * Tests verifying behavior of the {@link HotrodCache}.
 *
 */
@ExtendWith(VertxExtension.class)
@Timeout(timeUnit = TimeUnit.SECONDS, value = 5)
class HotrodCacheTest {

    private RemoteCacheContainer remoteCacheManager;
    private Vertx vertx;
    private HotrodCache<String, String> cache;

    /**
     * Sets up the fixture.
     */
    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        vertx = mock(Vertx.class);
        doAnswer(invocation -> {
            final Promise<Void> result = Promise.promise();
            final Handler<Promise<?>> blockingCodeHandler = invocation.getArgument(0);
            final Handler<Promise<?>> resultHandler = invocation.getArgument(1);
            blockingCodeHandler.handle(result);
            resultHandler.handle(result);
            return null;
        }).when(vertx).executeBlocking(any(Handler.class), any(Handler.class));
        remoteCacheManager = mock(RemoteCacheContainer.class);
        cache = new HotrodCache<String, String>(vertx, remoteCacheManager, "cache", "testKey", "testValue");
    }

    /**
     * Verifies that a request to retrieve a value from the cache
     * results in the value being retrieved from the data grid.
     * 
     * @param ctx The vert.x text context.
     */
    @Test
    void testGetSucceeds(final VertxTestContext ctx) {
        final BasicCache<Object, Object> grid = givenAConnectedCache();
        when(grid.get(anyString())).thenReturn("value");
        cache.connect()
            .compose(c -> c.get("key"))
            .setHandler(ctx.succeeding(v -> {
                ctx.verify(() -> {
                    verify(grid).get("key");
                    assertThat(v).isEqualTo("value");
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that a request to retrieve a value from the cache fails with the
     * root cause for the failure to access the data grid.
     * 
     * @param ctx The vert.x text context.
     */
    @Test
    void testGetFails(final VertxTestContext ctx) {
        final BasicCache<Object, Object> grid = givenAConnectedCache();
        when(grid.get(anyString())).thenThrow(new IllegalStateException());
        cache.connect()
            .compose(c -> c.get("key"))
            .setHandler(ctx.failing(t -> {
                ctx.verify(() -> {
                    verify(grid).get("key");
                    assertThat(t).isInstanceOf(IllegalStateException.class);
                });
                ctx.completeNow();
            }));
    }
    /**
     * Verifies that a request to put a value to the cache
     * results in the value being written to the data grid.
     * 
     * @param ctx The vert.x text context.
     */
    @Test
    void testPutSucceeds(final VertxTestContext ctx) {
        final BasicCache<Object, Object> grid = givenAConnectedCache();
        when(grid.put(anyString(), anyString())).thenReturn("oldValue");
        cache.connect()
            .compose(c -> c.put("key", "value"))
            .setHandler(ctx.succeeding(v -> {
                ctx.verify(() -> {
                    verify(grid).put("key", "value");
                    assertThat(v).isEqualTo("oldValue");
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that a request to put a value to the cache fails with the
     * root cause for the failure to access the data grid.
     * 
     * @param ctx The vert.x text context.
     */
    @Test
    void testPutFails(final VertxTestContext ctx) {
        final BasicCache<Object, Object> grid = givenAConnectedCache();
        when(grid.put(anyString(), anyString())).thenThrow(new IllegalStateException());
        cache.connect()
            .compose(c -> c.put("key", "value"))
            .setHandler(ctx.failing(t -> {
                ctx.verify(() -> {
                    verify(grid).put("key", "value");
                    assertThat(t).isInstanceOf(IllegalStateException.class);
                });
                ctx.completeNow();
            }));
    }

    private BasicCache<Object, Object> givenAConnectedCache() {
        @SuppressWarnings("unchecked")
        final BasicCache<Object, Object> result = mock(BasicCache.class);
        when(remoteCacheManager.getCache(anyString())).thenReturn(result);
        return result;
    }
}
