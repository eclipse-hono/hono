/*******************************************************************************
 * Copyright (c) 2016, 2021 Contributors to the Eclipse Foundation
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static com.google.common.truth.Truth.assertThat;

import java.net.HttpURLConnection;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.qpid.proton.amqp.transport.Source;
import org.eclipse.hono.client.CommandContext;
import org.eclipse.hono.client.CommandTargetMapper;
import org.eclipse.hono.client.DeviceConnectionClient;
import org.eclipse.hono.client.DeviceConnectionClientFactory;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.ProtocolAdapterCommandConsumer;
import org.eclipse.hono.client.ProtocolAdapterCommandConsumerFactory;
import org.eclipse.hono.client.SendMessageSampler;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.test.VertxMockSupport;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.ResourceIdentifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
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
    private HonoConnection connection;
    private DeviceConnectionClientFactory deviceConnectionClientFactory;
    private CommandTargetMapper commandTargetMapper;
    private DeviceConnectionClient devConClient;
    private ProtonReceiver mappingAndDelegatingCommandReceiver;
    private String tenantCommandAddress;
    private String tenantId;
    private String deviceId;

    /**
     * Sets up fixture.
     */
    @BeforeEach
    public void setUp() {

        vertx = mock(Vertx.class);
        final Context context = VertxMockSupport.mockContext(vertx);
        when(vertx.getOrCreateContext()).thenReturn(context);
        VertxMockSupport.runTimersImmediately(vertx);
        final EventBus eventBus = mock(EventBus.class);
        when(vertx.eventBus()).thenReturn(eventBus);

        deviceId = "theDevice";
        tenantId = "theTenant";
        final String adapterInstanceId = UUID.randomUUID().toString();

        final ClientConfigProperties props = new ClientConfigProperties();

        connection = HonoClientUnitTestHelper.mockHonoConnection(vertx, props);
        when(connection.getContainerId()).thenReturn(adapterInstanceId);

        final ProtonReceiver adapterInstanceCommandReceiver = HonoClientUnitTestHelper.mockProtonReceiver();
        when(adapterInstanceCommandReceiver.getSource()).thenReturn(mock(Source.class));

        when(connection.isConnected(anyLong())).thenReturn(Future.succeededFuture());
        when(connection.createReceiver(
                startsWith(CommandConstants.INTERNAL_COMMAND_ENDPOINT),
                any(ProtonQoS.class),
                any(ProtonMessageHandler.class),
                anyInt(),
                anyBoolean(),
                VertxMockSupport.anyHandler())).thenReturn(Future.succeededFuture(adapterInstanceCommandReceiver));
        mappingAndDelegatingCommandReceiver = HonoClientUnitTestHelper.mockProtonReceiver();
        when(mappingAndDelegatingCommandReceiver.getSource()).thenReturn(mock(Source.class));
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
        deviceConnectionClientFactory = mock(DeviceConnectionClientFactory.class);
        when(deviceConnectionClientFactory.connect()).thenReturn(Future.succeededFuture(mock(HonoConnection.class)));
        when(deviceConnectionClientFactory.getOrCreateDeviceConnectionClient(anyString()))
                .thenReturn(Future.succeededFuture(devConClient));
        when(devConClient.setCommandHandlingAdapterInstance(anyString(), anyString(), any(), any()))
                .thenReturn(Future.succeededFuture());
        when(devConClient.removeCommandHandlingAdapterInstance(anyString(), anyString(), any()))
                .thenReturn(Future.succeededFuture());
    }

    private ProtocolAdapterCommandConsumerFactoryImpl createCommandConsumerFactory() {
        final ProtocolAdapterCommandConsumerFactoryImpl factory = new ProtocolAdapterCommandConsumerFactoryImpl(
                connection, SendMessageSampler.Factory.noop());
        factory.initialize(
                commandTargetMapper,
                ProtocolAdapterCommandConsumerFactory.createCommandHandlingAdapterInfoAccess(deviceConnectionClientFactory));
        return factory;
    }

    /**
     * Verifies that an attempt to open a command consumer fails if the peer
     * rejects to open a receiver link.
     *
     * @param ctx The test context.
     */
    @Test
    public void testCreateCommandConsumerFailsIfPeerRejectsLink(final VertxTestContext ctx) {

        final ProtocolAdapterCommandConsumerFactoryImpl commandConsumerFactory = createCommandConsumerFactory();

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
                ctx.verify(() -> {
                    assertThat(ServiceInvocationException.extractStatusCode(t)).isEqualTo(HttpURLConnection.HTTP_UNAVAILABLE);
                });
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

        final ProtocolAdapterCommandConsumerFactoryImpl commandConsumerFactory = createCommandConsumerFactory();

        final Handler<CommandContext> commandHandler = VertxMockSupport.mockHandler();

        commandConsumerFactory.createCommandConsumer(tenantId, deviceId, commandHandler, null, null)
            .onComplete(ctx.succeeding(c -> {
                ctx.verify(() -> {
                    verify(connection).createReceiver(eq(tenantCommandAddress), eq(ProtonQoS.AT_LEAST_ONCE), any(), anyInt(),
                            eq(false), any());
                    verify(devConClient).setCommandHandlingAdapterInstance(eq(deviceId), anyString(), any(), any());
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

        final ProtocolAdapterCommandConsumerFactoryImpl commandConsumerFactory = createCommandConsumerFactory();

        final Handler<CommandContext> commandHandler = VertxMockSupport.mockHandler();

        final Duration lifespan = Duration.ofSeconds(10);
        final Future<ProtocolAdapterCommandConsumer> commandConsumerFuture = commandConsumerFactory.createCommandConsumer(tenantId,
                deviceId, commandHandler, lifespan, null);
        commandConsumerFuture.onComplete(ctx.succeeding(consumer -> {
            ctx.verify(() -> {
                verify(connection).createReceiver(eq(tenantCommandAddress), eq(ProtonQoS.AT_LEAST_ONCE), any(), anyInt(),
                        eq(false), any());
                verify(devConClient).setCommandHandlingAdapterInstance(eq(deviceId), anyString(), any(), any());
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

    /**
     * Verifies that upon getting a tenant timeout notification, the tenant-scoped
     * consumer link is closed if there are no consumers for that tenant registered
     * anymore.
     *
     * @param ctx The test context.
     * @throws InterruptedException If the test execution gets interrupted.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testTenantTimeoutClosesTenantLink(final VertxTestContext ctx) throws InterruptedException {

        final ArgumentCaptor<Handler<Message<Object>>> eventBusMsgHandler = ArgumentCaptor.forClass(Handler.class);
        when(vertx.eventBus().consumer(eq(Constants.EVENT_BUS_ADDRESS_TENANT_TIMED_OUT), eventBusMsgHandler.capture()))
                .thenReturn(mock(io.vertx.core.eventbus.MessageConsumer.class));

        final ProtocolAdapterCommandConsumerFactoryImpl commandConsumerFactory = createCommandConsumerFactory();

        // GIVEN a scenario where a command consumer is created and then closed again
        final VertxTestContext consumerCreationAndRemoval = new VertxTestContext();
        final Handler<CommandContext> commandHandler = VertxMockSupport.mockHandler();
        commandConsumerFactory.createCommandConsumer(tenantId, deviceId, commandHandler, null, null)
                .onComplete(consumerCreationAndRemoval.succeeding(consumer -> {
                    // close the consumer again
                    consumer.close(null)
                            .onComplete(consumerCreationAndRemoval.completing());
                }));
        assertThat(consumerCreationAndRemoval.awaitCompletion(2, TimeUnit.SECONDS)).isTrue();
        if (consumerCreationAndRemoval.failed()) {
            ctx.failNow(consumerCreationAndRemoval.causeOfFailure());
            return;
        }

        // WHEN the tenant timeout is triggered
        final Message<Object> eventBusMsg = mock(Message.class);
        when(eventBusMsg.body()).thenReturn(tenantId);
        eventBusMsgHandler.getValue().handle(eventBusMsg);

        // THEN the tenant-scoped receiver link is closed
        verify(connection).closeAndFree(eq(mappingAndDelegatingCommandReceiver), any());
        ctx.completeNow();
    }

    /**
     * Verifies that upon getting a tenant timeout notification, the tenant-scoped
     * consumer link isn't closed if there are still consumers for that tenant registered.
     *
     * @param ctx The test context.
     * @throws InterruptedException If the test execution gets interrupted.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testTenantTimeoutSkipsClosingTenantLink(final VertxTestContext ctx) throws InterruptedException {

        final ArgumentCaptor<Handler<Message<Object>>> eventBusMsgHandler = ArgumentCaptor.forClass(Handler.class);
        when(vertx.eventBus().consumer(eq(Constants.EVENT_BUS_ADDRESS_TENANT_TIMED_OUT), eventBusMsgHandler.capture()))
                .thenReturn(mock(io.vertx.core.eventbus.MessageConsumer.class));

        final ProtocolAdapterCommandConsumerFactoryImpl commandConsumerFactory = createCommandConsumerFactory();

        // GIVEN a scenario where a command consumer is created
        final VertxTestContext consumerCreation = new VertxTestContext();
        final Handler<CommandContext> commandHandler = VertxMockSupport.mockHandler();
        commandConsumerFactory.createCommandConsumer(tenantId, deviceId, commandHandler, null, null)
                .onComplete(consumerCreation.completing());
        assertThat(consumerCreation.awaitCompletion(2, TimeUnit.SECONDS)).isTrue();
        if (consumerCreation.failed()) {
            consumerCreation.failNow(consumerCreation.causeOfFailure());
            return;
        }

        // WHEN the tenant timeout is triggered
        final Message<Object> eventBusMsg = mock(Message.class);
        when(eventBusMsg.body()).thenReturn(tenantId);
        eventBusMsgHandler.getValue().handle(eventBusMsg);

        // THEN the tenant-scoped receiver link isn't closed because the command consumer is still alive
        verify(connection, never()).closeAndFree(eq(mappingAndDelegatingCommandReceiver), any());
        ctx.completeNow();
    }

}
