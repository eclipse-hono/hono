/*******************************************************************************
 * Copyright (c) 2018, 2022 Contributors to the Eclipse Foundation
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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import java.net.HttpURLConnection;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.apache.qpid.proton.amqp.messaging.Accepted;
import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.amqp.transport.AmqpError;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.application.client.DownstreamMessage;
import org.eclipse.hono.application.client.MessageConsumer;
import org.eclipse.hono.application.client.MessageContext;
import org.eclipse.hono.application.client.TimeUntilDisconnectNotification;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.SendMessageTimeoutException;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.amqp.GenericSenderLink;
import org.eclipse.hono.client.amqp.connection.AmqpUtils;
import org.eclipse.hono.client.amqp.connection.HonoProtonHelper;
import org.eclipse.hono.client.kafka.HonoTopic;
import org.eclipse.hono.client.kafka.KafkaRecordHelper;
import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.tests.CommandEndpointConfiguration.SubscriberRole;
import org.eclipse.hono.tests.DownstreamMessageAssertions;
import org.eclipse.hono.tests.EnabledIfMessagingSystemConfigured;
import org.eclipse.hono.tests.EnabledIfProtocolAdaptersAreRunning;
import org.eclipse.hono.tests.GenericKafkaSender;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.MessagingType;
import org.eclipse.hono.util.ResourceLimits;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import io.opentracing.noop.NoopSpan;
import io.vertx.core.Future;
import io.vertx.core.Handler;
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
@EnabledIfProtocolAdaptersAreRunning(amqpAdapter = true)
public class CommandAndControlAmqpIT extends AmqpAdapterTestBase {

    private static final String REJECTED_COMMAND_ERROR_MESSAGE = "rejected command error message";
    private static final int COMMANDS_TO_SEND = 60;
    private static final Duration TTL_COMMAND_RESPONSE = Duration.ofSeconds(20L);

    private String tenantId;
    private String deviceId;
    private final String password = "secret";

    static Stream<AmqpCommandEndpointConfiguration> allCombinations() {
        return Stream.of(
                new AmqpCommandEndpointConfiguration(SubscriberRole.DEVICE),
                new AmqpCommandEndpointConfiguration(SubscriberRole.GATEWAY_FOR_ALL_DEVICES),
                new AmqpCommandEndpointConfiguration(SubscriberRole.GATEWAY_FOR_SINGLE_DEVICE)
                );
    }

    /**
     * Creates a random tenant.
     * <p>
     * The tenant will be configured with a max TTL for command responses.
     *
     * @param ctx The vert.x test context.
     */
    @BeforeEach
    public void createRandomTenantAndInitDeviceId(final VertxTestContext ctx) {

        tenantId = helper.getRandomTenantId();
        deviceId = helper.getRandomDeviceId(tenantId);
        final Tenant tenantConfig = new Tenant().setResourceLimits(new ResourceLimits()
                .setMaxTtlCommandResponse(TTL_COMMAND_RESPONSE.toSeconds()));
        helper.registry.addTenant(tenantId, tenantConfig).onComplete(ctx.succeedingThenComplete());
    }

    private Future<MessageConsumer> createEventConsumer(
            final String tenantId,
            final Handler<DownstreamMessage<? extends MessageContext>> messageConsumer) {
        return helper.applicationClient.createEventConsumer(
                tenantId,
                messageConsumer::handle,
                remoteClose -> {});
    }

    private Future<ProtonReceiver> subscribeToCommands(
            final AmqpCommandEndpointConfiguration endpointConfig,
            final String tenantId,
            final String commandTargetDeviceId) {

        final Promise<ProtonReceiver> result = Promise.promise();
        context.runOnContext(go -> {
            log.debug("creating command consumer for tenant [{}]", tenantId);
            final ProtonReceiver recv = connection.createReceiver(endpointConfig.getSubscriptionAddress(
                    tenantId, commandTargetDeviceId));
            recv.setAutoAccept(false);
            recv.setPrefetch(0);
            recv.setQoS(ProtonQoS.AT_LEAST_ONCE);
            recv.openHandler(result);
            recv.open();
        });
        return result.future()
                .onSuccess(consumer -> log.debug("created command consumer [{}]", consumer.getSource().getAddress()));
    }

    private void connectAndSubscribe(
            final VertxTestContext ctx,
            final String commandTargetDeviceId,
            final AmqpCommandEndpointConfiguration endpointConfig,
            final BiFunction<ProtonReceiver, ProtonSender, ProtonMessageHandler> commandConsumerFactory,
            final int expectedNoOfCommands) throws InterruptedException {

        final VertxTestContext setup = new VertxTestContext();
        final Checkpoint setupDone = setup.checkpoint();
        final Checkpoint notificationReceived = setup.checkpoint();

        connectToAdapter(tenantId, deviceId, password, () -> createEventConsumer(tenantId, msg -> {
            // expect empty notification with TTD -1
            ctx.verify(() -> assertThat(msg.getContentType()).isEqualTo(EventConstants.CONTENT_TYPE_EMPTY_NOTIFICATION));
            final TimeUntilDisconnectNotification notification = msg.getTimeUntilDisconnectNotification().orElse(null);
            log.debug("received notification [{}]", notification);
            ctx.verify(() -> assertThat(notification).isNotNull());
            if (notification.getTtd() == -1) {
                notificationReceived.flag();
            }
        }))
        // use anonymous sender
        .compose(con -> createProducer(null, ProtonQoS.AT_LEAST_ONCE))
        .compose(sender -> subscribeToCommands(endpointConfig, tenantId, commandTargetDeviceId)
            .onSuccess(recv -> {
                recv.handler(commandConsumerFactory.apply(recv, sender));
                // make sure that there are always enough credits, even if commands are sent faster than answered
                recv.flow(expectedNoOfCommands);
            }))
        .onComplete(setup.succeeding(v -> setupDone.flag()));

        assertWithMessage("connect and subscribe finished within %s seconds", IntegrationTestSupport.getTestSetupTimeout())
                .that(setup.awaitCompletion(IntegrationTestSupport.getTestSetupTimeout(), TimeUnit.SECONDS))
                .isTrue();
        if (setup.failed()) {
            ctx.failNow(setup.causeOfFailure());
        }
    }

    private ProtonMessageHandler createCommandConsumer(
            final VertxTestContext ctx,
            final ProtonReceiver cmdReceiver,
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
            AmqpUtils.addProperty(commandResponse, MessageHelper.APP_PROPERTY_STATUS, HttpURLConnection.HTTP_OK);
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
            rejected.setError(new ErrorCondition(AmqpUtils.AMQP_BAD_REQUEST, REJECTED_COMMAND_ERROR_MESSAGE));
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

        final Checkpoint commandsReceived = ctx.checkpoint(COMMANDS_TO_SEND);

        final AtomicInteger counter = new AtomicInteger();
        testSendCommandSucceeds(
                ctx,
                commandTargetDeviceId,
                endpointConfig,
                (cmdReceiver, cmdResponseSender) -> (delivery, msg) -> {
                    ctx.verify(() -> {
                        assertThat(msg.getReplyTo()).isNull();
                        assertThat(msg.getSubject()).isEqualTo("setValue");
                        assertThat(msg.getAddress())
                                .isEqualTo(endpointConfig.getCommandMessageAddress(tenantId, commandTargetDeviceId));
                    });
                    log.debug("received command [name: {}]", msg.getSubject());
                    ProtonHelper.accepted(delivery, true);
                    cmdReceiver.flow(1);
                    commandsReceived.flag();
                }, payload -> {
                    counter.incrementAndGet();
                    return helper.sendOneWayCommand(
                            tenantId,
                            commandTargetDeviceId,
                            "setValue",
                            "text/plain",
                            payload,
                            helper.getSendCommandTimeout(counter.get() == 1));
                }, COMMANDS_TO_SEND);
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

        final int totalNoOfCommandsToSend = 60;
        connectAndSubscribe(
                ctx,
                commandTargetDeviceId,
                endpointConfig,
                (cmdReceiver, cmdResponseSender) -> createCommandConsumer(ctx, cmdReceiver, cmdResponseSender),
                totalNoOfCommandsToSend);
        if (ctx.failed()) {
            return;
        }

        final String replyId = "reply-id";
        final CountDownLatch commandsSucceeded = new CountDownLatch(totalNoOfCommandsToSend);
        final AtomicInteger commandsSent = new AtomicInteger(0);
        final AtomicLong lastReceivedTimestamp = new AtomicLong();

        final VertxTestContext setup = new VertxTestContext();

        final Future<MessageConsumer> asyncResponseConsumer = helper.applicationClient.createCommandResponseConsumer(
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

        asyncResponseConsumer.onComplete(setup.succeedingThenComplete());

        assertWithMessage("setup of command response consumer finished within %s seconds", IntegrationTestSupport.getTestSetupTimeout())
                .that(setup.awaitCompletion(IntegrationTestSupport.getTestSetupTimeout(), TimeUnit.SECONDS))
                .isTrue();
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
                helper.applicationClient.sendAsyncCommand(
                        tenantId,
                        commandTargetDeviceId,
                        "setValue",
                        correlationId,
                        replyId,
                        msg,
                        "text/plain",
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
        asyncResponseConsumer.result().close().onComplete(ar -> {
            if (commandsCompleted == commandsSent.get()) {
                ctx.completeNow();
            } else {
                ctx.failNow(new IllegalStateException("did not complete all commands sent"));
            }
        });
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

        final AtomicInteger counter = new AtomicInteger();
        testSendCommandSucceeds(
                ctx,
                commandTargetDeviceId,
                endpointConfig,
                (cmdReceiver, cmdResponseSender) -> createCommandConsumer(ctx, cmdReceiver, cmdResponseSender),
                payload -> {
                    counter.incrementAndGet();
                    return helper.sendCommand(
                            tenantId,
                            commandTargetDeviceId,
                            "setValue",
                            "text/plain",
                            payload,
                            helper.getSendCommandTimeout(counter.get() == 1))
                        .map(response -> {
                            ctx.verify(() -> {
                                DownstreamMessageAssertions.assertCommandAndControlApiProperties(
                                        response, tenantId, commandTargetDeviceId);
                                DownstreamMessageAssertions.assertMessageContainsTimeToLive(response, TTL_COMMAND_RESPONSE);
                            });
                            return (Void) null;
                        });
                },
                COMMANDS_TO_SEND);
    }

    private void testSendCommandSucceeds(
            final VertxTestContext ctx,
            final String commandTargetDeviceId,
            final AmqpCommandEndpointConfiguration endpointConfig,
            final BiFunction<ProtonReceiver, ProtonSender, ProtonMessageHandler> commandConsumerFactory,
            final Function<Buffer, Future<Void>> commandSender,
            final int totalNoOfCommandsToSend) throws InterruptedException {

        connectAndSubscribe(ctx, commandTargetDeviceId, endpointConfig, commandConsumerFactory, totalNoOfCommandsToSend);
        if (ctx.failed()) {
            return;
        }

        final Checkpoint sendCommandsSucceeded = ctx.checkpoint();
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

        final long timeToWait = totalNoOfCommandsToSend * 500;
        if (!commandsSucceeded.await(timeToWait, TimeUnit.MILLISECONDS)) {
            log.info("Timeout of {} milliseconds reached, stop waiting for commands to succeed", timeToWait);
        }
        final long commandsCompleted = totalNoOfCommandsToSend - commandsSucceeded.getCount();
        log.info("commands sent: {}, commands succeeded: {} after {} milliseconds",
                commandsSent.get(), commandsCompleted, lastReceivedTimestamp.get() - start);
        if (commandsCompleted == commandsSent.get()) {
            sendCommandsSucceeded.flag();
        } else {
            ctx.failNow(new IllegalStateException("did not complete all commands sent"));
        }
    }

    /**
     * Verifies that the adapter rejects malformed command messages sent by applications.
     * <p>
     * This test is applicable only if the messaging network type is AMQP.
     *
     * @param endpointConfig The endpoints to use for sending/receiving commands.
     * @param ctx The vert.x test context.
     * @throws InterruptedException if not all commands and responses are exchanged in time.
     */
    @ParameterizedTest(name = IntegrationTestSupport.PARAMETERIZED_TEST_NAME_PATTERN)
    @MethodSource("allCombinations")
    @Timeout(timeUnit = TimeUnit.SECONDS, value = 10)
    @EnabledIfMessagingSystemConfigured(type = MessagingType.amqp)
    public void testSendCommandViaAmqpFailsForMalformedMessage(
            final AmqpCommandEndpointConfiguration endpointConfig,
            final VertxTestContext ctx) throws InterruptedException {

        final String commandTargetDeviceId = endpointConfig.isSubscribeAsGateway()
                ? helper.setupGatewayDeviceBlocking(tenantId, deviceId, 5)
                : deviceId;

        final AtomicReference<GenericSenderLink> amqpCmdSenderRef = new AtomicReference<>();

        final VertxTestContext setup = new VertxTestContext();
        final Checkpoint setupDone = setup.checkpoint();
        final Checkpoint preconditions = setup.checkpoint(2);

        connectToAdapter(tenantId, deviceId, password, () -> createEventConsumer(tenantId, msg -> {
            // expect empty notification with TTD -1
            setup.verify(() -> assertThat(msg.getContentType())
                    .isEqualTo(EventConstants.CONTENT_TYPE_EMPTY_NOTIFICATION));
            final TimeUntilDisconnectNotification notification = msg.getTimeUntilDisconnectNotification().orElse(null);
            log.debug("received notification [{}]", notification);
            setup.verify(() -> assertThat(notification).isNotNull());
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
            .compose(ok -> helper.createGenericAmqpMessageSender(endpointConfig.getNorthboundEndpoint(), tenantId))
            .map(s -> {
                log.debug("created generic sender for sending commands [target address: {}]", endpointConfig.getSenderLinkTargetAddress(tenantId));
                amqpCmdSenderRef.set(s);
                preconditions.flag();
                return s;
            })
            .onComplete(setup.succeeding(v -> setupDone.flag()));

        assertWithMessage("setup of adapter finished within %s seconds", IntegrationTestSupport.getTestSetupTimeout())
                .that(setup.awaitCompletion(IntegrationTestSupport.getTestSetupTimeout(), TimeUnit.SECONDS))
                .isTrue();
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
        amqpCmdSenderRef.get().sendAndWaitForOutcome(messageWithoutSubject, NoopSpan.INSTANCE)
            .onComplete(ctx.failing(t -> {
                ctx.verify(() -> assertThat(t).isInstanceOf(ClientErrorException.class));
                expectedFailures.flag();
            }));

        log.debug("sending command message lacking message ID and correlation ID");
        final Message messageWithoutId = ProtonHelper.message("input data");
        messageWithoutId.setAddress(endpointConfig.getCommandMessageAddress(tenantId, commandTargetDeviceId));
        messageWithoutId.setSubject("setValue");
        messageWithoutId.setReplyTo("reply/to/address");
        amqpCmdSenderRef.get().sendAndWaitForOutcome(messageWithoutId, NoopSpan.INSTANCE)
            .onComplete(ctx.failing(t -> {
                ctx.verify(() -> assertThat(t).isInstanceOf(ClientErrorException.class));
                expectedFailures.flag();
            }));
    }

    /**
     * Verifies that the adapter rejects malformed command messages sent by applications.
     * <p>
     * This test is applicable only if the messaging network type is Kafka.
     *
     * @param endpointConfig The endpoints to use for sending/receiving commands.
     * @param ctx The vert.x test context.
     * @throws InterruptedException if not all commands and responses are exchanged in time.
     */
    @ParameterizedTest(name = IntegrationTestSupport.PARAMETERIZED_TEST_NAME_PATTERN)
    @MethodSource("allCombinations")
    @Timeout(timeUnit = TimeUnit.SECONDS, value = 10)
    @EnabledIfMessagingSystemConfigured(type = MessagingType.kafka)
    public void testSendCommandViaKafkaFailsForMalformedMessage(
            final AmqpCommandEndpointConfiguration endpointConfig,
            final VertxTestContext ctx) throws InterruptedException {

        final String commandTargetDeviceId = endpointConfig.isSubscribeAsGateway()
                ? helper.setupGatewayDeviceBlocking(tenantId, deviceId, 5)
                : deviceId;

        final AtomicReference<GenericKafkaSender> kafkaSenderRef = new AtomicReference<>();
        final CountDownLatch expectedCommandResponses = new CountDownLatch(1);

        final VertxTestContext setup = new VertxTestContext();
        final Checkpoint setupDone = setup.checkpoint();
        final Checkpoint ttdReceivedPrecondition = setup.checkpoint();

        final Future<MessageConsumer> kafkaAsyncErrorResponseConsumer = helper.createDeliveryFailureCommandResponseConsumer(
                ctx,
                tenantId,
                HttpURLConnection.HTTP_BAD_REQUEST,
                response -> {
                    ctx.verify(() -> {
                        DownstreamMessageAssertions.assertMessageContainsTimeToLive(response, TTL_COMMAND_RESPONSE);
                        assertThat(response.getProperties().getProperty(
                                KafkaRecordHelper.DELIVERY_FAILURE_NOTIFICATION_METADATA_PREFIX + ".foo",
                                String.class))
                            .isEqualTo("bar");
                        assertThat(response.getProperties().getPropertiesMap().containsKey("ignore-me")).isFalse();
                    });
                    expectedCommandResponses.countDown();
                },
                null);

        connectToAdapter(
                tenantId,
                deviceId,
                password,
                () -> createEventConsumer(tenantId, msg -> {
                    // expect empty notification with TTD -1
                    setup.verify(() -> assertThat(msg.getContentType())
                            .isEqualTo(EventConstants.CONTENT_TYPE_EMPTY_NOTIFICATION));
                    final TimeUntilDisconnectNotification notification = msg.getTimeUntilDisconnectNotification().orElse(null);
                    log.debug("received notification [{}]", notification);
                    setup.verify(() -> assertThat(notification).isNotNull());
                    if (notification.getTtd() == -1) {
                        ttdReceivedPrecondition.flag();
                    }
                }))
                .compose(con -> subscribeToCommands(endpointConfig, tenantId, commandTargetDeviceId)
                        .onSuccess(recv -> recv.handler((delivery, msg) -> ctx.failNow(
                                new IllegalStateException("should not have received command")))))
                .compose(ok -> helper.createGenericKafkaSender().onSuccess(kafkaSenderRef::set).mapEmpty())
                .compose(v -> kafkaAsyncErrorResponseConsumer)
                .onComplete(setup.succeeding(v -> setupDone.flag()));

        assertWithMessage("setup of adapter finished within %s seconds", IntegrationTestSupport.getTestSetupTimeout())
                .that(setup.awaitCompletion(IntegrationTestSupport.getTestSetupTimeout(), TimeUnit.SECONDS))
                .isTrue();
        if (setup.failed()) {
            ctx.failNow(setup.causeOfFailure());
            return;
        }

        final String commandTopic = new HonoTopic(HonoTopic.Type.COMMAND, tenantId).toString();

        log.debug("sending command message lacking subject and correlation ID - no failure response expected here");
        final Map<String, Object> properties1 = Map.of(
                MessageHelper.APP_PROPERTY_DEVICE_ID, deviceId,
                MessageHelper.SYS_PROPERTY_CONTENT_TYPE, MessageHelper.CONTENT_TYPE_OCTET_STREAM,
                KafkaRecordHelper.HEADER_RESPONSE_REQUIRED, true
        );
        kafkaSenderRef.get().sendAndWaitForOutcome(commandTopic, tenantId, deviceId, Buffer.buffer(), properties1)
                .onComplete(ctx.succeeding(ok -> {}));

        log.debug("sending command message lacking subject");
        final String correlationId = "1";
        final Map<String, Object> properties2 = Map.of(
                MessageHelper.SYS_PROPERTY_CORRELATION_ID, correlationId,
                MessageHelper.APP_PROPERTY_DEVICE_ID, deviceId,
                MessageHelper.SYS_PROPERTY_CONTENT_TYPE, MessageHelper.CONTENT_TYPE_OCTET_STREAM,
                KafkaRecordHelper.HEADER_RESPONSE_REQUIRED, true,
                "ignore-me", "please",
                KafkaRecordHelper.DELIVERY_FAILURE_NOTIFICATION_METADATA_PREFIX + ".foo", "bar"
        );
        kafkaSenderRef.get().sendAndWaitForOutcome(commandTopic, tenantId, deviceId, Buffer.buffer(), properties2)
                .onComplete(ctx.succeeding(ok -> {}));

        final long timeToWait = IntegrationTestSupport.getTimeoutMultiplicator() * 2500;
        if (!expectedCommandResponses.await(timeToWait, TimeUnit.MILLISECONDS)) {
            log.info("Timeout of {} milliseconds reached, stop waiting for command response", timeToWait);
        }
        kafkaAsyncErrorResponseConsumer.result().close()
                .onComplete(ar -> {
                    if (expectedCommandResponses.getCount() == 0) {
                        ctx.completeNow();
                    } else {
                        ctx.failNow(new IllegalStateException("did not receive command response"));
                    }
                });
    }

    /**
     * Verifies that the adapter immediately forwards the <em>released</em> disposition
     * if there is no credit left for sending the command to the device.
     * <p>
     * If Kafka is used, this means a corresponding error command response is published.
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
        final String firstCommandSubject = "firstCommandSubject";
        final Promise<Void> firstCommandReceived = Promise.promise();

        final VertxTestContext setup = new VertxTestContext();
        final Checkpoint setupDone = setup.checkpoint();
        final Checkpoint preconditions = setup.checkpoint(1);

        connectToAdapter(
                tenantId,
                deviceId,
                password,
                () -> createEventConsumer(tenantId, msg -> {
                    // expect empty notification with TTD -1
                    setup.verify(() -> assertThat(msg.getContentType())
                            .isEqualTo(EventConstants.CONTENT_TYPE_EMPTY_NOTIFICATION));
                    final TimeUntilDisconnectNotification notification = msg.getTimeUntilDisconnectNotification().orElse(null);
                    log.info("received notification [{}]", notification);
                    setup.verify(() -> assertThat(notification).isNotNull());
                    if (notification.getTtd() == -1) {
                        preconditions.flag();
                    }
                }))
            // omit tenant ID to test establishment of receiver link using tenant of authenticated device
            .compose(con -> subscribeToCommands(endpointConfig, "", commandTargetDeviceId))
            .onFailure(setup::failNow)
            .onSuccess(recv -> {
                recv.handler((delivery, msg) -> {
                    log.info("received command [name: {}, reply-to: {}, correlation-id: {}]",
                            msg.getSubject(), msg.getReplyTo(), msg.getCorrelationId());
                    ctx.verify(() -> {
                        assertThat(msg.getSubject()).isEqualTo(firstCommandSubject);
                    });
                    firstCommandReceived.complete();
                    ProtonHelper.accepted(delivery, true);
                    // don't send credits
                });
                recv.flow(1); // just give 1 initial credit
            })
            .onComplete(setup.succeeding(v -> setupDone.flag()));

        assertWithMessage("setup of adapter finished within %s seconds", IntegrationTestSupport.getTestSetupTimeout())
                .that(setup.awaitCompletion(IntegrationTestSupport.getTestSetupTimeout(), TimeUnit.SECONDS))
                .isTrue();
        if (setup.failed()) {
            ctx.failNow(setup.causeOfFailure());
            return;
        }

        // send first command
        helper.sendOneWayCommand(
                tenantId,
                commandTargetDeviceId,
                firstCommandSubject,
                "text/plain",
                Buffer.buffer("cmd"),
                helper.getSendCommandTimeout(true))
            // first command shall succeed because there's one initial credit
            .onFailure(ctx::failNow)
            .compose(ok -> {
                log.info("sent first command [subject: {}]", firstCommandSubject);
                return firstCommandReceived.future();
            })
            // send second command after first (one-way) command has been received
            .compose(ok -> helper.sendCommand(
                    tenantId,
                    commandTargetDeviceId,
                    "secondCommandSubject",
                    "text/plain",
                    Buffer.buffer("cmd"),
                    helper.getSendCommandTimeout(false)))
            // sending of second command is supposed to fail due to no credit
            .onComplete(ctx.failing(t -> {
                ctx.verify(() -> {
                    assertThat(t).isInstanceOf(ServerErrorException.class);
                    assertThat(((ServerErrorException) t).getErrorCode()).isEqualTo(HttpURLConnection.HTTP_UNAVAILABLE);
                    // with no explicit credit check, the AMQP adapter would just run into the
                    // "waiting for delivery update" timeout (after 1s) and the error here would be caused
                    // by a request timeout in the sendOneWayCommand() method above
                    assertThat(t).isNotInstanceOf(SendMessageTimeoutException.class);
                    assertThat(t.getMessage()).doesNotContain("timed out");
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that the adapter immediately forwards the <em>released</em> disposition
     * if there is no consumer for a sent command.
     * <p>
     * If Kafka is used, this means a corresponding error command response is published.
     *
     * @param endpointConfig The endpoints to use for sending/receiving commands.
     * @param ctx The vert.x test context.
     * @throws InterruptedException if not all commands and responses are exchanged in time.
     */
    @ParameterizedTest(name = IntegrationTestSupport.PARAMETERIZED_TEST_NAME_PATTERN)
    @MethodSource("allCombinations")
    @Timeout(timeUnit = TimeUnit.SECONDS, value = 10)
    public void testSendCommandFailsForNoConsumer(
            final AmqpCommandEndpointConfiguration endpointConfig,
            final VertxTestContext ctx) throws InterruptedException {

        final String commandTargetDeviceId = endpointConfig.isSubscribeAsGateway()
                ? helper.setupGatewayDeviceBlocking(tenantId, deviceId, 5)
                : deviceId;

        final String otherDeviceId = helper.getRandomDeviceId(tenantId);

        final VertxTestContext setup = new VertxTestContext();

        connectToAdapter(tenantId, otherDeviceId, password, (Supplier<Future<MessageConsumer>>) null)
                .compose(v -> helper.registry.addDeviceToTenant(tenantId, deviceId, password))
                // subscribe using otherDeviceId so that the Command Router creates the tenant-specific consumer
                .compose(con -> subscribeToCommands(endpointConfig, tenantId, otherDeviceId)
                        .onSuccess(recv -> recv.handler((delivery, msg) -> ctx.failNow(
                                new IllegalStateException("should not have received command")))))
                .onComplete(setup.succeedingThenComplete());

        assertWithMessage("setup of adapter finished within %s seconds", IntegrationTestSupport.getTestSetupTimeout())
                .that(setup.awaitCompletion(IntegrationTestSupport.getTestSetupTimeout(), TimeUnit.SECONDS))
                .isTrue();
        if (setup.failed()) {
            ctx.failNow(setup.causeOfFailure());
            return;
        }

        helper.sendCommand(
                tenantId,
                commandTargetDeviceId,
                "setValue",
                "text/plain",
                Buffer.buffer("cmd"),
                helper.getSendCommandTimeout(true))
                .onComplete(ctx.failing(t -> {
                    ctx.verify(() -> {
                        assertThat(t).isInstanceOf(ServerErrorException.class);
                        assertThat(((ServerErrorException) t).getErrorCode()).isEqualTo(HttpURLConnection.HTTP_UNAVAILABLE);
                        assertThat(t).isNotInstanceOf(SendMessageTimeoutException.class);
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that the adapter forwards the <em>rejected</em> disposition, received from a device, back to the
     * application.
     * <p>
     * If Kafka is used, this means a corresponding error command response is published.
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

        final int totalNoOfCommandsToSend = 3;
        connectAndSubscribe(ctx, commandTargetDeviceId, endpointConfig,
                (cmdReceiver, cmdResponseSender) -> createRejectingCommandConsumer(ctx, cmdReceiver),
                totalNoOfCommandsToSend);
        if (ctx.failed()) {
            return;
        }

        final String replyId = "reply-id";
        final CountDownLatch commandsFailed = new CountDownLatch(totalNoOfCommandsToSend);
        final AtomicInteger commandsSent = new AtomicInteger(0);
        final AtomicLong lastReceivedTimestamp = new AtomicLong();
        final long start = System.currentTimeMillis();
        final long commandTimeout = IntegrationTestSupport.getSendCommandTimeout();
        final Handler<Void> failureNotificationReceivedHandler = v -> {
            lastReceivedTimestamp.set(System.currentTimeMillis());
            commandsFailed.countDown();
        };

        final VertxTestContext setup = new VertxTestContext();
        final Future<MessageConsumer> kafkaAsyncErrorResponseConsumer = IntegrationTestSupport.isUsingKafkaMessaging()
                ? helper.createDeliveryFailureCommandResponseConsumer(
                        ctx,
                        tenantId,
                        HttpURLConnection.HTTP_BAD_REQUEST,
                        response -> {
                            ctx.verify(() -> {
                                DownstreamMessageAssertions.assertMessageContainsTimeToLive(response, TTL_COMMAND_RESPONSE);
                            });
                            failureNotificationReceivedHandler.handle(null);
                        },
                        REJECTED_COMMAND_ERROR_MESSAGE::equals)
                : Future.succeededFuture(null);
        kafkaAsyncErrorResponseConsumer.onComplete(setup.succeedingThenComplete());
        assertWithMessage("setup of command response consumer finished within %s seconds", IntegrationTestSupport.getTestSetupTimeout())
                .that(setup.awaitCompletion(IntegrationTestSupport.getTestSetupTimeout(), TimeUnit.SECONDS))
                .isTrue();
        if (setup.failed()) {
            ctx.failNow(setup.causeOfFailure());
            return;
        }

        while (commandsSent.get() < totalNoOfCommandsToSend) {
            final CountDownLatch commandSent = new CountDownLatch(1);
            context.runOnContext(go -> {
                final String correlationId = String.valueOf(commandsSent.getAndIncrement());
                final Buffer msg = Buffer.buffer("value: " + commandsSent.get());
                helper.applicationClient.sendAsyncCommand(
                        tenantId,
                        commandTargetDeviceId,
                        "setValue",
                        correlationId,
                        replyId,
                        msg,
                        "text/plain",
                        null)
                    .onComplete(sendAttempt -> {
                        if (IntegrationTestSupport.isUsingAmqpMessaging()) {
                            if (sendAttempt.succeeded()) {
                                log.info("sending command {} via AMQP succeeded unexpectedly", commandsSent.get());
                            } else {
                                if (sendAttempt.cause() instanceof ClientErrorException
                                        && ((ClientErrorException) sendAttempt.cause()).getErrorCode() == HttpURLConnection.HTTP_BAD_REQUEST
                                        && REJECTED_COMMAND_ERROR_MESSAGE.equals(sendAttempt.cause().getMessage())) {
                                    log.debug("sending command {} failed as expected: {}", commandsSent.get(),
                                            sendAttempt.cause().toString());
                                    failureNotificationReceivedHandler.handle(null);
                                } else {
                                    log.info("sending command {} failed with an unexpected error", commandsSent.get(),
                                            sendAttempt.cause());
                                }
                            }
                        } else if (sendAttempt.failed()) {
                            log.debug("sending command {} via Kafka failed unexpectedly",
                                    commandsSent.get(), sendAttempt.cause());
                        }
                        commandSent.countDown();
                    });
            });

            commandSent.await();
        }

        final long timeToWait = 300 + (totalNoOfCommandsToSend * commandTimeout);
        if (!commandsFailed.await(timeToWait, TimeUnit.MILLISECONDS)) {
            log.info("Timeout of {} milliseconds reached, stop waiting for commands", timeToWait);
        }
        final long commandsCompleted = totalNoOfCommandsToSend - commandsFailed.getCount();
        log.info("commands sent: {}, commands failed: {} after {} milliseconds",
                commandsSent.get(), commandsCompleted, lastReceivedTimestamp.get() - start);
        Optional.ofNullable(kafkaAsyncErrorResponseConsumer.result())
                .map(MessageConsumer::close)
                .orElseGet(Future::succeededFuture)
                .onComplete(ar -> {
                    if (commandsCompleted == commandsSent.get()) {
                        ctx.completeNow();
                    } else {
                        ctx.failNow(new IllegalStateException("did not complete all commands sent"));
                    }
                });
    }

    /**
     * Verifies that the adapter forwards the <em>released</em> disposition back to the
     * application if the device hasn't sent a disposition update for the delivery of
     * the command message sent to the device.
     * <p>
     * If Kafka is used, this means a corresponding error command response is published.
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
        final int totalNoOfCommandsToSend = 2;
        // command handler won't send a disposition update
        connectAndSubscribe(ctx, commandTargetDeviceId, endpointConfig,
                (cmdReceiver, cmdResponseSender) -> createNotSendingDeliveryUpdateCommandConsumer(ctx, cmdReceiver, receivedMessagesCounter), totalNoOfCommandsToSend);
        if (ctx.failed()) {
            return;
        }

        final String replyId = "reply-id";
        final CountDownLatch commandsFailed = new CountDownLatch(totalNoOfCommandsToSend);
        final AtomicInteger commandsSent = new AtomicInteger(0);
        final AtomicLong lastReceivedTimestamp = new AtomicLong();
        final long start = System.currentTimeMillis();
        final long commandTimeout = IntegrationTestSupport.getSendCommandTimeout();
        final Handler<Void> failureNotificationReceivedHandler = v -> {
            lastReceivedTimestamp.set(System.currentTimeMillis());
            commandsFailed.countDown();
        };

        final VertxTestContext setup = new VertxTestContext();
        final Future<MessageConsumer> kafkaAsyncErrorResponseConsumer = IntegrationTestSupport.isUsingKafkaMessaging()
                ? helper.createDeliveryFailureCommandResponseConsumer(ctx, tenantId,
                        HttpURLConnection.HTTP_UNAVAILABLE,
                        response -> failureNotificationReceivedHandler.handle(null),
                        null)
                : Future.succeededFuture(null);
        kafkaAsyncErrorResponseConsumer.onComplete(setup.succeedingThenComplete());
        assertWithMessage("setup of command response consumer finished within %s seconds", IntegrationTestSupport.getTestSetupTimeout())
                .that(setup.awaitCompletion(IntegrationTestSupport.getTestSetupTimeout(), TimeUnit.SECONDS))
                .isTrue();
        if (setup.failed()) {
            ctx.failNow(setup.causeOfFailure());
            return;
        }

        while (commandsSent.get() < totalNoOfCommandsToSend) {
            final CountDownLatch commandSent = new CountDownLatch(1);
            context.runOnContext(go -> {
                final String correlationId = String.valueOf(commandsSent.getAndIncrement());
                final Buffer msg = Buffer.buffer("value: " + commandsSent.get());
                helper.applicationClient.sendAsyncCommand(
                        tenantId,
                        commandTargetDeviceId,
                        "setValue",
                        correlationId,
                        replyId,
                        msg,
                        "text/plain",
                        null)
                    .onComplete(sendAttempt -> {
                        if (IntegrationTestSupport.isUsingAmqpMessaging()) {
                            if (sendAttempt.succeeded()) {
                                log.debug("sending command {} succeeded unexpectedly", commandsSent.get());
                            } else {
                                if (sendAttempt.cause() instanceof ServerErrorException
                                        && ((ServerErrorException) sendAttempt.cause()).getErrorCode() == HttpURLConnection.HTTP_UNAVAILABLE
                                        && !(sendAttempt.cause() instanceof SendMessageTimeoutException)) {
                                    log.debug("sending command {} failed as expected: {}", commandsSent.get(),
                                            sendAttempt.cause().toString());
                                    failureNotificationReceivedHandler.handle(null);
                                } else {
                                    log.debug("sending command {} failed with an unexpected error", commandsSent.get(),
                                            sendAttempt.cause());
                                }
                            }
                        } else if (sendAttempt.failed()) {
                            log.debug("sending command {} via Kafka failed unexpectedly",
                                    commandsSent.get(), sendAttempt.cause());
                        }
                        commandSent.countDown();
                    });
            });

            commandSent.await();
        }

        final long timeToWait = 300 + (totalNoOfCommandsToSend * commandTimeout);
        if (!commandsFailed.await(timeToWait, TimeUnit.MILLISECONDS)) {
            log.info("Timeout of {} milliseconds reached, stop waiting for commands", timeToWait);
        }
        assertThat(receivedMessagesCounter.get()).isEqualTo(totalNoOfCommandsToSend);
        final long commandsCompleted = totalNoOfCommandsToSend - commandsFailed.getCount();
        log.info("commands sent: {}, commands failed: {} after {} milliseconds",
                commandsSent.get(), commandsCompleted, lastReceivedTimestamp.get() - start);
        Optional.ofNullable(kafkaAsyncErrorResponseConsumer.result())
                .map(MessageConsumer::close)
                .orElseGet(Future::succeededFuture)
                .onComplete(ar -> {
                    if (commandsCompleted == commandsSent.get()) {
                        ctx.completeNow();
                    } else {
                        ctx.failNow(new IllegalStateException("did not complete all commands sent"));
                    }
                });
    }

    /**
     * Verifies that the adapter immediately closes the command receiver link of a gateway wanting
     * to receive commands of a device that doesn't have the gateway in its via-gateways.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    @Timeout(timeUnit = TimeUnit.SECONDS, value = 20)
    public void testCommandReceiverCreationFailsForDeviceNotInViaGateways(
            final VertxTestContext ctx) {

        final AmqpCommandEndpointConfiguration endpointConfig = new AmqpCommandEndpointConfiguration(
                SubscriberRole.GATEWAY_FOR_SINGLE_DEVICE);

        final String otherDeviceId = helper.getRandomDeviceId(tenantId);
        helper.registry
                .addDeviceToTenant(tenantId, deviceId, password)
                .compose(ok -> helper.registry.addDeviceToTenant(tenantId, otherDeviceId, password))
                .compose(ok -> connectToAdapter(IntegrationTestSupport.getUsername(deviceId, tenantId), password))
                .compose(conAck -> {
                    final Promise<Void> result = Promise.promise();
                    context.runOnContext(go -> {
                        final ProtonReceiver recv = connection
                                .createReceiver(endpointConfig.getSubscriptionAddress(tenantId, otherDeviceId));
                        recv.setQoS(ProtonQoS.AT_LEAST_ONCE);
                        recv.openHandler(openResult -> {
                            ctx.verify(() -> {
                                assertThat(openResult.failed() || !HonoProtonHelper.isLinkEstablished(openResult.result()))
                                        .isTrue();
                            });
                        });
                        recv.closeHandler(closeResult -> {
                            ctx.verify(() -> {
                                assertThat(recv.getRemoteCondition()).isNotNull();
                                assertThat(recv.getRemoteCondition().getCondition())
                                        .isEqualTo(AmqpError.UNAUTHORIZED_ACCESS);
                            });
                            result.tryComplete();
                        });
                        recv.detachHandler(closeResult -> result.tryFail("unexpected detach with closed=false"));
                        recv.open();
                    });
                    return result.future();
                })
                .onComplete(ctx.succeedingThenComplete());
    }

    /**
     * Registers a device and opens a connection to the AMQP adapter using
     * the device's credentials.
     *
     * @param tenantId The ID of the tenant that the device belongs to.
     * @param deviceId The identifier of the device.
     * @param password The password to use for authentication.
     * @param consumerFactory The factory for creating the consumer of messages published by the device or {@code null}
     *            if no consumer should be created.
     * @return A future that will be completed with the ProtonConnection with the adapter or failed if the
     *         connection could not be established.
     */
    protected final Future<ProtonConnection> connectToAdapter(
            final String tenantId,
            final String deviceId,
            final String password,
            final Supplier<Future<MessageConsumer>> consumerFactory) {

        return helper.registry
        .addDeviceToTenant(tenantId, deviceId, password)
        .compose(ok -> Optional.ofNullable(consumerFactory)
                .map(Supplier::get)
                .orElseGet(Future::succeededFuture))
        .compose(ok -> connectToAdapter(IntegrationTestSupport.getUsername(deviceId, tenantId), password))
        .recover(t -> {
            log.error("failed to establish connection to AMQP adapter [host: {}, port: {}]",
                    IntegrationTestSupport.AMQP_HOST, IntegrationTestSupport.AMQP_PORT, t);
            return Future.failedFuture(t);
        });

    }
}
