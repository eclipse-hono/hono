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

package org.eclipse.hono.tests.amqp;

import java.net.HttpURLConnection;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.apache.qpid.proton.amqp.messaging.Accepted;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.CommandClient;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.tests.GenericMessageSender;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.TenantObject;
import org.eclipse.hono.util.TimeUntilDisconnectNotification;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonMessageHandler;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonReceiver;


/**
 * Integration tests for sending commands to device connected to the MQTT adapter.
 *
 */
@RunWith(VertxUnitRunner.class)
public class CommandAndControlAmqpIT extends AmqpAdapterTestBase {

    private static final String COMMAND_ADDRESS_TEMPLATE = CommandConstants.COMMAND_ENDPOINT + "/%s/%s";

    private String tenantId;
    private String deviceId;
    private String password = "secret";
    private TenantObject tenant;

    /**
     * Sets up the fixture.
     */
    @Before
    public void setUp() {
        log.info("running {}", testName.getMethodName());
        tenantId = helper.getRandomTenantId();
        deviceId = helper.getRandomDeviceId(tenantId);
        tenant = TenantObject.from(tenantId, true);
    }

    private Future<MessageConsumer> createEventConsumer(final String tenantId, final Consumer<Message> messageConsumer) {

        return helper.honoClient.createEventConsumer(tenantId, messageConsumer, remoteClose -> {});
    }

    private Future<ProtonReceiver> subscribeToCommands(
            final String tenantId,
            final String deviceId,
            final ProtonMessageHandler msgHandler) {

        final Future<ProtonReceiver> result = Future.future();
        context.runOnContext(go -> {
            final ProtonReceiver recv = connection.createReceiver(String.format(COMMAND_ADDRESS_TEMPLATE, tenantId, deviceId));
            recv.setQoS(ProtonQoS.AT_LEAST_ONCE);
            recv.openHandler(result);
            recv.handler(msgHandler);
            recv.open();
        });
        return result.map(commandConsumer -> {
            log.debug("created command consumer [{}]", commandConsumer.getSource().getAddress());
            return commandConsumer;
        });
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

        connectToAdapter(tenant, deviceId, password, () -> createEventConsumer(tenantId, msg -> {
            // expect empty notification with TTD -1
            ctx.assertEquals(EventConstants.CONTENT_TYPE_EMPTY_NOTIFICATION, msg.getContentType());
            final TimeUntilDisconnectNotification notification = TimeUntilDisconnectNotification.fromMessage(msg).orElse(null);
            log.debug("received notification [{}]", notification);
            ctx.assertNotNull(notification);
            if (notification.getTtd() == -1) {
                notificationReceived.complete();
            }
        }))
        .compose(con -> createProducer(null))
        .compose(sender -> subscribeToCommands(tenantId, deviceId, (delivery, msg) -> {
            ctx.assertNotNull(msg.getReplyTo());
            ctx.assertNotNull(msg.getSubject());
            ctx.assertNotNull(msg.getCorrelationId());
            final String command = msg.getSubject();
            final Object correlationId = msg.getCorrelationId();
            log.debug("received command [name: {}, reply-to: {}, correlation-id: {}]", command, msg.getReplyTo(), correlationId);
            // send response
            final Message commandResponse = ProtonHelper.message(command + " ok");
            commandResponse.setAddress(msg.getReplyTo());
            commandResponse.setCorrelationId(correlationId);
            commandResponse.setContentType("text/plain");
            MessageHelper.addProperty(commandResponse, MessageHelper.APP_PROPERTY_STATUS, HttpURLConnection.HTTP_OK);
            log.debug("sending response [to: {}, correlation-id: {}]", commandResponse.getAddress(), commandResponse.getCorrelationId());
            sender.send(commandResponse, updatedDelivery -> {
                if (!Accepted.class.isInstance(updatedDelivery.getRemoteState())) {
                    log.error("AMQP adapter did not accept command response [remote state: {}]",
                            updatedDelivery.getRemoteState().getClass().getSimpleName());
                }
            });
        }))
        .setHandler(ctx.asyncAssertSuccess(ok -> setup.complete()));
        setup.await();
        notificationReceived.await();

        final int totalNoOfcommandsToSend = 60;
        final CountDownLatch responsesReceived = new CountDownLatch(totalNoOfcommandsToSend);
        final AtomicInteger commandsSent = new AtomicInteger(0);
        final AtomicLong lastReceivedTimestamp = new AtomicLong();
        final long start = System.currentTimeMillis();

        final Async commandClientCreation = ctx.async();
        final Future<CommandClient> commandClient = helper.honoClient.getOrCreateCommandClient(tenantId, deviceId, "test-client")
                .setHandler(ctx.asyncAssertSuccess(c -> {
                    c.setRequestTimeout(200);
                    commandClientCreation.complete();
                }));
        commandClientCreation.await();

        while (commandsSent.get() < totalNoOfcommandsToSend) {
            final Async commandSent = ctx.async();
            context.runOnContext(go -> {
                final Buffer msg = Buffer.buffer("value: " + commandsSent.getAndIncrement());
                commandClient.result().sendCommand("setValue", "text/plain", msg, null).setHandler(sendAttempt -> {
                    if (sendAttempt.failed()) {
                        log.debug("error sending command {}", commandsSent.get(), sendAttempt.cause());
                    } else {
                        lastReceivedTimestamp.set(System.currentTimeMillis());
                        responsesReceived.countDown();
                        if (responsesReceived.getCount() % 20 == 0) {
                            log.info("responses received: {}", totalNoOfcommandsToSend - responsesReceived.getCount());
                        }
                    }
                    if (commandsSent.get() % 20 == 0) {
                        log.info("commands sent: " + commandsSent.get());
                    }
                    commandSent.complete();
                });
            });

            commandSent.await();
        }

        final long timeToWait = totalNoOfcommandsToSend * 200;
        if (!responsesReceived.await(timeToWait, TimeUnit.MILLISECONDS)) {
            log.info("Timeout of {} milliseconds reached, stop waiting to receive command responses.", timeToWait);
        }
        final long messagesReceived = totalNoOfcommandsToSend - responsesReceived.getCount();
        log.info("sent {} commands and received {} responses in {} milliseconds",
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

        connectToAdapter(tenant, deviceId, password, () -> createEventConsumer(tenantId, msg -> {
            // expect empty notification with TTD -1
            ctx.assertEquals(EventConstants.CONTENT_TYPE_EMPTY_NOTIFICATION, msg.getContentType());
            final TimeUntilDisconnectNotification notification = TimeUntilDisconnectNotification.fromMessage(msg).orElse(null);
            log.debug("received notification [{}]", notification);
            ctx.assertNotNull(notification);
            if (notification.getTtd() == -1) {
                notificationReceived.complete();
            }
        })).compose(con -> subscribeToCommands(tenantId, deviceId, (delivery, msg) -> {
            ctx.fail("should not have received command");
        })).setHandler(ctx.asyncAssertSuccess(ok -> setup.complete()));
        setup.await();
        notificationReceived.await();

        final AtomicReference<GenericMessageSender> sender = new AtomicReference<>();
        final Async senderCreation = ctx.async();
        final String targetAddress = String.format(COMMAND_ADDRESS_TEMPLATE, tenantId, deviceId);

        helper.honoClient.createGenericMessageSender(targetAddress).map(s -> {
            log.debug("created generic sender for sending commands [target address: {}]", targetAddress);
            sender.set(s);
            senderCreation.complete();
            return s;
        });
        senderCreation.await(2000);

        log.debug("sending command message lacking subject");
        final Message messageWithoutSubject = ProtonHelper.message("input data");
        messageWithoutSubject.setMessageId("message-id");
        messageWithoutSubject.setReplyTo("reply/to/address");
        sender.get().sendAndWaitForOutcome(messageWithoutSubject).setHandler(ctx.asyncAssertFailure(t -> {
            ctx.assertTrue(t instanceof ClientErrorException);
        }));

        log.debug("sending command message lacking message ID and correlation ID");
        final Message messageWithoutId = ProtonHelper.message("input data");
        messageWithoutId.setSubject("setValue");
        messageWithoutId.setReplyTo("reply/to/address");
        sender.get().sendAndWaitForOutcome(messageWithoutId).setHandler(ctx.asyncAssertFailure(t -> {
            ctx.assertTrue(t instanceof ClientErrorException);
        }));
    }

    /**
     * Registers a device and opens a connection to the MQTT adapter using
     * the device's credentials.
     * 
     * @param tenant The tenant that the device belongs to.
     * @param deviceId The identifier of the device.
     * @param password The password to use for authentication.
     * @param consumerFactory The factory for creating the consumer of messages
     *                   published by the device or {@code null} if no consumer
     *                   should be created.
     * @return A future that will be completed with the CONNACK packet received
     *         from the adapter or failed if the connection could not be established. 
     */
    protected final Future<ProtonConnection> connectToAdapter(
            final TenantObject tenant,
            final String deviceId,
            final String password,
            final Supplier<Future<MessageConsumer>> consumerFactory) {

        return helper.registry
        .addDeviceForTenant(tenant, deviceId, password)
        .compose(ok -> Optional.ofNullable(consumerFactory)
                .map(factory -> factory.get())
                .orElse(Future.succeededFuture()))
        .compose(ok -> connectToAdapter(IntegrationTestSupport.getUsername(deviceId, tenant.getTenantId()), password))
        .recover(t -> {
            log.error("failed to establish connection to AMQP adapter [host: {}, port: {}]",
                    IntegrationTestSupport.AMQP_HOST, IntegrationTestSupport.AMQP_PORT, t);
            return Future.failedFuture(t);
        });

    }

}
