/**
 * Copyright (c) 2020, 2022 Contributors to the Eclipse Foundation
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.hono.test.VertxMockSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Vertx;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;


/**
 * Tests verifying behavior of the {@link BasicCache}.
 */
@ExtendWith(VertxExtension.class)
@Timeout(timeUnit = TimeUnit.SECONDS, value = 5)
abstract class AbstractBasicCacheTest {

    protected Vertx vertx;

    /**
     * Gets the cache to be tested.
     *
     * @return The not yet started cache instance.
     */
    protected abstract BasicCache<String, String> getCache();

    /**
     * Gets the Infinispan cache used by the cache instance.
     *
     * @return The connected Infinispan cache.
     */
    protected abstract org.infinispan.commons.api.BasicCache<Object, Object> givenAConnectedInfinispanCache();

    /**
     * Sets up the fixture.
     */
    @BeforeEach
    void setUpVertx() {
        vertx = mock(Vertx.class);
        VertxMockSupport.executeBlockingCodeImmediately(vertx);
    }

    /**
     * Verifies that a request to retrieve a value from the cache
     * results in the value being retrieved from the data grid.
     *
     * @param ctx The vert.x text context.
     */
    @Test
    void testGetSucceeds(final VertxTestContext ctx) {
        final var grid = givenAConnectedInfinispanCache();
        when(grid.getAsync(anyString())).thenReturn(CompletableFuture.completedFuture("value"));
        getCache().start()
            .compose(ok -> getCache().get("key"))
            .onComplete(ctx.succeeding(v -> {
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
        final var grid = givenAConnectedInfinispanCache();
        when(grid.getAsync(anyString())).thenThrow(new IllegalStateException());
        getCache().start()
            .compose(ok -> getCache().get("key"))
            .onComplete(ctx.failing(t -> {
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
        final var grid = givenAConnectedInfinispanCache();
        when(grid.putAsync(anyString(), anyString())).thenReturn(CompletableFuture.completedFuture((Void) null));
        getCache().start()
            .compose(ok -> getCache().put("key", "value"))
            .onComplete(ctx.succeeding(ok -> {
                ctx.verify(() -> {
                    verify(grid).putAsync("key", "value");
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that a request to put a value with a lifespan to the cache
     * results in the value being written to the data grid with the given lifespan.
     *
     * @param ctx The vert.x text context.
     */
    @Test
    void testPutWithLifespanSucceeds(final VertxTestContext ctx) {
        final var grid = givenAConnectedInfinispanCache();
        when(grid.putAsync(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
                .thenReturn(CompletableFuture.completedFuture((Void) null));
        getCache().start()
                .compose(ok -> getCache().put("key", "value", 1, TimeUnit.SECONDS))
                .onComplete(ctx.succeeding(v -> {
                    ctx.verify(() -> {
                        verify(grid).putAsync("key", "value", 1, TimeUnit.SECONDS);
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
        final var grid = givenAConnectedInfinispanCache();
        when(grid.putAsync(anyString(), anyString())).thenThrow(new IllegalStateException());
        getCache().start()
            .compose(ok -> getCache().put("key", "value"))
            .onComplete(ctx.failing(t -> {
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
        final var grid = givenAConnectedInfinispanCache();
        when(grid.removeAsync("key", "value"))
                .thenReturn(CompletableFuture.completedFuture(true));
        getCache().start()
                .compose(ok -> getCache().remove("key", "value"))
                .onComplete(ctx.succeeding(v -> {
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
        final var grid = givenAConnectedInfinispanCache();
        when(grid.removeAsync("key", "value"))
                .thenReturn(CompletableFuture.completedFuture(false));
        getCache().start()
                .compose(ok -> getCache().remove("key", "value"))
                .onComplete(ctx.succeeding(v -> {
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
        final var grid = givenAConnectedInfinispanCache();
        final Map<Object, Object> mapValue = new HashMap<>();
        when(grid.getAllAsync(anySet())).thenReturn(CompletableFuture.completedFuture(mapValue));
        final Set<String> keys = Set.of("key");
        getCache().start()
                .compose(ok -> getCache().getAll(keys))
                .onComplete(ctx.succeeding(v -> {
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
        final var grid = givenAConnectedInfinispanCache();
        when(grid.getAllAsync(anySet())).thenThrow(new IllegalStateException());
        final Set<String> keys = Set.of("key");
        getCache().start()
                .compose(ok -> getCache().getAll(keys))
                .onComplete(ctx.failing(t -> {
                    ctx.verify(() -> {
                        verify(grid).getAllAsync(keys);
                        assertThat(t).isInstanceOf(IllegalStateException.class);
                    });
                    ctx.completeNow();
                }));
    }
}
