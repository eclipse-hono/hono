/*******************************************************************************
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
import java.util.function.Function;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.ResourceIdentifier;
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

    private static final int COMMANDS_TO_SEND = 60;

    private String tenantId;
    private String deviceId;
    private final String password = "secret";
    private Tenant tenant;

    /**
     * Sets up the fixture.
     */
    @Override
    @Before
    public void setUp() {
        LOGGER.info("running {}", testName.getMethodName());
        tenantId = helper.getRandomTenantId();
        deviceId = helper.getRandomDeviceId(tenantId);
        tenant = new Tenant();
    }

    private Future<MessageConsumer> createConsumer(final String tenantId, final Consumer<Message> messageConsumer) {

        return helper.applicationClientFactory.createEventConsumer(tenantId, messageConsumer, remoteClose -> {});
    }

    private Future<Void> subscribeToCommands(final Handler<MqttPublishMessage> msgHandler, final int qos) {
        final Future<Void> result = Future.future();
        context.runOnContext(go -> {
            mqttClient.publishHandler(msgHandler);
            mqttClient.subscribeCompletionHandler(subAckMsg -> {
                if (subAckMsg.grantedQoSLevels().contains(qos)) {
                    result.complete();
                } else {
                    result.fail("could not subscribe to command topic");
                }
            });
            mqttClient.subscribe(getCommandEndpoint() + "/+/+/req/#", qos);
        });
        return result;
    }

    /**
     * Checks whether the legacy Command & Control endpoint shall be used.
     * <p>
     * Returns {@code false} by default. Subclasses may return {@code true} here to perform tests using the legacy
     * command endpoint.
     *
     * @return {@code true} if the legacy command endpoint shall be used.
     */
    protected boolean useLegacyCommandEndpoint() {
        return false;
    }

    private String getCommandEndpoint() {
        return useLegacyCommandEndpoint() ? CommandConstants.COMMAND_LEGACY_ENDPOINT : CommandConstants.COMMAND_ENDPOINT;
    }

    private String getCommandSenderLinkTargetAddress(final String tenantId, final String deviceId) {
        if (useLegacyCommandEndpoint()) {
            return String.format("%s/%s/%s", CommandConstants.COMMAND_LEGACY_ENDPOINT, tenantId, deviceId);
        }
        return String.format("%s/%s", CommandConstants.COMMAND_ENDPOINT, tenantId);
    }

    private String getCommandMessageTargetAddress(final String tenantId, final String deviceId) {
        return String.format("%s/%s/%s", getCommandEndpoint(), tenantId, deviceId);
    }

    /**
     * Verifies that the adapter forwards on-way commands from
     * an application to a device.
     * 
     * @param ctx The vert.x test context.
     * @throws InterruptedException if not all commands and responses are exchanged in time.
     */
    @Test
    public void testSendOneWayCommandSucceeds(final TestContext ctx) throws InterruptedException {

        final int commandsToSend = COMMANDS_TO_SEND;
        final Async commandsReceived = ctx.async(commandsToSend);
        testSendCommandSucceeds(ctx, msg -> {
            final ResourceIdentifier topic = ResourceIdentifier.fromString(msg.topicName());
            ctx.assertEquals(getCommandEndpoint(), topic.getEndpoint());
            // extract command
            final String command = topic.getResourcePath()[5];
            LOGGER.trace("received one-way command [name: {}]", command);
            ctx.assertEquals("setValue", command);
            commandsReceived.countDown();
        }, payload -> {
            return helper.sendOneWayCommand(tenantId, deviceId, "setValue", "text/plain", payload, null, 1000);
        }, commandsToSend, 0);
        commandsReceived.await();
    }

    /**
     * Verifies that the adapter forwards commands with Qos 0 and response hence and forth between an application and a
     * device.
     *
     * @param ctx The vert.x test context.
     * @throws InterruptedException if not all commands and responses are exchanged in time.
     */
    @Test
    public void testSendCommandSucceedsWithQos0(final TestContext ctx) throws InterruptedException {
        testSendCommandSucceeds(ctx, 0);
    }

    /**
     * Verifies that the adapter forwards commands with Qos 1 and response hence and forth between an application and a
     * device.
     *
     * @param ctx The vert.x test context.
     * @throws InterruptedException if not all commands and responses are exchanged in time.
     */
    @Test
    public void testSendCommandSucceedsWithQos1(final TestContext ctx) throws InterruptedException {
        testSendCommandSucceeds(ctx, 1);
    }

    private void testSendCommandSucceeds(final TestContext ctx, final int qos) throws InterruptedException {

        testSendCommandSucceeds(ctx, msg -> {
            final ResourceIdentifier topic = ResourceIdentifier.fromString(msg.topicName());
            if (getCommandEndpoint().equals(topic.getEndpoint())) {
                // extract command and request ID
                final String commandRequestId = topic.getResourcePath()[4];
                final String command = topic.getResourcePath()[5];
                LOGGER.trace("received command [name: {}, req-id: {}]", command, commandRequestId);
                // send response
                final String responseTopic = String.format("%s///res/%s/%d", getCommandEndpoint(), commandRequestId, HttpURLConnection.HTTP_OK);
                mqttClient.publish(
                        responseTopic,
                        Buffer.buffer(command + ": ok"),
                        MqttQoS.AT_MOST_ONCE,
                        false,
                        false);
            }
        }, payload -> {
            return helper.sendCommand(tenantId, deviceId, "setValue", "text/plain", payload, null, 200)
                    .map(response -> {
                        ctx.assertEquals(deviceId, response.getApplicationProperty(MessageHelper.APP_PROPERTY_DEVICE_ID, String.class));
                        ctx.assertEquals(tenantId, response.getApplicationProperty(MessageHelper.APP_PROPERTY_TENANT_ID, String.class));
                        return response;
                    });
        }, COMMANDS_TO_SEND, qos);
    }

    /**
     * Verifies that the adapter forwards commands and response hence and forth between
     * an application and a device.
     * 
     * @param ctx The vert.x test context.
     * @throws InterruptedException if not all commands and responses are exchanged in time.
     */
    private void testSendCommandSucceeds(
            final TestContext ctx,
            final Handler<MqttPublishMessage> commandConsumer,
            final Function<Buffer, Future<?>> commandSender,
            final int totalNoOfCommandsToSend,
            final int qos) throws InterruptedException {

        final Async setup = ctx.async();
        final Async notificationReceived = ctx.async();

        helper.registry
        .addDeviceForTenant(tenantId, tenant, deviceId, password)
        .compose(ok -> connectToAdapter(IntegrationTestSupport.getUsername(deviceId, tenantId), password))
        .compose(ok -> createConsumer(tenantId, msg -> {
            // expect empty notification with TTD -1
            ctx.assertEquals(EventConstants.CONTENT_TYPE_EMPTY_NOTIFICATION, msg.getContentType());
            final TimeUntilDisconnectNotification notification = TimeUntilDisconnectNotification.fromMessage(msg).orElse(null);
            LOGGER.info("received notification [{}]", notification);
            ctx.assertNotNull(notification);
            if (notification.getTtd() == -1) {
                notificationReceived.complete();
            }
        }))
        .compose(conAck -> subscribeToCommands(commandConsumer, qos))
        .setHandler(ctx.asyncAssertSuccess(ok -> setup.complete()));
        setup.await();
        notificationReceived.await();

        final CountDownLatch commandsSucceeded = new CountDownLatch(totalNoOfCommandsToSend);
        final AtomicInteger commandsSent = new AtomicInteger(0);
        final AtomicLong lastReceivedTimestamp = new AtomicLong(0);
        final long start = System.currentTimeMillis();

        while (commandsSent.get() < totalNoOfCommandsToSend) {
            final Async commandSent = ctx.async();
            context.runOnContext(go -> {
                final Buffer msg = Buffer.buffer("value: " + commandsSent.getAndIncrement());
                commandSender.apply(msg).setHandler(sendAttempt -> {
                    if (sendAttempt.failed()) {
                        LOGGER.info("error sending command {}", commandsSent.get(), sendAttempt.cause());
                    } else {
                        lastReceivedTimestamp.set(System.currentTimeMillis());
                        commandsSucceeded.countDown();
                        if (commandsSucceeded.getCount() % 20 == 0) {
                            LOGGER.info("commands succeeded: {}", totalNoOfCommandsToSend - commandsSucceeded.getCount());
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

        final long timeToWait = totalNoOfCommandsToSend * 200;
        if (!commandsSucceeded.await(timeToWait, TimeUnit.MILLISECONDS)) {
            LOGGER.info("Timeout of {} milliseconds reached, stop waiting for commands to succeed", timeToWait);
        }
        if (lastReceivedTimestamp.get() == 0L) {
            // no message has been received at all
            lastReceivedTimestamp.set(System.currentTimeMillis());
        }
        final long commandsCompleted = totalNoOfCommandsToSend - commandsSucceeded.getCount();
        LOGGER.info("commands sent: {}, commands succeeded: {} after {} milliseconds",
                commandsSent.get(), commandsCompleted, lastReceivedTimestamp.get() - start);
        if (commandsCompleted != commandsSent.get()) {
            ctx.fail("did not complete all commands sent");
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

        helper.registry
                .addDeviceForTenant(tenantId, tenant, deviceId, password)
                .compose(ok -> connectToAdapter(IntegrationTestSupport.getUsername(deviceId, tenantId), password))
                .compose(ok -> createConsumer(tenantId, msg -> {
                    // expect empty notification with TTD -1
                    ctx.assertEquals(EventConstants.CONTENT_TYPE_EMPTY_NOTIFICATION, msg.getContentType());
                    final TimeUntilDisconnectNotification notification = TimeUntilDisconnectNotification
                            .fromMessage(msg).orElse(null);
                    LOGGER.info("received notification [{}]", notification);
                    ctx.assertNotNull(notification);
                    if (notification.getTtd() == -1) {
                        notificationReceived.complete();
                    }
                })).compose(conAck -> subscribeToCommands(msg -> {
                    ctx.fail("should not have received command");
                }, 0)).setHandler(ctx.asyncAssertSuccess(ok -> setup.complete()));
        setup.await();
        notificationReceived.await();

        final AtomicReference<MessageSender> sender = new AtomicReference<>();
        final Async senderCreation = ctx.async();
        final String targetAddress = getCommandSenderLinkTargetAddress(tenantId, deviceId);

        helper.applicationClientFactory.createGenericMessageSender(targetAddress).map(s -> {
            LOGGER.debug("created generic sender for sending commands [target address: {}]", targetAddress);
            sender.set(s);
            senderCreation.complete();
            return s;
        });
        senderCreation.await(2000);

        LOGGER.debug("sending command message lacking subject");
        final Message messageWithoutSubject = ProtonHelper.message("input data");
        messageWithoutSubject.setAddress(getCommandMessageTargetAddress(tenantId, deviceId));
        messageWithoutSubject.setMessageId("message-id");
        messageWithoutSubject.setReplyTo("reply/to/address");
        sender.get().sendAndWaitForOutcome(messageWithoutSubject).setHandler(ctx.asyncAssertFailure(t -> {
            ctx.assertTrue(t instanceof ClientErrorException);
        }));

        LOGGER.debug("sending command message lacking message ID and correlation ID");
        final Message messageWithoutId = ProtonHelper.message("input data");
        messageWithoutId.setAddress(getCommandMessageTargetAddress(tenantId, deviceId));
        messageWithoutId.setSubject("setValue");
        messageWithoutId.setReplyTo("reply/to/address");
        sender.get().sendAndWaitForOutcome(messageWithoutId).setHandler(ctx.asyncAssertFailure(t -> {
            ctx.assertTrue(t instanceof ClientErrorException);
        }));
    }
}
