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

package org.eclipse.hono.client.command;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import static com.google.common.truth.Truth.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.eclipse.hono.client.amqp.connection.ConnectionLifecycle;
import org.eclipse.hono.client.amqp.connection.HonoConnection;
import org.eclipse.hono.client.amqp.connection.ReconnectListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;

import io.opentracing.Span;
import io.opentracing.Tracer;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Tests verifying behavior of {@link CommandRouterCommandConsumerFactory}.
 *
 */
@ExtendWith(VertxExtension.class)
@Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
public class CommandRouterCommandConsumerFactoryTest {

    private static final int BATCH_SIZE = 100;
    private static final int BATCH_MAX_TIMEOUT = 500;

    private CommandRouterClient commandRouterClient;
    private CommandRouterCommandConsumerFactory factory;
    private Tracer tracer;

    /**
     * Sets up the fixture.
     */
    @BeforeEach
    void setUp() {
        tracer = mock(Tracer.class);
        final Tracer.SpanBuilder builder = mock(Tracer.SpanBuilder.class);
        when(builder.addReference(any(), any())).thenReturn(builder);
        when(builder.ignoreActiveSpan()).thenReturn(builder);
        when(builder.withTag(anyString(), anyString())).thenReturn(builder);
        when(builder.start()).thenReturn(mock(Span.class));
        when(tracer.buildSpan(any())).thenReturn(builder);

        commandRouterClient = mock(CommandRouterClient.class, withSettings().extraInterfaces(ConnectionLifecycle.class));
        when(commandRouterClient.registerCommandConsumer(anyString(), anyString(), anyBoolean(), anyString(), any(Duration.class), any()))
            .thenReturn(Future.succeededFuture());
        when(commandRouterClient.unregisterCommandConsumers(any(), any(), any())).thenReturn(Future.succeededFuture());
        factory = new CommandRouterCommandConsumerFactory(commandRouterClient, "test-adapter", tracer, BATCH_SIZE, BATCH_MAX_TIMEOUT);
    }

    @SuppressWarnings("unchecked")
    @Test
    void testFactoryReenablesCommandRouting() {

        // GIVEN a consumer factory with command consumers registered for 3 tenants
        final ConnectionLifecycle<HonoConnection> conLifecycle = (ConnectionLifecycle<HonoConnection>) commandRouterClient;
        final ArgumentCaptor<ReconnectListener<HonoConnection>> reconnectListener = ArgumentCaptor.forClass(ReconnectListener.class);
        verify(conLifecycle).addReconnectListener(reconnectListener.capture());
        factory.setMaxTenantIdsPerRequest(2);

        factory.createCommandConsumer("tenant1", "device1", "adapter", ctx -> Future.succeededFuture(), Duration.ofMinutes(10), null);
        factory.createCommandConsumer("tenant2", "device2", "adapter", ctx -> Future.succeededFuture(), Duration.ofMinutes(10), null);
        factory.createCommandConsumer("tenant3", "device3", "adapter", ctx -> Future.succeededFuture(), Duration.ofMinutes(10), null);

        // WHEN connection is lost and re-established
        final List<String> enabledTenants = new ArrayList<>();
        when(commandRouterClient.enableCommandRouting(anyList(), any())).thenAnswer(invocation -> {
            enabledTenants.addAll(invocation.getArgument(0));
            return Future.succeededFuture();
        });
        reconnectListener.getValue().onReconnect(mock(HonoConnection.class));

        // THEN
        verify(commandRouterClient).enableCommandRouting(argThat(list -> {
            return list.size() == 2;
        }), any());
        verify(commandRouterClient).enableCommandRouting(argThat(list -> {
            return list.size() == 1;
        }), any());
        assertThat(enabledTenants).containsExactly("tenant1", "tenant2", "tenant3");
    }

    @Test
    void testClosingConsumersViaLimit(final Vertx vertx, final VertxTestContext ctx) {

        vertx.runOnContext(h -> {
            // GIVEN a consumer factory with command consumers registered for 100 devices
            final List<Future<CommandConsumer>> consumers = new ArrayList();
            for (int i = 0; i < BATCH_SIZE; i++) {
                consumers.add(factory.createCommandConsumer("tenant1", "device" + i, "adapter",
                        ctx2 -> Future.succeededFuture(), Duration.ofMinutes(10), null));
            }

            // WHEN the consumers are released
            consumers.forEach(f -> f.onComplete(r -> r.result().release(true, null)));

            // THEN commandRouterClient is called to unregister BATCH_COUNT command consumers
            ctx.verify(() -> {
                verify(commandRouterClient).unregisterCommandConsumers(argThat(list -> {
                    return list.size() == consumers.size();
                }), any(), any());

            });
            ctx.completeNow();
        });
    }

    @Test
    void testClosingConsumersViaTimer(final Vertx vertx, final VertxTestContext ctx) {

        vertx.runOnContext(event -> {
            // GIVEN a consumer factory with command consumers registered for 1 device
            final Future<CommandConsumer> consumer = factory.createCommandConsumer("tenant1", "device", "adapter",
                    ctx2 -> Future.succeededFuture(), Duration.ofMinutes(10), null);

            // WHEN the consumer is released
            consumer.onComplete(f -> f.result().release(true, null));
        });

        ctx.verify(() -> {
            // AND wait until the timer is executed
            synchronized (ctx) {
                ctx.wait(BATCH_MAX_TIMEOUT + 100);
            }

            // THEN commandRouterClient is called to unregister 1 command consumer
            verify(commandRouterClient).unregisterCommandConsumers(argThat(list -> {
                return list.size() == 1;
            }), any(), any());
        });
        ctx.completeNow();
    }
}
