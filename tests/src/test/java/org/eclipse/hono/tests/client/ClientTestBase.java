/**
 * Copyright (c) 2016, 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 *
 */

package org.eclipse.hono.tests.client;

import static java.net.HttpURLConnection.*;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import org.apache.qpid.proton.amqp.messaging.Released;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.client.RegistrationClient;
import org.eclipse.hono.client.impl.HonoClientImpl;
import org.eclipse.hono.connection.ConnectionFactoryImpl.ConnectionFactoryBuilder;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.RegistrationResult;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.proton.ProtonClientOptions;

/**
 * Base class for integration tests.
 *
 */
public abstract class ClientTestBase {

    /**
     * The identifier of the device used throughout the test cases.
     */
    protected static final String DEVICE_ID = "device-0";

    private static final String TEST_TENANT_ID = System.getProperty(IntegrationTestSupport.PROPERTY_TENANT, Constants.DEFAULT_TENANT);
    private static final String CONTENT_TYPE_TEXT_PLAIN = "text/plain";
    private static final Vertx  vertx = Vertx.vertx();
    private static final long   DEFAULT_TEST_TIMEOUT = 5000; // ms

    /**
     * A logger to be used by subclasses.
     */
    protected final Logger LOGGER = LoggerFactory.getLogger(getClass());

    protected HonoClient honoClient;
    protected HonoClient honoDeviceRegistryClient;
    protected HonoClient downstreamClient;
    private RegistrationClient registrationClient;
    private MessageSender sender;
    private MessageConsumer consumer;

    /**
     * Sets up the environment:
     * <ol>
     * <li>connect to the AMQP messaging network</li>
     * <li>connects to the Hono Server</li>
     * <li>connects to the Hono Device Registry</li>
     * <li>creates a RegistrationClient for TEST_TENANT_ID</li>
     * <li>creates a MessageSender for TEST_TENANT_ID</li>
     * </ul>
     *
     * @param ctx The test context
     */
    @Before
    public void connect(final TestContext ctx) {

        final Async done = ctx.async();

        Future<HonoClient> downstreamTracker = Future.future();
        Future<HonoClient> honoTracker = Future.future();
        Future<MessageSender> setupTracker = Future.future();
        CompositeFuture.all(setupTracker, downstreamTracker).setHandler(ctx.asyncAssertSuccess(s -> {
            sender = setupTracker.result();
            LOGGER.info("connections to Hono server, Hono device registry and AMQP messaging network established");
            done.complete();
        }));

        downstreamClient = new HonoClientImpl(vertx, ConnectionFactoryBuilder.newBuilder()
                .vertx(vertx)
                .host(IntegrationTestSupport.DOWNSTREAM_HOST)
                .port(IntegrationTestSupport.DOWNSTREAM_PORT)
                .pathSeparator(IntegrationTestSupport.PATH_SEPARATOR)
                .user(IntegrationTestSupport.DOWNSTREAM_USER)
                .password(IntegrationTestSupport.DOWNSTREAM_PWD)
                .build());
        downstreamClient.connect(new ProtonClientOptions(), downstreamTracker.completer());

        honoClient = new HonoClientImpl(vertx, ConnectionFactoryBuilder.newBuilder()
                .vertx(vertx)
                .host(IntegrationTestSupport.HONO_HOST)
                .port(IntegrationTestSupport.HONO_PORT)
                .user(IntegrationTestSupport.HONO_USER)
                .password(IntegrationTestSupport.HONO_PWD)
                .build());
        honoDeviceRegistryClient = new HonoClientImpl(vertx, ConnectionFactoryBuilder.newBuilder()
                .vertx(vertx)
                .host(IntegrationTestSupport.HONO_DEVICEREGISTRY_HOST)
                .port(IntegrationTestSupport.HONO_DEVICEREGISTRY_PORT)
                .user(IntegrationTestSupport.HONO_USER)
                .password(IntegrationTestSupport.HONO_PWD)
                .build());

        // step 1
        // connect to Hono server
        honoClient.connect(new ProtonClientOptions(), honoTracker.completer());
        honoTracker.compose(hono -> {
            // step 2
            // connect to device registry
            Future<HonoClient> deviceRegistryFuture = Future.future();
            honoDeviceRegistryClient.connect(new ProtonClientOptions(), deviceRegistryFuture.completer());
            return deviceRegistryFuture;
        }).compose(honoDeviceRegistry -> {
            // step 3
            // create registration client
            Future<RegistrationClient> regTracker = Future.future();
            honoDeviceRegistry.createRegistrationClient(TEST_TENANT_ID, regTracker.completer());
            return regTracker;
        }).compose(regClient -> {
            // step 4
            // create producer client
            registrationClient = regClient;
            createProducer(TEST_TENANT_ID, setupTracker.completer());
        }, setupTracker);

        done.await(5000);
    }

    /**
     * Deregisters the test device (DEVICE_ID) and disconnects from
     * the Hono server, Hono device registry and AMQP messaging network.
     *
     * @param ctx The test context
     */
    @After
    public void deregister(final TestContext ctx) {
        if (registrationClient != null) {

            final Async done = ctx.async();
            LOGGER.debug("deregistering device [{}]", DEVICE_ID);
            registrationClient.deregister(DEVICE_ID, r -> {
                if (r.failed()) {
                    LOGGER.info("deregistration of device [{}] failed", DEVICE_ID, r.cause());
                }
                done.complete();
            });
            done.await(2000);
        }

        disconnect(ctx);
    }

    private void disconnect(final TestContext ctx) {

        final Async shutdown = ctx.async();
        final Future<Void> honoTracker = Future.future();
        final Future<Void> qpidTracker = Future.future();
        CompositeFuture.all(honoTracker, qpidTracker).setHandler(r -> {
            if (r.failed()) {
                LOGGER.info("error while disconnecting: ", r.cause());
            }
            shutdown.complete();
        });

        if (sender != null) {
            final Future<Void> regClientTracker = Future.future();
            registrationClient.close(regClientTracker.completer());
            regClientTracker.compose(r -> {
                Future<Void> senderTracker = Future.future();
                sender.close(senderTracker.completer());
                return senderTracker;
            }).compose(r -> {
                Future<Void> honoClientShutdownTracker = Future.future();
                honoClient.shutdown(honoClientShutdownTracker.completer());
                return honoClientShutdownTracker;
            }).compose(r -> {
                honoDeviceRegistryClient.shutdown(honoTracker.completer());
            }, honoTracker);
        } else {
            honoTracker.complete();
        }

        Future<Void> receiverTracker = Future.future();
        if (consumer != null) {
            consumer.close(receiverTracker.completer());
        } else {
            receiverTracker.complete();
        }
        receiverTracker.compose(r -> {
            downstreamClient.shutdown(qpidTracker.completer());
        }, qpidTracker);

        shutdown.await(2000);
    }

    /**
     * Creates a test specific message sender.
     *
     * @param tenantId     The tenant to create the sender for.
     * @param setupTracker The handler to invoke with the result.
     */
    abstract void createProducer(final String tenantId, final Handler<AsyncResult<MessageSender>> setupTracker);

    /**
     * Creates a test specific message consumer.
     *
     * @param tenantId        The tenant to create the consumer for.
     * @param messageConsumer The handler to invoke for every message received.
     * @param setupTracker    The handler to invoke with the result.
     */
    abstract void createConsumer(final String tenantId, final Consumer<Message> messageConsumer, final Handler<AsyncResult<MessageConsumer>> setupTracker);

    @Test
    public void testClosedLinkIsRemovedFromCachedSenders(final TestContext ctx) {

        final Async setup = ctx.async();
        createConsumer(
                TEST_TENANT_ID,
                msg -> LOGGER.trace("received {}", msg),
                ctx.asyncAssertSuccess(done -> setup.complete()));

        setup.await(2000);

        final Async closed = ctx.async();
        sender.setErrorHandler(ctx.asyncAssertFailure(s -> {
            final JsonArray status = honoClient.getSenderStatus();
            LOGGER.debug("status: {}", status.encodePrettily());
            ctx.assertTrue(status.size() == 0);
            closed.complete();
        }));
        sender.send("non-existing-device", new byte[]{0x00}, "application/binary", "any", capacityAvailable -> {});
        closed.await(2000);
    }

    @Test
    public void testSendingMessages(final TestContext ctx) throws Exception {

        final Async received = ctx.async(IntegrationTestSupport.MSG_COUNT);
        final Async accepted = ctx.async(IntegrationTestSupport.MSG_COUNT);
        final Async setup = ctx.async();

        final Future<MessageConsumer> setupTracker = Future.future();
        setupTracker.setHandler(ctx.asyncAssertSuccess(ok -> {
            consumer = setupTracker.result();
            setup.complete();
        }));

        sender.setDefaultDispositionHandler((id, disposition) -> {
            accepted.countDown();
            //TODO temp fix to the test, until we have a logic to resend released qos1 messages
            if (Released.class.isInstance(disposition.getRemoteState())) {
                received.countDown();
            }
        });
        Future<RegistrationResult> tokenTracker = Future.future();

        Future<RegistrationResult> regTracker = Future.future();
        registrationClient.register(DEVICE_ID, null, regTracker.completer());
        regTracker.compose(r -> {
            if (r.getStatus() == HTTP_CREATED || r.getStatus() == HTTP_CONFLICT) {
                // test can also commence if device already exists
                LOGGER.debug("registration succeeded");
                registrationClient.assertRegistration(DEVICE_ID, tokenTracker.completer());
            } else {
                LOGGER.debug("device registration failed with status [{}]", r);
                tokenTracker.fail("failed to register device");
            }
            return tokenTracker;
        }).compose(r -> {
            if (r.getStatus() == HTTP_OK) {
                createConsumer(TEST_TENANT_ID, msg -> {
                    LOGGER.trace("received {}", msg);
                    assertMessagePropertiesArePresent(ctx, msg);
                    assertAdditionalMessageProperties(ctx, msg);
                    received.countDown();
                }, setupTracker.completer());
            } else {
                setupTracker.fail("cannot assert registration status");
            }
        }, setupTracker);

        setup.await(1000);

        final String registrationAssertion = tokenTracker.result().getPayload().getString(RegistrationConstants.FIELD_ASSERTION);
        long start = System.currentTimeMillis();
        final AtomicInteger messagesSent = new AtomicInteger();

        IntStream.range(0, IntegrationTestSupport.MSG_COUNT).forEach(i -> {
            Async latch = ctx.async();
            sender.send(DEVICE_ID, "payload" + i, CONTENT_TYPE_TEXT_PLAIN, registrationAssertion, capacityAvailable -> {
                latch.complete();
                if (messagesSent.incrementAndGet() % 200 == 0) {
                    LOGGER.info("messages sent: {}", messagesSent.get());
                }
            });
            latch.await();
        });

        long timeToWait = Math.max(DEFAULT_TEST_TIMEOUT, Math.round(IntegrationTestSupport.MSG_COUNT * 1.2));
        received.await(timeToWait);
        accepted.await(timeToWait);
        LOGGER.info("sent {} and received {} messages after {} milliseconds",
                messagesSent.get(), IntegrationTestSupport.MSG_COUNT - received.count(), System.currentTimeMillis() - start);
    }

    /**
     * Verifies that a client which is authorized to WRITE to resources for the DEFAULT_TENANT only,
     * is not allowed to write to a resource concerning another tenant than the DEFAULT_TENANT.
     *
     * @param ctx The test context
     */
    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testCreateSenderFailsForTenantWithoutAuthorization(final TestContext ctx) {

        createProducer(
                "non-authorized",
                ctx.asyncAssertFailure(failed -> LOGGER.debug("creation of sender failed: {}", failed.getMessage())
        ));
    }

    /**
     * Verifies that a client which is authorized to consume messages for the DEFAULT_TENANT only,
     * is not allowed to consume messages for another tenant than the DEFAULT_TENANT.
     *
     * @param ctx The test context
     */
    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testCreateConsumerFailsForTenantWithoutAuthorization(final TestContext ctx) {

        createConsumer(
                "non-authorized",
                message -> {},
                ctx.asyncAssertFailure(failed -> LOGGER.debug("creation of receiver failed: {}", failed.getMessage())
        ));
    }

    private void assertMessagePropertiesArePresent(final TestContext ctx, final Message msg) {
        ctx.assertNotNull(MessageHelper.getDeviceId(msg));
        ctx.assertNotNull(MessageHelper.getTenantIdAnnotation(msg));
        ctx.assertNotNull(MessageHelper.getDeviceIdAnnotation(msg));
    }

    protected void assertAdditionalMessageProperties(final TestContext ctx, final Message msg) {
        // empty
    }
}
