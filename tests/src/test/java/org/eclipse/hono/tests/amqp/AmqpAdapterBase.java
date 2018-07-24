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

import java.net.HttpURLConnection;
import java.util.function.Consumer;
import java.util.function.Function;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.client.impl.HonoClientImpl;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.tests.client.ClientTestBase;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.MessageHelper;
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
import io.vertx.proton.ProtonClientOptions;
import io.vertx.proton.ProtonHelper;

/**
 * Base class for the AMQP Adapter integration tests.
 */
public abstract class AmqpAdapterBase extends ClientTestBase {

    private static final String DEVICE_PASSWORD = "device-password";

    /**
     * Support logging of test name.
     */
    @Rule
    public final TestName testName = new TestName();

    /**
     * The Vert.x instance to run on.
     */
    private static Vertx vertx;

    /**
     * A helper for accessing the AMQP 1.0 messaging network and for managing devices tenants, devices and credentials.
     */
    protected static IntegrationTestSupport helper;

    /**
     * A client for connecting to the AMQP protocol adapter.
     */
    protected HonoClient adapterClient;

    private MessageSender sender;
    private MessageConsumer consumer;

    /**
     * Creates a test specific message consumer.
     *
     * @param tenantId        The tenant to create the consumer for.
     * @param messageConsumer The handler to invoke for every message received.
     * @return A future succeeding with the created consumer.
     */
    protected abstract Future<MessageConsumer> createConsumer(String tenantId, Consumer<Message> messageConsumer);

    /**
     * Creates a test specific message sender.
     *
     * @param tenantId     The tenant to create the sender for.
     * @return A future succeeding with the created sender.
     */
    protected abstract Future<MessageSender> createProducer(String tenantId);

    /**
     * Create a HTTP client for accessing the device registry (for registering devices and credentials) and
     * an AMQP 1.0 client for consuming messages from the messaging network.
     * 
     * @param context The Vert.x test context.
     */
    @BeforeClass
    public static void init(final TestContext context) {
        vertx = Vertx.vertx();
        helper = new IntegrationTestSupport(vertx);
        helper.init(context);
    }

    /**
     * Shut down the client connected to the messaging network.
     * 
     * @param context The Vert.x test context.
     */
    @AfterClass
    public static void shutdown(final TestContext context) {
        helper.disconnect(context);
    }

    /**
     * Logs a message before running a test case.
     */
    @Before
    public void setUp() {
        log.info("running {}", testName.getMethodName());
    }

    /**
     * Disconnect the AMQP 1.0 client connected to the AMQP Adapter and close senders and consumers.
     * Also delete all random tenants and devices generated during the execution of a test case.
     * 
     * @param context The Vert.x test context.
     */
    @After
    public void postTest(final TestContext context) {
        disconnect(context);
        helper.deleteObjects(context);
    }

    /**
     * Verifies that a BAD inbound message (i.e when the message content-type does not match the payload) is rejected by
     * the adapter. This test case uses the credentials on record for device 4711 of the default tenant.
     * 
     * @param context The Vert.x context for running asynchronous tests.
     */
    @Test(timeout = 4000)
    public void testAdapterRejectsBadInboundMessage(final TestContext context) {

        final String tenantId = helper.getRandomTenantId();
        final String deviceId = helper.getRandomDeviceId(tenantId);

        final Async setup = context.async();
        setupProtocolAdapter(tenantId, deviceId, false, context)
        .compose(s -> {
           sender = s;
           return Future.succeededFuture();
        }).setHandler(context.asyncAssertSuccess(ok -> setup.complete()));
        setup.await();

        final Message msg = ProtonHelper.message("msg payload");

        msg.setContentType("application/vnd.eclipse-hono-empty-notification");

        sender.sendAndWaitForOutcome(msg).setHandler(context.asyncAssertFailure(t -> {

            context.assertTrue(ClientErrorException.class.isInstance(t));

            context.assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, ((ClientErrorException) t).getErrorCode());
        }));
    }

    /**
     * Verifies that the AMQP adapter rejects messages from a device that belongs to a tenant for which it has been
     * disabled. This test case only considers events since telemetry senders do not wait for response from the adapter.
     * 
     * @param context The Vert.x test context.
     */
    @Test(timeout = 4000)
    public void testUploadMessageFailsForTenantDisabledAdapter(final TestContext context) {

        final String tenantId = helper.getRandomTenantId();
        final String deviceId = helper.getRandomDeviceId(tenantId);

        final Async setup = context.async();
        setupProtocolAdapter(tenantId, deviceId, true, context)
        .compose(s -> {
           sender = s;
           return Future.succeededFuture();
        }).setHandler(context.asyncAssertSuccess(ok -> setup.complete()));
        setup.await();

        final Message msg = ProtonHelper.message("some payload");
        sender.sendAndWaitForOutcome(msg).setHandler(context.asyncAssertFailure(t -> {

            context.assertTrue(ClientErrorException.class.isInstance(t));

            // HonoClient maps AmqpError.UNAUTHORIZED_ACCESS to HttpURLConnection.HTTP_BAD_REQUEST
            // -> so we test the message

            final String expectedErrorMsg = String.format("This adapter is not enabled for tenant [tenantId: %s].",
                    tenantId);

            context.assertEquals(expectedErrorMsg, ((ClientErrorException) t).getMessage());
        }));
    }

    /**
     * Verifies that the AMQP Adapter will fail to authenticate a device whose username does not match the expected pattern
     * {@code [<authId>@<tenantId>]}.
     * 
     * @param context The Vert.x test context.
     */
    @Test(timeout = 1000)
    public void testAuthenticationWithInvalidUsernamePattern(final TestContext context) {
        connectToAdapter("invalidaUsername", DEVICE_PASSWORD, true)
        .setHandler(context.asyncAssertFailure(t -> {
            // SASL handshake failed
            context.assertTrue(ClientErrorException.class.isInstance(t));
            context.assertEquals(HttpURLConnection.HTTP_UNAUTHORIZED, ((ClientErrorException) t).getErrorCode());
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
        setupProtocolAdapter(tenantId, deviceId, false, context)
        .compose(s -> {
           sender = s;
           return Future.succeededFuture();
        }).setHandler(context.asyncAssertSuccess(ok -> setup.complete()));
        setup.await();

        final Function<Handler<Void>, Future<Void>> receiver = callback -> {
            return createConsumer(tenantId, msg -> {
                assertMessageProperties(context, msg);
                assertAdditionalMessageProperties(context, msg);
                callback.handle(null);
            }).map(c -> {
                consumer = c;
                return null;
            });
        };

        doUploadMessages(context, receiver, payload -> {
            final Async sendingComplete = context.async();
            final Message msg = ProtonHelper.message(payload);
            sender.sendAndWaitForOutcome(msg).setHandler(outcome -> {
                sendingComplete.complete();
            });
            sendingComplete.await();
        });
    }

    // ------------------------------< private methods >---

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
     * @param ctx The Vert.x test context.
     * 
     * @return A completed future with a message sender as value.
     */
    private Future<MessageSender> setupProtocolAdapter(final String tenantId, final String deviceId, final boolean disableTenant, final TestContext ctx) {

        final String username = IntegrationTestSupport.getUsername(deviceId, tenantId);

        final TenantObject tenant = TenantObject.from(tenantId, true);
        if (disableTenant) {
            tenant.addAdapterConfiguration(TenantObject.newAdapterConfig(Constants.PROTOCOL_ADAPTER_TYPE_AMQP, false));
        }

        return helper.registry
                .addDeviceForTenant(tenant, deviceId, DEVICE_PASSWORD)
                .compose(ok -> {
                    // connect and create sender
                    return connectToAdapter(username, DEVICE_PASSWORD, false)
                            .compose(connectedClient -> createProducer(tenantId))
                            .map(s -> {
                                return s;
                            });
                });
    }

    private Future<HonoClient> connectToAdapter(final String username, final String password, final boolean dontReconnect) {
        adapterClient = prepareAmqpAdapterClient(username, password, dontReconnect);
        return adapterClient.connect(new ProtonClientOptions());
    }

    private HonoClient prepareAmqpAdapterClient(final String username, final String password, final boolean dontReconnect) {
        final ClientConfigProperties config = new ClientConfigProperties();
        config.setName(testName.getMethodName());
        config.setHost(IntegrationTestSupport.AMQP_HOST);
        config.setPort(IntegrationTestSupport.AMQP_PORT);
        config.setUsername(username);
        config.setPassword(password);
        if (dontReconnect) {
            // client should not attempt to reconnect when authc fails.
            config.setReconnectAttempts(0);
        }
        return new HonoClientImpl(vertx, config);
    }

    private void disconnect(final TestContext context) {
        final Async shutdown = context.async();
        final Future<Void> clientTracker = Future.future();
        final Future<Void> consumerTracker = Future.future();

        CompositeFuture.all(clientTracker, consumerTracker).setHandler(outcome -> {
            if (outcome.failed()) {
                log.info("Error while disconnecting: ", outcome.cause());
            }
            shutdown.complete();
        });
        if (adapterClient != null) {
            final Future<Void> senderTracker = Future.future();
            if (sender != null) {
                sender.close(senderTracker.completer());
            } else {
                senderTracker.complete();
            }
            senderTracker.compose(ok -> {
                adapterClient.shutdown(clientTracker.completer());
            }, clientTracker);
        } else {
            clientTracker.complete();
        }

        if (consumer != null) {
            consumer.close(consumerTracker.completer());
        } else {
            consumerTracker.complete();
        }

        shutdown.await(1000); // in ms
    }
    private void assertMessageProperties(final TestContext ctx, final Message msg) {
        ctx.assertNotNull(MessageHelper.getDeviceId(msg));
        ctx.assertNotNull(MessageHelper.getTenantIdAnnotation(msg));
        ctx.assertNotNull(MessageHelper.getDeviceIdAnnotation(msg));
        ctx.assertNull(MessageHelper.getRegistrationAssertion(msg));
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
    protected void assertAdditionalMessageProperties(final TestContext ctx, final Message msg) {
        // empty
    }

}
