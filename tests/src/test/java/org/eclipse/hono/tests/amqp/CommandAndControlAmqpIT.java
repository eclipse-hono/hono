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

import static org.assertj.core.api.Assertions.assertThat;

import java.net.HttpURLConnection;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.apache.qpid.proton.amqp.messaging.Accepted;
import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.AsyncCommandClient;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.CommandClient;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.tests.CommandEndpointConfiguration.SubscriberRole;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.util.BufferResult;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.TimeUntilDisconnectNotification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
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
@ExtendWith(VertxExtension.class)
public class CommandAndControlAmqpIT extends AmqpAdapterTestBase {

    private static final String REJECTED_COMMAND_ERROR_MESSAGE = "rejected command error message";
    private String tenantId;
    private String deviceId;
    private final String password = "secret";
    private Tenant tenant;

    static Stream<AmqpCommandEndpointConfiguration> allCombinations() {
        return Stream.of(
                new AmqpCommandEndpointConfiguration(SubscriberRole.DEVICE, true, true),
                new AmqpCommandEndpointConfiguration(SubscriberRole.DEVICE, true, false),
                new AmqpCommandEndpointConfiguration(SubscriberRole.DEVICE, false, true),
                new AmqpCommandEndpointConfiguration(SubscriberRole.DEVICE, false, false),

                // gateway devices are supported with north bound "command" endpoint only
                new AmqpCommandEndpointConfiguration(SubscriberRole.GATEWAY_FOR_ALL_DEVICES, false, false),
                new AmqpCommandEndpointConfiguration(SubscriberRole.GATEWAY_FOR_ALL_DEVICES, true, false),
                new AmqpCommandEndpointConfiguration(SubscriberRole.GATEWAY_FOR_SINGLE_DEVICE, false, false),
                new AmqpCommandEndpointConfiguration(SubscriberRole.GATEWAY_FOR_SINGLE_DEVICE, true, false)
                );
    }

    /**
     * Sets up the fixture.
     * 
     * @param testInfo Meta info about the test being run.
     */
    @BeforeEach
    public void setUp(final TestInfo testInfo) {
        log.info("running {}", testInfo.getDisplayName());
        tenantId = helper.getRandomTenantId();
        deviceId = helper.getRandomDeviceId(tenantId);
        tenant = new Tenant();
    }

    private Future<MessageConsumer> createEventConsumer(final String tenantId, final Consumer<Message> messageConsumer) {

        return helper.applicationClientFactory.createEventConsumer(tenantId, messageConsumer, remoteClose -> {});
    }

    private Future<ProtonReceiver> subscribeToCommands(
            final AmqpCommandEndpointConfiguration endpointConfig,
            final String tenantId,
            final String commandTargetDeviceId,
            final ProtonMessageHandler msgHandler) {

        final Future<ProtonReceiver> result = Future.future();
        context.runOnContext(go -> {
            final ProtonReceiver recv = connection.createReceiver(endpointConfig.getSubscriptionAddress(tenantId, commandTargetDeviceId));
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
            final VertxTestContext ctx,
            final String commandTargetDeviceId,
            final AmqpCommandEndpointConfiguration endpointConfig,
            final Function<ProtonSender, ProtonMessageHandler> commandConsumerFactory) throws InterruptedException {

        final VertxTestContext setup = new VertxTestContext();
        final Checkpoint notificationReceived = setup.checkpoint();

        connectToAdapter(tenantId, tenant, deviceId, password, () -> createEventConsumer(tenantId, msg -> {
            // expect empty notification with TTD -1
            ctx.verify(() -> assertThat(msg.getContentType()).isEqualTo(EventConstants.CONTENT_TYPE_EMPTY_NOTIFICATION));
            final TimeUntilDisconnectNotification notification = TimeUntilDisconnectNotification.fromMessage(msg).orElse(null);
            log.debug("received notification [{}]", notification);
            ctx.verify(() -> assertThat(notification).isNotNull());
            if (notification.getTtd() == -1) {
                notificationReceived.flag();
            }
        }))
        // use anonymous sender
        .compose(con -> createProducer(null))
        .compose(sender -> subscribeToCommands(endpointConfig, tenantId, commandTargetDeviceId, commandConsumerFactory.apply(sender)))
        .setHandler(setup.completing());

        assertThat(setup.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
        if (setup.failed()) {
            ctx.failNow(setup.causeOfFailure());
        }
    }

    private ProtonMessageHandler createCommandConsumer(final VertxTestContext ctx, final ProtonSender sender) {

        return (delivery, msg) -> {
            ctx.verify(() -> {
                assertThat(msg.getReplyTo()).isNotNull();
                assertThat(msg.getSubject()).isNotNull();
                assertThat(msg.getCorrelationId()).isNotNull();
            });
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

    private ProtonMessageHandler createRejectingCommandConsumer(final VertxTestContext ctx) {
        return (delivery, msg) -> {
            ctx.verify(() -> {
                assertThat(msg.getReplyTo()).isNotNull();
                assertThat(msg.getSubject()).isNotNull();
                assertThat(msg.getCorrelationId()).isNotNull();
            });
            final String command = msg.getSubject();
            final Object correlationId = msg.getCorrelationId();
            log.debug("received command [name: {}, reply-to: {}, correlation-id: {}]", command, msg.getReplyTo(), correlationId);
            final Rejected rejected = new Rejected();
            rejected.setError(new ErrorCondition(Constants.AMQP_BAD_REQUEST, REJECTED_COMMAND_ERROR_MESSAGE));
            delivery.disposition(rejected, true);
        };
    }

    /**
     * Verifies that the adapter forwards one-way commands from
     * an application to a device.
     * 
     * @param endpointConfig The endpoints to use for sending/receiving commands.
     * @param ctx The vert.x test context.
     * @throws InterruptedException if not all commands and responses are exchanged in time.
     */
    @ParameterizedTest(name = IntegrationTestSupport.PARAMETERIZED_TEST_NAME_PATTERN)
    @MethodSource("allCombinations")
    public void testSendOneWayCommandSucceeds(
            final AmqpCommandEndpointConfiguration endpointConfig,
            final VertxTestContext ctx) throws InterruptedException {

        final String commandTargetDeviceId = endpointConfig.isSubscribeAsGateway()
                ? helper.setupGatewayDeviceBlocking(tenantId, deviceId, 5)
                : deviceId;

        final int commandsToSend = 60;
        final Checkpoint commandsReceived = ctx.checkpoint(commandsToSend);

        final AtomicInteger counter = new AtomicInteger();
        testSendCommandSucceeds(
                ctx,
                commandTargetDeviceId,
                endpointConfig,
                sender -> (delivery, msg) -> {
                    ctx.verify(() -> {
                        assertThat(msg.getReplyTo()).isNull();
                        assertThat(msg.getSubject()).isEqualTo("setValue");
                    });
                    log.debug("received command [name: {}]", msg.getSubject());
                    commandsReceived.flag();
                }, payload -> {
                    return helper.sendOneWayCommand(
                            tenantId,
                            commandTargetDeviceId,
                            "setValue",
                            "text/plain",
                            payload,
                            // set "forceCommandRerouting" message property so that half the command are rerouted via the AMQP network
                            IntegrationTestSupport.newCommandMessageProperties(() -> counter.getAndIncrement() >= commandsToSend/2),
                            200,
                            endpointConfig.isLegacyNorthboundEndpoint());
                }, commandsToSend);
    }

    static Stream<AmqpCommandEndpointConfiguration> testSendAsyncCommandsSucceeds() {
        return Stream.of(
                new AmqpCommandEndpointConfiguration(SubscriberRole.DEVICE, true, false),
                new AmqpCommandEndpointConfiguration(SubscriberRole.DEVICE, false, false),

                // gateway devices are supported with north bound "command" endpoint only
                new AmqpCommandEndpointConfiguration(SubscriberRole.GATEWAY_FOR_ALL_DEVICES, false, false),
                new AmqpCommandEndpointConfiguration(SubscriberRole.GATEWAY_FOR_ALL_DEVICES, true, false),
                new AmqpCommandEndpointConfiguration(SubscriberRole.GATEWAY_FOR_SINGLE_DEVICE, false, false),
                new AmqpCommandEndpointConfiguration(SubscriberRole.GATEWAY_FOR_SINGLE_DEVICE, true, false)
                );
    }

    /**
     * Verifies that the adapter forwards commands and responses hence and forth between
     * an application and a device that have been sent using the async API.
     * 
     * @param endpointConfig The endpoints to use for sending/receiving commands.
     * @param ctx The vert.x test context.
     * @throws InterruptedException if not all commands and responses are exchanged in time.
     */
    @ParameterizedTest(name = IntegrationTestSupport.PARAMETERIZED_TEST_NAME_PATTERN)
    @MethodSource
    public void testSendAsyncCommandsSucceeds(
            final AmqpCommandEndpointConfiguration endpointConfig,
            final VertxTestContext ctx) throws InterruptedException {

        final String commandTargetDeviceId = endpointConfig.isSubscribeAsGateway()
                ? helper.setupGatewayDeviceBlocking(tenantId, deviceId, 5)
                : deviceId;

        connectAndSubscribe(ctx, commandTargetDeviceId, endpointConfig, sender -> createCommandConsumer(ctx, sender));

        final String replyId = "reply-id";
        final int totalNoOfCommandsToSend = 60;
        final CountDownLatch commandsSucceeded = new CountDownLatch(totalNoOfCommandsToSend);
        final AtomicInteger commandsSent = new AtomicInteger(0);
        final AtomicLong lastReceivedTimestamp = new AtomicLong();

        final VertxTestContext setup = new VertxTestContext();

        final Future<MessageConsumer> asyncResponseConsumer = helper.applicationClientFactory.createAsyncCommandResponseConsumer(
                tenantId,
                replyId,
                response -> {
                    lastReceivedTimestamp.set(System.currentTimeMillis());
                    commandsSucceeded.countDown();
                    if (commandsSucceeded.getCount() % 20 == 0) {
                        log.info("command responses received: {}", totalNoOfCommandsToSend - commandsSucceeded.getCount());
                    }
                },
                null);
        final Future<AsyncCommandClient> asyncCommandClient = helper.applicationClientFactory.getOrCreateAsyncCommandClient(tenantId);

        CompositeFuture.all(asyncResponseConsumer, asyncCommandClient).setHandler(setup.completing());

        assertThat(setup.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
        if (setup.failed()) {
            ctx.failNow(setup.causeOfFailure());
        }

        final long start = System.currentTimeMillis();

        while (commandsSent.get() < totalNoOfCommandsToSend) {
            final CountDownLatch commandSent = new CountDownLatch(1);
            context.runOnContext(go -> {
                final String correlationId = String.valueOf(commandsSent.getAndIncrement());
                final Buffer msg = Buffer.buffer("value: " + correlationId);
                asyncCommandClient.result().sendAsyncCommand(
                        commandTargetDeviceId,
                        "setValue",
                        "text/plain",
                        msg,
                        correlationId,
                        replyId,
                        null)
                .setHandler(sendAttempt -> {
                    if (sendAttempt.failed()) {
                        log.debug("error sending command {}", correlationId, sendAttempt.cause());
                    }
                    if (commandsSent.get() % 20 == 0) {
                        log.info("commands sent: " + commandsSent.get());
                    }
                    commandSent.countDown();
                });
            });

            commandSent.await();
        }

        final long timeToWait = totalNoOfCommandsToSend * 200;
        if (!commandsSucceeded.await(timeToWait, TimeUnit.MILLISECONDS)) {
            log.info("Timeout of {} milliseconds reached, stop waiting for command responses", timeToWait);
        }
        final long commandsCompleted = totalNoOfCommandsToSend - commandsSucceeded.getCount();
        log.info("commands sent: {}, responses received: {} after {} milliseconds",
                commandsSent.get(), commandsCompleted, lastReceivedTimestamp.get() - start);
        if (commandsCompleted == commandsSent.get()) {
            ctx.completeNow();
        } else {
            ctx.failNow(new IllegalStateException("did not complete all commands sent"));
        }
    }

    /**
     * Verifies that the adapter forwards commands and response hence and forth between
     * an application and a device.
     * 
     * @param endpointConfig The endpoints to use for sending/receiving commands.
     * @param ctx The vert.x test context.
     * @throws InterruptedException if not all commands and responses are exchanged in time.
     */
    @ParameterizedTest(name = IntegrationTestSupport.PARAMETERIZED_TEST_NAME_PATTERN)
    @MethodSource("allCombinations")
    public void testSendCommandSucceeds(
            final AmqpCommandEndpointConfiguration endpointConfig,
            final VertxTestContext ctx) throws InterruptedException {

        final String commandTargetDeviceId = endpointConfig.isSubscribeAsGateway()
                ? helper.setupGatewayDeviceBlocking(tenantId, deviceId, 5)
                : deviceId;

        final int commandsToSend = 60;
        final AtomicInteger counter = new AtomicInteger();
        testSendCommandSucceeds(
                ctx,
                commandTargetDeviceId,
                endpointConfig,
                sender -> createCommandConsumer(ctx, sender),
                payload -> {
                    return helper.sendCommand(
                            tenantId,
                            commandTargetDeviceId,
                            "setValue",
                            "text/plain",
                            payload,
                            // set "forceCommandRerouting" message property so that half the command are rerouted via the AMQP network
                            IntegrationTestSupport.newCommandMessageProperties(() -> counter.getAndIncrement() >= commandsToSend/2),
                            200,
                            endpointConfig.isLegacyNorthboundEndpoint())
                            .map(response -> {
                                ctx.verify(() -> {
                                    assertThat(response.getApplicationProperty(MessageHelper.APP_PROPERTY_DEVICE_ID, String.class)).isEqualTo(deviceId);
                                    assertThat(response.getApplicationProperty(MessageHelper.APP_PROPERTY_TENANT_ID, String.class)).isEqualTo(tenantId);
                                });
                                return response;
                            });
                },
                commandsToSend);
    }

    private void testSendCommandSucceeds(
            final VertxTestContext ctx,
            final String commandTargetDeviceId,
            final AmqpCommandEndpointConfiguration endpointConfig,
            final Function<ProtonSender, ProtonMessageHandler> commandConsumerFactory,
            final Function<Buffer, Future<?>> commandSender,
            final int totalNoOfCommandsToSend) throws InterruptedException {

        connectAndSubscribe(ctx, commandTargetDeviceId, endpointConfig, commandConsumerFactory);

        final CountDownLatch commandsSucceeded = new CountDownLatch(totalNoOfCommandsToSend);
        final AtomicInteger commandsSent = new AtomicInteger(0);
        final AtomicLong lastReceivedTimestamp = new AtomicLong();
        final long start = System.currentTimeMillis();

        while (commandsSent.get() < totalNoOfCommandsToSend) {
            final CountDownLatch commandSent = new CountDownLatch(1);
            context.runOnContext(go -> {
                final Buffer payload = Buffer.buffer("value: " + commandsSent.getAndIncrement());
                commandSender.apply(payload).setHandler(sendAttempt -> {
                    if (sendAttempt.failed()) {
                        log.debug("error sending command {}", commandsSent.get(), sendAttempt.cause());
                    } else {
                        lastReceivedTimestamp.set(System.currentTimeMillis());
                        commandsSucceeded.countDown();
                        if (commandsSucceeded.getCount() % 20 == 0) {
                            log.info("commands succeeded: {}", totalNoOfCommandsToSend - commandsSucceeded.getCount());
                        }
                    }
                    if (commandsSent.get() % 20 == 0) {
                        log.info("commands sent: " + commandsSent.get());
                    }
                    commandSent.countDown();
                });
            });

            commandSent.await();
        }

        final long timeToWait = totalNoOfCommandsToSend * 200;
        if (!commandsSucceeded.await(timeToWait, TimeUnit.MILLISECONDS)) {
            log.info("Timeout of {} milliseconds reached, stop waiting for commands to succeed", timeToWait);
        }
        final long commandsCompleted = totalNoOfCommandsToSend - commandsSucceeded.getCount();
        log.info("commands sent: {}, commands succeeded: {} after {} milliseconds",
                commandsSent.get(), commandsCompleted, lastReceivedTimestamp.get() - start);
        if (commandsCompleted == commandsSent.get()) {
            ctx.completeNow();
        } else {
            ctx.failNow(new IllegalStateException("did not complete all commands sent"));
        }
    }

    /**
     * Verifies that the adapter rejects malformed command messages sent by applications.
     * 
     * @param endpointConfig The endpoints to use for sending/receiving commands.
     * @param ctx The vert.x test context.
     * @throws InterruptedException if not all commands and responses are exchanged in time.
     */
    @ParameterizedTest(name = IntegrationTestSupport.PARAMETERIZED_TEST_NAME_PATTERN)
    @MethodSource("allCombinations")
    @Timeout(timeUnit = TimeUnit.SECONDS, value = 10)
    public void testSendCommandFailsForMalformedMessage(
            final AmqpCommandEndpointConfiguration endpointConfig,
            final VertxTestContext ctx) throws InterruptedException {

        final String commandTargetDeviceId = endpointConfig.isSubscribeAsGateway()
                ? helper.setupGatewayDeviceBlocking(tenantId, deviceId, 5)
                : deviceId;

        final AtomicReference<MessageSender> sender = new AtomicReference<>();
        final String targetAddress = endpointConfig.getSenderLinkTargetAddress(tenantId, commandTargetDeviceId);

        final VertxTestContext setup = new VertxTestContext();
        final Checkpoint preconditions = setup.checkpoint(2);

        connectToAdapter(tenantId, tenant, deviceId, password, () -> createEventConsumer(tenantId, msg -> {
            // expect empty notification with TTD -1
            ctx.verify(() -> assertThat(msg.getContentType()).isEqualTo(EventConstants.CONTENT_TYPE_EMPTY_NOTIFICATION));
            final TimeUntilDisconnectNotification notification = TimeUntilDisconnectNotification.fromMessage(msg).orElse(null);
            log.debug("received notification [{}]", notification);
            ctx.verify(() -> assertThat(notification).isNotNull());
            if (notification.getTtd() == -1) {
                preconditions.flag();
            }
        }))
        .compose(con -> subscribeToCommands(endpointConfig, tenantId, commandTargetDeviceId, (delivery, msg) -> {
            ctx.failNow(new IllegalStateException("should not have received command"));
        }))
        .compose(ok -> helper.applicationClientFactory.createGenericMessageSender(targetAddress))
        .map(s -> {
            log.debug("created generic sender for sending commands [target address: {}]", targetAddress);
            sender.set(s);
            preconditions.flag();
            return s;
        })
        .setHandler(setup.completing());

        assertThat(setup.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
        if (setup.failed()) {
            ctx.failNow(setup.causeOfFailure());
        }

        final Checkpoint expectedFailures = ctx.checkpoint(2);

        log.debug("sending command message lacking subject");
        final Message messageWithoutSubject = ProtonHelper.message("input data");
        messageWithoutSubject.setAddress(endpointConfig.getCommandMessageAddress(tenantId, commandTargetDeviceId));
        messageWithoutSubject.setMessageId("message-id");
        messageWithoutSubject.setReplyTo("reply/to/address");
        sender.get().sendAndWaitForOutcome(messageWithoutSubject).setHandler(ctx.failing(t -> {
            ctx.verify(() -> assertThat(t).isInstanceOf(ClientErrorException.class));
            expectedFailures.flag();
        }));

        log.debug("sending command message lacking message ID and correlation ID");
        final Message messageWithoutId = ProtonHelper.message("input data");
        messageWithoutId.setAddress(endpointConfig.getCommandMessageAddress(tenantId, commandTargetDeviceId));
        messageWithoutId.setSubject("setValue");
        messageWithoutId.setReplyTo("reply/to/address");
        sender.get().sendAndWaitForOutcome(messageWithoutId).setHandler(ctx.failing(t -> {
            ctx.verify(() -> assertThat(t).isInstanceOf(ClientErrorException.class));
            expectedFailures.flag();
        }));
    }

    /**
     * Verifies that the adapter forwards the <em>rejected</em> disposition, received from a device, back to the
     * application.
     *
     * @param endpointConfig The endpoints to use for sending/receiving commands.
     * @param ctx The vert.x test context.
     * @throws InterruptedException if not all commands and responses are exchanged in time.
     */
    @ParameterizedTest(name = IntegrationTestSupport.PARAMETERIZED_TEST_NAME_PATTERN)
    @MethodSource("allCombinations")
    @Timeout(timeUnit = TimeUnit.SECONDS, value = 10)
    public void testSendCommandFailsForCommandRejectedByDevice(
            final AmqpCommandEndpointConfiguration endpointConfig,
            final VertxTestContext ctx) throws InterruptedException {

        final String commandTargetDeviceId = endpointConfig.isSubscribeAsGateway()
                ? helper.setupGatewayDeviceBlocking(tenantId, deviceId, 5)
                : deviceId;

        connectAndSubscribe(ctx, commandTargetDeviceId, endpointConfig, sender -> createRejectingCommandConsumer(ctx));

        final int totalNoOfCommandsToSend = 3;
        final CountDownLatch commandsFailed = new CountDownLatch(totalNoOfCommandsToSend);
        final AtomicInteger commandsSent = new AtomicInteger(0);
        final AtomicLong lastReceivedTimestamp = new AtomicLong();
        final long start = System.currentTimeMillis();

        final VertxTestContext commandClientCreation = new VertxTestContext();
        final Future<CommandClient> commandClient = helper.applicationClientFactory.getOrCreateCommandClient(tenantId, "test-client")
                .setHandler(ctx.succeeding(c -> {
                    c.setRequestTimeout(300);
                    commandClientCreation.completeNow();
                }));

        assertThat(commandClientCreation.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
        if (commandClientCreation.failed()) {
            ctx.failNow(commandClientCreation.causeOfFailure());
        }

        while (commandsSent.get() < totalNoOfCommandsToSend) {
            final CountDownLatch commandSent = new CountDownLatch(1);
            context.runOnContext(go -> {
                final Buffer msg = Buffer.buffer("value: " + commandsSent.getAndIncrement());
                final Future<BufferResult> sendCmdFuture = commandClient.result().sendCommand(commandTargetDeviceId, "setValue", "text/plain",
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
                    commandSent.countDown();
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
        if (commandsCompleted == commandsSent.get()) {
            ctx.completeNow();
        } else {
            ctx.failNow(new IllegalStateException("did not complete all commands sent"));
        }
    }

    /**
     * Registers a device and opens a connection to the AMQP adapter using
     * the device's credentials.
     * 
     * @param tenantId The ID of the tenant that the device belongs to.
     * @param tenant The tenant that the device belongs to.
     * @param deviceId The identifier of the device.
     * @param password The password to use for authentication.
     * @param consumerFactory The factory for creating the consumer of messages published by the device or {@code null}
     *            if no consumer should be created.
     * @return A future that will be completed with the ProtonConnection with the adapter or failed if the
     *         connection could not be established.
     */
    protected final Future<ProtonConnection> connectToAdapter(
            final String tenantId,
            final Tenant tenant,
            final String deviceId,
            final String password,
            final Supplier<Future<MessageConsumer>> consumerFactory) {

        return helper.registry
                .addDeviceForTenant(tenantId, tenant, deviceId, password)
        .compose(ok -> Optional.ofNullable(consumerFactory)
                .map(factory -> factory.get())
                .orElse(Future.succeededFuture()))
                .compose(ok -> connectToAdapter(IntegrationTestSupport.getUsername(deviceId, tenantId), password))
        .recover(t -> {
            log.error("failed to establish connection to AMQP adapter [host: {}, port: {}]",
                    IntegrationTestSupport.AMQP_HOST, IntegrationTestSupport.AMQP_PORT, t);
            return Future.failedFuture(t);
        });

    }

}
