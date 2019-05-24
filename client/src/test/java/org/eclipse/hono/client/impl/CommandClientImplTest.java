/*******************************************************************************
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
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

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.HashMap;
import java.util.Map;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.RequestResponseClientConfigProperties;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.util.Constants;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.ProtonSender;

/**
 * Tests verifying behavior of {@link CommandClientImpl}.
 *
 */
@RunWith(VertxUnitRunner.class)
public class CommandClientImplTest {

    private static final String DEVICE_ID = "device";
    private static final String REPLY_ID = "very-unique";
    /**
     * Time out test cases after 3 seconds.
     */
    @Rule
    public Timeout globalTimeout = Timeout.seconds(3);

    private Vertx vertx;
    private ProtonSender sender;
    private ProtonReceiver receiver;
    private CommandClientImpl client;

    /**
     * Sets up the fixture.
     */
    @Before
    public void setUp() {

        vertx = mock(Vertx.class);
        receiver = HonoClientUnitTestHelper.mockProtonReceiver();
        sender = HonoClientUnitTestHelper.mockProtonSender();
        final ClientConfigProperties config = new RequestResponseClientConfigProperties();
        final HonoConnection connection = HonoClientUnitTestHelper.mockHonoConnection(vertx, config);
        client = new CommandClientImpl(
                connection,
                Constants.DEFAULT_TENANT,
                DEVICE_ID,
                REPLY_ID,
                sender,
                receiver);
    }

    /**
     * Verifies that a command sent has its properties set correctly.
     *
     * <ul>
     * <li>subject set to given command</li>
     * <li>message-id not null</li>
     * <li>content-type set to given type</li>
     * <li>reply-to address set to default address created from device and UUID</li>
     * </ul>
     * 
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testSendCommandSetsProperties(final TestContext ctx) {
        final Map<String, Object> applicationProperties = new HashMap<String, Object>();
        applicationProperties.put("appKey", "appValue");

        client.sendCommand("doSomething", "text/plain", Buffer.buffer("payload"), applicationProperties);
        final ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(sender).send(messageCaptor.capture(), any(Handler.class));
        assertThat(messageCaptor.getValue().getSubject(), is("doSomething"));
        assertNotNull(messageCaptor.getValue().getMessageId());
        assertThat(messageCaptor.getValue().getContentType(), is("text/plain"));
        assertThat(messageCaptor.getValue().getReplyTo(),
                is(String.format("%s/%s/%s/%s", client.getReplyToEndpointName(), Constants.DEFAULT_TENANT, DEVICE_ID, REPLY_ID)));
        assertNotNull(messageCaptor.getValue().getApplicationProperties());
        assertThat(messageCaptor.getValue().getApplicationProperties().getValue().get("appKey"), is("appValue"));
    }

    /**
     * Verifies that a one-way command sends a message with an empty reply-to property.
     *
     * <ul>
     * <li>subject set to given command</li>
     * <li>message-id not null</li>
     * <li>content-type set to given type</li>
     * <li>reply-to address set to {@code null}</li>
     * <li>correlationId set to a UUID</li>
     * </ul>
     *
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testSendOneWayCommandSetsCorrelationIdAndEmptyReplyTo(final TestContext ctx) {
        final Map<String, Object> applicationProperties = new HashMap<String, Object>();
        applicationProperties.put("appKey", "appValue");

        client.sendOneWayCommand("doSomething", "text/plain", Buffer.buffer("payload"), applicationProperties);
        final ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(sender).send(messageCaptor.capture(), any(Handler.class));
        assertThat(messageCaptor.getValue().getSubject(), is("doSomething"));
        assertNotNull(messageCaptor.getValue().getMessageId());
        assertThat(messageCaptor.getValue().getContentType(), is("text/plain"));
        assertNull(messageCaptor.getValue().getReplyTo());
        assertNotNull(messageCaptor.getValue().getApplicationProperties());
        assertThat(messageCaptor.getValue().getApplicationProperties().getValue().get("appKey"), is("appValue"));
    }
}
