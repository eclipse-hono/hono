/*******************************************************************************
 * Copyright (c) 2019, 2022 Contributors to the Eclipse Foundation
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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Stream;

import javax.jms.IllegalStateException;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.application.client.DownstreamMessage;
import org.eclipse.hono.application.client.MessageConsumer;
import org.eclipse.hono.application.client.MessageContext;
import org.eclipse.hono.application.client.TimeUntilDisconnectNotification;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.SendMessageTimeoutException;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.amqp.GenericSenderLink;
import org.eclipse.hono.client.kafka.HonoTopic;
import org.eclipse.hono.client.kafka.KafkaRecordHelper;
import org.eclipse.hono.tests.CommandEndpointConfiguration.SubscriberRole;
import org.eclipse.hono.tests.DownstreamMessageAssertions;
import org.eclipse.hono.tests.EnabledIfMessagingSystemConfigured;
import org.eclipse.hono.tests.EnabledIfProtocolAdaptersAreRunning;
import org.eclipse.hono.tests.GenericKafkaSender;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.MessagingType;
import org.eclipse.hono.util.ResourceIdentifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.mqtt.MqttPubAckMessage;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.opentracing.noop.NoopSpan;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.impl.NetSocketInternal;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.mqtt.impl.MqttClientImpl;
import io.vertx.mqtt.messages.MqttPublishMessage;
import io.vertx.proton.ProtonHelper;


/**
 * Integration tests for sending commands to device connected to the MQTT adapter.
 *
 */
@ExtendWith(VertxExtension.class)
@EnabledIfProtocolAdaptersAreRunning(mqttAdapter = true)
public class CommandAndControlMqttIT extends MqttTestBase {

    private static final int COMMANDS_TO_SEND = 60;

    private String tenantId;
    private String deviceId;
    private final String password = "secret";

    static Stream<MqttCommandEndpointConfiguration> allCombinations() {
        return Stream.of(
                new MqttCommandEndpointConfiguration(SubscriberRole.DEVICE),
                new MqttCommandEndpointConfiguration(SubscriberRole.GATEWAY_FOR_ALL_DEVICES),
                new MqttCommandEndpointConfiguration(SubscriberRole.GATEWAY_FOR_SINGLE_DEVICE)
                );
    }

    static Stream<MqttCommandEndpointConfiguration> gatewayForSingleDevice() {
        return Stream.of(
                new MqttCommandEndpointConfiguration(SubscriberRole.GATEWAY_FOR_SINGLE_DEVICE)
        );
    }

    /**
     * Adds a random tenant.
     *
     * @param ctx The vert.x test context.
     */
    @BeforeEach
    public void addRandomTenant(final VertxTestContext ctx) {
        tenantId = helper.getRandomTenantId();
        deviceId = helper.getRandomDeviceId(tenantId);
        helper.registry.addTenant(tenantId).onComplete(ctx.succeedingThenComplete());
    }

    private Future<MessageConsumer> createConsumer(
            final String tenantId,
            final Handler<DownstreamMessage<? extends MessageContext>> messageConsumer) {
        return helper.applicationClient.createEventConsumer(
                tenantId,
                messageConsumer::handle,
                close -> {});
    }

    private Future<Void> subscribeToCommands(
            final String commandTargetDeviceId,
            final Handler<MqttPublishMessage> msgHandler,
            final MqttCommandEndpointConfiguration endpointConfig,
            final MqttQoS qos) {

        final Promise<Void> result = Promise.promise();
        context.runOnContext(go -> {
            mqttClient.publishHandler(msgHandler);
            mqttClient.subscribeCompletionHandler(subAckMsg -> {
                if (subAckMsg.grantedQoSLevels().contains(qos.value())) {
                    result.complete();
                } else {
                    result.fail("could not subscribe to command topic");
                }
            });
            mqttClient.subscribe(endpointConfig.getCommandTopicFilter(commandTargetDeviceId), qos.value());
        });
        return result.future();
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
    @Timeout(timeUnit = TimeUnit.SECONDS, value = 10)
    public void testSendOneWayCommandSucceeds(
            final MqttCommandEndpointConfiguration endpointConfig,
            final VertxTestContext ctx) throws InterruptedException {

        final String commandTargetDeviceId = endpointConfig.isSubscribeAsGateway()
                ? helper.setupGatewayDeviceBlocking(tenantId, deviceId, 5)
                : deviceId;
        final Checkpoint commandsReceived = ctx.checkpoint(COMMANDS_TO_SEND);

        final AtomicInteger counter = new AtomicInteger();
        testSendCommandSucceeds(ctx, commandTargetDeviceId, msg -> {
            LOGGER.trace("received one-way command [topic: {}]", msg.topicName());
            final ResourceIdentifier topic = ResourceIdentifier.fromString(msg.topicName());
            ctx.verify(() -> {
                endpointConfig.assertCommandPublishTopicStructure(topic, commandTargetDeviceId, true, "setValue");
            });
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
        }, endpointConfig, COMMANDS_TO_SEND, MqttQoS.AT_MOST_ONCE);
    }

    /**
     * Verifies that the adapter forwards commands with Qos 0 and response hence and forth between an application and a
     * device.
     *
     * @param endpointConfig The endpoints to use for sending/receiving commands.
     * @param ctx The vert.x test context.
     * @throws InterruptedException if not all commands and responses are exchanged in time.
     */
    @ParameterizedTest(name = IntegrationTestSupport.PARAMETERIZED_TEST_NAME_PATTERN)
    @MethodSource("allCombinations")
    public void testSendCommandSucceedsWithQos0(
            final MqttCommandEndpointConfiguration endpointConfig,
            final VertxTestContext ctx) throws InterruptedException {

        testSendCommandSucceeds(ctx, endpointConfig, MqttQoS.AT_MOST_ONCE);
    }

    /**
     * Verifies that the adapter forwards commands with Qos 1 and response hence and forth between an application and a
     * device.
     *
     * @param endpointConfig The endpoints to use for sending/receiving commands.
     * @param ctx The vert.x test context.
     * @throws InterruptedException if not all commands and responses are exchanged in time.
     */
    @ParameterizedTest(name = IntegrationTestSupport.PARAMETERIZED_TEST_NAME_PATTERN)
    @MethodSource("allCombinations")
    public void testSendCommandSucceedsWithQos1(
            final MqttCommandEndpointConfiguration endpointConfig,
            final VertxTestContext ctx) throws InterruptedException {

        testSendCommandSucceeds(ctx, endpointConfig, MqttQoS.AT_LEAST_ONCE);
    }

    private void testSendCommandSucceeds(
            final VertxTestContext ctx,
            final MqttCommandEndpointConfiguration endpointConfig,
            final MqttQoS qos) throws InterruptedException {

        final String commandTargetDeviceId = endpointConfig.isSubscribeAsGateway()
                ? helper.setupGatewayDeviceBlocking(tenantId, deviceId, 5)
                : deviceId;

        final AtomicInteger counter = new AtomicInteger();
        testSendCommandSucceeds(ctx, commandTargetDeviceId, msg -> {
            LOGGER.trace("received command [{}]", msg.topicName());
            final ResourceIdentifier topic = ResourceIdentifier.fromString(msg.topicName());

            ctx.verify(() -> {
                endpointConfig.assertCommandPublishTopicStructure(topic, commandTargetDeviceId, false, "setValue");
            });

            final String commandRequestId = topic.elementAt(4);
            final String command = topic.elementAt(5);

            // send response
            mqttClient.publish(
                    endpointConfig.getResponseTopic(
                            counter.get(),
                            commandTargetDeviceId,
                            commandRequestId,
                            HttpURLConnection.HTTP_OK),
                    Buffer.buffer(command + ": ok"),
                    qos,
                    false,
                    false);
        }, payload -> {
            final String contentType = payload != null ? "text/plain" : null;
            counter.incrementAndGet();
            return helper.sendCommand(
                    tenantId,
                    commandTargetDeviceId,
                    "setValue",
                    contentType,
                    payload,
                    helper.getSendCommandTimeout(counter.get() == 1))
                    .map(response -> {
                        ctx.verify(() -> {
                            DownstreamMessageAssertions.assertCommandAndControlApiProperties(
                                    response, tenantId, commandTargetDeviceId);
                        });
                        return (Void) null;
                    });
        }, endpointConfig, COMMANDS_TO_SEND, qos);
    }

    private void testSendCommandSucceeds(
            final VertxTestContext ctx,
            final String commandTargetDeviceId,
            final Handler<MqttPublishMessage> commandConsumer,
            final Function<Buffer, Future<Void>> commandSender,
            final MqttCommandEndpointConfiguration endpointConfig,
            final int totalNoOfCommandsToSend,
            final MqttQoS subscribeQos) throws InterruptedException {

        final VertxTestContext setup = new VertxTestContext();
        final Checkpoint ready = setup.checkpoint(2);

        helper.registry
        .addDeviceToTenant(tenantId, deviceId, password)
        .compose(ok -> connectToAdapter(IntegrationTestSupport.getUsername(deviceId, tenantId), password))
        .compose(ok -> createConsumer(tenantId, msg -> {
            // expect empty notification with TTD -1
            setup.verify(() -> assertThat(msg.getContentType()).isEqualTo(EventConstants.CONTENT_TYPE_EMPTY_NOTIFICATION));
            final TimeUntilDisconnectNotification notification = msg.getTimeUntilDisconnectNotification().orElse(null);
            LOGGER.info("received notification [{}]", notification);
            setup.verify(() -> assertThat(notification).isNotNull());
            if (notification.getTtd() == -1) {
                ready.flag();
            }
        }))
        .compose(conAck -> subscribeToCommands(commandTargetDeviceId, commandConsumer, endpointConfig, subscribeQos))
        .onComplete(setup.succeeding(ok -> ready.flag()));

        assertWithMessage("setup of adapter finished within %s seconds", IntegrationTestSupport.getTestSetupTimeout())
                .that(setup.awaitCompletion(IntegrationTestSupport.getTestSetupTimeout(), TimeUnit.SECONDS))
                .isTrue();
        if (setup.failed()) {
            ctx.failNow(setup.causeOfFailure());
            return;
        }

        final Checkpoint sendCommandsSucceeded = ctx.checkpoint();
        final CountDownLatch commandsSucceeded = new CountDownLatch(totalNoOfCommandsToSend);
        final AtomicInteger commandsSent = new AtomicInteger(0);
        final AtomicLong lastReceivedTimestamp = new AtomicLong(0);
        final long start = System.currentTimeMillis();

        while (commandsSent.get() < totalNoOfCommandsToSend) {
            final CountDownLatch commandSent = new CountDownLatch(1);
            context.runOnContext(go -> {
                commandsSent.getAndIncrement();
                final Buffer msg = commandsSent.get() % 2 == 0
                        ? Buffer.buffer("value: " + commandsSent.get())
                        : null; // use 'null' payload for half the commands, ensuring such commands also get forwarded
                commandSender.apply(msg).onComplete(sendAttempt -> {
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
                    commandSent.countDown();
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
    @Timeout(timeUnit = TimeUnit.SECONDS, value = 20)
    @EnabledIfMessagingSystemConfigured(type = MessagingType.amqp)
    public void testSendCommandViaAmqpFailsForMalformedMessage(
            final MqttCommandEndpointConfiguration endpointConfig,
            final VertxTestContext ctx) throws InterruptedException {

        final String commandTargetDeviceId = endpointConfig.isSubscribeAsGateway()
                ? helper.setupGatewayDeviceBlocking(tenantId, deviceId, 5)
                : deviceId;
        final AtomicReference<GenericSenderLink> amqpCmdSenderRef = new AtomicReference<>();
        final String linkTargetAddress = endpointConfig.getSenderLinkTargetAddress(tenantId);

        final VertxTestContext setup = new VertxTestContext();
        final Checkpoint ready = setup.checkpoint(2);

        createConsumer(tenantId, msg -> {
            // expect empty notification with TTD -1
            setup.verify(() -> assertThat(msg.getContentType()).isEqualTo(EventConstants.CONTENT_TYPE_EMPTY_NOTIFICATION));
            final TimeUntilDisconnectNotification notification = msg.getTimeUntilDisconnectNotification().orElse(null);
            LOGGER.info("received notification [{}]", notification);
            if (notification.getTtd() == -1) {
                ready.flag();
            }
        })
        .compose(consumer -> helper.registry.addDeviceToTenant(tenantId, deviceId, password))
        .compose(ok -> connectToAdapter(IntegrationTestSupport.getUsername(deviceId, tenantId), password))
        .compose(conAck -> subscribeToCommands(commandTargetDeviceId, msg -> {
            // all commands should get rejected because they fail to pass the validity check
            ctx.failNow(new IllegalStateException("should not have received command"));
        }, endpointConfig, MqttQoS.AT_MOST_ONCE))
        .compose(ok -> helper.createGenericAmqpMessageSender(endpointConfig.getNorthboundEndpoint(), tenantId))
        .onComplete(setup.succeeding(genericSender -> {
            LOGGER.debug("created generic sender for sending commands [target address: {}]", linkTargetAddress);
            amqpCmdSenderRef.set(genericSender);
            ready.flag();
        }));

        assertWithMessage("setup of adapter finished within %s seconds", IntegrationTestSupport.getTestSetupTimeout())
                .that(setup.awaitCompletion(IntegrationTestSupport.getTestSetupTimeout(), TimeUnit.SECONDS))
                .isTrue();
        if (setup.failed()) {
            ctx.failNow(setup.causeOfFailure());
            return;
        }

        final Checkpoint failedAttempts = ctx.checkpoint(2);
        final String messageAddress = endpointConfig.getCommandMessageAddress(tenantId, commandTargetDeviceId);

        LOGGER.debug("sending command message lacking subject");
        final Message messageWithoutSubject = ProtonHelper.message("input data");
        messageWithoutSubject.setAddress(messageAddress);
        messageWithoutSubject.setMessageId("message-id");
        messageWithoutSubject.setReplyTo("reply/to/address");
        amqpCmdSenderRef.get().sendAndWaitForOutcome(messageWithoutSubject, NoopSpan.INSTANCE).onComplete(ctx.failing(t -> {
            ctx.verify(() -> assertThat(t).isInstanceOf(ClientErrorException.class));
            failedAttempts.flag();
        }));

        LOGGER.debug("sending command message lacking message ID and correlation ID");
        final Message messageWithoutId = ProtonHelper.message("input data");
        messageWithoutId.setAddress(messageAddress);
        messageWithoutId.setSubject("setValue");
        messageWithoutId.setReplyTo("reply/to/address");
        amqpCmdSenderRef.get().sendAndWaitForOutcome(messageWithoutId, NoopSpan.INSTANCE).onComplete(ctx.failing(t -> {
            ctx.verify(() -> assertThat(t).isInstanceOf(ClientErrorException.class));
            failedAttempts.flag();
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
    @Timeout(timeUnit = TimeUnit.SECONDS, value = 20)
    @EnabledIfMessagingSystemConfigured(type = MessagingType.kafka)
    public void testSendCommandViaKafkaFailsForMalformedMessage(
            final MqttCommandEndpointConfiguration endpointConfig,
            final VertxTestContext ctx) throws InterruptedException {

        final String commandTargetDeviceId = endpointConfig.isSubscribeAsGateway()
                ? helper.setupGatewayDeviceBlocking(tenantId, deviceId, 5)
                : deviceId;
        final AtomicReference<GenericKafkaSender> kafkaSenderRef = new AtomicReference<>();
        final CountDownLatch expectedCommandResponses = new CountDownLatch(1);

        final VertxTestContext setup = new VertxTestContext();
        final Checkpoint ready = setup.checkpoint(2);

        final Future<MessageConsumer> kafkaAsyncErrorResponseConsumer = helper.createDeliveryFailureCommandResponseConsumer(
                ctx,
                tenantId,
                HttpURLConnection.HTTP_BAD_REQUEST,
                response -> expectedCommandResponses.countDown(),
                null);

        createConsumer(tenantId, msg -> {
            // expect empty notification with TTD -1
            setup.verify(() -> assertThat(msg.getContentType()).isEqualTo(EventConstants.CONTENT_TYPE_EMPTY_NOTIFICATION));
            final TimeUntilDisconnectNotification notification = msg.getTimeUntilDisconnectNotification().orElse(null);
            LOGGER.info("received notification [{}]", notification);
            if (notification.getTtd() == -1) {
                ready.flag();
            }
        })
                .compose(consumer -> helper.registry.addDeviceToTenant(tenantId, deviceId, password))
                .compose(ok -> connectToAdapter(IntegrationTestSupport.getUsername(deviceId, tenantId), password))
                .compose(conAck -> subscribeToCommands(commandTargetDeviceId, msg -> {
                    // all commands should get rejected because they fail to pass the validity check
                    ctx.failNow(new IllegalStateException("should not have received command"));
                }, endpointConfig, MqttQoS.AT_MOST_ONCE))
                .compose(ok -> helper.createGenericKafkaSender().onSuccess(kafkaSenderRef::set).mapEmpty())
                .compose(ok -> kafkaAsyncErrorResponseConsumer)
                .onComplete(setup.succeeding(v -> ready.flag()));

        assertWithMessage("setup of adapter finished within %s seconds", IntegrationTestSupport.getTestSetupTimeout())
                .that(setup.awaitCompletion(IntegrationTestSupport.getTestSetupTimeout(), TimeUnit.SECONDS))
                .isTrue();
        if (setup.failed()) {
            ctx.failNow(setup.causeOfFailure());
            return;
        }

        final String commandTopic = new HonoTopic(HonoTopic.Type.COMMAND, tenantId).toString();

        LOGGER.debug("sending command message lacking subject and correlation ID - no failure response expected here");
        final Map<String, Object> properties1 = Map.of(
                MessageHelper.APP_PROPERTY_DEVICE_ID, deviceId,
                MessageHelper.SYS_PROPERTY_CONTENT_TYPE, MessageHelper.CONTENT_TYPE_OCTET_STREAM,
                KafkaRecordHelper.HEADER_RESPONSE_REQUIRED, true
        );
        kafkaSenderRef.get().sendAndWaitForOutcome(commandTopic, tenantId, deviceId, Buffer.buffer(), properties1)
                .onComplete(ctx.succeeding(ok -> {}));

        LOGGER.debug("sending command message lacking subject");
        final String correlationId = "1";
        final Map<String, Object> properties2 = Map.of(
                MessageHelper.SYS_PROPERTY_CORRELATION_ID, correlationId,
                MessageHelper.APP_PROPERTY_DEVICE_ID, deviceId,
                MessageHelper.SYS_PROPERTY_CONTENT_TYPE, MessageHelper.CONTENT_TYPE_OCTET_STREAM,
                KafkaRecordHelper.HEADER_RESPONSE_REQUIRED, true
        );
        kafkaSenderRef.get().sendAndWaitForOutcome(commandTopic, tenantId, deviceId, Buffer.buffer(), properties2)
                .onComplete(ctx.succeeding(ok -> {}));

        final long timeToWait = 2500;
        if (!expectedCommandResponses.await(timeToWait, TimeUnit.MILLISECONDS)) {
            LOGGER.info("Timeout of {} milliseconds reached, stop waiting for command response", timeToWait);
        }
        kafkaAsyncErrorResponseConsumer.result().close()
                .onComplete(ar -> {
                    if (expectedCommandResponses.getCount() == 0) {
                        ctx.completeNow();
                    } else {
                        ctx.failNow(new java.lang.IllegalStateException("did not receive command response"));
                    }
                });
    }

    /**
     * Verifies that the adapter forwards the <em>released</em> disposition back to the
     * application if the device hasn't sent an acknowledgement for the command message
     * published to the device.
     *
     * @param endpointConfig The endpoints to use for sending/receiving commands.
     * @param ctx The vert.x test context.
     * @throws InterruptedException if not all commands and responses are exchanged in time.
     */
    @ParameterizedTest(name = IntegrationTestSupport.PARAMETERIZED_TEST_NAME_PATTERN)
    @MethodSource("allCombinations")
    @Timeout(timeUnit = TimeUnit.SECONDS, value = 20)
    public void testSendCommandFailsForCommandNotAcknowledgedByDevice(
            final MqttCommandEndpointConfiguration endpointConfig,
            final VertxTestContext ctx) throws InterruptedException {

        final MqttQoS subscribeQos = MqttQoS.AT_LEAST_ONCE;

        final VertxTestContext setup = new VertxTestContext();
        final Checkpoint ready = setup.checkpoint(2);

        final String commandTargetDeviceId = endpointConfig.isSubscribeAsGateway()
                ? helper.setupGatewayDeviceBlocking(tenantId, deviceId, 5)
                : deviceId;

        final int totalNoOfCommandsToSend = 2;
        final CountDownLatch commandsFailed = new CountDownLatch(totalNoOfCommandsToSend);
        final AtomicInteger receivedMessagesCounter = new AtomicInteger(0);
        final AtomicInteger counter = new AtomicInteger();
        final Handler<MqttPublishMessage> commandConsumer = msg -> {
            LOGGER.trace("received command [{}] - no response sent here", msg.topicName());
            final ResourceIdentifier topic = ResourceIdentifier.fromString(msg.topicName());
            ctx.verify(() -> {
                endpointConfig.assertCommandPublishTopicStructure(topic, commandTargetDeviceId, false, "setValue");
            });
            receivedMessagesCounter.incrementAndGet();
        };
        final Function<Buffer, Future<Void>> commandSender = payload -> {
            counter.incrementAndGet();
            return helper.sendCommand(tenantId, commandTargetDeviceId, "setValue", "text/plain", payload,
                    helper.getSendCommandTimeout(counter.get() == 1))
                .mapEmpty();
        };

        helper.registry
                .addDeviceToTenant(tenantId, deviceId, password)
                .compose(ok -> connectToAdapter(IntegrationTestSupport.getUsername(deviceId, tenantId), password))
                // let the MqttClient skip sending the PubAck messages
                .compose(ok -> injectMqttClientPubAckBlocker(new AtomicBoolean(true)))
                .compose(ok -> createConsumer(tenantId, msg -> {
                    // expect empty notification with TTD -1
                    setup.verify(() -> assertThat(msg.getContentType()).isEqualTo(EventConstants.CONTENT_TYPE_EMPTY_NOTIFICATION));
                    final TimeUntilDisconnectNotification notification = msg.getTimeUntilDisconnectNotification().orElse(null);
                    LOGGER.info("received notification [{}]", notification);
                    setup.verify(() -> assertThat(notification).isNotNull());
                    if (notification.getTtd() == -1) {
                        ready.flag();
                    }
                }))
                .compose(conAck -> subscribeToCommands(commandTargetDeviceId, commandConsumer, endpointConfig, subscribeQos))
                .onComplete(setup.succeeding(ok -> ready.flag()));

        assertWithMessage("setup of adapter finished within %s seconds", IntegrationTestSupport.getTestSetupTimeout())
                .that(setup.awaitCompletion(IntegrationTestSupport.getTestSetupTimeout(), TimeUnit.SECONDS))
                .isTrue();
        if (setup.failed()) {
            ctx.failNow(setup.causeOfFailure());
            return;
        }

        final AtomicInteger commandsSent = new AtomicInteger(0);
        final AtomicLong lastReceivedTimestamp = new AtomicLong(0);
        final long start = System.currentTimeMillis();

        while (commandsSent.get() < totalNoOfCommandsToSend) {
            final CountDownLatch commandSent = new CountDownLatch(1);
            context.runOnContext(go -> {
                final Buffer msg = Buffer.buffer("value: " + commandsSent.getAndIncrement());
                commandSender.apply(msg).onComplete(sendAttempt -> {
                    if (sendAttempt.succeeded()) {
                        LOGGER.debug("sending command {} succeeded unexpectedly", commandsSent.get());
                    } else {
                        if (sendAttempt.cause() instanceof ServerErrorException
                                && ((ServerErrorException) sendAttempt.cause()).getErrorCode() == HttpURLConnection.HTTP_UNAVAILABLE
                                && !(sendAttempt.cause() instanceof SendMessageTimeoutException)) {
                            LOGGER.debug("sending command {} failed as expected: {}", commandsSent.get(),
                                    sendAttempt.cause().toString());
                            lastReceivedTimestamp.set(System.currentTimeMillis());
                            commandsFailed.countDown();
                            if (commandsFailed.getCount() % 20 == 0) {
                                LOGGER.info("commands failed as expected: {}",
                                        totalNoOfCommandsToSend - commandsFailed.getCount());
                            }
                        } else {
                            LOGGER.debug("sending command {} failed with an unexpected error", commandsSent.get(),
                                    sendAttempt.cause());
                        }
                    }
                    commandSent.countDown();
                });
            });

            commandSent.await();
        }

        // have to wait an extra MqttAdapterProperties.DEFAULT_COMMAND_ACK_TIMEOUT (100ms) for each command message
        final long timeToWait = totalNoOfCommandsToSend * 300;
        if (!commandsFailed.await(timeToWait, TimeUnit.MILLISECONDS)) {
            LOGGER.info("Timeout of {} milliseconds reached, stop waiting for commands", timeToWait);
        }
        assertThat(receivedMessagesCounter.get()).isEqualTo(totalNoOfCommandsToSend);
        final long commandsCompleted = totalNoOfCommandsToSend - commandsFailed.getCount();
        LOGGER.info("commands sent: {}, commands failed: {} after {} milliseconds",
                commandsSent.get(), commandsCompleted, lastReceivedTimestamp.get() - start);
        if (commandsCompleted == commandsSent.get()) {
            ctx.completeNow();
        } else {
            ctx.failNow(new java.lang.IllegalStateException("did not complete all commands sent"));
        }
    }

    /**
     * Verifies that the adapter doesn't send a positive acknowledgement when a gateway subscribes
     * for commands of a device that doesn't have the gateway in its via-gateways.
     *
     * @param endpointConfig The endpoints to use for sending/receiving commands.
     * @param ctx The vert.x test context.
     */
    @ParameterizedTest(name = IntegrationTestSupport.PARAMETERIZED_TEST_NAME_PATTERN)
    @MethodSource("gatewayForSingleDevice")
    @Timeout(timeUnit = TimeUnit.SECONDS, value = 20)
    public void testSubscribeFailsForDeviceNotInViaGateways(
            final MqttCommandEndpointConfiguration endpointConfig,
            final VertxTestContext ctx) {

        final String otherDeviceId = helper.getRandomDeviceId(tenantId);
        helper.registry
                .addDeviceToTenant(tenantId, deviceId, password)
                .compose(ok -> helper.registry.addDeviceToTenant(tenantId, otherDeviceId, password))
                .compose(ok -> connectToAdapter(IntegrationTestSupport.getUsername(deviceId, tenantId), password))
                .compose(conAck -> {
                    final Promise<Void> result = Promise.promise();
                    context.runOnContext(go -> {
                        mqttClient.subscribeCompletionHandler(subAckMsg -> {
                            ctx.verify(() -> {
                                assertThat(subAckMsg.grantedQoSLevels().size()).isEqualTo(1);
                                assertThat(subAckMsg.grantedQoSLevels().get(0)).isEqualTo(MqttQoS.FAILURE.value());
                            });
                            result.complete();
                        });
                        mqttClient.subscribe(endpointConfig.getCommandTopicFilter(otherDeviceId), MqttQoS.AT_LEAST_ONCE.value());
                    });
                    return result.future();
                })
                .onComplete(ctx.succeedingThenComplete());

    }

    private Future<Void> injectMqttClientPubAckBlocker(final AtomicBoolean outboundPubAckBlocked) {
        // The vert.x MqttClient automatically sends a PubAck after having received a Qos 1 Publish message,
        // as of now, there is no configuration option to prevent this (see https://github.com/vert-x3/vertx-mqtt/issues/120).
        // Therefore the underlying NetSocket pipeline is used here to filter out the outbound PubAck messages.
        try {
            final Method connectionMethod = MqttClientImpl.class.getDeclaredMethod("connection");
            connectionMethod.setAccessible(true);
            final NetSocketInternal connection = (NetSocketInternal) connectionMethod.invoke(mqttClient);
            connection.channelHandlerContext().pipeline().addBefore("handler", "OutboundPubAckBlocker",
                    new ChannelOutboundHandlerAdapter() {
                @Override
                public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise)
                        throws Exception {
                    if (outboundPubAckBlocked.get() && msg instanceof io.netty.handler.codec.mqtt.MqttPubAckMessage) {
                        LOGGER.debug("suppressing PubAck, message id: {}", ((MqttPubAckMessage) msg).variableHeader().messageId());
                    } else {
                        super.write(ctx, msg, promise);
                    }
                }
            });
            return Future.succeededFuture();
        } catch (final Exception e) {
            LOGGER.error("failed to inject PubAck blocking handler");
            return Future.failedFuture(new Exception("failed to inject PubAck blocking handler", e));
        }
    }
}
