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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.infinispan.client.hotrod.MetadataValue;
import org.infinispan.client.hotrod.RemoteCacheContainer;
import org.infinispan.client.hotrod.configuration.Configuration;
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
        cache = new HotrodCache<>(vertx, remoteCacheManager, "cache", "testKey", "testValue");
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

    /**
     * Verifies that a request to remove a cache entry with a version
     * results in the value being removed in the data grid.
     *
     * @param ctx The vert.x text context.
     */
    @Test
    void testRemoveWithVersionSucceeds(final VertxTestContext ctx) {
        final org.infinispan.client.hotrod.RemoteCache<Object, Object> grid = givenAConnectedCache();
        when(grid.removeWithVersion(anyString(), anyLong())).thenReturn(true);
        cache.connect()
                .compose(c -> c.removeWithVersion("key", 1L))
                .setHandler(ctx.succeeding(v -> {
                    ctx.verify(() -> {
                        verify(grid).removeWithVersion("key", 1L);
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
    void testRemoveWithVersionFails(final VertxTestContext ctx) {
        final org.infinispan.client.hotrod.RemoteCache<Object, Object> grid = givenAConnectedCache();
        when(grid.removeWithVersion(anyString(), anyLong())).thenThrow(new IllegalStateException());
        cache.connect()
                .compose(c -> c.removeWithVersion("key", 1L))
                .setHandler(ctx.failing(t -> {
                    ctx.verify(() -> {
                        verify(grid).removeWithVersion("key", 1L);
                        assertThat(t).isInstanceOf(IllegalStateException.class);
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that a request to get a cache entry along with its version
     * results in the value being retrieved from the data grid.
     *
     * @param ctx The vert.x text context.
     */
    @Test
    void testGetWithVersionSucceeds(final VertxTestContext ctx) {
        final org.infinispan.client.hotrod.RemoteCache<Object, Object> grid = givenAConnectedCache();
        @SuppressWarnings("unchecked")
        final MetadataValue<Object> metadataValue = mock(MetadataValue.class);
        final Object value = "testValue";
        when(metadataValue.getValue()).thenReturn(value);
        final long version = 1L;
        when(metadataValue.getVersion()).thenReturn(version);
        when(grid.getWithMetadata(anyString())).thenReturn(metadataValue);
        cache.connect()
                .compose(c -> c.getWithVersion("key"))
                .setHandler(ctx.succeeding(v -> {
                    ctx.verify(() -> {
                        verify(grid).getWithMetadata("key");
                        assertThat(v.getVersion()).isEqualTo(version);
                        assertThat(v.getValue()).isEqualTo(value);
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that a request to get a cache entry along with its version
     * fails with the root cause for the failure to access the data grid.
     *
     * @param ctx The vert.x text context.
     */
    @Test
    void testGetWithVersionFails(final VertxTestContext ctx) {
        final org.infinispan.client.hotrod.RemoteCache<Object, Object> grid = givenAConnectedCache();
        when(grid.getWithMetadata(anyString())).thenThrow(new IllegalStateException());
        cache.connect()
                .compose(c -> c.getWithVersion("key"))
                .setHandler(ctx.failing(t -> {
                    ctx.verify(() -> {
                        verify(grid).getWithMetadata("key");
                        assertThat(t).isInstanceOf(IllegalStateException.class);
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
        final org.infinispan.client.hotrod.RemoteCache<Object, Object> grid = givenAConnectedCache();
        final Map<Object, Object> mapValue = new HashMap<>();
        when(grid.getAll(anySet())).thenReturn(mapValue);
        final Set<String> keys = Set.of("key");
        cache.connect()
                .compose(c -> c.getAll(keys))
                .setHandler(ctx.succeeding(v -> {
                    ctx.verify(() -> {
                        verify(grid).getAll(keys);
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
        final org.infinispan.client.hotrod.RemoteCache<Object, Object> grid = givenAConnectedCache();
        when(grid.getAll(anySet())).thenThrow(new IllegalStateException());
        final Set<String> keys = Set.of("key");
        cache.connect()
                .compose(c -> c.getAll(keys))
                .setHandler(ctx.failing(t -> {
                    ctx.verify(() -> {
                        verify(grid).getAll(keys);
                        assertThat(t).isInstanceOf(IllegalStateException.class);
                    });
                    ctx.completeNow();
                }));
    }

    private org.infinispan.client.hotrod.RemoteCache<Object, Object> givenAConnectedCache() {
        final Configuration configuration = mock(Configuration.class);
        @SuppressWarnings("unchecked")
        final org.infinispan.client.hotrod.RemoteCache<Object, Object> result = mock(org.infinispan.client.hotrod.RemoteCache.class);
        when(remoteCacheManager.getCache(anyString(), anyBoolean())).thenReturn(result);
        when(remoteCacheManager.getConfiguration()).thenReturn(configuration);
        when(configuration.forceReturnValues()).thenReturn(false);
        return result;
    }
}
