/*******************************************************************************
 * Copyright (c) 2016, 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.HttpURLConnection;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.eclipse.hono.client.CommandContext;
import org.eclipse.hono.client.CommandTargetMapper;
import org.eclipse.hono.client.DeviceConnectionClient;
import org.eclipse.hono.client.DeviceConnectionClientFactory;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.ProtocolAdapterCommandConsumer;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.ResourceIdentifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.proton.ProtonMessageHandler;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonReceiver;


/**
 * Verifies behavior of {@link ProtocolAdapterCommandConsumerFactoryImpl}.
 *
 */
@ExtendWith(VertxExtension.class)
@Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
public class ProtocolAdapterCommandConsumerFactoryImplTest {

    private Vertx vertx;
    private Context context;
    private ClientConfigProperties props;
    private HonoConnection connection;
    private ProtocolAdapterCommandConsumerFactoryImpl commandConsumerFactory;
    private CommandTargetMapper commandTargetMapper;
    private DeviceConnectionClient devConClient;
    private ProtonReceiver adapterInstanceCommandReceiver;
    private ProtonReceiver mappingAndDelegatingCommandReceiver;
    private String adapterInstanceCommandConsumerAddress;
    private String tenantCommandAddress;
    private String tenantId;
    private String deviceId;
    private String gatewayId;
    private String adapterInstanceId;

    /**
     * Sets up fixture.
     */
    @BeforeEach
    public void setUp() {

        vertx = mock(Vertx.class);
        context = HonoClientUnitTestHelper.mockContext(vertx);
        when(vertx.getOrCreateContext()).thenReturn(context);
        doAnswer(invocation -> {
            final Handler<Void> handler = invocation.getArgument(1);
            handler.handle(null);
            return null;
        }).when(vertx).setTimer(anyLong(), VertxMockSupport.anyHandler());
        final EventBus eventBus = mock(EventBus.class);
        when(vertx.eventBus()).thenReturn(eventBus);

        deviceId = "theDevice";
        gatewayId = "theGateway";
        tenantId = "theTenant";
        adapterInstanceId = UUID.randomUUID().toString();

        props = new ClientConfigProperties();

        connection = HonoClientUnitTestHelper.mockHonoConnection(vertx, props);
        when(connection.getContainerId()).thenReturn(adapterInstanceId);

        adapterInstanceCommandReceiver = mock(ProtonReceiver.class);
        adapterInstanceCommandConsumerAddress = CommandConstants.INTERNAL_COMMAND_ENDPOINT + "/" + adapterInstanceId;

        when(connection.isConnected(anyLong())).thenReturn(Future.succeededFuture());
        when(connection.createReceiver(
                eq(adapterInstanceCommandConsumerAddress),
                any(ProtonQoS.class),
                any(ProtonMessageHandler.class),
                anyInt(),
                anyBoolean(),
                VertxMockSupport.anyHandler())).thenReturn(Future.succeededFuture(adapterInstanceCommandReceiver));
        mappingAndDelegatingCommandReceiver = mock(ProtonReceiver.class);
        tenantCommandAddress = ResourceIdentifier.from(CommandConstants.NORTHBOUND_COMMAND_REQUEST_ENDPOINT, tenantId, null).toString();
        when(connection.createReceiver(
                eq(tenantCommandAddress),
                any(ProtonQoS.class),
                any(ProtonMessageHandler.class),
                anyInt(),
                anyBoolean(),
                VertxMockSupport.anyHandler())).thenReturn(Future.succeededFuture(mappingAndDelegatingCommandReceiver));
        commandTargetMapper = mock(CommandTargetMapper.class);
        devConClient = mock(DeviceConnectionClient.class);
        final DeviceConnectionClientFactory deviceConnectionClientFactory = mock(DeviceConnectionClientFactory.class);
        when(deviceConnectionClientFactory.connect()).thenReturn(Future.succeededFuture(mock(HonoConnection.class)));
        when(deviceConnectionClientFactory.getOrCreateDeviceConnectionClient(anyString()))
                .thenReturn(Future.succeededFuture(devConClient));
        when(devConClient.setCommandHandlingAdapterInstance(anyString(), anyString(), any(), anyBoolean(), any()))
                .thenReturn(Future.succeededFuture());
        when(devConClient.removeCommandHandlingAdapterInstance(anyString(), anyString(), any()))
                .thenReturn(Future.succeededFuture(Boolean.TRUE));

        commandConsumerFactory = new ProtocolAdapterCommandConsumerFactoryImpl(connection);
        commandConsumerFactory.initialize(commandTargetMapper, deviceConnectionClientFactory);
    }

    /**
     * Verifies that an attempt to open a command consumer fails if the peer
     * rejects to open a receiver link.
     *
     * @param ctx The test context.
     */
    @Test
    public void testCreateCommandConsumerFailsIfPeerRejectsLink(final VertxTestContext ctx) {

        final Handler<CommandContext> commandHandler = VertxMockSupport.mockHandler();
        final ServerErrorException ex = new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE);
        when(connection.createReceiver(
                anyString(),
                any(ProtonQoS.class),
                any(ProtonMessageHandler.class),
                anyInt(),
                anyBoolean(),
                VertxMockSupport.anyHandler()))
        .thenReturn(Future.failedFuture(ex));

        commandConsumerFactory.createCommandConsumer(tenantId, deviceId, commandHandler, null, null)
            .onComplete(ctx.failing(t -> {
                ctx.verify(() -> assertThat(((ServiceInvocationException) t).getErrorCode()).isEqualTo(HttpURLConnection.HTTP_UNAVAILABLE));
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that creating a command consumer successfully creates a tenant-scoped
     * receiver link and registers the command handling adapter instance.
     *
     * @param ctx The test context.
     */
    @Test
    public void testCreateCommandConsumerSucceeds(final VertxTestContext ctx) {

        final Handler<CommandContext> commandHandler = VertxMockSupport.mockHandler();

        commandConsumerFactory.createCommandConsumer(tenantId, deviceId, commandHandler, null, null)
            .onComplete(ctx.succeeding(c -> {
                ctx.verify(() -> {
                    verify(connection).createReceiver(eq(tenantCommandAddress), eq(ProtonQoS.AT_LEAST_ONCE), any(), anyInt(),
                            eq(false), any());
                    verify(devConClient).setCommandHandlingAdapterInstance(eq(deviceId), anyString(), any(), eq(false), any());
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that creating a command consumer with a positive lifespan and
     * closing the consumer afterwards succeeds.
     *
     * @param ctx The test context.
     */
    @Test
    public void testCreateTimeLimitedCommandConsumerSucceeds(final VertxTestContext ctx) {

        final Handler<CommandContext> commandHandler = VertxMockSupport.mockHandler();

        final Duration lifespan = Duration.ofSeconds(10);
        final Future<ProtocolAdapterCommandConsumer> commandConsumerFuture = commandConsumerFactory.createCommandConsumer(tenantId,
                deviceId, commandHandler, lifespan, null);
        commandConsumerFuture.onComplete(ctx.succeeding(consumer -> {
            ctx.verify(() -> {
                verify(connection).createReceiver(eq(tenantCommandAddress), eq(ProtonQoS.AT_LEAST_ONCE), any(), anyInt(),
                        eq(false), any());
                verify(devConClient).setCommandHandlingAdapterInstance(eq(deviceId), anyString(), any(), eq(false), any());
                // verify closing the consumer is successful
                consumer.close(any()).onComplete(ctx.succeeding(v -> {
                    ctx.verify(() -> {
                        // verify command handling adapter instance has been explicitly removed (since lifespan hasn't elapsed yet)
                        verify(devConClient).removeCommandHandlingAdapterInstance(eq(deviceId), anyString(), any());
                    });
                    ctx.completeNow();
                }));
            });
        }));
    }

}
