/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
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
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import org.apache.qpid.proton.amqp.messaging.Accepted;
import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.amqp.messaging.Target;
import org.apache.qpid.proton.amqp.transport.AmqpError;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.tests.client.ClientTestBase;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.TenantObject;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.proton.ProtonClient;
import io.vertx.proton.ProtonClientOptions;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonSender;

/**
 * Base class for the AMQP adapter integration tests.
 */
public abstract class AmqpAdapterTestBase extends ClientTestBase {

    private static final String DEVICE_PASSWORD = "device-password";
    private static final String INVALID_CONTENT_TYPE = "application/vnd.eclipse-hono-empty-notification";

    @Rule
    public TestName testName = new TestName();

    protected static IntegrationTestSupport helper;
    protected ProtonConnection connection;

    private ProtonSender sender;
    private MessageConsumer consumer;

    private static Vertx vertx;

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
     * Create a HTTP client for accessing the device registry (for registering devices and credentials) and
     * an AMQP 1.0 client for consuming messages from the messaging network.
     * 
     * @param ctx The Vert.x test context.
     */
    @BeforeClass
    public static void setup(final TestContext ctx) {
        vertx = Vertx.vertx();
        helper = new IntegrationTestSupport(vertx);
        helper.init(ctx);
    }

    /**
     * Shut down the client connected to the messaging network.
     * 
     * @param ctx The Vert.x test context.
     */
    @AfterClass
    public static void disconnect(final TestContext ctx) {
        helper.disconnect(ctx);
    }

    /**
     * Logs a message before running a test case.
     */
    @Before
    public void before() {
        log.info("running {}", testName.getMethodName());
    }

    /**
     * Disconnect the AMQP 1.0 client connected to the AMQP Adapter and close senders and consumers.
     * Also delete all random tenants and devices generated during the execution of a test case.
     * 
     * @param context The Vert.x test context.
     */
    @After
    public void after(final TestContext context) {
        helper.deleteObjects(context);
        close(context);
    }

    /**
     * Verifies that a BAD inbound message (i.e when the message content-type does not match the payload) is rejected by
     * the adapter.
     * 
     * @param context The Vert.x context for running asynchronous tests.
     */
    @Test
    public void testAdapterRejectsBadInboundMessage(final TestContext context) {
        final String tenantId = helper.getRandomTenantId();
        final String deviceId = helper.getRandomDeviceId(tenantId);

        final Async setup = context.async();
        setupProtocolAdapter(tenantId, deviceId, false)
        .compose(s -> {
            sender = s;
            return Future.succeededFuture();
        }).setHandler(context.asyncAssertSuccess(ok -> setup.complete()));
        setup.await();

        final Async completionTracker = context.async();
        final Message msg = ProtonHelper.message("some payload");
        msg.setContentType(INVALID_CONTENT_TYPE);
        msg.setAddress(getEndpointName());
        sender.send(msg, delivery -> {

            context.assertTrue(Rejected.class.isInstance(delivery.getRemoteState()));

            final Rejected rejected = (Rejected) delivery.getRemoteState();
            final ErrorCondition error = rejected.getError();
            final String expected = String.format("Content-Type: [%s] does not match payload", INVALID_CONTENT_TYPE);

            context.assertEquals(Constants.AMQP_BAD_REQUEST, error.getCondition());
            context.assertEquals(expected, error.getDescription());

            completionTracker.complete();
        });
        completionTracker.await();
    }

    /**
     * Verifies that the AMQP Adapter will fail to authenticate a device whose username does not match the expected pattern
     * {@code [<authId>@<tenantId>]}.
     * 
     * @param context The Vert.x test context.
     */
    @Test
    public void testAuthenticationWithInvalidUsernamePattern(final TestContext context) {
        connectToAdapter("invalidaUsername", DEVICE_PASSWORD)
        .setHandler(context.asyncAssertFailure(t -> {
            // SASL handshake failed
            context.assertTrue(SecurityException.class.isInstance(t));
        }));
    }

    /**
     * Verifies that the AMQP adapter rejects messages from a device that belongs to a tenant for which it has been
     * disabled. This test case only considers events since telemetry senders do not wait for response from the adapter.
     * 
     * @param context The Vert.x test context.
     */
    @Test
    public void testUploadMessageFailsForTenantDisabledAdapter(final TestContext context) {
        final String tenantId = helper.getRandomTenantId();
        final String deviceId = helper.getRandomDeviceId(tenantId);

        final Async setup = context.async();
        setupProtocolAdapter(tenantId, deviceId, true)
        .compose(s -> {
            sender = s;
            return Future.succeededFuture();
        }).setHandler(context.asyncAssertSuccess(ok -> setup.complete()));
        setup.await();

        final Async completionTracker = context.async();
        final Message msg = ProtonHelper.message("some payload");
        msg.setAddress(getEndpointName());

        sender.send(msg, delivery -> {
            context.assertTrue(Rejected.class.isInstance(delivery.getRemoteState()));

            final Rejected rejected = (Rejected) delivery.getRemoteState();
            final ErrorCondition error = rejected.getError();
            final String expected = String.format("This adapter is not enabled for tenant [tenantId: %s].", tenantId);

            context.assertEquals(AmqpError.UNAUTHORIZED_ACCESS, error.getCondition());
            context.assertEquals(expected, error.getDescription());

            completionTracker.complete();
        });
        completionTracker.await();

    }

    /**
     * Verifies that the AMQP Adapter rejects (closes) AMQP links that contains a target address.
     * @param context The Vert.x test context.
     */
    @Test
    public void testAnonymousRelayRequired(final TestContext context) {
        final String tenantId = helper.getRandomTenantId();
        final String deviceId = helper.getRandomDeviceId(tenantId);
        final String targetAddress = String.format("%s/%s/%s", getEndpointName(), tenantId, deviceId);

        final TenantObject tenant = TenantObject.from(tenantId, true);
        final Async setup = context.async();
        helper.registry
                .addDeviceForTenant(tenant, deviceId, DEVICE_PASSWORD)
                .setHandler(context.asyncAssertSuccess(ok -> setup.complete()));
        setup.await();

        final String username = IntegrationTestSupport.getUsername(deviceId, tenantId);

        // connect and create sender (with a valid target address)
        connectToAdapter(username, DEVICE_PASSWORD)
                .compose(connection -> {
                    this.connection = connection;
                    final Target target = new Target();
                    target.setAddress(targetAddress);
                    return createProducer(target);
                }).setHandler(context.asyncAssertFailure(t -> {
                    final ErrorCondition expected = ProtonHelper.condition(Constants.AMQP_BAD_REQUEST,
                            "This adapter does not accept a target address on receiver links");

                    context.assertEquals(expected.toString(), t.getMessage());
                }));
    }

    /**
     * Verifies that a number of messages published through the AMQP adapter can be successfully consumed by
     * applications connected to the AMQP messaging network.
     *
     * @param context The Vert.x test context.
     * @throws InterruptedException Exception.
     */
    @Test
    public void testUploadingXnumberOfMessages(final TestContext context) throws InterruptedException {
        final String tenantId = helper.getRandomTenantId();
        final String deviceId = helper.getRandomDeviceId(tenantId);

        final Async setup = context.async();
        setupProtocolAdapter(tenantId, deviceId, false)
        .compose(s -> {
            sender = s;
            return Future.succeededFuture();
        }).setHandler(context.asyncAssertSuccess(ok -> setup.complete()));
        setup.await();

        final Function<Handler<Void>, Future<Void>> receiver = callback -> {
            return createConsumer(tenantId, msg -> {
                callback.handle(null);
            }).map(c -> {
                consumer = c;
                return null;
            });
        };

        doUploadMessages(context, receiver, payload -> {
            final Async sendingComplete = context.async();
            final Message msg = ProtonHelper.message(payload);
            msg.setAddress(getEndpointName());
            sender.send(msg, delivery -> {
                context.assertTrue(Accepted.class.isInstance(delivery.getRemoteState()));
                sendingComplete.complete();
            });
            sendingComplete.await();
        });
    }

    //------------------------------------------< private methods >---
    /**
     * Creates a test specific message sender.
     * 
     * @param target   The tenant to create the sender for.
     * @return    A future succeeding with the created sender.
     * 
     * @throws NullPointerException if the target or connection is null.
     */
    private Future<ProtonSender> createProducer(final Target target) {
        Objects.requireNonNull(target, "Target cannot be null");
        Objects.requireNonNull(connection, "connection cannot be null");

        final Future<ProtonSender>  result = Future.future();
        final ProtonSender sender = connection.createSender(target.getAddress());
        sender.setQoS(ProtonQoS.AT_LEAST_ONCE);
        sender.openHandler(remoteAttach -> {
            if (remoteAttach.succeeded()) {
                result.complete(sender);
            } else {
                result.fail(remoteAttach.cause());
            }
        }).open();
        return result;
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
    private Future<ProtonSender> setupProtocolAdapter(final String tenantId, final String deviceId, final boolean disableTenant) {

        final String username = IntegrationTestSupport.getUsername(deviceId, tenantId);

        final TenantObject tenant = TenantObject.from(tenantId, true);
        if (disableTenant) {
            tenant.addAdapterConfiguration(TenantObject.newAdapterConfig(Constants.PROTOCOL_ADAPTER_TYPE_AMQP, false));
        }

        return helper.registry
        .addDeviceForTenant(tenant, deviceId, DEVICE_PASSWORD)
        .compose(ok -> {
           // connect and create sender (with a null target address)
            return connectToAdapter(username, DEVICE_PASSWORD)
                    .compose(connection -> {
                        this.connection = connection;
                        return createProducer(new Target());
                    });
        });
    }

    private Future<ProtonConnection> connectToAdapter(final String username, final String password) {

        final Future<ProtonConnection> result = Future.future();
        final ProtonClientOptions options = new ProtonClientOptions();
        final ProtonClient client = ProtonClient.create(vertx);

        client.connect(options, IntegrationTestSupport.AMQP_HOST, IntegrationTestSupport.AMQP_PORT, username, password, conAttempt -> {
            if (conAttempt.failed()) {
                result.fail(conAttempt.cause());
            } else {
                final ProtonConnection adapterConnection = conAttempt.result();
                adapterConnection.openHandler(remoteOpen -> {
                    if (remoteOpen.succeeded()) {
                        result.complete(adapterConnection);
                    }
                }).open();
            }
        });
        return result;
    }

    private void close(final TestContext ctx) {
        final Async shutdown = ctx.async();
        final Future<Void> connectionTracker = Future.future();
        final Future<Void> senderTracker = Future.future();

        if (sender != null) {
            sender.closeHandler(remoteClose -> {
                if (remoteClose.succeeded()) {
                    senderTracker.complete();
                } else {
                    senderTracker.fail(remoteClose.cause());
                }
            }).close();
        } else {
            senderTracker.complete();
        }

        if (connection != null) {
            connection.closeHandler(remoteClose -> {
                if (remoteClose.succeeded()) {
                    connectionTracker.complete();
                } else {
                    connectionTracker.fail(remoteClose.cause());
                }
            }).close();
        } else {
            connectionTracker.complete();
        }

        final Future<Void> receiverTracker = Future.future();
        if (consumer != null) {
            consumer.close(receiverTracker.completer());
        } else {
            receiverTracker.complete();
        }

        CompositeFuture.all(connectionTracker, senderTracker, receiverTracker).setHandler(ok -> {
           shutdown.complete();
        });
        shutdown.await();
    }

}
