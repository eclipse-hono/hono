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

package org.eclipse.hono.tests.client;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RegistrationConstants;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.proton.ProtonClientOptions;
import io.vertx.proton.ProtonDelivery;

/**
 * Base class for integration tests.
 *
 */
public abstract class MessagingClientTestBase extends ClientTestBase {

    /**
     * The identifier of the device used throughout the test cases.
     */
    protected static final String DEVICE_ID = "device-0";

    private static final String TEST_TENANT_ID = System.getProperty(IntegrationTestSupport.PROPERTY_TENANT, Constants.DEFAULT_TENANT);
    private static final String CONTENT_TYPE_TEXT_PLAIN = "text/plain";

    /**
     * A helper accessing the AMQP 1.0 Messaging Network and
     * for managing tenants/devices/credentials.
     */
    protected static IntegrationTestSupport helper;
    /**
     * A client for connecting to Hono Messaging.
     */
    protected static HonoClient honoMessagingClient;
    /**
     * The vert.x context to use for interacting with Hono Messaging.
     */
    protected static Context honoMessagingContext;

    private static HonoClient honoDeviceRegistryClient;

    /**
     * Support outputting current test's name.
     */
    @Rule
    public TestName testName = new TestName();

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
     * @param deviceId     The device to create the sender for.
     * @return A future succeeding with the created sender.
     */
    protected Future<MessageSender> createProducer(final String tenantId, final String deviceId) {
        return honoMessagingClient.getOrCreateTelemetrySender(tenantId, deviceId);
    }

    /**
     * Sets up the environment.
     * <p>
     * <ol>
     * <li>initializes the helper for accessing the registry</li>
     * <li>connects to the AMQP messaging network</li>
     * <li>connects to the Hono Server</li>
     * <li>connects to the Hono Device Registry</li>
     * </ol>
     * 
     * @param ctx The vert.x test context.
     */
    @BeforeClass
    public static void init(final TestContext ctx) {

        VERTX = Vertx.vertx();

        final ClientConfigProperties downstreamProps = new ClientConfigProperties();
        downstreamProps.setHost(IntegrationTestSupport.DOWNSTREAM_HOST);
        downstreamProps.setPort(IntegrationTestSupport.DOWNSTREAM_PORT);
        downstreamProps.setPathSeparator(IntegrationTestSupport.PATH_SEPARATOR);
        downstreamProps.setUsername(IntegrationTestSupport.RESTRICTED_CONSUMER_NAME);
        downstreamProps.setPassword(IntegrationTestSupport.RESTRICTED_CONSUMER_PWD);
        downstreamProps.setInitialCredits(100);

        helper = new IntegrationTestSupport(VERTX);
        helper.init(ctx, downstreamProps);

        final ClientConfigProperties honoProps = new ClientConfigProperties();
        honoProps.setHost(IntegrationTestSupport.HONO_HOST);
        honoProps.setPort(IntegrationTestSupport.HONO_PORT);
        honoProps.setUsername(IntegrationTestSupport.HONO_USER);
        honoProps.setPassword(IntegrationTestSupport.HONO_PWD);
        honoMessagingClient = HonoClient.newClient(VERTX, honoProps);

        final ClientConfigProperties registryProps = new ClientConfigProperties();
        registryProps.setHost(IntegrationTestSupport.HONO_DEVICEREGISTRY_HOST);
        registryProps.setPort(IntegrationTestSupport.HONO_DEVICEREGISTRY_AMQP_PORT);
        registryProps.setUsername(IntegrationTestSupport.HONO_USER);
        registryProps.setPassword(IntegrationTestSupport.HONO_PWD);
        honoDeviceRegistryClient = HonoClient.newClient(VERTX, registryProps);

        final ProtonClientOptions options = new ProtonClientOptions()
                .setConnectTimeout(5000)
                .setReconnectAttempts(1);

        final Async connectionEstablished = ctx.async();

        final Future<HonoClient> registryClientTracker = honoDeviceRegistryClient.connect(options);
        final Future<HonoClient> messagingClientTracker = Future.future();
        honoMessagingContext = VERTX.getOrCreateContext();
        honoMessagingContext.runOnContext(connect -> {
            honoMessagingClient.connect(options).setHandler(messagingClientTracker);
        });
        CompositeFuture.all(registryClientTracker, messagingClientTracker)
        .setHandler(ctx.asyncAssertSuccess(ok -> connectionEstablished.complete()));
        connectionEstablished.await(DEFAULT_TEST_TIMEOUT);
    }

    /**
     * Prints out the current test name.
     *
     * @param ctx The test context
     */
    @Before
    public void printTestName(final TestContext ctx) {

        log.info("running {}", testName.getMethodName());

    }

    /**
     * Unregisters the test device and disconnects from
     * Hono Messaging, Device Registration service and the AMQP messaging network.
     *
     * @param ctx The test context
     */
    @After
    public void unregisterDevices(final TestContext ctx) {

        helper.deleteObjects(ctx);

    }

    /**
     * Closes the AMQP 1.0 Messaging Network client.
     * 
     * @param ctx The vert.x test context.
     */
    @AfterClass
    public static void disconnect(final TestContext ctx) {

        helper.disconnect(ctx);
        final Async closing = ctx.async();

        final Future<Void> registryTracker = Future.future();
        if (honoDeviceRegistryClient == null) {
            registryTracker.complete();
        } else {
            honoDeviceRegistryClient.shutdown(registryTracker);
        }

        final Future<Void> honoTracker = Future.future();
        if (honoMessagingClient == null) {
            honoTracker.complete();
        } else {
            honoMessagingContext.runOnContext(shutdown ->
                honoMessagingClient.shutdown(honoTracker));
        }

        CompositeFuture.all(honoTracker, registryTracker).setHandler(r -> {
            closing.complete();
        });

        closing.await(DEFAULT_TEST_TIMEOUT);
        honoMessagingContext = null;
        VERTX.close();
    }

    /**
     * Verifies that a number of messages uploaded to Hono's Telemetry or Event API can be successfully
     * consumed via the AMQP Messaging Network.
     * 
     * @param ctx The test context.
     * @throws InterruptedException if test execution is interrupted.
     */
    @Test
    public void testSendingMessages(final TestContext ctx) throws InterruptedException {

        final AtomicReference<String> registrationAssertion = new AtomicReference<>();
        final String deviceId = helper.getRandomDeviceId(TEST_TENANT_ID);

        final Async setup = ctx.async();
        helper.registry.registerDevice(TEST_TENANT_ID, deviceId)
        .compose(ok -> honoDeviceRegistryClient.getOrCreateRegistrationClient(TEST_TENANT_ID))
        .compose(registrationClient -> registrationClient.assertRegistration(deviceId))
        .map(assertionResult -> {
            registrationAssertion.set(assertionResult.getString(RegistrationConstants.FIELD_ASSERTION));
            return assertionResult;
        }).setHandler(ctx.asyncAssertSuccess(ok -> setup.complete()));
        setup.await();

        final Function<Handler<Void>, Future<?>> receiverFactory = msgConsumer -> {
            return createConsumer(TEST_TENANT_ID, msg -> {
                assertMessageProperties(ctx, msg);
                assertAdditionalMessageProperties(ctx, msg);
                msgConsumer.handle(null);
            });
        };

        doUploadMessages(ctx, receiverFactory, payload -> {

            final Future<ProtonDelivery> result = Future.future();

            honoMessagingContext.runOnContext(send -> {
                createProducer(TEST_TENANT_ID, deviceId)
                .map(sender -> {
                    final Handler<Void> msgSender = s -> {
                        sender.send(
                                deviceId,
                                payload,
                                CONTENT_TYPE_TEXT_PLAIN,
                                registrationAssertion.get()).setHandler(result);
                    };
                    if (sender.getCredit() <= 0) {
                        sender.sendQueueDrainHandler(msgSender);
                    } else {
                        msgSender.handle(null);
                    }
                    return sender;
                }).otherwise(t -> {
                    result.fail(t);
                    return null;
                });
            });
            return result;
        });
    }

    /**
     * Verifies that a client which is authorized to WRITE to resources for the DEFAULT_TENANT only,
     * is not allowed to write to a resource concerning another tenant than the DEFAULT_TENANT.
     *
     * @param ctx The test context
     */
    @Test
    public void testCreateSenderFailsForTenantWithoutAuthorization(final TestContext ctx) {

        final Async failure = ctx.async();
        createProducer("non-authorized", null).setHandler(
                ctx.asyncAssertFailure(failed -> {
                    log.debug("creation of sender failed: {}", failed.getMessage());
                    failure.complete();
                }
        ));
        failure.await(DEFAULT_TEST_TIMEOUT);
    }

    /**
     * Verifies that a client which is authorized to consume messages for the DEFAULT_TENANT only,
     * is not allowed to consume messages for another tenant than the DEFAULT_TENANT.
     *
     * @param ctx The test context
     */
    @Test
    public void testCreateConsumerFailsForTenantWithoutAuthorization(final TestContext ctx) {

        final Async failure = ctx.async();
        createConsumer("non-authorized", message -> {}).setHandler(
                ctx.asyncAssertFailure(failed -> {
                    log.debug("creation of receiver failed: {}", failed.getMessage());
                    failure.complete();
                }
        ));
        failure.await(DEFAULT_TEST_TIMEOUT);
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
