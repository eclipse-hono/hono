/*******************************************************************************
 * Copyright (c) 2016, 2020 Contributors to the Eclipse Foundation
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
import static org.junit.jupiter.api.Assumptions.assumingThat;

import java.net.HttpURLConnection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

import org.apache.qpid.proton.amqp.Binary;
import org.apache.qpid.proton.amqp.UnsignedLong;
import org.apache.qpid.proton.amqp.messaging.Accepted;
import org.apache.qpid.proton.amqp.messaging.Data;
import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.apache.qpid.proton.amqp.transport.LinkError;
import org.apache.qpid.proton.message.Message;
import org.assertj.core.api.Assertions;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.tests.Tenants;
import org.eclipse.hono.util.Adapter;
import org.eclipse.hono.util.AmqpErrorException;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.MessageHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.SelfSignedCertificate;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxTestContext;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonSender;

/**
 * Base class for the AMQP adapter integration tests.
 */
public abstract class AmqpUploadTestBase extends AmqpAdapterTestBase {

    private static final long DEFAULT_TEST_TIMEOUT = 15000; // ms
    private static final String DEVICE_PASSWORD = "device-password";

    private ProtonSender sender;
    private MessageConsumer consumer;

    private void assertMessageProperties(final VertxTestContext ctx, final Message msg) {
        ctx.verify(() -> {
            assertThat(MessageHelper.getDeviceId(msg)).isNotNull();
            assertThat(MessageHelper.getTenantIdAnnotation(msg)).isNotNull();
            assertThat(MessageHelper.getDeviceIdAnnotation(msg)).isNotNull();
            assertThat(MessageHelper.getRegistrationAssertion(msg)).isNull();
            assertThat(msg.getCreationTime()).isGreaterThan(0);
        });
        assertAdditionalMessageProperties(ctx, msg);
    }

    /**
     * Perform additional checks on a received message.
     * <p>
     * This default implementation does nothing. Subclasses should override this method to implement
     * reasonable checks.
     *
     * @param ctx The test context.
     * @param msg The message to perform checks on.
     */
    protected void assertAdditionalMessageProperties(final VertxTestContext ctx, final Message msg) {
        // empty
    }

    /**
     * Creates a test specific message consumer.
     *
     * @param tenantId        The tenant to create the consumer for.
     * @param messageConsumer The handler to invoke for every message received.
     * @return A future succeeding with the created consumer.
     */
    protected abstract Future<MessageConsumer> createConsumer(String tenantId, Consumer<Message> messageConsumer);

    /**
     * Gets the endpoint name.
     *
     * @return The name of the endpoint.
     */
    protected abstract String getEndpointName();

    /**
     * Delete all random tenants and devices generated during the execution of a test case.
     *
     * @param context The Vert.x test context.
     */
    @AfterEach
    public void after(final VertxTestContext context) {
        helper.deleteObjects(context);
    }

    /**
     * Disconnect the AMQP 1.0 client connected to the AMQP Adapter and close senders and consumers.
     *
     * @param context The Vert.x test context.
     */
    @AfterEach
    public void disconnectAdapter(final VertxTestContext context) {
        close(context);
    }

    /**
     * Checks if the AMQP adapter supports enforcing the max-message-size announced
     * in its attach frames.
     *
     * @return {@code true} if system property <em>vertx-proton.issue57.fixed</em> is set to <em>true</em>
     *         or is not defined at all.
     */
    protected final boolean adapterEnforcesMessageSizeLimit() {
        return System.getProperty("vertx-proton.issue57.fixed", "true").equals("true");
    }

    /**
     * Verifies that a message containing a payload which has the <em>emtpy notification</em>
     * content type is rejected by the adapter.
     *
     * @param context The Vert.x context for running asynchronous tests.
     * @throws InterruptedException if test is interrupted while running.
     */
    @Test
    @Timeout(timeUnit = TimeUnit.SECONDS, value = 10)
    public void testAdapterRejectsBadInboundMessage(final VertxTestContext context) throws InterruptedException {

        final String tenantId = helper.getRandomTenantId();
        final String deviceId = helper.getRandomDeviceId(tenantId);

        final VertxTestContext setup = new VertxTestContext();

        setupProtocolAdapter(tenantId, deviceId, false)
            .map(s -> {
                setup.verify(() -> {
                    final UnsignedLong maxMessageSize = s.getRemoteMaxMessageSize();
                    assertThat(maxMessageSize).as("check adapter's attach frame includes max-message-size").isNotNull();
                    assertThat(maxMessageSize.longValue()).as("check message size is limited").isGreaterThan(0);
                });
                sender = s;
                return s;
            })
            .onComplete(setup.completing());

        assertThat(setup.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
        if (setup.failed()) {
            context.failNow(setup.causeOfFailure());
            return;
        }

        final Message msg = ProtonHelper.message("some payload");
        msg.setContentType(EventConstants.CONTENT_TYPE_EMPTY_NOTIFICATION);
        msg.setAddress(getEndpointName());
        sender.send(msg, delivery -> {

            context.verify(() -> {
                assertThat(delivery.getRemoteState()).isInstanceOf(Rejected.class);
                final Rejected rejected = (Rejected) delivery.getRemoteState();
                final ErrorCondition error = rejected.getError();
                assertThat((Object) error.getCondition()).isEqualTo(Constants.AMQP_BAD_REQUEST);
            });
            context.completeNow();
        });

    }

    /**
     * Verifies that a message containing a payload which exceeds the configured max payload size is rejected by the adapter.
     *
     * @param context The Vert.x context for running asynchronous tests.
     * @throws InterruptedException if test is interrupted while running.
     */
    @Disabled
    @Test
    @Timeout(timeUnit = TimeUnit.SECONDS, value = 10)
    public void testAdapterRejectsMessageExceedingMaxPayloadSize(final VertxTestContext context) throws InterruptedException {

        final String tenantId = helper.getRandomTenantId();
        final String deviceId = helper.getRandomDeviceId(tenantId);

        final VertxTestContext setup = new VertxTestContext();
        final AtomicInteger maxPayloadSize = new AtomicInteger();

        createConsumer(tenantId, msg -> {
            context.failNow(new AssertionError("should not have received message"));
        })
        .compose(consumer -> setupProtocolAdapter(tenantId, deviceId, false))
            .map(s -> {
                setup.verify(() -> {
                    final UnsignedLong maxMessageSize = s.getRemoteMaxMessageSize();
                    assertThat(maxMessageSize).as("check adapter's attach frame includes max-message-size").isNotNull();
                    assertThat(maxMessageSize.longValue()).as("check message size is limited").isGreaterThan(0);
                    maxPayloadSize.set(maxMessageSize.intValue());
                });
                sender = s;
                return s;
            })
            .onComplete(setup.completing());

        assertThat(setup.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
        if (setup.failed()) {
            context.failNow(setup.causeOfFailure());
            return;
        }

        final Message msg = ProtonHelper.message();
        msg.setContentType("opaque/binary");
        msg.setAddress(getEndpointName());
        msg.setBody(new Data(new Binary(IntegrationTestSupport.getPayload(maxPayloadSize.get()))));

        sender.send(msg, delivery -> {

            context.verify(() -> {
                assertThat(delivery.getRemoteState()).isInstanceOf(Rejected.class);
                final Rejected rejected = (Rejected) delivery.getRemoteState();
                final ErrorCondition error = rejected.getError();
                assertThat((Object) error.getCondition()).isEqualTo(LinkError.MESSAGE_SIZE_EXCEEDED);
            });
            context.completeNow();
        });

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
                return createProducer(targetAddress);
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

        final VertxTestContext setup = new VertxTestContext();
        setupProtocolAdapter(tenantId, deviceId, false)
            .onComplete(setup.succeeding(s -> {
                setup.verify(() -> {
                    final UnsignedLong maxMessageSize = s.getRemoteMaxMessageSize();
                    assertThat(maxMessageSize).as("check adapter's attach frame includes max-message-size").isNotNull();
                    assertThat(maxMessageSize.longValue()).as("check message size is limited").isGreaterThan(0);
                });
                sender = s;
                setup.completeNow();
            }));

        assertThat(setup.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
        assertThat(setup.failed())
            .as("successfully connect to adapter")
            .isFalse();

        testUploadMessages(tenantId, senderQos);
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
                return helper.registry.addDeviceForTenant(tenantId, tenant, deviceId, cert);
            })
            .compose(ok -> connectToAdapter(deviceCert))
            .compose(con -> createProducer(null))
            .onComplete(setup.succeeding(s -> {
                setup.verify(() -> {
                    final UnsignedLong maxMessageSize = s.getRemoteMaxMessageSize();
                    assertThat(maxMessageSize).as("check adapter's attach frame includes max-message-size").isNotNull();
                    assertThat(maxMessageSize.longValue()).as("check message size is limited").isGreaterThan(0);
                });
                sender = s;
                setup.completeNow();
            }));

        assertThat(setup.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
        assertThat(setup.failed())
            .as("successfully connect to adapter")
            .isFalse();

        testUploadMessages(tenantId, senderQos);
    }

    //------------------------------------------< private methods >---

    private void testUploadMessages(
            final String tenantId,
            final ProtonQoS senderQoS) throws InterruptedException {

        final VertxTestContext messageSending = new VertxTestContext();

        final Function<Handler<Void>, Future<Void>> receiver = callback -> {
            return createConsumer(tenantId, msg -> {
                if (log.isTraceEnabled()) {
                    log.trace("received message [{}]: {}",
                            msg.getContentType(), MessageHelper.getPayloadAsString(msg));
                }
                assertMessageProperties(messageSending, msg);
                callback.handle(null);
            }).map(c -> {
                consumer = c;
                return null;
            });
        };

        doUploadMessages(messageSending, receiver, payload -> {

            final Message msg = ProtonHelper.message();
            MessageHelper.setPayload(msg, "opaque/binary", payload);
            msg.setAddress(getEndpointName());
            final Promise<?> sendingComplete = Promise.promise();
            final Handler<ProtonSender> sendMsgHandler = replenishedSender -> {
                replenishedSender.sendQueueDrainHandler(null);
                switch (senderQoS) {
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
        }, senderQoS);
    }

    /**
     * Upload a number of messages to Hono's Telemetry/Event APIs.
     *
     * @param messageSending The Vert.x test context to use for tracking the messages being received.
     * @param receiverFactory The factory to use for creating the receiver for consuming
     *                        messages from the messaging network.
     * @param sender The sender for sending messaging to the Hono server.
     * @param senderQoS The QoS used by the sender.
     * @throws InterruptedException if test execution is interrupted.
     */
    protected void doUploadMessages(
            final VertxTestContext messageSending,
            final Function<Handler<Void>, Future<Void>> receiverFactory,
            final Function<Buffer, Future<?>> sender,
            final ProtonQoS senderQoS) throws InterruptedException {

        final AtomicInteger messagesReceived = new AtomicInteger(0);

        final VertxTestContext receiverCreation = new VertxTestContext();

        receiverFactory.apply(msgReceived -> {
            final int msgNo = messagesReceived.incrementAndGet();
            if (msgNo % 200 == 0) {
                log.info("messages received: {}", msgNo);
            }
        })
        .onComplete(receiverCreation.completing());

        assertThat(receiverCreation.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
        assertThat(receiverCreation.failed()).isFalse();

        final int maxMessageSize = this.sender.getRemoteMaxMessageSize().intValue();
        log.debug("adapter uses max-message-size of {} bytes", maxMessageSize);

        assumingThat(adapterEnforcesMessageSizeLimit() && senderQoS == ProtonQoS.AT_LEAST_ONCE,
                () -> {
                    final CountDownLatch maxMessageSizeLatch = new CountDownLatch(1);
                    sender.apply(Buffer.buffer(IntegrationTestSupport.getPayload(maxMessageSize)))
                        .onSuccess(v -> {
                            messageSending.failNow(new AssertionError("should not have been able to send message exceeding max payload size"));
                        })
                        .onFailure(t -> {
                            log.debug("failed to send message exceeding max-message-size using {} delivery semantics", senderQoS);
                            messageSending.verify(() -> {
                                assertThat(t).isInstanceOf(AmqpErrorException.class);
                                final AmqpErrorException amqpError = (AmqpErrorException) t;
                                assertThat((Object) amqpError.getError()).isEqualTo(LinkError.MESSAGE_SIZE_EXCEEDED);
                            });
                        })
                        .onComplete(o -> maxMessageSizeLatch.countDown());
                    maxMessageSizeLatch.await(helper.getTestSetupTimeout(), TimeUnit.SECONDS);
                    });

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
        .run();


        final long timeToWait = Math.max(DEFAULT_TEST_TIMEOUT, Math.round(IntegrationTestSupport.MSG_COUNT * 1.2));
        assertThat(messageSending.awaitCompletion(timeToWait, TimeUnit.MILLISECONDS)).isTrue();
        if (messageSending.failed()) {
            Assertions.fail("failed to upload messages", messageSending.causeOfFailure());
        }
        assertThat(messagesReceived.get())
            .as(String.format("assert all %d expected messages are received", IntegrationTestSupport.MSG_COUNT))
            .isGreaterThanOrEqualTo(IntegrationTestSupport.MSG_COUNT);
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
     * @param deviceId The device to add to the tenant identified by tenantId.
     * @param disableTenant If true, disable the protocol adapter for the tenant.
     *
     * @return A future succeeding with the created sender.
     */
    private Future<ProtonSender> setupProtocolAdapter(
            final String tenantId,
            final String deviceId,
            final boolean disableTenant) {

        final String username = IntegrationTestSupport.getUsername(deviceId, tenantId);

        final Tenant tenant = new Tenant();
        if (disableTenant) {
            tenant.addAdapterConfig(new Adapter(Constants.PROTOCOL_ADAPTER_TYPE_AMQP).setEnabled(false));
        }

        return helper.registry
                .addDeviceForTenant(tenantId, tenant, deviceId, DEVICE_PASSWORD)
                .compose(ok -> connectToAdapter(username, DEVICE_PASSWORD))
                .compose(con -> createProducer(null))
                .recover(t -> {
                    log.error("error setting up AMQP protocol adapter", t);
                    return Future.failedFuture(t);
                });
    }

    private void close(final VertxTestContext ctx) {

        final Promise<ProtonConnection> connectionTracker = Promise.promise();
        final Promise<ProtonSender> senderTracker = Promise.promise();
        final Promise<Void> receiverTracker = Promise.promise();

        if (sender == null) {
            senderTracker.complete();
        } else {
            context.runOnContext(go -> {
                sender.closeHandler(senderTracker);
                sender.close();
            });
        }

        if (consumer == null) {
            receiverTracker.complete();
        } else {
            consumer.close(receiverTracker);
        }

        if (connection == null || connection.isDisconnected()) {
            connectionTracker.complete();
        } else {
            context.runOnContext(go -> {
                connection.closeHandler(connectionTracker);
                connection.close();
            });
        }

        CompositeFuture.join(connectionTracker.future(), senderTracker.future(), receiverTracker.future())
        .onComplete(c -> {
           context = null;
           ctx.completeNow();
        });
    }
}
