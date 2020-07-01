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

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheContainer;
import org.infinispan.client.hotrod.impl.MetadataValueImpl;
import org.infinispan.commons.api.BasicCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;


/**
 * Tests verifying behavior of the {@link HotrodCache}.
 *
 */
@ExtendWith(VertxExtension.class)
@Timeout(timeUnit = TimeUnit.SECONDS, value = 5)
class HotrodCacheTest extends AbstractBasicCacheTest {

    private RemoteCacheContainer remoteCacheManager;

    /**
     * Sets up the fixture.
     */
    @BeforeEach
    void setUpCache() {
        remoteCacheManager = mock(RemoteCacheContainer.class);
        cache = new HotrodCache<>(vertx, remoteCacheManager, "cache", "testKey", "testValue");
    }

    @Override
    protected org.infinispan.client.hotrod.RemoteCache<Object, Object> givenAConnectedCache() {
        @SuppressWarnings("unchecked")
        final org.infinispan.client.hotrod.RemoteCache<Object, Object> result = mock(org.infinispan.client.hotrod.RemoteCache.class);
        when(remoteCacheManager.getCache(anyString())).thenReturn(result);
        return result;
    }

    @Override
    protected void mockRemoveWithValue(final BasicCache<Object, Object> cache, final String key, final Object value,
            final boolean removeOperationResult) {
        final org.infinispan.client.hotrod.RemoteCache<Object, Object> remoteCache = (RemoteCache<Object, Object>) cache;
        if (removeOperationResult) {
            final long version = 1;
            when(remoteCache.getWithMetadataAsync(eq(key)))
                    .thenReturn(CompletableFuture.completedFuture(new MetadataValueImpl<>(-1, -1, -1, -1,
                            version, value)));
            when(remoteCache.removeWithVersionAsync(eq(key), eq(version)))
                    .thenReturn(CompletableFuture.completedFuture(true));
        } else {
            when(remoteCache.getWithMetadataAsync(eq(key))).thenReturn(CompletableFuture.completedFuture(null));
        }
    }

    @Override
    protected void verifyRemoveWithValue(final BasicCache<Object, Object> cache, final String key,
            final Object value, final boolean expectedRemoveOperationResult) {
        final org.infinispan.client.hotrod.RemoteCache<Object, Object> remoteCache = (RemoteCache<Object, Object>) cache;
        verify(remoteCache).getWithMetadataAsync(key);
        if (expectedRemoveOperationResult) {
            verify(remoteCache).removeWithVersionAsync(eq(key), anyLong());
        }
    }
}
