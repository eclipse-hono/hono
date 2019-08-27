/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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

import static org.eclipse.hono.client.impl.VertxMockSupport.anyHandler;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.apache.qpid.proton.amqp.messaging.Target;
import org.apache.qpid.proton.amqp.transport.Source;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.config.ClientConfigProperties;
import org.junit.Rule;
import org.junit.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.rules.Timeout;
import org.mockito.ArgumentCaptor;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.ProtonSender;


/**
 * Tests verifying behavior of {@link AbstractHonoClient}.
 *
 */
public class AbstractHonoClientTest {

    /**
     * Time out each test after five seconds.
     */
    @Rule
    public final Timeout timeout = Timeout.seconds(5);

    /**
     * Verifies that the given application properties are propagated to
     * the message.
     *
     */
    @Test
    public void testApplicationPropertiesAreSetAtTheMessage() {

        final Message msg = mock(Message.class);
        final Map<String, Object> applicationProps = new HashMap<>();
        applicationProps.put("string-key", "value");
        applicationProps.put("int-key", 15);
        applicationProps.put("long-key", 1000L);

        final ArgumentCaptor<ApplicationProperties> applicationPropsCaptor = ArgumentCaptor.forClass(ApplicationProperties.class);

        AbstractHonoClient.setApplicationProperties(msg, applicationProps);

        verify(msg).setApplicationProperties(applicationPropsCaptor.capture());
        assertThat(applicationPropsCaptor.getValue().getValue(), is(applicationProps));
    }

    /**
     * Verifies that the automatic close of inactive sender links is disabled by default.
     */
    @Test
    public void testNoAutoCloseOnDefault() {
        final AbstractHonoClient client = createClient();
        client.startAutoCloseLinksTimer();
        verify(client.connection.getVertx(), never()).setTimer(anyLong(), anyHandler());
    }

    /**
     * Verifies that a timer is started to automatically close inactive sender links if a timeout is configured.
     */
    @Test
    public void testTimeoutExceededAndNeverSent() {
        final AbstractHonoClient client = createClient();
        client.connection.getConfig().setInactiveLinkTimeout(Duration.ofMillis(1));
        when(client.sender.attachments().get(AbstractHonoClient.KEY_LAST_SEND_TIME, Long.class)).thenReturn(0L);

        client.startAutoCloseLinksTimer();
        verify(client.connection.getVertx()).setTimer(anyLong(), anyHandler());
        verify(client.connection).closeAndFree(eq(client.sender), anyHandler());
    }

    /**
     * Verifies that the timer to automatically close inactive sender links is restarted if the configured timeout is
     * not exceeded.
     */
    @Test
    public void testTimerIsRestartedWhenTimeoutNotExceeded() {
        final long timeoutMillis = 10L;
        final AbstractHonoClient client = createClient();
        client.connection.getConfig().setInactiveLinkTimeout(Duration.ofMillis(timeoutMillis));
        when(client.sender.attachments().get(AbstractHonoClient.KEY_LAST_SEND_TIME, Long.class))
                .thenReturn(System.currentTimeMillis());

        client.startAutoCloseLinksTimer();
        verify(client.connection.getVertx(), timeout(timeoutMillis).atLeast(2)).setTimer(anyLong(), anyHandler());
        verify(client.connection).closeAndFree(eq(client.sender), anyHandler());
    }

    private AbstractHonoClient createClient() {
        final ProtonSender protonSender = HonoClientUnitTestHelper.mockProtonSender();
        when(protonSender.getTarget()).thenReturn(new Target());

        final Vertx vertx = mock(Vertx.class);
        when(vertx.setTimer(anyLong(), anyHandler())).thenAnswer(invocation -> {
            final Handler<Long> handler = invocation.getArgument(1);
            final long timerId = 1;
            handler.handle(timerId);
            return timerId;
        });

        final HonoConnection connection = mock(HonoConnection.class);
        when(connection.getVertx()).thenReturn(vertx);
        when(connection.getConfig()).thenReturn(new ClientConfigProperties());

        final AbstractHonoClient client = new AbstractHonoClient(connection) {
        };
        client.sender = protonSender;
        return client;
    }

    /**
     * Verifies that the remaining timeout is the difference between now and lastSend but min. 0 and max. the configured
     * timeout.
     */
    @Test
    public void testRemainingTimeout() {
        final AbstractHonoClient client = new AbstractHonoClient(mock(HonoConnectionImpl.class)) {
        };
        assertEquals(0L, client.getRemainingTimeout(0, 20, 5));
        assertEquals(0L, client.getRemainingTimeout(10, 20, 5));
        assertEquals(1L, client.getRemainingTimeout(10, 20, 11));
        assertEquals(5L, client.getRemainingTimeout(20, 20, 5));
    }

    /**
     * Verifies that when closing an AMQP link locally a message with it's adress is published on the event bus.
     * 
     * @param sender The sender link whose close event should be published - may be {@code null}.
     * @param receiver The receiver link whose close event should be published - may be {@code null}.
     */
    @ParameterizedTest
    @MethodSource("links")
    public void testCloseEventIsPublished(final ProtonSender sender, final ProtonReceiver receiver) {

        final Vertx vertx = mock(Vertx.class);
        when(vertx.eventBus()).thenReturn(mock(EventBus.class));

        final AbstractHonoClient client = getClient(sender, receiver, vertx);

        client.closeLinks(v -> {
        });

        if (sender == null) {
            verify(vertx.eventBus(), never()).publish(eq(AbstractHonoClient.EVENT_BUS_ADDRESS_CLIENT_SENDER_CLOSE),
                    anyString());
        } else {
            verify(vertx.eventBus()).publish(eq(AbstractHonoClient.EVENT_BUS_ADDRESS_CLIENT_SENDER_CLOSE),
                    eq("senderAddress"));
        }

        if (receiver == null) {
            verify(vertx.eventBus(), never()).publish(eq(AbstractHonoClient.EVENT_BUS_ADDRESS_CLIENT_RECEIVER_CLOSE),
                    anyString());
        } else {
            verify(vertx.eventBus()).publish(eq(AbstractHonoClient.EVENT_BUS_ADDRESS_CLIENT_RECEIVER_CLOSE),
                    eq("receiverAddress"));
        }
    }

    private static Stream<Arguments> links() {
        final Target target = new Target();
        target.setAddress("senderAddress");
        final ProtonSender protonSender = HonoClientUnitTestHelper.mockProtonSender();
        when(protonSender.getTarget()).thenReturn(target);

        final ProtonReceiver protonReceiver = HonoClientUnitTestHelper.mockProtonReceiver();
        final Source source = mock(Source.class);
        when(source.getAddress()).thenReturn("receiverAddress");
        when(protonReceiver.getSource()).thenReturn(source);

        return Stream.of(
                Arguments.of(null, null),
                Arguments.of(protonSender, null),
                Arguments.of(null, protonReceiver),
                Arguments.of(protonSender, protonReceiver));
    }

    private AbstractHonoClient getClient(final ProtonSender protonSender, final ProtonReceiver protonReceiver,
            final Vertx vertx) {
        final HonoConnection connection = mock(HonoConnection.class);
        when(connection.getVertx()).thenReturn(vertx);

        doAnswer(i -> {
            final Handler handler = i.getArgument(1);
            handler.handle(null);
            return null;
        }).when(connection).closeAndFree(any(), anyHandler());

        final AbstractHonoClient client = new AbstractHonoClient(connection) {
        };
        client.sender = protonSender;
        client.receiver = protonReceiver;
        return client;
    }
}
