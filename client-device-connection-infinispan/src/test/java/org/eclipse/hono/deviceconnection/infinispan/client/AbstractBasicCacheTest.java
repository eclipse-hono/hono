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
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

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
abstract class AbstractBasicCacheTest {

    protected Vertx vertx;
    protected BasicCache<String, String> cache;

    protected abstract org.infinispan.commons.api.BasicCache<Object, Object> givenAConnectedCache();

    /**
     * Sets up the fixture.
     */
    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUpVertx() {
        vertx = mock(Vertx.class);
        doAnswer(invocation -> {
            final Promise<Void> result = Promise.promise();
            final Handler<Promise<?>> blockingCodeHandler = invocation.getArgument(0);
            final Handler<Promise<?>> resultHandler = invocation.getArgument(1);
            blockingCodeHandler.handle(result);
            resultHandler.handle(result);
            return null;
        }).when(vertx).executeBlocking(any(Handler.class), any(Handler.class));
    }

    /**
     * Verifies that a request to retrieve a value from the cache
     * results in the value being retrieved from the data grid.
     *
     * @param ctx The vert.x text context.
     */
    @Test
    void testGetSucceeds(final VertxTestContext ctx) {
        final org.infinispan.commons.api.BasicCache<Object, Object> grid = givenAConnectedCache();
        when(grid.getAsync(anyString())).thenReturn(CompletableFuture.completedFuture("value"));
        cache.connect()
            .compose(c -> c.get("key"))
            .setHandler(ctx.succeeding(v -> {
                ctx.verify(() -> {
                    verify(grid).getAsync("key");
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
        final org.infinispan.commons.api.BasicCache<Object, Object> grid = givenAConnectedCache();
        when(grid.getAsync(anyString())).thenThrow(new IllegalStateException());
        cache.connect()
            .compose(c -> c.get("key"))
            .setHandler(ctx.failing(t -> {
                ctx.verify(() -> {
                    verify(grid).getAsync("key");
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
        final org.infinispan.commons.api.BasicCache<Object, Object> grid = givenAConnectedCache();
        when(grid.putAsync(anyString(), anyString())).thenReturn(CompletableFuture.completedFuture("oldValue"));
        cache.connect()
            .compose(c -> c.put("key", "value"))
            .setHandler(ctx.succeeding(v -> {
                ctx.verify(() -> {
                    verify(grid).putAsync("key", "value");
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
        final org.infinispan.commons.api.BasicCache<Object, Object> grid = givenAConnectedCache();
        when(grid.putAsync(anyString(), anyString())).thenThrow(new IllegalStateException());
        cache.connect()
            .compose(c -> c.put("key", "value"))
            .setHandler(ctx.failing(t -> {
                ctx.verify(() -> {
                    verify(grid).putAsync("key", "value");
                    assertThat(t).isInstanceOf(IllegalStateException.class);
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that a request to remove a cache entry with a version
     * results in the value being removed in the data grid.
     *
     * @param ctx The vert.x text context.
     */
    @Test
    void testRemoveWithValueSucceeds(final VertxTestContext ctx) {
        final org.infinispan.commons.api.BasicCache<Object, Object> grid = givenAConnectedCache();
        when(grid.removeAsync(anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(true));
        cache.connect()
                .compose(c -> c.remove("key", "value"))
                .setHandler(ctx.succeeding(v -> {
                    ctx.verify(() -> {
                        verify(grid).removeAsync("key", "value");
                        assertThat(v).isTrue();
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that a request to remove a cache entry with a version
     * fails with the root cause for the failure to access the data grid.
     *
     * @param ctx The vert.x text context.
     */
    @Test
    void testRemoveWithValueFails(final VertxTestContext ctx) {
        final org.infinispan.commons.api.BasicCache<Object, Object> grid = givenAConnectedCache();
        when(grid.removeAsync(anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(false));
        cache.connect()
                .compose(c -> c.remove("key", "value"))
                .setHandler(ctx.succeeding(v -> {
                    ctx.verify(() -> {
                        verify(grid).removeAsync("key", "value");
                        assertThat(v).isFalse();
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that a request to get a map of all cache entries with given keys
     * results in the map value being retrieved from the data grid.
     *
     * @param ctx The vert.x text context.
     */
    @Test
    void testGetAllSucceeds(final VertxTestContext ctx) {
        final org.infinispan.commons.api.BasicCache<Object, Object> grid = givenAConnectedCache();
        final Map<Object, Object> mapValue = new HashMap<>();
        when(grid.getAllAsync(anySet())).thenReturn(CompletableFuture.completedFuture(mapValue));
        final Set<String> keys = Set.of("key");
        cache.connect()
                .compose(c -> c.getAll(keys))
                .setHandler(ctx.succeeding(v -> {
                    ctx.verify(() -> {
                        verify(grid).getAllAsync(keys);
                        assertThat(v).isEqualTo(mapValue);
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that a request to get a map of all cache entries with given keys
     * fails with the root cause for the failure to access the data grid.
     *
     * @param ctx The vert.x text context.
     */
    @Test
    void testGetAllFails(final VertxTestContext ctx) {
        final org.infinispan.commons.api.BasicCache<Object, Object> grid = givenAConnectedCache();
        when(grid.getAllAsync(anySet())).thenThrow(new IllegalStateException());
        final Set<String> keys = Set.of("key");
        cache.connect()
                .compose(c -> c.getAll(keys))
                .setHandler(ctx.failing(t -> {
                    ctx.verify(() -> {
                        verify(grid).getAllAsync(keys);
                        assertThat(t).isInstanceOf(IllegalStateException.class);
                    });
                    ctx.completeNow();
                }));
    }

}
