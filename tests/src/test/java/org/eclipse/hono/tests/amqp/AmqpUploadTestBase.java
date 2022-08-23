/*******************************************************************************
 * Copyright (c) 2016, 2022 Contributors to the Eclipse Foundation
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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.qpid.proton.amqp.Binary;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.UnsignedLong;
import org.apache.qpid.proton.amqp.messaging.Accepted;
import org.apache.qpid.proton.amqp.messaging.Data;
import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.apache.qpid.proton.amqp.transport.LinkError;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.application.client.DownstreamMessage;
import org.eclipse.hono.application.client.MessageConsumer;
import org.eclipse.hono.application.client.MessageContext;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.amqp.connection.AmqpErrorException;
import org.eclipse.hono.client.amqp.connection.AmqpUtils;
import org.eclipse.hono.service.management.device.Device;
import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.tests.DownstreamMessageAssertions;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.tests.Tenants;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.QoS;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.SelfSignedCertificate;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxTestContext;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonSender;

/**
 * Base class for the AMQP adapter integration tests.
 */
public abstract class AmqpUploadTestBase extends AmqpAdapterTestBase {

    /**
     * The default password of devices.
     */
    protected static final String DEVICE_PASSWORD = "device-password";

    private static final long DEFAULT_TEST_TIMEOUT = 15000; // ms

    /**
     * Creates a test specific message consumer.
     *
     * @param tenantId        The tenant to create the consumer for.
     * @param messageConsumer The handler to invoke for every message received.
     * @return A future succeeding with the created consumer.
     */
    protected abstract Future<MessageConsumer> createConsumer(String tenantId, Handler<DownstreamMessage<? extends MessageContext>> messageConsumer);

    /**
     * Gets the endpoint name.
     *
     * @return The name of the endpoint.
     */
    protected abstract String getEndpointName();

    /**
     * Prepares the configuration to register with the tenant.
     *
     * @param tenantConfig The configuration.
     */
    protected void prepareTenantConfig(final Tenant tenantConfig) {
    }

    /**
     * Gets the generic quality-of-service level corresponding to
     * AMQP delivery semantics.
     *
     * @param qos The AMQP delivery semantics.
     * @return The quality-of-service level.
     */
    protected static QoS getQoS(final ProtonQoS qos) {
        switch (qos) {
        case AT_MOST_ONCE: return QoS.AT_MOST_ONCE;
        default: return QoS.AT_LEAST_ONCE;
        }
    }

    /**
     * Perform additional checks on a received message.
     * <p>
     * This default implementation does nothing. Subclasses should override this method to implement
     * reasonable checks.
     *
     * @param msg The message to perform checks on.
     * @throws RuntimeException if any of the checks fail.
     */
    protected void assertAdditionalMessageProperties(final DownstreamMessage<? extends MessageContext> msg) {
        // empty
    }

    /**
     * Verifies that the adapter forwards an empty message with a custom content type other
     * than {@value EventConstants#CONTENT_TYPE_EMPTY_NOTIFICATION} to downstream consumers.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    @Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
    public void testUploadEmptyMessageWithCustomContentTypeSucceeds(final VertxTestContext ctx) {

        final String tenantId = helper.getRandomTenantId();
        final String deviceId = helper.getRandomDeviceId(tenantId);
        final Tenant tenantConfig = new Tenant();
        prepareTenantConfig(tenantConfig);

        final String customContentType = "application/custom";

        setupProtocolAdapter(tenantId, tenantConfig, deviceId, ProtonQoS.AT_LEAST_ONCE)
            .compose(s -> {
                this.sender = s;
                return createConsumer(tenantId, msg -> {
                    log.trace("received {}", msg);
                    ctx.verify(() -> {
                        DownstreamMessageAssertions.assertTelemetryApiProperties(msg);
                        DownstreamMessageAssertions.assertMessageContainsAdapterAndAddress(msg);
                        assertThat(msg.getContentType()).isEqualTo(customContentType);
                        assertThat(msg.getPayload()).isNull();
                        assertAdditionalMessageProperties(msg);
                    });
                    ctx.completeNow();
                });
            })
            .onSuccess(consumer -> {

                final Handler<ProtonSender> sendMsgHandler = replenishedSender -> {
                    replenishedSender.sendQueueDrainHandler(null);
                    final var msg = ProtonHelper.message();
                    msg.setAddress(getEndpointName());
                    msg.setContentType(customContentType);
                    replenishedSender.send(msg, delivery -> {
                        if (!Accepted.class.isInstance(delivery.getRemoteState())) {
                            ctx.failNow(AmqpErrorException.from(delivery.getRemoteState()));
                        }
                    });
                };

                context.runOnContext(go -> {
                    if (sender.getCredit() <= 0) {
                        log.trace("waiting for credit ...");
                        sender.sendQueueDrainHandler(sendMsgHandler);
                    } else {
                        sendMsgHandler.handle(sender);
                    }
                });
            });
    }

    /**
     * Verifies that the adapter rejects empty messages that have no content type set.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    @Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
    public void testUploadEmptyMessageWithoutContentTypeFails(final VertxTestContext ctx) {

        final String tenantId = helper.getRandomTenantId();
        final String deviceId = helper.getRandomDeviceId(tenantId);
        final Tenant tenantConfig = new Tenant();
        prepareTenantConfig(tenantConfig);

        setupProtocolAdapter(tenantId, tenantConfig, deviceId, ProtonQoS.AT_LEAST_ONCE)
            .compose(s -> {
                this.sender = s;
                return createConsumer(tenantId, msg -> {
                    log.trace("received {}", msg);
                    ctx.failNow("downstream consumer should not have received message");
                });
            })
            .onSuccess(consumer -> {

                final Handler<ProtonSender> sendMsgHandler = replenishedSender -> {
                    replenishedSender.sendQueueDrainHandler(null);
                    final var msg = ProtonHelper.message();
                    msg.setAddress(getEndpointName());
                    replenishedSender.send(msg, delivery -> {
                        if (delivery.getRemoteState() instanceof Rejected rejected) {
                            ctx.verify(() -> {
                                assertThat(rejected.getError().getCondition()).isEqualTo(AmqpUtils.AMQP_BAD_REQUEST);
                            });
                            ctx.completeNow();
                        } else {
                            ctx.failNow("message should have been rejected by AMQP adapter");
                        }
                    });
                };

                context.runOnContext(go -> {
                    if (sender.getCredit() <= 0) {
                        log.trace("waiting for credit ...");
                        sender.sendQueueDrainHandler(sendMsgHandler);
                    } else {
                        sendMsgHandler.handle(sender);
                    }
                });
            });

    }

    /**
     * Verifies that the adapter forwards a non-empty message without any content type set in the request
     * to downstream consumers using the {@value MessageHelper#CONTENT_TYPE_OCTET_STREAM} content type.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    @Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
    public void testUploadNonEmptyMessageWithoutContentTypeSucceeds(final VertxTestContext ctx) {

        final String tenantId = helper.getRandomTenantId();
        final String deviceId = helper.getRandomDeviceId(tenantId);
        final Tenant tenantConfig = new Tenant();
        prepareTenantConfig(tenantConfig);

        setupProtocolAdapter(tenantId, tenantConfig, deviceId, ProtonQoS.AT_LEAST_ONCE)
            .compose(s -> {
                this.sender = s;
                return createConsumer(tenantId, msg -> {
                    log.trace("received {}", msg);
                    ctx.verify(() -> {
                        DownstreamMessageAssertions.assertTelemetryApiProperties(msg);
                        DownstreamMessageAssertions.assertMessageContainsAdapterAndAddress(msg);
                        assertThat(msg.getContentType()).isEqualTo(MessageHelper.CONTENT_TYPE_OCTET_STREAM);
                        assertThat(msg.getPayload().length()).isGreaterThan(0);
                        assertAdditionalMessageProperties(msg);
                    });
                    ctx.completeNow();
                });
            })
            .onSuccess(consumer -> {

                final Handler<ProtonSender> sendMsgHandler = replenishedSender -> {
                    replenishedSender.sendQueueDrainHandler(null);
                    final var msg = ProtonHelper.message(getEndpointName(), "some payload");
                    replenishedSender.send(msg, delivery -> {
                        if (!Accepted.class.isInstance(delivery.getRemoteState())) {
                            ctx.failNow(AmqpErrorException.from(delivery.getRemoteState()));
                        }
                    });
                };

                context.runOnContext(go -> {
                    if (sender.getCredit() <= 0) {
                        log.trace("waiting for credit ...");
                        sender.sendQueueDrainHandler(sendMsgHandler);
                    } else {
                        sendMsgHandler.handle(sender);
                    }
                });
            });

    }

    /**
     * Verifies that the adapter rejects non-empty messages that are marked as empty notifications.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    @Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
    public void testUploadNonEmptyMessageMarkedAsEmptyNotificationFails(final VertxTestContext ctx) {

        final String tenantId = helper.getRandomTenantId();
        final String deviceId = helper.getRandomDeviceId(tenantId);
        final Tenant tenantConfig = new Tenant();
        prepareTenantConfig(tenantConfig);

        setupProtocolAdapter(tenantId, tenantConfig, deviceId, ProtonQoS.AT_LEAST_ONCE)
            .compose(s -> {
                this.sender = s;
                ctx.verify(() -> {
                    final UnsignedLong maxMessageSize = s.getRemoteMaxMessageSize();
                    assertWithMessage("max-message-size included in adapter's attach frame")
                            .that(maxMessageSize).isNotNull();
                    assertWithMessage("max-message-size").that(maxMessageSize.longValue()).isGreaterThan(0);
                });
                return createConsumer(tenantId, msg -> {
                    log.trace("received {}", msg);
                    ctx.failNow("downstream consumer should not have received message");
                });
            })
            .onSuccess(consumer -> {

                final Handler<ProtonSender> sendMsgHandler = replenishedSender -> {
                    replenishedSender.sendQueueDrainHandler(null);
                    final var msg = ProtonHelper.message(getEndpointName(), "some payload");
                    msg.setContentType(EventConstants.CONTENT_TYPE_EMPTY_NOTIFICATION);
                    replenishedSender.send(msg, delivery -> {
                        if (delivery.getRemoteState() instanceof Rejected rejected) {
                            ctx.verify(() -> {
                                assertThat(rejected.getError().getCondition()).isEqualTo(AmqpUtils.AMQP_BAD_REQUEST);
                            });
                            ctx.completeNow();
                        } else {
                            ctx.failNow("message should have been rejected by AMQP adapter");
                        }
                    });
                };

                context.runOnContext(go -> {
                    if (sender.getCredit() <= 0) {
                        log.trace("waiting for credit ...");
                        sender.sendQueueDrainHandler(sendMsgHandler);
                    } else {
                        sendMsgHandler.handle(sender);
                    }
                });
            });

    }

    /**
     * Verifies that the adapter closes the link when a device sends a message containing a payload which
     * exceeds the configured max payload size.
     *
     * @param ctx The Vert.x test context.
     */
    @Test
    @Timeout(timeUnit = TimeUnit.SECONDS, value = 10)
    public void testAdapterClosesLinkOnMessageExceedingMaxPayloadSize(final VertxTestContext ctx) {

        final String tenantId = helper.getRandomTenantId();
        final String deviceId = helper.getRandomDeviceId(tenantId);

        createConsumer(
                tenantId,
                msg -> ctx.failNow(new AssertionError("should not have received message")))
            .compose(consumer -> setupProtocolAdapter(tenantId, new Tenant(), deviceId, ProtonQoS.AT_LEAST_ONCE))
            .onComplete(ctx.succeeding(s -> {

                s.detachHandler(remoteDetach -> {
                    ctx.verify(() -> {
                        final ErrorCondition errorCondition = s.getRemoteCondition();
                        assertThat(remoteDetach.succeeded()).isFalse();
                        assertThat(errorCondition).isNotNull();
                        assertThat((Comparable<Symbol>) errorCondition.getCondition()).isEqualTo(LinkError.MESSAGE_SIZE_EXCEEDED);
                    });
                    log.info("AMQP adapter detached link as expected");
                    s.close();
                    ctx.completeNow();
                });

                final UnsignedLong maxMessageSize = s.getRemoteMaxMessageSize();
                log.info("AMQP adapter uses max-message-size {}", maxMessageSize);

                ctx.verify(() -> {
                    assertWithMessage("max-message-size included in adapter's attach frame")
                            .that(maxMessageSize).isNotNull();
                    assertWithMessage("max-message-size").that(maxMessageSize.longValue()).isGreaterThan(0);
                });

                final Message msg = ProtonHelper.message();
                msg.setContentType("opaque/binary");
                msg.setAddress(getEndpointName());
                msg.setBody(new Data(new Binary(IntegrationTestSupport.getPayload(maxMessageSize.intValue()))));

                context.runOnContext(go -> {
                    log.debug("sending message");
                    s.send(msg);
                });
            }));
    }

    /**
     * Verifies that an edge device is auto-provisioned if it connects via a gateway equipped with the corresponding
     * authority.
     *
     * @param ctx The Vert.x test context.
     */
    @Test
    @Timeout(timeUnit = TimeUnit.SECONDS, value = 15)
    public void testAutoProvisioningViaGateway(final VertxTestContext ctx) {

        final String tenantId = helper.getRandomTenantId();
        final String gatewayId = helper.getRandomDeviceId(tenantId);
        final Device gateway = new Device()
                .setAuthorities(Collections.singleton(RegistryManagementConstants.AUTHORITY_AUTO_PROVISIONING_ENABLED));

        final String username = IntegrationTestSupport.getUsername(gatewayId, tenantId);
        final String edgeDeviceId = helper.getRandomDeviceId(tenantId);
        final Promise<Void> provisioningNotificationReceived = Promise.promise();

        helper.createAutoProvisioningMessageConsumers(ctx, provisioningNotificationReceived, tenantId, edgeDeviceId)
                .compose(ok -> helper.registry.addDeviceForTenant(tenantId, new Tenant(), gatewayId, gateway, DEVICE_PASSWORD))
                .compose(ok -> connectToAdapter(username, DEVICE_PASSWORD))
                .compose(con -> createProducer(null, ProtonQoS.AT_LEAST_ONCE))
                .compose(sender -> {
                    final Message msg = ProtonHelper.message("apFoobar");
                    msg.setContentType("text/plain");
                    msg.setAddress(String.format("%s/%s/%s", getEndpointName(), tenantId, edgeDeviceId));

                    final Promise<Void> result = Promise.promise();
                    sender.send(msg, delivery -> {
                        ctx.verify(() -> assertThat(delivery.getRemoteState()).isInstanceOf(Accepted.class));
                        result.complete();
                    });

                    return result.future();
                })
                .compose(ok -> provisioningNotificationReceived.future())
                .compose(ok -> helper.registry.getRegistrationInfo(tenantId, edgeDeviceId))
                .onComplete(ctx.succeeding(registrationResult -> {
                    ctx.verify(() -> {
                        final var info = registrationResult.bodyAsJsonObject();
                        IntegrationTestSupport.assertDeviceStatusProperties(
                                info.getJsonObject(RegistryManagementConstants.FIELD_STATUS),
                                true);
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that the AMQP Adapter rejects (closes) AMQP links that contain a target address.
     *
     * @param context The Vert.x test context.
     */
    @Test
    @Timeout(timeUnit = TimeUnit.SECONDS, value = 10)
    public void testAnonymousRelayRequired(final VertxTestContext context) {

        final String tenantId = helper.getRandomTenantId();
        final String deviceId = helper.getRandomDeviceId(tenantId);
        final String username = IntegrationTestSupport.getUsername(deviceId, tenantId);
        final String targetAddress = String.format("%s/%s/%s", getEndpointName(), tenantId, deviceId);

        final Tenant tenant = new Tenant();
        helper.registry
            .addDeviceForTenant(tenantId, tenant, deviceId, DEVICE_PASSWORD)
            // connect and create sender (with a valid target address)
            .compose(ok -> connectToAdapter(username, DEVICE_PASSWORD))
            .compose(con -> {
                this.connection = con;
                return createProducer(targetAddress, ProtonQoS.AT_LEAST_ONCE);
            })
            .onComplete(context.failing(t -> {
                log.info("failed to open sender", t);
                context.completeNow();
            }));
    }

    /**
     * Verifies that a number of messages published through the AMQP adapter can be successfully consumed by
     * applications connected to the AMQP messaging network.
     *
     * @param senderQos The delivery semantics to use for the device.
     * @throws InterruptedException if test is interrupted while running.
     */
    @ParameterizedTest(name = IntegrationTestSupport.PARAMETERIZED_TEST_NAME_PATTERN)
    @MethodSource("senderQoSTypes")
    public void testUploadMessagesUsingSaslPlain(final ProtonQoS senderQos) throws InterruptedException {

        final String tenantId = helper.getRandomTenantId();
        final String deviceId = helper.getRandomDeviceId(tenantId);
        final Tenant tenantConfig = new Tenant();
        prepareTenantConfig(tenantConfig);

        final VertxTestContext setup = new VertxTestContext();
        setupProtocolAdapter(tenantId, tenantConfig, deviceId, senderQos)
            .onComplete(setup.succeeding(s -> {
                setup.verify(() -> {
                    final UnsignedLong maxMessageSize = s.getRemoteMaxMessageSize();
                    assertWithMessage("max-message-size included in adapter's attach frame")
                            .that(maxMessageSize).isNotNull();
                    assertWithMessage("max-message-size").that(maxMessageSize.longValue()).isGreaterThan(0);
                });
                sender = s;
                setup.completeNow();
            }));

        assertThat(setup.awaitCompletion(IntegrationTestSupport.getTestSetupTimeout(), TimeUnit.SECONDS)).isTrue();
        assertWithMessage("adapter connection failure occurred")
                .that(setup.failed())
                .isFalse();

        testUploadMessages(tenantId, senderQos, this::getEndpointName);
    }

    /**
     * Verifies that a number of messages uploaded to the AMQP adapter using client certificate
     * based authentication can be successfully consumed via the AMQP Messaging Network.
     *
     * @param senderQos The delivery semantics used by the device for uploading messages.
     * @throws InterruptedException if test execution is interrupted.
     */
    @ParameterizedTest(name = IntegrationTestSupport.PARAMETERIZED_TEST_NAME_PATTERN)
    @MethodSource("senderQoSTypes")
    public void testUploadMessagesUsingSaslExternal(final ProtonQoS senderQos) throws InterruptedException {

        final String tenantId = helper.getRandomTenantId();
        final String deviceId = helper.getRandomDeviceId(tenantId);

        final SelfSignedCertificate deviceCert = SelfSignedCertificate.create(deviceId + ".iot.eclipse.org");

        final VertxTestContext setup = new VertxTestContext();

        helper.getCertificate(deviceCert.certificatePath())
            .compose(cert -> {
                final var tenant = Tenants.createTenantForTrustAnchor(cert);
                prepareTenantConfig(tenant);
                return helper.registry.addDeviceForTenant(tenantId, tenant, deviceId, cert);
            })
            .compose(ok -> connectToAdapter(deviceCert))
            .compose(con -> createProducer(null, senderQos))
            .onComplete(setup.succeeding(s -> {
                setup.verify(() -> {
                    final UnsignedLong maxMessageSize = s.getRemoteMaxMessageSize();
                    assertWithMessage("max-message-size included in adapter's attach frame")
                            .that(maxMessageSize).isNotNull();
                    assertWithMessage("max-message-size").that(maxMessageSize.longValue()).isGreaterThan(0);
                });
                sender = s;
                setup.completeNow();
            }));

        assertThat(setup.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
        assertWithMessage("adapter connection failure occurred")
                .that(setup.failed())
                .isFalse();

        testUploadMessages(tenantId, senderQos, this::getEndpointName);
    }

    /**
     * Verifies that a number of messages uploaded to the AMQP adapter via an authenticated gateway can be
     * successfully consumed via the messaging infrastructure.
     *
     * @param senderQos The delivery semantics to use for the device.
     * @throws InterruptedException if the fixture cannot be created.
     */
    @ParameterizedTest(name = IntegrationTestSupport.PARAMETERIZED_TEST_NAME_PATTERN)
    @MethodSource("senderQoSTypes")
    public void testUploadMessagesViaGateway(final ProtonQoS senderQos) throws InterruptedException {

        final String tenantId = helper.getRandomTenantId();
        final Tenant tenantConfig = new Tenant();
        prepareTenantConfig(tenantConfig);
        final String gatewayId = helper.getRandomDeviceId(tenantId);
        final String deviceId = helper.getRandomDeviceId(tenantId);
        final Device device = new Device().setVia(List.of(gatewayId));

        final String gwUsername = IntegrationTestSupport.getUsername(gatewayId, tenantId);
        final String gwPassword = "secret";

        final VertxTestContext setup = new VertxTestContext();
        helper.registry.addDeviceForTenant(tenantId, tenantConfig, gatewayId, gwPassword)
            .compose(ok -> helper.registry.registerDevice(tenantId, deviceId, device))
            .compose(ok -> connectToAdapter(gwUsername, gwPassword))
            .compose(con -> createProducer(null, senderQos))
            .onSuccess(s -> sender = s)
            .onComplete(setup.succeedingThenComplete());

        assertThat(setup.awaitCompletion(IntegrationTestSupport.getTestSetupTimeout(), TimeUnit.SECONDS)).isTrue();
        assertWithMessage("adapter connection failure occurred")
                .that(setup.failed())
                .isFalse();

        final AtomicInteger count = new AtomicInteger(0);
        final String[] addresses = new String[] {
                "%s/%s/%s".formatted(getEndpointName(), tenantId, deviceId),
                "%s//%s".formatted(getEndpointName(), deviceId)
                };
        testUploadMessages(tenantId, senderQos, () -> addresses[count.getAndIncrement() % 2]);
    }

    //------------------------------------------< private methods >---

    private void testUploadMessages(
            final String tenantId,
            final ProtonQoS senderQoS,
            final Supplier<String> messageAddress) throws InterruptedException {

        final VertxTestContext messageSending = new VertxTestContext();

        final Function<Handler<Void>, Future<Void>> receiver = callback -> {
            return createConsumer(tenantId, msg -> {
                if (log.isTraceEnabled()) {
                    log.trace("received message [{}]: {}",
                            msg.getContentType(), msg.getPayload().toString());
                }
                messageSending.verify(() -> {
                    DownstreamMessageAssertions.assertTelemetryApiProperties(msg);
                    DownstreamMessageAssertions.assertMessageContainsAdapterAndAddress(msg);
                    assertThat(msg.getQos()).isEqualTo(AmqpUploadTestBase.getQoS(senderQoS));
                    assertAdditionalMessageProperties(msg);
                    callback.handle(null);
                });
            }).mapEmpty();
        };

        doUploadMessages(messageSending, receiver, senderQoS, messageAddress);
    }

    private void doUploadMessages(
            final VertxTestContext messageSending,
            final Function<Handler<Void>, Future<Void>> receiverFactory,
            final ProtonQoS senderQos,
            final Supplier<String> messageAddress) throws InterruptedException {

        doUploadMessages(messageSending, receiverFactory, payload -> {

            final Message msg = ProtonHelper.message();
            AmqpUtils.setPayload(msg, "opaque/binary", payload);
            msg.setAddress(messageAddress.get());
            final Promise<Void> sendingComplete = Promise.promise();
            final Handler<ProtonSender> sendMsgHandler = replenishedSender -> {
                replenishedSender.sendQueueDrainHandler(null);
                switch (senderQos) {
                case AT_LEAST_ONCE:
                    replenishedSender.send(msg, delivery -> {
                        if (Accepted.class.isInstance(delivery.getRemoteState())) {
                            sendingComplete.complete();
                        } else {
                            sendingComplete.fail(AmqpErrorException.from(delivery.getRemoteState()));
                        }
                    });
                    break;
                case AT_MOST_ONCE:
                    replenishedSender.send(msg);
                    sendingComplete.complete();
                    break;
                }
            };
            context.runOnContext(go -> {
                if (sender.getCredit() <= 0) {
                    log.trace("wait for credit ...");
                    sender.sendQueueDrainHandler(sendMsgHandler);
                } else {
                    sendMsgHandler.handle(sender);
                }
            });
            return sendingComplete.future();
        });

    }
    /**
     * Upload a number of messages to Hono's Telemetry/Event APIs.
     *
     * @param messageSending The Vert.x test context to use for tracking the messages being received.
     * @param receiverFactory The factory to use for creating the receiver for consuming
     *                        messages from the messaging network.
     * @param sender The sender for sending messaging to the Hono server.
     * @throws InterruptedException if test execution is interrupted.
     */
    protected void doUploadMessages(
            final VertxTestContext messageSending,
            final Function<Handler<Void>, Future<Void>> receiverFactory,
            final Function<Buffer, Future<Void>> sender) throws InterruptedException {

        final AtomicInteger messagesReceived = new AtomicInteger(0);

        final VertxTestContext receiverCreation = new VertxTestContext();

        receiverFactory.apply(msgReceived -> {
            final int msgNo = messagesReceived.incrementAndGet();
            if (msgNo % 200 == 0) {
                log.info("messages received: {}", msgNo);
            }
        })
        .onComplete(receiverCreation.succeedingThenComplete());

        assertThat(receiverCreation.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
        assertThat(receiverCreation.failed()).isFalse();

        final AtomicInteger messagesSent = new AtomicInteger(0);

        new Thread(() -> {

            while (messagesReceived.get() < IntegrationTestSupport.MSG_COUNT) {

                final int msgNo = messagesSent.incrementAndGet();
                final String payload = "temp: " + msgNo;

                final CountDownLatch msgSent = new CountDownLatch(1);

                sender.apply(Buffer.buffer(payload)).onComplete(sendAttempt -> {
                    if (sendAttempt.failed()) {
                        if (sendAttempt.cause() instanceof ServerErrorException &&
                                ((ServerErrorException) sendAttempt.cause()).getErrorCode() == HttpURLConnection.HTTP_UNAVAILABLE) {
                            // no credit available
                            // do not expect this message to be received
                            log.info("skipping message no {}, no credit", msgNo);
                            messagesReceived.incrementAndGet();
                        } else {
                            log.info("error sending message no {}", msgNo, sendAttempt.cause());
                        }
                    }
                    msgSent.countDown();
                });
                try {
                    msgSent.await();
                    log.trace("sent message no {}", msgNo);
                    if (msgNo % 200 == 0) {
                        log.info("messages sent: {}", msgNo);
                    }
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            messageSending.completeNow();
        }, "message sender")
        .start();


        final long timeToWait = Math.max(DEFAULT_TEST_TIMEOUT, Math.round(IntegrationTestSupport.MSG_COUNT * 1.2));
        assertThat(messageSending.awaitCompletion(timeToWait, TimeUnit.MILLISECONDS)).isTrue();
        if (messageSending.failed()) {
            Assertions.fail("failed to upload messages", messageSending.causeOfFailure());
        }
        assertWithMessage("number of received messages")
                .that(messagesReceived.get())
                .isAtLeast(IntegrationTestSupport.MSG_COUNT);
    }

    /**
     * Sets up the protocol adapter by doing the following:
     * <ul>
     * <li>Add a device (with credentials) for the tenant identified by the given tenantId.</li>
     * <li>Create an AMQP 1.0 client and authenticate it to the protocol adapter with username: {@code [device-id@tenantId]}.</li>
     * <li>After a successful connection, create a producer/sender for sending messages to the protocol adapter.</li>
     * </ul>
     *
     * @param tenantId The tenant to register with the device registry.
     * @param tenantConfig The tenant's configuration data.
     * @param deviceId The device to add to the tenant identified by tenantId.
     * @param senderQos The delivery semantics used by the device for uploading messages.
     *
     * @return A future succeeding with the created sender.
     */
    protected Future<ProtonSender> setupProtocolAdapter(
            final String tenantId,
            final Tenant tenantConfig,
            final String deviceId,
            final ProtonQoS senderQos) {

        final String username = IntegrationTestSupport.getUsername(deviceId, tenantId);

        return helper.registry
                .addDeviceForTenant(tenantId, tenantConfig, deviceId, DEVICE_PASSWORD)
                .compose(ok -> connectToAdapter(username, DEVICE_PASSWORD))
                .compose(con -> createProducer(null, senderQos))
                .recover(t -> {
                    log.error("error setting up AMQP protocol adapter", t);
                    return Future.failedFuture(t);
                });
    }
}
