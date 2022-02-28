/*******************************************************************************
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
 *******************************************************************************/

package org.eclipse.hono.client.device.amqp.internal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.client.ReconnectListener;
import org.eclipse.hono.client.amqp.test.AmqpClientUnitTestHelper;
import org.eclipse.hono.test.VertxMockSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.proton.ProtonMessageHandler;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonReceiver;

/**
 * Verifies behavior of {@link AmqpAdapterClientCommandConsumer}.
 *
 */
@ExtendWith(VertxExtension.class)
@Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
public class AmqpAdapterClientCommandConsumerTest {

    private HonoConnection connection;
    private ProtonReceiver originalReceiver;

    /**
     * Sets up fixture.
     */
    @BeforeEach
    public void setUp() {

        connection = AmqpClientUnitTestHelper.mockHonoConnection(mock(Vertx.class));
        when(connection.isConnected(anyLong())).thenReturn(Future.succeededFuture());
        originalReceiver = createNewProtonReceiver(connection);
    }

    /**
     * Verifies that the creation of the command consumer succeeds.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testCreateSucceeds() {

        final Future<MessageConsumer> consumerFuture = AmqpAdapterClientCommandConsumer.create(connection,
                mock(BiConsumer.class));

        verify(connection).createReceiver(eq("command"), eq(ProtonQoS.AT_LEAST_ONCE), any(),
                VertxMockSupport.anyHandler());

        assertThat(consumerFuture.succeeded()).isTrue();
        assertThat(consumerFuture.result()).isNotNull();
    }

    /**
     * Verifies that the creation of the device-specific command consumer succeeds.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testDeviceSpecificCreateSucceeds() {

        final String tenantId = "testTenantId";
        final String deviceId = "testDeviceId";
        final Future<MessageConsumer> consumerFuture = AmqpAdapterClientCommandConsumer.create(connection, tenantId,
                deviceId, mock(BiConsumer.class));

        verify(connection).createReceiver(eq("command/" + tenantId + "/" + deviceId), eq(ProtonQoS.AT_LEAST_ONCE),
                any(), VertxMockSupport.anyHandler());

        assertThat(consumerFuture.succeeded()).isTrue();
        assertThat(consumerFuture.result()).isNotNull();
    }

    /**
     * Verifies that the proton receiver is recreated after a reconnect.
     */
    @Test
    public void testReceiverIsRecreatedOnConnectionFailure() {

        final AtomicReference<ReconnectListener<HonoConnection>> reconnectListener = new AtomicReference<>();
        doAnswer(invocation -> {
            reconnectListener.set(invocation.getArgument(0));
            return null;
        }).when(connection).addReconnectListener(any());

        // GIVEN a connected command consumer
        @SuppressWarnings("unchecked")
        final Future<MessageConsumer> consumerFuture = AmqpAdapterClientCommandConsumer.create(connection,
                mock(BiConsumer.class));

        final AmqpAdapterClientCommandConsumer commandConsumer = (AmqpAdapterClientCommandConsumer) consumerFuture
                .result();

        // WHEN the connection is re-established
        final ProtonReceiver newReceiver = createNewProtonReceiver(connection);

        reconnectListener.get().onReconnect(null);

        // THEN the receiver is recreated
        verify(connection, times(2)).createReceiver(
                eq("command"),
                eq(ProtonQoS.AT_LEAST_ONCE),
                any(ProtonMessageHandler.class),
                VertxMockSupport.anyHandler());

        final ProtonReceiver actual = commandConsumer.getReceiver();
        assertThat(actual).isNotEqualTo(originalReceiver);
        assertThat(actual).isEqualTo(newReceiver);

    }

    private ProtonReceiver createNewProtonReceiver(final HonoConnection honoConnectionMock) {
        final ProtonReceiver receiver = mock(ProtonReceiver.class);
        when(honoConnectionMock.createReceiver(any(), any(), any(), any()))
                .thenReturn(Future.succeededFuture(receiver));
        return receiver;
    }

}
