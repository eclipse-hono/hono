/*******************************************************************************
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

package org.eclipse.hono.deviceconnection.infinispan;

import static org.eclipse.hono.util.Constants.DEFAULT_TENANT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.HttpURLConnection;
import java.util.concurrent.CompletableFuture;

import org.eclipse.hono.util.DeviceConnectionResult;
import org.infinispan.client.hotrod.RemoteCacheContainer;
import org.infinispan.commons.api.BasicCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.opentracing.Span;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;


/**
 * Tests verifying behavior of {@link RemoteCacheBasedDeviceConnectionService}.
 *
 */
@ExtendWith(VertxExtension.class)
public class RemoteCacheBasedDeviceConnectionServiceTest {

    private RemoteCacheBasedDeviceConnectionService svc;
    private Span span;
    private BasicCache<Object, Object> cache;
    private RemoteCacheContainer cacheManager;

    /**
     * Sets up fixture.
     */
    @SuppressWarnings("unchecked")
    @BeforeEach
    public void setUp() {
        span = mock(Span.class);
        cache = mock(BasicCache.class);
        cacheManager = mock(RemoteCacheContainer.class);
        when(cacheManager.getCache("device-connection")).thenReturn(cache);

        svc = new RemoteCacheBasedDeviceConnectionService();
        svc.setCacheManager(cacheManager);
    }

    @SuppressWarnings("unchecked")
    private Future<Void> givenAStartedService() {

        final Context ctx = mock(Context.class);
        doAnswer(invocation -> {
            final Future<RemoteCacheContainer> result = Future.future();
            final Handler<Future<RemoteCacheContainer>> blockingCode = invocation.getArgument(0);
            final Handler<AsyncResult<RemoteCacheContainer>> resultHandler = invocation.getArgument(1);
            blockingCode.handle(result);
            resultHandler.handle(result);
            return null;
        }).when(ctx).executeBlocking(any(Handler.class), any(Handler.class));
        final EventBus eventBus = mock(EventBus.class);
        when(eventBus.consumer(anyString())).thenReturn(mock(MessageConsumer.class));
        final Vertx vertx = mock(Vertx.class);
        when(vertx.eventBus()).thenReturn(eventBus);

        final Future<Void> startFuture = Future.future();
        svc.init(vertx, ctx);
        svc.start(startFuture);
        return startFuture;
    }

    /**
     * Verifies that the last known gateway id can be set via the <em>setLastKnownGatewayForDevice</em> operation.
     *
     * @param ctx The vert.x context.
     */
    @Test
    public void testSetLastKnownGatewayForDevice(final VertxTestContext ctx) {

        final String deviceId = "testDevice";
        final String gatewayId = "testGateway";
        when(cache.putAsync(anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));

        givenAStartedService()
        .compose(ok -> {
            final Future<DeviceConnectionResult> setLastGwResult = Future.future();
            svc.setLastKnownGatewayForDevice(DEFAULT_TENANT, deviceId, gatewayId, span, setLastGwResult);
            return setLastGwResult;
        })
        .setHandler(ctx.succeeding(result -> {
            ctx.verify(() -> {
                assertEquals(HttpURLConnection.HTTP_NO_CONTENT, result.getStatus());
                verify(cache).putAsync(anyString(), eq(gatewayId));
            });
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that the <em>getLastKnownGatewayForDevice</em> operation fails if no such entry is associated
     * with the given device.
     *
     * @param ctx The vert.x context.
     */
    @Test
    public void testGetLastKnownGatewayForDeviceNotFound(final VertxTestContext ctx) {

        final String deviceId = "testDevice";
        final CompletableFuture<Object> result = CompletableFuture.completedFuture(null);
        when(cache.getAsync(anyString())).thenReturn(result);

        givenAStartedService()
        .compose(ok -> {
            final Future<DeviceConnectionResult> getLastGwResult = Future.future();
            svc.getLastKnownGatewayForDevice(DEFAULT_TENANT, deviceId, span, getLastGwResult);
            return getLastGwResult;
        })
        .setHandler(ctx.succeeding(deviceConnectionResult -> {
            ctx.verify(() -> {
                assertEquals(HttpURLConnection.HTTP_NOT_FOUND, deviceConnectionResult.getStatus());
                assertNull(deviceConnectionResult.getPayload());
            });
            ctx.completeNow();
        }));
    }
}
