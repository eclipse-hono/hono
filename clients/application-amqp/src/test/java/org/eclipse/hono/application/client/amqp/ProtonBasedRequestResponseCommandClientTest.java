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
package org.eclipse.hono.application.client.amqp;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;

import java.net.HttpURLConnection;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.client.amqp.connection.AmqpUtils;
import org.eclipse.hono.client.amqp.connection.HonoConnection;
import org.eclipse.hono.client.amqp.connection.SendMessageSampler;
import org.eclipse.hono.client.amqp.test.AmqpClientUnitTestHelper;
import org.eclipse.hono.test.VertxMockSupport;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.MessageHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;

import io.opentracing.noop.NoopSpan;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonMessageHandler;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.ProtonSender;

/**
 * Tests verifying behavior of {@link ProtonBasedRequestResponseCommandClient}.
 */
@ExtendWith(VertxExtension.class)
@Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
public class ProtonBasedRequestResponseCommandClientTest {
    private HonoConnection connection;
    private ProtonBasedRequestResponseCommandClient requestResponseCommandClient;
    private ProtonSender sender;
    private ProtonDelivery protonDelivery;
    private String tenantId;
    private String deviceId;
    private Vertx vertx;

    /**
     * Sets up the fixture.
     */
    @BeforeEach
    void setUp() {
        vertx = mock(Vertx.class);
        connection = AmqpClientUnitTestHelper.mockHonoConnection(vertx);

        final ProtonReceiver receiver = AmqpClientUnitTestHelper.mockProtonReceiver();
        when(connection.createReceiver(anyString(), any(ProtonQoS.class), any(ProtonMessageHandler.class),
                VertxMockSupport.anyHandler()))
                        .thenReturn(Future.succeededFuture(receiver));

        protonDelivery = mock(ProtonDelivery.class);
        sender = AmqpClientUnitTestHelper.mockProtonSender();
        when(sender.send(any(Message.class), VertxMockSupport.anyHandler())).thenReturn(protonDelivery);
        when(connection.createSender(anyString(), any(), any())).thenReturn(Future.succeededFuture(sender));
        requestResponseCommandClient = new ProtonBasedRequestResponseCommandClient(connection, SendMessageSampler.Factory.noop());

        tenantId = UUID.randomUUID().toString();
        deviceId = UUID.randomUUID().toString();
    }

    /**
     * Verifies that a command has been sent to a device and also a response for that command from that device has been
     * received successfully.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testSendCommandSucceeds(final VertxTestContext ctx) {
        final String subject = "setVolume";
        final String replyId = UUID.randomUUID().toString();
        final int commandStatus = HttpURLConnection.HTTP_OK;
        final String responseContentType = MessageHelper.CONTENT_TYPE_APPLICATION_JSON;
        final String responsePayload = "{\"status\": 1}";

        // WHEN sending a command with some application properties and payload
        requestResponseCommandClient
                .sendCommand(tenantId, deviceId, subject, null, Buffer.buffer("{\"value\": 20}"), replyId,
                        null, NoopSpan.INSTANCE.context())
                .onComplete(ctx.succeeding(response -> {
                    ctx.verify(() -> {
                        // VERIFY the properties of the received response.
                        assertThat(response.getStatus()).isEqualTo(commandStatus);
                        assertThat(response.getContentType()).isEqualTo(responseContentType);
                        assertThat(response.getPayload().toString()).isEqualTo(responsePayload);
                    });
                    ctx.completeNow();
                }));

        // VERIFY that the command has been sent and its properties
        final Message sentMessage = AmqpClientUnitTestHelper.assertMessageHasBeenSent(sender);
        assertThat(sentMessage.getSubject()).isEqualTo(subject);
        assertThat(sentMessage.getReplyTo()).endsWith(replyId);

        final Message response = ProtonHelper.message();
        response.setCorrelationId(sentMessage.getMessageId());
        AmqpUtils.addTenantId(response, tenantId);
        AmqpUtils.addDeviceId(response, deviceId);
        AmqpUtils.addStatus(response, commandStatus);
        AmqpUtils.setPayload(response, MessageHelper.CONTENT_TYPE_APPLICATION_JSON, Buffer.buffer(responsePayload));
        AmqpClientUnitTestHelper.assertReceiverLinkCreated(connection).handle(protonDelivery, response);
    }

    /**
     * Verifies that sending a command results in a failed Future if the command response contains an error status.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testSendCommandFailsForErrorStatusResponse(final VertxTestContext ctx) {
        final String subject = "setVolume";
        final String replyId = UUID.randomUUID().toString();
        final int commandStatus = HttpURLConnection.HTTP_BAD_REQUEST;
        final String responsePayload = "Unsupported command";

        // WHEN sending a command with some application properties and payload
        requestResponseCommandClient
                .sendCommand(tenantId, deviceId, subject, null, Buffer.buffer("{\"value\": 20}"), replyId,
                        null, NoopSpan.INSTANCE.context())
                .onComplete(ctx.failing(thr -> {
                    ctx.verify(() -> {
                        // VERIFY the returned exception
                        assertThat(thr).isInstanceOf(ServiceInvocationException.class);
                        assertThat(((ServiceInvocationException) thr).getErrorCode()).isEqualTo(commandStatus);
                        assertThat(thr.getMessage()).isEqualTo(responsePayload);
                    });
                    ctx.completeNow();
                }));

        // VERIFY that the command has been sent and its properties
        final Message sentMessage = AmqpClientUnitTestHelper.assertMessageHasBeenSent(sender);
        assertThat(sentMessage.getSubject()).isEqualTo(subject);
        assertThat(sentMessage.getReplyTo()).endsWith(replyId);

        final Message response = ProtonHelper.message();
        response.setCorrelationId(sentMessage.getMessageId());
        AmqpUtils.addTenantId(response, tenantId);
        AmqpUtils.addDeviceId(response, deviceId);
        AmqpUtils.addStatus(response, commandStatus);
        AmqpUtils.setPayload(response, MessageHelper.CONTENT_TYPE_TEXT_PLAIN, Buffer.buffer(responsePayload));
        AmqpClientUnitTestHelper.assertReceiverLinkCreated(connection).handle(protonDelivery, response);
    }

    /**
     * Verifies that a command sent has its properties set correctly.
     *
     * <ul>
     * <li>subject set to given command</li>
     * <li>message-id not null</li>
     * <li>content-type set to given type</li>
     * <li>target address set to command/${tenant_id}/${device_id}</li>
     * <li>reply-to address set to command_response/${tenant_id}/${reply_id}</li>
     * </ul>
     */
    @Test
    public void testSendCommandSetsProperties() {
        final String replyId = UUID.randomUUID().toString();

        requestResponseCommandClient.sendCommand(
                tenantId,
                deviceId,
                "doSomething",
                "text/plain",
                Buffer.buffer("payload"),
                replyId,
                Duration.ofSeconds(10),
                NoopSpan.INSTANCE.context());

        final ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(sender).send(messageCaptor.capture(), VertxMockSupport.anyHandler());
        assertThat(messageCaptor.getValue().getAddress()).isEqualTo(
                String.format("%s/%s/%s", CommandConstants.NORTHBOUND_COMMAND_REQUEST_ENDPOINT, tenantId, deviceId));
        assertThat(messageCaptor.getValue().getReplyTo()).isEqualTo(
                String.format("%s/%s/%s", CommandConstants.NORTHBOUND_COMMAND_RESPONSE_ENDPOINT, tenantId, replyId));
        assertThat(messageCaptor.getValue().getSubject()).isEqualTo("doSomething");
        assertNotNull(messageCaptor.getValue().getMessageId());
        assertThat(messageCaptor.getValue().getContentType()).isEqualTo("text/plain");
    }

    /**
     * Verifies that sending a command times out with a failed future containing the appropriate error code.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    void testSendCommandAndReceiveResponseTimesOut(final VertxTestContext ctx) {
        final String subject = "sendCommandTimesOut";
        final String replyId = UUID.randomUUID().toString();

        // Run the timer immediately to simulate the time out.
        VertxMockSupport.runTimersImmediately(vertx);

        // WHEN sending a command with some application properties and payload
        requestResponseCommandClient
                .sendCommand(tenantId, deviceId, subject, null, Buffer.buffer("test"), replyId,
                        Duration.ofMillis(1), NoopSpan.INSTANCE.context())
                .onComplete(ctx.failing(error -> {
                    ctx.verify(() -> {
                        // VERIFY the error code.
                        assertThat(error).isInstanceOf(ServerErrorException.class);
                        assertThat(((ServerErrorException) error).getErrorCode())
                                .isEqualTo(HttpURLConnection.HTTP_UNAVAILABLE);
                    });
                    ctx.completeNow();
                }));
    }
}
