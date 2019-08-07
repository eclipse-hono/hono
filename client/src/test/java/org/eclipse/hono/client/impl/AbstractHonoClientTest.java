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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.apache.qpid.proton.amqp.messaging.Target;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.config.ClientConfigProperties;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.proton.ProtonSender;


/**
 * Tests verifying behavior of {@link AbstractHonoClient}.
 *
 */
@RunWith(VertxUnitRunner.class)
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
     * @param ctx The vert.x test context.
     */
    @Test
    public void testApplicationPropertiesAreSetAtTheMessage(final TestContext ctx) {

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
     * TODO.
     */
    @Test
    public void testNoAutoCloseOnDefault() {
        final AbstractHonoClient client = createClient();
        client.startAutoCloseTimer();
        verify(client.connection.getVertx(), never()).setTimer(eq(1L), anyHandler());
    }

    /**
     * TODO.
     */
    @Test
    public void testTimeoutExceededAndNeverSent() {
        final AbstractHonoClient client = createClient();
        client.connection.getConfig().setInactiveLinkTimeout(1L);
        when(client.sender.attachments().get(AbstractHonoClient.ATTACHMENT_LAST_SEND_TIME, Long.class)).thenReturn(0L);

        client.startAutoCloseTimer();
        verify(client.connection.getVertx()).setTimer(eq(1L), anyHandler());
        verify(client.connection).closeAndFree(eq(client.sender), anyHandler());
    }

    /**
     * TODO.
     * @throws InterruptedException if sleep is interrupted.
     */
    @Test
    public void testTimerIsRestartedWhenTimeoutNotExceeded() throws InterruptedException {
        final AbstractHonoClient client = createClient();
        client.connection.getConfig().setInactiveLinkTimeout(100L);
        final long now = Instant.now().toEpochMilli();
        when(client.sender.attachments().get(AbstractHonoClient.ATTACHMENT_LAST_SEND_TIME, Long.class)).thenReturn(now);
        Thread.sleep(1);

        client.startAutoCloseTimer();
        verify(client.connection.getVertx(), atLeastOnce()).setTimer(anyLong(), anyHandler());
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
     * TODO.
     */
    @Test
    public void testRemainingTimeout() {
        final AbstractHonoClient client = new AbstractHonoClient(mock(HonoConnectionImpl.class)) {
        };
        assertEquals(0L, client.getRemainingTimeout(10, 20, 5));
        assertEquals(0L, client.getRemainingTimeout(0, 20, 5)); // never seen
        assertEquals(1L, client.getRemainingTimeout(10, 20, 11));
        assertEquals(5L, client.getRemainingTimeout(20, 20, 5));
    }
}
