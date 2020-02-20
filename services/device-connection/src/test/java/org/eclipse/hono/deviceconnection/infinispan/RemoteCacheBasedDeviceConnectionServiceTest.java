/*******************************************************************************
 * Copyright (c) 2019, 2020 Contributors to the Eclipse Foundation
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.HttpURLConnection;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.deviceconnection.infinispan.client.DeviceConnectionInfo;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.DeviceConnectionResult;
import org.infinispan.client.hotrod.RemoteCacheContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
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
    private DeviceConnectionInfo cache;

    /**
     * Sets up fixture.
     */
    @BeforeEach
    public void setUp() {
        final SpanContext spanContext = mock(SpanContext.class);
        span = mock(Span.class);
        when(span.context()).thenReturn(spanContext);
        cache = mock(DeviceConnectionInfo.class);
        svc = new RemoteCacheBasedDeviceConnectionService(cache);
    }

    @SuppressWarnings("unchecked")
    private Future<Void> givenAStartedService() {

        final Vertx vertx = mock(Vertx.class);
        doAnswer(invocation -> {
            final Promise<RemoteCacheContainer> result = Promise.promise();
            final Handler<Future<RemoteCacheContainer>> blockingCode = invocation.getArgument(0);
            final Handler<AsyncResult<RemoteCacheContainer>> resultHandler = invocation.getArgument(1);
            blockingCode.handle(result.future());
            resultHandler.handle(result.future());
            return null;
        }).when(vertx).executeBlocking(any(Handler.class), any(Handler.class));
        final EventBus eventBus = mock(EventBus.class);
        when(eventBus.consumer(anyString())).thenReturn(mock(MessageConsumer.class));
        when(vertx.eventBus()).thenReturn(eventBus);
        final Context ctx = mock(Context.class);

        final Promise<Void> startPromise = Promise.promise();
        svc.init(vertx, ctx);
        svc.start(startPromise);
        return startPromise.future();
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
        when(cache.setLastKnownGatewayForDevice(anyString(), anyString(), anyString(), any(SpanContext.class)))
            .thenReturn(Future.succeededFuture());

        givenAStartedService()
        .compose(ok -> {
            final Promise<DeviceConnectionResult> setLastGwResult = Promise.promise();
            svc.setLastKnownGatewayForDevice(Constants.DEFAULT_TENANT, deviceId, gatewayId, span, setLastGwResult);
            return setLastGwResult.future();
        })
        .setHandler(ctx.succeeding(result -> {
            ctx.verify(() -> {
                assertThat(result.getStatus()).isEqualTo(HttpURLConnection.HTTP_NO_CONTENT);
                verify(cache).setLastKnownGatewayForDevice(eq(Constants.DEFAULT_TENANT), eq(deviceId), eq(gatewayId), any(SpanContext.class));
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
        when(cache.getLastKnownGatewayForDevice(anyString(), anyString(), any(SpanContext.class)))
            .thenReturn(Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_NOT_FOUND)));

        givenAStartedService()
        .compose(ok -> {
            final Promise<DeviceConnectionResult> getLastGwResult = Promise.promise();
            svc.getLastKnownGatewayForDevice(Constants.DEFAULT_TENANT, deviceId, span, getLastGwResult);
            return getLastGwResult.future();
        })
        .setHandler(ctx.succeeding(deviceConnectionResult -> {
            ctx.verify(() -> {
                assertThat(deviceConnectionResult.getStatus()).isEqualTo(HttpURLConnection.HTTP_NOT_FOUND);
                assertThat(deviceConnectionResult.getPayload()).isNull();
            });
            ctx.completeNow();
        }));
    }
}
