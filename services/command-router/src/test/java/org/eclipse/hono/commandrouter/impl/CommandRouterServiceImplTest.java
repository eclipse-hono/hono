/**
 * Copyright (c) 2021, 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.commandrouter.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;

import java.net.HttpURLConnection;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.registry.DeviceRegistrationClient;
import org.eclipse.hono.client.registry.TenantClient;
import org.eclipse.hono.client.telemetry.EventSender;
import org.eclipse.hono.client.util.MessagingClientProvider;
import org.eclipse.hono.commandrouter.CommandConsumerFactory;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.deviceconnection.infinispan.client.DeviceConnectionInfo;
import org.eclipse.hono.test.VertxMockSupport;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.MessagingType;
import org.eclipse.hono.util.RegistrationAssertion;
import org.eclipse.hono.util.TenantConstants;
import org.eclipse.hono.util.TenantObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.opentracing.SpanContext;
import io.opentracing.noop.NoopSpan;
import io.opentracing.noop.NoopTracerFactory;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Tests verifying behavior of {@link CommandRouterServiceImpl}.
 *
 */
@ExtendWith(VertxExtension.class)
public class CommandRouterServiceImplTest {

    private CommandRouterServiceImpl service;
    private MessagingClientProvider<CommandConsumerFactory> commandConsumerFactoryProvider;
    private CommandConsumerFactory amqpCommandConsumerFactory;
    private MessagingClientProvider<EventSender> eventSenderProvider;
    private DeviceConnectionInfo deviceConnectionInfo;
    private EventSender eventSender;
    private Context context;
    private Vertx vertx;
    private TenantClient tenantClient;

    /**
     * Sets up the fixture.
     */
    @BeforeEach
    void setUp() {
        final var registrationClient = mock(DeviceRegistrationClient.class);
        when(registrationClient.stop()).thenReturn(Future.succeededFuture());
        tenantClient = mock(TenantClient.class);
        when(tenantClient.stop()).thenReturn(Future.succeededFuture());
        when(tenantClient.get(anyString(), any())).thenAnswer(invocation -> {
            return Future.succeededFuture(TenantObject.from(invocation.getArgument(0)));
        });
        deviceConnectionInfo = mock(DeviceConnectionInfo.class);
        when(deviceConnectionInfo.setCommandHandlingAdapterInstance(anyString(), anyString(), anyString(), any(),
                any())).thenReturn(Future.succeededFuture());
        amqpCommandConsumerFactory = mock(CommandConsumerFactory.class);
        when(amqpCommandConsumerFactory.getMessagingType()).thenReturn(MessagingType.amqp);
        when(amqpCommandConsumerFactory.start()).thenReturn(Future.succeededFuture());
        when(amqpCommandConsumerFactory.stop()).thenReturn(Future.succeededFuture());
        when(amqpCommandConsumerFactory.createCommandConsumer(anyString(), any())).thenReturn(Future.succeededFuture());
        commandConsumerFactoryProvider = new MessagingClientProvider<>();
        commandConsumerFactoryProvider.setClient(amqpCommandConsumerFactory);
        eventSender = mock(EventSender.class);
        when(eventSender.getMessagingType()).thenReturn(MessagingType.amqp);
        when(eventSender.start()).thenReturn(Future.succeededFuture());
        when(eventSender.stop()).thenReturn(Future.succeededFuture());
        when(eventSender.sendEvent(any(), any(), any(), any(), any(), any())).thenReturn(Future.succeededFuture());
        eventSenderProvider = new MessagingClientProvider<>();
        eventSenderProvider.setClient(eventSender);
        vertx = mock(Vertx.class);
        context = VertxMockSupport.mockContext(vertx);
        when(context.owner()).thenReturn(vertx);
        service = new CommandRouterServiceImpl(
                new ServiceConfigProperties(),
                registrationClient,
                tenantClient,
                deviceConnectionInfo,
                commandConsumerFactoryProvider,
                eventSenderProvider,
                new UnknownStatusProvidingService(),
                NoopTracerFactory.create());
        service.setContext(context);
        service.start();
    }

    /**
     * Verifies that a command consumer gets registered in both AMQP and Kafka messaging systems if both are available
     * and the tenant is configured to use Kafka.
     * It also checks that <em>connected notification</em> event is propagated properly.
     *
     * @param sendEvent {@code true} if <em>connected notification</em> event should be sent.
     * @param ctx The vert.x test context.
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testRegisterCommandRouterAlsoUsingAmqpMessagingSystem(final boolean sendEvent, final VertxTestContext ctx) {
        // GIVEN a tenant configured to use a Kafka messaging system
        final String tenantId = "tenant";
        final TenantObject tenant = new TenantObject(tenantId, true);
        tenant.setProperty(TenantConstants.FIELD_EXT,
                Map.of(TenantConstants.FIELD_EXT_MESSAGING_TYPE, MessagingType.kafka.name()));
        when(tenantClient.get(eq(tenantId), any(SpanContext.class))).thenReturn(Future.succeededFuture(tenant));
        // AND a commandConsumerFactoryProvider with both AMQP (see setUp()) and Kafka client providers
        final CommandConsumerFactory kafkaCommandConsumerFactory = mock(CommandConsumerFactory.class);
        when(kafkaCommandConsumerFactory.getMessagingType()).thenReturn(MessagingType.kafka);
        when(kafkaCommandConsumerFactory.start()).thenReturn(Future.succeededFuture());
        when(kafkaCommandConsumerFactory.stop()).thenReturn(Future.succeededFuture());
        when(kafkaCommandConsumerFactory.createCommandConsumer(anyString(), any())).thenReturn(Future.succeededFuture());
        commandConsumerFactoryProvider.setClient(kafkaCommandConsumerFactory);
        // let the AMQP client consumer creation fail
        when(amqpCommandConsumerFactory.createCommandConsumer(anyString(), any())).thenReturn(Future.failedFuture("expected failure"));

        // WHEN registering a command consumer for the tenant
        service.registerCommandConsumer(tenantId, "deviceId", sendEvent, "adapterInstanceId", null, NoopSpan.INSTANCE)
                .onComplete(ctx.succeeding(res -> {
                    ctx.verify(() -> {
                        // THEN both kinds of consumer clients get used
                        verify(kafkaCommandConsumerFactory).createCommandConsumer(anyString(), any());
                        verify(amqpCommandConsumerFactory).createCommandConsumer(anyString(), any());
                        // and the register operation succeeds even though the AMQP client registration failed
                        assertThat(res.getStatus()).isEqualTo(HttpURLConnection.HTTP_NO_CONTENT);
                        if (sendEvent) {
                            assertEmptyNotificationHasBeenSentDownstream(tenantId, "deviceId", -1);
                        } else {
                            assertNoEventHasBeenSentDownstream();
                        }
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that command consumer unregister is successful and <em>disconnected notification</em> event is
     * propagated properly.
     *
     * @param sendEvent {@code true} if <em>disconnected notification</em> event should be sent.
     * @param ctx The vert.x test context.
     */
    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    public void testUnregisterCommandConsumer(final boolean sendEvent, final VertxTestContext ctx) {
        // GIVEN a tenant configured to use a Kafka messaging system
        final String tenantId = "tenant";
        final TenantObject tenant = new TenantObject(tenantId, true);
        tenant.setProperty(TenantConstants.FIELD_EXT,
                Map.of(TenantConstants.FIELD_EXT_MESSAGING_TYPE, MessagingType.kafka.name()));
        when(tenantClient.get(eq(tenantId), any(SpanContext.class))).thenReturn(Future.succeededFuture(tenant));
        when(deviceConnectionInfo.removeCommandHandlingAdapterInstance(anyString(), anyString(), anyString(), any()))
                .thenReturn(Future.succeededFuture());

        // WHEN unregistering a command consumer for the tenant
        service.unregisterCommandConsumer(tenantId, "deviceId", sendEvent, "adapterInstanceId", NoopSpan.INSTANCE)
                .onComplete(ctx.succeeding(res -> {
                    ctx.verify(() -> {
                        // THEN operation succeeds and ttd event is propagated properly
                        assertThat(res.getStatus()).isEqualTo(HttpURLConnection.HTTP_NO_CONTENT);
                        if (sendEvent) {
                            assertEmptyNotificationHasBeenSentDownstream(tenantId, "deviceId", 0);
                        } else {
                            assertNoEventHasBeenSentDownstream();
                        }
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that command consumer unregisters does not send 'disconnectedTtdEvent'when removal of the command
     * consumer mapping entry fails (which would be the case when another command consumer mapping had been registered
     * in the meantime, meaning the device has already reconnected).
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testUnregistersCommandConsumerSkipTtdEventIfRemoveConsumerFails(final VertxTestContext ctx) {
        // GIVEN a tenant configured to use a Kafka messaging system
        final String tenantId = "tenant";
        final TenantObject tenant = new TenantObject(tenantId, true);
        tenant.setProperty(TenantConstants.FIELD_EXT,
                Map.of(TenantConstants.FIELD_EXT_MESSAGING_TYPE, MessagingType.kafka.name()));
        when(tenantClient.get(eq(tenantId), any(SpanContext.class))).thenReturn(Future.succeededFuture(tenant));
        when(deviceConnectionInfo.removeCommandHandlingAdapterInstance(anyString(), anyString(), anyString(), any()))
                .thenReturn(Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_PRECON_FAILED)));

        // WHEN registering a command consumer for the tenant
        service.unregisterCommandConsumer(tenantId, "deviceId", true,
                "adapterInstanceId", NoopSpan.INSTANCE)
                .onComplete(ctx.succeeding(res -> {
                    ctx.verify(() -> {
                        // THEN operation fails and ttd event is not propagated
                        assertThat(res.getStatus()).isEqualTo(HttpURLConnection.HTTP_PRECON_FAILED);
                        assertNoEventHasBeenSentDownstream();
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that command routing is enabled for a given set of tenant IDs.
     */
    @Test
    public void testEnableCommandRoutingCreatesCommandConsumers() {

        final Deque<Handler<Void>> eventLoop = new LinkedList<>();
        doAnswer(invocation -> {
            eventLoop.addLast(invocation.getArgument(0));
            return null;
        }).when(context).runOnContext(VertxMockSupport.anyHandler());

        final List<String> firstTenants = List.of("tenant1", "tenant2", "tenant3");
        final List<String> secondTenants = List.of("tenant3", "tenant4");
        // WHEN submitting the first list of tenants to enable
        service.enableCommandRouting(firstTenants, NoopSpan.INSTANCE);
        // THEN no command consumer has been created yet
        verify(amqpCommandConsumerFactory, never()).createCommandConsumer(anyString(), any());
        // AND a task for processing the first tenant has been scheduled
        assertThat(eventLoop).hasSize(1);
        // WHEN submitting the second list of tenants
        service.enableCommandRouting(secondTenants, NoopSpan.INSTANCE);
        // still no command consumer has been created
        verify(amqpCommandConsumerFactory, never()).createCommandConsumer(anyString(), any());
        // AND no additional task has been scheduled
        assertThat(eventLoop).hasSize(1);

        // WHEN running the next task on the event loop
        final var task = eventLoop.pollFirst();
        task.handle(null);
        // THEN a command consumer is being created
        verify(amqpCommandConsumerFactory).createCommandConsumer(eq("tenant1"), any());
        // AND a new task has been added to the event loop
        assertThat(eventLoop).hasSize(1);
        // WHEN running the next task on the event loop
        eventLoop.pollFirst().handle(null);
        // THEN a command consumer is being created
        verify(amqpCommandConsumerFactory).createCommandConsumer(eq("tenant2"), any());
        // AND a new task has been added to the event loop
        assertThat(eventLoop).hasSize(1);
        // WHEN running the next task on the event loop
        eventLoop.pollFirst().handle(null);
        // THEN a single command consumer is being created for the duplicate tenant3 ID
        verify(amqpCommandConsumerFactory).createCommandConsumer(eq("tenant3"), any());
        // AND a new task has been added to the event loop
        assertThat(eventLoop).hasSize(1);
        // WHEN running the next task on the event loop
        eventLoop.pollFirst().handle(null);
        // THEN a command consumer is being created
        verify(amqpCommandConsumerFactory).createCommandConsumer(eq("tenant4"), any());
        // AND no new task has been added to the event loop
        assertThat(eventLoop).isEmpty();
    }

    /**
     * Verifies that exponential back-off is used for rescheduling attempts
     * to enable command routing if an attempt fails.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testEnableCommandRoutingUsesExponentialBackoff() {

        final Deque<Handler<?>> eventLoop = new LinkedList<>();
        doAnswer(invocation -> {
            eventLoop.addLast(invocation.getArgument(0));
            return null;
        }).when(context).runOnContext(VertxMockSupport.anyHandler());

        when(vertx.setTimer(anyLong(), VertxMockSupport.anyHandler())).thenAnswer(invocation -> {
            eventLoop.addLast(invocation.getArgument(1));
            return 1L;
        });

        when(tenantClient.get(eq("tenant1"), any())).thenReturn(
                Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE)),
                Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE)),
                Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE)),
                Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE)),
                Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE)),
                Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE)),
                Future.succeededFuture(TenantObject.from("tenant1")));

        final List<String> firstTenants = List.of("tenant1");
        service.enableCommandRouting(firstTenants, NoopSpan.INSTANCE);
        // THEN no command consumer has been created yet
        verify(amqpCommandConsumerFactory, never()).createCommandConsumer(anyString(), any());
        // AND a first task to process the first tenant ID has been scheduled
        verify(context).runOnContext(VertxMockSupport.anyHandler());
        assertThat(eventLoop).hasSize(1);

        // WHEN running the next task on the event loop
        eventLoop.pollFirst().handle(null);
        // THEN no command consumer has been created yet
        verify(amqpCommandConsumerFactory, never()).createCommandConsumer(anyString(), any());
        // AND a new task for retrying the attempt has been scheduled with a
        // delay corresponding to the number of unsuccessful attempts that have been made
        verify(vertx).setTimer(eq(400L), VertxMockSupport.anyHandler());
        assertThat(eventLoop).hasSize(1);

        // WHEN running the next task on the event loop
        eventLoop.pollFirst().handle(null);
        // THEN no command consumer has been created yet
        verify(amqpCommandConsumerFactory, never()).createCommandConsumer(anyString(), any());
        // AND a new task for retrying the attempt has been scheduled with the
        // delay corresponding to the number of unsuccessful attempts that have been made
        verify(vertx).setTimer(eq(800L), VertxMockSupport.anyHandler());
        assertThat(eventLoop).hasSize(1);

        // WHEN running the next task on the event loop
        eventLoop.pollFirst().handle(null);
        // THEN no command consumer has been created yet
        verify(amqpCommandConsumerFactory, never()).createCommandConsumer(anyString(), any());
        // AND a new task for retrying the attempt has been scheduled with a
        // delay corresponding to the number of unsuccessful attempts that have been made
        verify(vertx).setTimer(eq(1600L), VertxMockSupport.anyHandler());
        assertThat(eventLoop).hasSize(1);

        // WHEN adding the same tenant again in between re-tries
        service.enableCommandRouting(firstTenants, NoopSpan.INSTANCE);
        // THEN no additional task has been scheduled
        assertThat(eventLoop).hasSize(1);

        // WHEN running the next task on the event loop
        eventLoop.pollFirst().handle(null);
        // THEN no command consumer has been created yet
        verify(amqpCommandConsumerFactory, never()).createCommandConsumer(anyString(), any());
        // AND a new task for retrying the attempt has been scheduled with a
        // delay corresponding to the number of unsuccessful attempts that have been made
        verify(vertx).setTimer(eq(3200L), VertxMockSupport.anyHandler());
        assertThat(eventLoop).hasSize(1);

        // WHEN running the next task on the event loop
        eventLoop.pollFirst().handle(null);
        // THEN no command consumer has been created yet
        verify(amqpCommandConsumerFactory, never()).createCommandConsumer(anyString(), any());
        // AND a new task for retrying the attempt has been scheduled with a
        // delay corresponding to the number of unsuccessful attempts that have been made
        verify(vertx).setTimer(eq(6400L), VertxMockSupport.anyHandler());
        assertThat(eventLoop).hasSize(1);

        // WHEN running the next task on the event loop
        eventLoop.pollFirst().handle(null);
        // THEN no command consumer has been created yet
        verify(amqpCommandConsumerFactory, never()).createCommandConsumer(anyString(), any());
        // AND a new task for retrying the attempt has been scheduled with maximum delay
        verify(vertx).setTimer(eq(10000L), VertxMockSupport.anyHandler());
        assertThat(eventLoop).hasSize(1);

        // WHEN running the next task on the event loop
        eventLoop.pollFirst().handle(null);
        // THEN a command consumer has been created for the tenant
        verify(amqpCommandConsumerFactory).createCommandConsumer(eq("tenant1"), any());
        // AND no new task for retrying the attempt has been added to the event loop
        assertThat(eventLoop).hasSize(0);
    }

    /**
     * Verifies that the stop method effectively prevents command routing being re-enabled for
     * remaining tenant IDs.
     */
    @Test
    public void testStopPreventsCommandConsumersFromBeingReenabled() {

        final Deque<Handler<?>> eventLoop = new LinkedList<>();
        doAnswer(invocation -> {
            eventLoop.addLast(invocation.getArgument(0));
            return null;
        }).when(context).runOnContext(VertxMockSupport.anyHandler());

        final List<String> firstTenants = List.of("tenant1");
        service.enableCommandRouting(firstTenants, NoopSpan.INSTANCE);

        // THEN no command consumer has been created yet
        verify(amqpCommandConsumerFactory, never()).createCommandConsumer(anyString(), any());
        // BUT a first task to process the first tenant ID has been scheduled
        verify(context).runOnContext(VertxMockSupport.anyHandler());
        assertThat(eventLoop).hasSize(1);

        // WHEN the component has been stopped
        service.stop();
        // THEN no new tenant IDs can be submitted anymore
        final var result = service.enableCommandRouting(List.of("tenant X"), NoopSpan.INSTANCE);
        assertThat(result.succeeded()).isTrue();
        assertThat(result.result().getStatus()).isEqualTo(HttpURLConnection.HTTP_UNAVAILABLE);

        // WHEN running the next task on the event loop
        eventLoop.pollFirst().handle(null);
        // THEN no command consumer has been created
        verify(amqpCommandConsumerFactory, never()).createCommandConsumer(anyString(), any());
        // AND no new task has been scheduled
        assertThat(eventLoop).hasSize(0);
    }

    /**
     * Asserts that an empty notification has been sent downstream.
     *
     * @param tenant The tenant to check the message against.
     * @param deviceId The device to check the message against.
     * @param ttd The time-until-disconnect value to check the message against.
     * @throws NullPointerException if any of the parameters are {@code null}.
     * @throws AssertionError if no empty notification matching the given parameters has been sent.
     */
    protected void assertEmptyNotificationHasBeenSentDownstream(
            final String tenant,
            final String deviceId,
            final Integer ttd) {

        Objects.requireNonNull(tenant);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(ttd);

        verify(eventSender).sendEvent(
                argThat(tenantObject -> tenantObject.getTenantId().equals(tenant)),
                argThat(assertion -> assertion.getDeviceId().equals(deviceId)),
                eq(EventConstants.CONTENT_TYPE_EMPTY_NOTIFICATION),
                any(),
                argThat(props -> ttd.equals(props.get(CommandConstants.MSG_PROPERTY_DEVICE_TTD))),
                any());
    }

    /**
     * Asserts that no message has been sent using the event sender.
     *
     * @throws AssertionError if a message has been sent.
     */
    protected void assertNoEventHasBeenSentDownstream() {
        verify(eventSender, never()).sendEvent(
                any(TenantObject.class),
                any(RegistrationAssertion.class),
                anyString(),
                any(),
                any(),
                any());
    }
}
