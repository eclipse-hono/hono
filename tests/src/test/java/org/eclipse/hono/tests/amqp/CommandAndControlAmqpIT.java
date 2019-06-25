/*******************************************************************************
 * Copyright (c) 2018, 2019 Contributors to the Eclipse Foundation
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
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.qpid.proton.amqp.messaging.Accepted;
import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.AsyncCommandClient;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.CommandClient;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.util.BufferResult;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.TenantObject;
import org.eclipse.hono.util.TimeUntilDisconnectNotification;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.CompositeFuture;
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
import io.vertx.proton.ProtonSender;


/**
 * Integration tests for sending commands to a device connected to the AMQP adapter.
 *
 */
@RunWith(VertxUnitRunner.class)
public class CommandAndControlAmqpIT extends AmqpAdapterTestBase {

    private static final String REJECTED_COMMAND_ERROR_MESSAGE = "rejected command error message";
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

    private String getCommandSubscriptionAddress(final String tenantId, final String deviceId) {
        return String.format("%s/%s/%s", getCommandEndpoint(), tenantId, deviceId);
    }

    private Future<MessageConsumer> createEventConsumer(final String tenantId, final Consumer<Message> messageConsumer) {

        return helper.applicationClientFactory.createEventConsumer(tenantId, messageConsumer, remoteClose -> {});
    }

    private Future<ProtonReceiver> subscribeToCommands(
            final String tenantId,
            final String deviceId,
            final ProtonMessageHandler msgHandler) {

        final Future<ProtonReceiver> result = Future.future();
        context.runOnContext(go -> {
            final ProtonReceiver recv = connection.createReceiver(getCommandSubscriptionAddress(tenantId, deviceId));
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

    private void connectAndSubscribe(
            final TestContext ctx,
            final Function<ProtonSender, ProtonMessageHandler> commandConsumerFactory) {

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
        .compose(sender -> subscribeToCommands(tenantId, deviceId, commandConsumerFactory.apply(sender)))
        .setHandler(ctx.asyncAssertSuccess(ok -> setup.complete()));
        setup.await();
        notificationReceived.await();
    }

    private ProtonMessageHandler createCommandConsumer(final TestContext ctx, final ProtonSender sender) {

        return (delivery, msg) -> {
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
        };
    }

    private ProtonMessageHandler createRejectingCommandConsumer(final TestContext ctx) {
        return (delivery, msg) -> {
            ctx.assertNotNull(msg.getReplyTo());
            ctx.assertNotNull(msg.getSubject());
            ctx.assertNotNull(msg.getCorrelationId());
            final String command = msg.getSubject();
            final Object correlationId = msg.getCorrelationId();
            log.debug("received command [name: {}, reply-to: {}, correlation-id: {}]", command, msg.getReplyTo(), correlationId);
            final Rejected rejected = new Rejected();
            rejected.setError(new ErrorCondition(Constants.AMQP_BAD_REQUEST, REJECTED_COMMAND_ERROR_MESSAGE));
            delivery.disposition(rejected, true);
        };
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

        final int commandsToSend = 60;
        final Async commandsReceived = ctx.async(commandsToSend);

        testSendCommandSucceeds(ctx, sender -> (delivery, msg) -> {
            ctx.assertNull(msg.getReplyTo());
            ctx.assertEquals("setValue", msg.getSubject());
            log.debug("received command [name: {}]", msg.getSubject());
            commandsReceived.countDown();
        }, (commandClient, payload) -> {
            return commandClient.sendOneWayCommand("setValue", "text/plain", payload, null);
        }, commandsToSend);

        commandsReceived.await();
    }

    /**
     * Verifies that the adapter forwards commands and responses hence and forth between
     * an application and a device that have been sent using the async API.
     * 
     * 
     * @param ctx The vert.x test context.
     * @throws InterruptedException if not all commands and responses are exchanged in time.
     */
    @Test
    public void testSendAsyncCommandsSucceeds(final TestContext ctx) throws InterruptedException {

        connectAndSubscribe(ctx, sender -> createCommandConsumer(ctx, sender));

        final String replyId = "reply-id";
        final int totalNoOfcommandsToSend = 60;
        final CountDownLatch commandsSucceeded = new CountDownLatch(totalNoOfcommandsToSend);
        final AtomicInteger commandsSent = new AtomicInteger(0);
        final AtomicLong lastReceivedTimestamp = new AtomicLong();

        final Async commandClientCreation = ctx.async();

        final Future<MessageConsumer> asyncResponseConsumer = helper.applicationClientFactory.createAsyncCommandResponseConsumer(
                tenantId,
                replyId,
                response -> {
                    lastReceivedTimestamp.set(System.currentTimeMillis());
                    commandsSucceeded.countDown();
                    if (commandsSucceeded.getCount() % 20 == 0) {
                        log.info("command responses received: {}", totalNoOfcommandsToSend - commandsSucceeded.getCount());
                    }
                },
                null);
        final Future<AsyncCommandClient> asyncCommandClient = helper.applicationClientFactory.getOrCreateAsyncCommandClient(tenantId, deviceId);

        CompositeFuture.all(asyncResponseConsumer, asyncCommandClient).setHandler(ctx.asyncAssertSuccess(ok -> commandClientCreation.complete()));
        commandClientCreation.await();

        final long start = System.currentTimeMillis();

        while (commandsSent.get() < totalNoOfcommandsToSend) {
            final Async commandSent = ctx.async();
            context.runOnContext(go -> {
                final String correlationId = String.valueOf(commandsSent.getAndIncrement());
                final Buffer msg = Buffer.buffer("value: " + correlationId);
                asyncCommandClient.result().sendAsyncCommand("setValue", "text/plain", msg, correlationId, replyId, null).setHandler(sendAttempt -> {
                    if (sendAttempt.failed()) {
                        log.debug("error sending command {}", correlationId, sendAttempt.cause());
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
        if (!commandsSucceeded.await(timeToWait, TimeUnit.MILLISECONDS)) {
            log.info("Timeout of {} milliseconds reached, stop waiting for command responses", timeToWait);
        }
        final long commandsCompleted = totalNoOfcommandsToSend - commandsSucceeded.getCount();
        log.info("commands sent: {}, responses received: {} after {} milliseconds",
                commandsSent.get(), commandsCompleted, lastReceivedTimestamp.get() - start);
        if (commandsCompleted != commandsSent.get()) {
            ctx.fail("did not complete all commands sent");
        }
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

        testSendCommandSucceeds(
                ctx,
                sender -> createCommandConsumer(ctx, sender),
                (commandClient, payload) -> {
                    return commandClient.sendCommand("setValue", "text/plain", payload, null)
                            .map(response -> {
                                ctx.assertEquals(deviceId, response.getApplicationProperty(MessageHelper.APP_PROPERTY_DEVICE_ID, String.class));
                                ctx.assertEquals(tenantId, response.getApplicationProperty(MessageHelper.APP_PROPERTY_TENANT_ID, String.class));
                                return response;
                            });
                },
                60);
    }

    private void testSendCommandSucceeds(
            final TestContext ctx,
            final Function<ProtonSender, ProtonMessageHandler> commandConsumerFactory,
            final BiFunction<CommandClient, Buffer, Future<?>> commandSender,
            final int totalNoOfcommandsToSend) throws InterruptedException {

        connectAndSubscribe(ctx, commandConsumerFactory);

        final CountDownLatch commandsSucceeded = new CountDownLatch(totalNoOfcommandsToSend);
        final AtomicInteger commandsSent = new AtomicInteger(0);
        final AtomicLong lastReceivedTimestamp = new AtomicLong();
        final long start = System.currentTimeMillis();

        final Async commandClientCreation = ctx.async();
        final Future<CommandClient> commandClient = helper.applicationClientFactory.getOrCreateCommandClient(tenantId, deviceId, "test-client")
                .setHandler(ctx.asyncAssertSuccess(c -> {
                    c.setRequestTimeout(200);
                    commandClientCreation.complete();
                }));
        commandClientCreation.await();

        while (commandsSent.get() < totalNoOfcommandsToSend) {
            final Async commandSent = ctx.async();
            context.runOnContext(go -> {
                final Buffer msg = Buffer.buffer("value: " + commandsSent.getAndIncrement());
                commandSender.apply(commandClient.result(), msg).setHandler(sendAttempt -> {
                    if (sendAttempt.failed()) {
                        log.debug("error sending command {}", commandsSent.get(), sendAttempt.cause());
                    } else {
                        lastReceivedTimestamp.set(System.currentTimeMillis());
                        commandsSucceeded.countDown();
                        if (commandsSucceeded.getCount() % 20 == 0) {
                            log.info("commands succeeded: {}", totalNoOfcommandsToSend - commandsSucceeded.getCount());
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
        if (!commandsSucceeded.await(timeToWait, TimeUnit.MILLISECONDS)) {
            log.info("Timeout of {} milliseconds reached, stop waiting for commands to succeed", timeToWait);
        }
        final long commandsCompleted = totalNoOfcommandsToSend - commandsSucceeded.getCount();
        log.info("commands sent: {}, commands succeeded: {} after {} milliseconds",
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

        final AtomicReference<MessageSender> sender = new AtomicReference<>();
        final Async senderCreation = ctx.async();
        final String targetAddress = getCommandSenderLinkTargetAddress(tenantId, deviceId);

        helper.applicationClientFactory.createGenericMessageSender(targetAddress).map(s -> {
            log.debug("created generic sender for sending commands [target address: {}]", targetAddress);
            sender.set(s);
            senderCreation.complete();
            return s;
        });
        senderCreation.await(2000);

        log.debug("sending command message lacking subject");
        final Message messageWithoutSubject = ProtonHelper.message("input data");
        messageWithoutSubject.setAddress(getCommandMessageTargetAddress(tenantId, deviceId));
        messageWithoutSubject.setMessageId("message-id");
        messageWithoutSubject.setReplyTo("reply/to/address");
        sender.get().sendAndWaitForOutcome(messageWithoutSubject).setHandler(ctx.asyncAssertFailure(t -> {
            ctx.assertTrue(t instanceof ClientErrorException);
        }));

        log.debug("sending command message lacking message ID and correlation ID");
        final Message messageWithoutId = ProtonHelper.message("input data");
        messageWithoutId.setAddress(getCommandMessageTargetAddress(tenantId, deviceId));
        messageWithoutId.setSubject("setValue");
        messageWithoutId.setReplyTo("reply/to/address");
        sender.get().sendAndWaitForOutcome(messageWithoutId).setHandler(ctx.asyncAssertFailure(t -> {
            ctx.assertTrue(t instanceof ClientErrorException);
        }));
    }

    /**
     * Verifies that the adapter forwards the <em>rejected</em> disposition, received from a device, back to the
     * application.
     *
     * @param ctx The vert.x test context.
     * @throws InterruptedException if not all commands and responses are exchanged in time.
     */
    @Test
    public void testSendCommandFailsForCommandRejectedByDevice(final TestContext ctx) throws InterruptedException {

        connectAndSubscribe(ctx, sender -> createRejectingCommandConsumer(ctx));

        final int totalNoOfCommandsToSend = 3;
        final CountDownLatch commandsFailed = new CountDownLatch(totalNoOfCommandsToSend);
        final AtomicInteger commandsSent = new AtomicInteger(0);
        final AtomicLong lastReceivedTimestamp = new AtomicLong();
        final long start = System.currentTimeMillis();

        final Async commandClientCreation = ctx.async();
        final Future<CommandClient> commandClient = helper.applicationClientFactory.getOrCreateCommandClient(tenantId, deviceId, "test-client")
                .setHandler(ctx.asyncAssertSuccess(c -> {
                    c.setRequestTimeout(300);
                    commandClientCreation.complete();
                }));
        commandClientCreation.await();

        while (commandsSent.get() < totalNoOfCommandsToSend) {
            final Async commandSent = ctx.async();
            context.runOnContext(go -> {
                final Buffer msg = Buffer.buffer("value: " + commandsSent.getAndIncrement());
                final Future<BufferResult> sendCmdFuture = commandClient.result().sendCommand("setValue", "text/plain",
                        msg, null);
                sendCmdFuture.setHandler(sendAttempt -> {
                    if (sendAttempt.succeeded()) {
                        log.debug("sending command {} succeeded unexpectedly", commandsSent.get());
                    } else {
                        if (sendAttempt.cause() instanceof ClientErrorException
                                && ((ClientErrorException) sendAttempt.cause()).getErrorCode() == HttpURLConnection.HTTP_BAD_REQUEST
                                && REJECTED_COMMAND_ERROR_MESSAGE.equals(sendAttempt.cause().getMessage())) {
                            log.debug("sending command {} failed as expected: {}", commandsSent.get(),
                                    sendAttempt.cause().toString());
                            lastReceivedTimestamp.set(System.currentTimeMillis());
                            commandsFailed.countDown();
                            if (commandsFailed.getCount() % 20 == 0) {
                                log.info("commands failed as expected: {}",
                                        totalNoOfCommandsToSend - commandsFailed.getCount());
                            }
                        } else {
                            log.debug("sending command {} failed with an unexpected error", commandsSent.get(),
                                    sendAttempt.cause());
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

        final long timeToWait = totalNoOfCommandsToSend * 300 + 300;
        if (!commandsFailed.await(timeToWait, TimeUnit.MILLISECONDS)) {
            log.info("Timeout of {} milliseconds reached, stop waiting for commands", timeToWait);
        }
        final long commandsCompleted = totalNoOfCommandsToSend - commandsFailed.getCount();
        log.info("commands sent: {}, commands failed: {} after {} milliseconds",
                commandsSent.get(), commandsCompleted, lastReceivedTimestamp.get() - start);
        if (commandsCompleted != commandsSent.get()) {
            ctx.fail("did not complete all commands sent");
        }
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
