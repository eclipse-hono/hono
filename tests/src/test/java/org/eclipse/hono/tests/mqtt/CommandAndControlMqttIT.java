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

package org.eclipse.hono.tests.mqtt;

import java.net.HttpURLConnection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.tests.GenericMessageSender;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.ResourceIdentifier;
import org.eclipse.hono.util.TenantObject;
import org.eclipse.hono.util.TimeUntilDisconnectNotification;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.mqtt.messages.MqttPublishMessage;
import io.vertx.proton.ProtonHelper;


/**
 * Integration tests for sending commands to device connected to the MQTT adapter.
 *
 */
@RunWith(VertxUnitRunner.class)
public class CommandAndControlMqttIT extends MqttTestBase {

    private static final String COMMAND_TOPIC_TEMPLATE = CommandConstants.COMMAND_ENDPOINT + "/%s/%s";
    private static final String COMMAND_RESPONSE_TOPIC_TEMPLATE = "control///res/%s/%d";

    private String tenantId;
    private String deviceId;
    private String password = "secret";
    private TenantObject tenant;

    /**
     * Sets up the fixture.
     */
    @Before
    public void setUp() {
        LOGGER.info("running {}", testName.getMethodName());
        tenantId = helper.getRandomTenantId();
        deviceId = helper.getRandomDeviceId(tenantId);
        tenant = TenantObject.from(tenantId, true);
    }

    private Future<MessageConsumer> createConsumer(final String tenantId, final Consumer<Message> messageConsumer) {

        return helper.honoClient.createEventConsumer(tenantId, messageConsumer, remoteClose -> {});
    }

    private Future<Void> subscribeToCommands(final Handler<MqttPublishMessage> msgHandler) {
        final Future<Void> result = Future.future();
        context.runOnContext(go -> {
            mqttClient.publishHandler(msgHandler);
            mqttClient.subscribeCompletionHandler(subAckMsg -> {
                if (subAckMsg.grantedQoSLevels().contains(0)) {
                    result.complete();
                } else {
                    result.fail("could not subscribe to command topic");
                }
            });
            mqttClient.subscribe("control/+/+/req/#", 0);
        });
        return result;
    }

    /**
     * Verifies that the adapter forwards commands and response hence and forth between
     * an application and a device.
     * 
     * @param ctx The vert.x test context.
     * @throws InterruptedException if not all commands and responses are exchanged in time.
     */
    @Test
    public void testSendCommandSucceeds(final TestContext ctx) throws InterruptedException {

        final Async setup = ctx.async();
        final Async notificationReceived = ctx.async();

        connectToAdapter(tenant, deviceId, password, () -> createConsumer(tenantId, msg -> {
            // expect empty notification with TTD -1
            ctx.assertEquals(EventConstants.CONTENT_TYPE_EMPTY_NOTIFICATION, msg.getContentType());
            final TimeUntilDisconnectNotification notification = TimeUntilDisconnectNotification.fromMessage(msg).orElse(null);
            LOGGER.info("received notification [{}]", notification);
            ctx.assertNotNull(notification);
            if (notification.getTtd() == -1) {
                notificationReceived.complete();
            }
        })).compose(conAck -> subscribeToCommands(msg -> {
            final ResourceIdentifier topic = ResourceIdentifier.fromString(msg.topicName());
            if (CommandConstants.COMMAND_ENDPOINT.equals(topic.getEndpoint())) {
                // extract command and request ID
                final String commandRequestId = topic.getResourcePath()[4];
                final String command = topic.getResourcePath()[5];
                LOGGER.trace("received command [name: {}, req-id: {}]", command, commandRequestId);
                // send response
                final String responseTopic = String.format(COMMAND_RESPONSE_TOPIC_TEMPLATE, commandRequestId, HttpURLConnection.HTTP_OK);
                LOGGER.trace("publishing response [topic: {}]", responseTopic);
                mqttClient.publish(
                        responseTopic,
                        Buffer.buffer(command + ": ok"),
                        MqttQoS.AT_MOST_ONCE,
                        false,
                        false);
            }
        })).setHandler(ctx.asyncAssertSuccess(ok -> setup.complete()));
        setup.await();
        notificationReceived.await();

        final int totalNoOfcommandsToSend = 60;
        final CountDownLatch responsesReceived = new CountDownLatch(totalNoOfcommandsToSend);
        final AtomicInteger commandsSent = new AtomicInteger(0);
        final AtomicLong lastReceivedTimestamp = new AtomicLong();
        final long start = System.currentTimeMillis();

        while (commandsSent.get() < totalNoOfcommandsToSend) {
            final Async commandSent = ctx.async();
            context.runOnContext(go -> {
                final Buffer msg = Buffer.buffer("value: " + commandsSent.getAndIncrement());
                helper.sendCommand(tenantId, deviceId, "setValue", "text/plain", msg, 200).setHandler(sendAttempt -> {
                    if (sendAttempt.failed()) {
                        LOGGER.debug("error sending command {}", commandsSent.get(), sendAttempt.cause());
                    } else {
                        lastReceivedTimestamp.set(System.currentTimeMillis());
                        responsesReceived.countDown();
                        if (responsesReceived.getCount() % 20 == 0) {
                            LOGGER.info("responses received: {}", totalNoOfcommandsToSend - responsesReceived.getCount());
                        }
                    }
                    if (commandsSent.get() % 20 == 0) {
                        LOGGER.info("commands sent: " + commandsSent.get());
                    }
                    commandSent.complete();
                });
            });

            commandSent.await();
        }

        final long timeToWait = totalNoOfcommandsToSend * 200;
        if (!responsesReceived.await(timeToWait, TimeUnit.MILLISECONDS)) {
            LOGGER.info("Timeout of {} milliseconds reached, stop waiting to receive command responses.", timeToWait);
        }
        final long messagesReceived = totalNoOfcommandsToSend - responsesReceived.getCount();
        LOGGER.info("sent {} commands and received {} responses in {} milliseconds",
                commandsSent.get(), messagesReceived, lastReceivedTimestamp.get() - start);
        if (messagesReceived != commandsSent.get()) {
            ctx.fail("did not receive a response for each command sent");
        }
    }

    /**
     * Verifies that the adapter rejects malformed command messages sent by applications.
     * 
     * @param ctx The vert.x test context.
     * @throws InterruptedException if not all commands and responses are exchanged in time.
     */
    @Test
    public void testSendCommandFailsForMalformedMessage(final TestContext ctx) throws InterruptedException {

        final Async setup = ctx.async();
        final Async notificationReceived = ctx.async();

        connectToAdapter(tenant, deviceId, password, () -> createConsumer(tenantId, msg -> {
            // expect empty notification with TTD -1
            ctx.assertEquals(EventConstants.CONTENT_TYPE_EMPTY_NOTIFICATION, msg.getContentType());
            final TimeUntilDisconnectNotification notification = TimeUntilDisconnectNotification.fromMessage(msg).orElse(null);
            LOGGER.info("received notification [{}]", notification);
            ctx.assertNotNull(notification);
            if (notification.getTtd() == -1) {
                notificationReceived.complete();
            }
        })).compose(conAck -> subscribeToCommands(msg -> {
            ctx.fail("should not have received command");
        })).setHandler(ctx.asyncAssertSuccess(ok -> setup.complete()));
        setup.await();
        notificationReceived.await();

        final AtomicReference<GenericMessageSender> sender = new AtomicReference<>();
        final Async senderCreation = ctx.async();
        final String commandTopic = String.format(COMMAND_TOPIC_TEMPLATE, tenantId, deviceId);

        helper.honoClient.createGenericMessageSender(commandTopic).map(s -> {
            sender.set(s);
            senderCreation.complete();
            return s;
        });
        senderCreation.await(2000);

        // send a message without subject
        final Message messageWithoutSubject = ProtonHelper.message("input data");
        messageWithoutSubject.setMessageId("message-id");
        messageWithoutSubject.setReplyTo("reply/to/address");
        sender.get().sendAndWaitForOutcome(messageWithoutSubject).setHandler(ctx.asyncAssertFailure(t -> {
            ctx.assertTrue(t instanceof ClientErrorException);
        }));

        // send a message without message and correlation ID
        final Message messageWithoutId = ProtonHelper.message("input data");
        messageWithoutSubject.setSubject("setValue");
        messageWithoutSubject.setReplyTo("reply/to/address");
        sender.get().sendAndWaitForOutcome(messageWithoutId).setHandler(ctx.asyncAssertFailure(t -> {
            ctx.assertTrue(t instanceof ClientErrorException);
        }));

        // send a message without reply-to address
        final Message messageWithoutReplyTo = ProtonHelper.message("input data");
        messageWithoutSubject.setSubject("setValue");
        messageWithoutSubject.setMessageId("message-id");
        sender.get().sendAndWaitForOutcome(messageWithoutReplyTo).setHandler(ctx.asyncAssertFailure(t -> {
            ctx.assertTrue(t instanceof ClientErrorException);
        }));
    }
}
