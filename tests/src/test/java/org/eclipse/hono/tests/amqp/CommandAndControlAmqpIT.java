/*******************************************************************************
 * Copyright (c) 2018, 2020 Contributors to the Eclipse Foundation
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
import java.util.function.BiFunction;
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
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.tests.CommandEndpointConfiguration.SubscriberRole;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.util.BufferResult;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.TimeUntilDisconnectNotification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
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
                new AmqpCommandEndpointConfiguration(SubscriberRole.DEVICE),
                new AmqpCommandEndpointConfiguration(SubscriberRole.GATEWAY_FOR_ALL_DEVICES),
                new AmqpCommandEndpointConfiguration(SubscriberRole.GATEWAY_FOR_SINGLE_DEVICE)
                );
    }

    /**
     * Sets up the fixture.
     *
     * @param testInfo Meta info about the test being run.
     */
    @Override
    @BeforeEach
    public void setUp(final TestInfo testInfo, final VertxTestContext ctx) {

        log.info("running {}", testInfo.getDisplayName());
        helper = new IntegrationTestSupport(vertx);
        tenantId = helper.getRandomTenantId();
        deviceId = helper.getRandomDeviceId(tenantId);
        tenant = new Tenant();
        helper.init().onComplete(ctx.completing());
    }

    private Future<MessageConsumer> createEventConsumer(final String tenantId, final Consumer<Message> messageConsumer) {

        return helper.applicationClientFactory.createEventConsumer(tenantId, messageConsumer, remoteClose -> {});
    }

    private Future<ProtonReceiver> subscribeToCommands(
            final AmqpCommandEndpointConfiguration endpointConfig,
            final String tenantId,
            final String commandTargetDeviceId) {

        final Promise<ProtonReceiver> result = Promise.promise();
        context.runOnContext(go -> {
            final ProtonReceiver recv = connection.createReceiver(endpointConfig.getSubscriptionAddress(tenantId, commandTargetDeviceId));
            recv.setAutoAccept(false);
            recv.setPrefetch(0);
            recv.setQoS(ProtonQoS.AT_LEAST_ONCE);
            recv.openHandler(result);
            recv.open();
        });
        return result.future().map(commandConsumer -> {
            log.debug("created command consumer [{}]", commandConsumer.getSource().getAddress());
            return commandConsumer;
        });
    }

    private void connectAndSubscribe(
            final VertxTestContext ctx,
            final String commandTargetDeviceId,
            final AmqpCommandEndpointConfiguration endpointConfig,
            final BiFunction<ProtonReceiver, ProtonSender, ProtonMessageHandler> commandConsumerFactory) throws InterruptedException {

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
        .compose(sender -> subscribeToCommands(endpointConfig, tenantId, commandTargetDeviceId)
            .map(recv -> {
                recv.handler(commandConsumerFactory.apply(recv, sender));
                recv.flow(50);
                return null;
            }))
        .onComplete(setup.completing());

        assertThat(setup.awaitCompletion(helper.getTestSetupTimeout(), TimeUnit.SECONDS)).isTrue();
        if (setup.failed()) {
            ctx.failNow(setup.causeOfFailure());
        }
    }

    private ProtonMessageHandler createCommandConsumer(final VertxTestContext ctx, final ProtonReceiver cmdReceiver,
            final ProtonSender cmdResponseSender) {

        return (delivery, msg) -> {
            ctx.verify(() -> {
                assertThat(msg.getReplyTo()).isNotNull();
                assertThat(msg.getSubject()).isNotNull();
                assertThat(msg.getCorrelationId()).isNotNull();
            });
            final String command = msg.getSubject();
            final Object correlationId = msg.getCorrelationId();
            log.debug("received command [name: {}, reply-to: {}, correlation-id: {}]", command, msg.getReplyTo(), correlationId);
            ProtonHelper.accepted(delivery, true);
            cmdReceiver.flow(1);
            // send response
            final Message commandResponse = ProtonHelper.message(command + " ok");
            commandResponse.setAddress(msg.getReplyTo());
            commandResponse.setCorrelationId(correlationId);
            commandResponse.setContentType("text/plain");
            MessageHelper.addProperty(commandResponse, MessageHelper.APP_PROPERTY_STATUS, HttpURLConnection.HTTP_OK);
            log.debug("sending response [to: {}, correlation-id: {}]", commandResponse.getAddress(), commandResponse.getCorrelationId());
            cmdResponseSender.send(commandResponse, updatedDelivery -> {
                if (!Accepted.class.isInstance(updatedDelivery.getRemoteState())) {
                    log.error("AMQP adapter did not accept command response [remote state: {}]",
                            updatedDelivery.getRemoteState().getClass().getSimpleName());
                }
            });
        };
    }

    private ProtonMessageHandler createRejectingCommandConsumer(final VertxTestContext ctx, final ProtonReceiver receiver) {
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
            receiver.flow(1);
        };
    }

    private ProtonMessageHandler createNotSendingDeliveryUpdateCommandConsumer(final VertxTestContext ctx,
            final ProtonReceiver receiver, final AtomicInteger receivedMessagesCounter) {
        return (delivery, msg) -> {
            receivedMessagesCounter.incrementAndGet();
            ctx.verify(() -> {
                assertThat(msg.getReplyTo()).isNotNull();
                assertThat(msg.getSubject()).isNotNull();
                assertThat(msg.getCorrelationId()).isNotNull();
            });
            final String command = msg.getSubject();
            final Object correlationId = msg.getCorrelationId();
            log.debug("received command [name: {}, reply-to: {}, correlation-id: {}]", command, msg.getReplyTo(), correlationId);
            receiver.flow(1);
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
                (cmdReceiver, cmdResponseSender) -> (delivery, msg) -> {
                    ctx.verify(() -> {
                        assertThat(msg.getReplyTo()).isNull();
                        assertThat(msg.getSubject()).isEqualTo("setValue");
                    });
                    log.debug("received command [name: {}]", msg.getSubject());
                    ProtonHelper.accepted(delivery, true);
                    cmdReceiver.flow(1);
                    commandsReceived.flag();
                }, payload -> {
                    return helper.sendOneWayCommand(
                            tenantId,
                            commandTargetDeviceId,
                            "setValue",
                            "text/plain",
                            payload,
                            // set "forceCommandRerouting" message property so that half the command are rerouted via the AMQP network
                            IntegrationTestSupport.newCommandMessageProperties(() -> counter.getAndIncrement() >= commandsToSend / 2),
                            helper.isTestEnvironment() ? 1000 : 200);
                }, commandsToSend);
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
    @MethodSource("allCombinations")
    public void testSendAsyncCommandsSucceeds(
            final AmqpCommandEndpointConfiguration endpointConfig,
            final VertxTestContext ctx) throws InterruptedException {

        final String commandTargetDeviceId = endpointConfig.isSubscribeAsGateway()
                ? helper.setupGatewayDeviceBlocking(tenantId, deviceId, 5)
                : deviceId;

        connectAndSubscribe(ctx, commandTargetDeviceId, endpointConfig,
                (cmdReceiver, cmdResponseSender) -> createCommandConsumer(ctx, cmdReceiver, cmdResponseSender));
        if (ctx.failed()) {
            return;
        }

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

        CompositeFuture.all(asyncResponseConsumer, asyncCommandClient).onComplete(setup.completing());

        assertThat(setup.awaitCompletion(helper.getTestSetupTimeout(), TimeUnit.SECONDS)).isTrue();
        if (setup.failed()) {
            ctx.failNow(setup.causeOfFailure());
            return;
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
                .onComplete(sendAttempt -> {
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
                (cmdReceiver, cmdResponseSender) -> createCommandConsumer(ctx, cmdReceiver, cmdResponseSender),
                payload -> {
                    return helper.sendCommand(
                            tenantId,
                            commandTargetDeviceId,
                            "setValue",
                            "text/plain",
                            payload,
                            // set "forceCommandRerouting" message property so that half the command are rerouted via the AMQP network
                            IntegrationTestSupport.newCommandMessageProperties(() -> counter.getAndIncrement() >= commandsToSend / 2),
                            helper.getSendCommandTimeout())
                        .map(response -> {
                            ctx.verify(() -> {
                                assertThat(response.getApplicationProperty(MessageHelper.APP_PROPERTY_DEVICE_ID, String.class)).isEqualTo(commandTargetDeviceId);
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
            final BiFunction<ProtonReceiver, ProtonSender, ProtonMessageHandler> commandConsumerFactory,
            final Function<Buffer, Future<?>> commandSender,
            final int totalNoOfCommandsToSend) throws InterruptedException {

        connectAndSubscribe(ctx, commandTargetDeviceId, endpointConfig, commandConsumerFactory);
        if (ctx.failed()) {
            return;
        }

        final CountDownLatch commandsSucceeded = new CountDownLatch(totalNoOfCommandsToSend);
        final AtomicInteger commandsSent = new AtomicInteger(0);
        final AtomicLong lastReceivedTimestamp = new AtomicLong();
        final long start = System.currentTimeMillis();

        while (commandsSent.get() < totalNoOfCommandsToSend) {
            final int currentMessage = commandsSent.incrementAndGet();
            final CountDownLatch commandSent = new CountDownLatch(1);
            context.runOnContext(go -> {
                final Buffer payload = Buffer.buffer("value: " + currentMessage);
                commandSender.apply(payload).onComplete(sendAttempt -> {
                    if (sendAttempt.failed()) {
                        log.debug("error sending command {}", currentMessage, sendAttempt.cause());
                    } else {
                        lastReceivedTimestamp.set(System.currentTimeMillis());
                        commandsSucceeded.countDown();
                        log.debug("sent command no {}", currentMessage);
                        if (commandsSucceeded.getCount() % 20 == 0) {
                            log.info("commands succeeded: {}", totalNoOfCommandsToSend - commandsSucceeded.getCount());
                        }
                    }
                    commandSent.countDown();
                });
            });

            commandSent.await();
            if (currentMessage % 20 == 0) {
                log.info("commands sent: " + currentMessage);
            }
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
        final String targetAddress = endpointConfig.getSenderLinkTargetAddress(tenantId);

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
        .compose(con -> subscribeToCommands(endpointConfig, tenantId, commandTargetDeviceId)
            .map(recv -> {
                recv.handler((delivery, msg) -> ctx
                        .failNow(new IllegalStateException("should not have received command")));
                return null;
            }))
        .compose(ok -> helper.applicationClientFactory.createGenericMessageSender(targetAddress))
        .map(s -> {
            log.debug("created generic sender for sending commands [target address: {}]", targetAddress);
            sender.set(s);
            preconditions.flag();
            return s;
        })
        .onComplete(setup.completing());

        assertThat(setup.awaitCompletion(helper.getTestSetupTimeout(), TimeUnit.SECONDS)).isTrue();
        if (setup.failed()) {
            ctx.failNow(setup.causeOfFailure());
            return;
        }

        final Checkpoint expectedFailures = ctx.checkpoint(2);

        log.debug("sending command message lacking subject");
        final Message messageWithoutSubject = ProtonHelper.message("input data");
        messageWithoutSubject.setAddress(endpointConfig.getCommandMessageAddress(tenantId, commandTargetDeviceId));
        messageWithoutSubject.setMessageId("message-id");
        messageWithoutSubject.setReplyTo("reply/to/address");
        sender.get().sendAndWaitForOutcome(messageWithoutSubject).onComplete(ctx.failing(t -> {
            ctx.verify(() -> assertThat(t).isInstanceOf(ClientErrorException.class));
            expectedFailures.flag();
        }));

        log.debug("sending command message lacking message ID and correlation ID");
        final Message messageWithoutId = ProtonHelper.message("input data");
        messageWithoutId.setAddress(endpointConfig.getCommandMessageAddress(tenantId, commandTargetDeviceId));
        messageWithoutId.setSubject("setValue");
        messageWithoutId.setReplyTo("reply/to/address");
        sender.get().sendAndWaitForOutcome(messageWithoutId).onComplete(ctx.failing(t -> {
            ctx.verify(() -> assertThat(t).isInstanceOf(ClientErrorException.class));
            expectedFailures.flag();
        }));
    }

    /**
     * Verifies that the adapter immediately forwards the <em>released</em> disposition
     * if there is no credit left for sending the command to the device.
     *
     * @param endpointConfig The endpoints to use for sending/receiving commands.
     * @param ctx The vert.x test context.
     * @throws InterruptedException if not all commands and responses are exchanged in time.
     */
    @ParameterizedTest(name = IntegrationTestSupport.PARAMETERIZED_TEST_NAME_PATTERN)
    @MethodSource("allCombinations")
    @Timeout(timeUnit = TimeUnit.SECONDS, value = 10)
    public void testSendCommandFailsWhenNoCredit(
            final AmqpCommandEndpointConfiguration endpointConfig,
            final VertxTestContext ctx) throws InterruptedException {

        final String commandTargetDeviceId = endpointConfig.isSubscribeAsGateway()
                ? helper.setupGatewayDeviceBlocking(tenantId, deviceId, 5)
                : deviceId;

        final VertxTestContext setup = new VertxTestContext();
        final Checkpoint preconditions = setup.checkpoint(1);

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
        .compose(con -> subscribeToCommands(endpointConfig, tenantId, commandTargetDeviceId)
            .map(recv -> {
                recv.handler((delivery, msg) -> {
                    log.debug("received command [name: {}, reply-to: {}, correlation-id: {}]", msg.getSubject(), msg.getReplyTo(),
                            msg.getCorrelationId());
                    ProtonHelper.accepted(delivery, true);
                    // don't send credits
                });
                recv.flow(1); // just give 1 initial credit
                return null;
            }))
        .onComplete(setup.completing());

        assertThat(setup.awaitCompletion(helper.getTestSetupTimeout(), TimeUnit.SECONDS)).isTrue();
        if (setup.failed()) {
            ctx.failNow(setup.causeOfFailure());
            return;
        }

        final Checkpoint expectedSteps = ctx.checkpoint(2);

        // send first command
        helper.sendOneWayCommand(tenantId, commandTargetDeviceId, "setValue", "text/plain",
                Buffer.buffer("cmd"), null, helper.getSendCommandTimeout())
        // first command shall succeed because there's one initial credit
        .onComplete(ctx.succeeding(v -> {
            expectedSteps.flag();
            // send second command
            helper.sendOneWayCommand(
                    tenantId,
                    commandTargetDeviceId,
                    "setValue",
                    "text/plain",
                    Buffer.buffer("cmd"),
                    null,
                    helper.getSendCommandTimeout())
                // second command shall fail because there's no credit left
                .onComplete(ctx.failing(t -> {
                    ctx.verify(() -> {
                        assertThat(t).isInstanceOf(ServerErrorException.class);
                        assertThat(((ServerErrorException) t).getErrorCode()).isEqualTo(HttpURLConnection.HTTP_UNAVAILABLE);
                        // with no explicit credit check, the AMQP adapter would just run into the "waiting for delivery update" timeout (after 1s)
                        // and the error here would be caused by a request timeout in the sendOneWayCommand() method above
                        assertThat(t.getMessage()).doesNotContain("timed out");
                    });
                    expectedSteps.flag();
                }));
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

        connectAndSubscribe(ctx, commandTargetDeviceId, endpointConfig,
                (cmdReceiver, cmdResponseSender) -> createRejectingCommandConsumer(ctx, cmdReceiver));
        if (ctx.failed()) {
            return;
        }

        final int totalNoOfCommandsToSend = 3;
        final CountDownLatch commandsFailed = new CountDownLatch(totalNoOfCommandsToSend);
        final AtomicInteger commandsSent = new AtomicInteger(0);
        final AtomicLong lastReceivedTimestamp = new AtomicLong();
        final long start = System.currentTimeMillis();

        final VertxTestContext commandClientCreation = new VertxTestContext();
        final Future<CommandClient> commandClient = helper.applicationClientFactory.getOrCreateCommandClient(tenantId, "test-client")
                .onSuccess(c -> c.setRequestTimeout(300))
                .onComplete(commandClientCreation.completing());

        assertThat(commandClientCreation.awaitCompletion(helper.getTestSetupTimeout(), TimeUnit.SECONDS)).isTrue();
        if (commandClientCreation.failed()) {
            ctx.failNow(commandClientCreation.causeOfFailure());
            return;
        }

        while (commandsSent.get() < totalNoOfCommandsToSend) {
            final CountDownLatch commandSent = new CountDownLatch(1);
            context.runOnContext(go -> {
                final Buffer msg = Buffer.buffer("value: " + commandsSent.getAndIncrement());
                final Future<BufferResult> sendCmdFuture = commandClient.result().sendCommand(commandTargetDeviceId, "setValue", "text/plain",
                        msg, null);
                sendCmdFuture.onComplete(sendAttempt -> {
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
     * Verifies that the adapter forwards the <em>released</em> disposition back to the
     * application if the device hasn't sent a disposition update for the delivery of
     * the command message sent to the device.
     *
     * @param ctx The vert.x test context.
     * @throws InterruptedException if not all commands and responses are exchanged in time.
     */
    @Test
    @Timeout(timeUnit = TimeUnit.SECONDS, value = 10)
    public void testSendCommandFailsForCommandNotAcknowledgedByDevice(
            final VertxTestContext ctx) throws InterruptedException {

        final AmqpCommandEndpointConfiguration endpointConfig = new AmqpCommandEndpointConfiguration(SubscriberRole.DEVICE);
        final String commandTargetDeviceId = endpointConfig.isSubscribeAsGateway()
                ? helper.setupGatewayDeviceBlocking(tenantId, deviceId, 5)
                : deviceId;

        final AtomicInteger receivedMessagesCounter = new AtomicInteger(0);
        // command handler won't send a disposition update
        connectAndSubscribe(ctx, commandTargetDeviceId, endpointConfig,
                (cmdReceiver, cmdResponseSender) -> createNotSendingDeliveryUpdateCommandConsumer(ctx, cmdReceiver, receivedMessagesCounter));
        if (ctx.failed()) {
            return;
        }

        final int totalNoOfCommandsToSend = 2;
        final CountDownLatch commandsFailed = new CountDownLatch(totalNoOfCommandsToSend);
        final AtomicInteger commandsSent = new AtomicInteger(0);
        final AtomicLong lastReceivedTimestamp = new AtomicLong();
        final long start = System.currentTimeMillis();

        final VertxTestContext commandClientCreation = new VertxTestContext();
        final Future<CommandClient> commandClient = helper.applicationClientFactory.getOrCreateCommandClient(tenantId, "test-client")
                .onSuccess(c -> c.setRequestTimeout(1300)) // have to wait more than AmqpAdapterProperties.DEFAULT_SEND_MESSAGE_TO_DEVICE_TIMEOUT (1000ms) for the first command message
                .onComplete(commandClientCreation.completing());

        assertThat(commandClientCreation.awaitCompletion(helper.getTestSetupTimeout(), TimeUnit.SECONDS)).isTrue();
        if (commandClientCreation.failed()) {
            ctx.failNow(commandClientCreation.causeOfFailure());
            return;
        }

        while (commandsSent.get() < totalNoOfCommandsToSend) {
            final CountDownLatch commandSent = new CountDownLatch(1);
            context.runOnContext(go -> {
                final Buffer msg = Buffer.buffer("value: " + commandsSent.getAndIncrement());
                final Future<BufferResult> sendCmdFuture = commandClient.result().sendCommand(commandTargetDeviceId, "setValue", "text/plain",
                        msg, null);
                sendCmdFuture.onComplete(sendAttempt -> {
                    if (sendAttempt.succeeded()) {
                        log.debug("sending command {} succeeded unexpectedly", commandsSent.get());
                    } else {
                        if (sendAttempt.cause() instanceof ServerErrorException
                                && ((ServerErrorException) sendAttempt.cause()).getErrorCode() == HttpURLConnection.HTTP_UNAVAILABLE) {
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

        // have to wait more than AmqpAdapterProperties.DEFAULT_SEND_MESSAGE_TO_DEVICE_TIMEOUT (1000ms) for each command message
        final long timeToWait = 300 + (totalNoOfCommandsToSend * 1300);
        if (!commandsFailed.await(timeToWait, TimeUnit.MILLISECONDS)) {
            log.info("Timeout of {} milliseconds reached, stop waiting for commands", timeToWait);
        }
        assertThat(receivedMessagesCounter.get()).isEqualTo(totalNoOfCommandsToSend);
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
                .map(Supplier::get)
                .orElse(Future.succeededFuture()))
        .compose(ok -> connectToAdapter(IntegrationTestSupport.getUsername(deviceId, tenantId), password))
        .recover(t -> {
            log.error("failed to establish connection to AMQP adapter [host: {}, port: {}]",
                    IntegrationTestSupport.AMQP_HOST, IntegrationTestSupport.AMQP_PORT, t);
            return Future.failedFuture(t);
        });

    }
}
