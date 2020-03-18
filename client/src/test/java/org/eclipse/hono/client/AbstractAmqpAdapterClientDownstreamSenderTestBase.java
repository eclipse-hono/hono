/*******************************************************************************
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Map;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.impl.HonoClientUnitTestHelper;
import org.eclipse.hono.util.MessageHelper;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.proton.ProtonSender;

/**
 * Base class for tests of the downstream senders of the AMQP Adapter client.
 *
 */
public abstract class AbstractAmqpAdapterClientDownstreamSenderTestBase {

    protected final String tenantId = "test-tenant";
    protected final String deviceId = "test-device";
    protected final String contentType = "text/plain";
    protected final byte[] payload = "test-value".getBytes();
    protected final String testPropertyKey = "test-key";
    protected final String testPropertyValue = "test-value";
    protected final Map<String, ?> applicationProperties = Collections.singletonMap(testPropertyKey, testPropertyValue);

    protected ProtonSender sender;
    protected HonoConnection connection;
    protected ArgumentCaptor<Message> messageArgumentCaptor;

    /**
     * Sets up fixture.
     */
    @BeforeEach
    public void setUp() {
        sender = HonoClientUnitTestHelper.mockProtonSender();
        final Vertx vertx = mock(Vertx.class);
        connection = HonoClientUnitTestHelper.mockHonoConnection(vertx);
        when(connection.createSender(any(), any(), any())).thenReturn(Future.succeededFuture(sender));
        messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
    }

    /**
     * Executes the assertions that checkthat the message produced conforms to the expectations of the AMQP adapter.
     * 
     * @param expectedAddress The expected target address.
     */
    protected void assertMessageConformsAmqpAdapterSpec(final String expectedAddress) {

        verify(sender).send(messageArgumentCaptor.capture(), any());

        final Message message = messageArgumentCaptor.getValue();

        assertThat(message.getAddress()).isEqualTo(expectedAddress);

        assertThat(MessageHelper.getPayloadAsString(message)).isEqualTo(new String(payload));
        assertThat(message.getContentType()).isEqualTo(contentType);

        final Map<String, Object> applicationProperties = message.getApplicationProperties().getValue();
        assertThat(applicationProperties.get(testPropertyKey)).isEqualTo(testPropertyValue);

        assertThat(applicationProperties.get("device_id")).isEqualTo(deviceId);
    }

}
