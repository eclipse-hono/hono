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
import org.eclipse.hono.client.RegistrationClient;
import org.eclipse.hono.client.impl.HonoClientImpl;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.connection.ConnectionFactory;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RegistrationConstants;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.proton.ProtonClientOptions;

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
    private static final Vertx  vertx = Vertx.vertx();

    /**
     * A client for connecting to Hono Messaging.
     */
    protected HonoClient honoClient;
    /**
     * A client for connecting to the AMQP Messaging Network.
     */
    protected HonoClient downstreamClient;
    private RegistrationClient registrationClient;
    private HonoClient honoDeviceRegistryClient;

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
     * Sets up the environment.
     * <p>
     * <ol>
     * <li>connect to the AMQP messaging network</li>
     * <li>connects to the Hono Server</li>
     * <li>connects to the Hono Device Registry</li>
     * <li>creates a RegistrationClient for TEST_TENANT_ID</li>
     * <li>creates a MessageSender for TEST_TENANT_ID</li>
     * </ol>
     *
     * @param ctx The test context
     */
    @Before
    public void connect(final TestContext ctx) {

        final ClientConfigProperties downstreamProps = new ClientConfigProperties();
        downstreamProps.setHost(IntegrationTestSupport.DOWNSTREAM_HOST);
        downstreamProps.setPort(IntegrationTestSupport.DOWNSTREAM_PORT);
        downstreamProps.setPathSeparator(IntegrationTestSupport.PATH_SEPARATOR);
        downstreamProps.setUsername(IntegrationTestSupport.RESTRICTED_CONSUMER_NAME);
        downstreamProps.setPassword(IntegrationTestSupport.RESTRICTED_CONSUMER_PWD);
        downstreamClient = new HonoClientImpl(
                vertx,
                ConnectionFactory.newConnectionFactory(vertx, downstreamProps),
                downstreamProps);

        final ClientConfigProperties honoProps = new ClientConfigProperties();
        honoProps.setHost(IntegrationTestSupport.HONO_HOST);
        honoProps.setPort(IntegrationTestSupport.HONO_PORT);
        honoProps.setUsername(IntegrationTestSupport.HONO_USER);
        honoProps.setPassword(IntegrationTestSupport.HONO_PWD);
        honoClient = new HonoClientImpl(
                vertx,
                ConnectionFactory.newConnectionFactory(vertx, honoProps),
                honoProps);

        final ClientConfigProperties registryProps = new ClientConfigProperties();
        registryProps.setHost(IntegrationTestSupport.HONO_DEVICEREGISTRY_HOST);
        registryProps.setPort(IntegrationTestSupport.HONO_DEVICEREGISTRY_AMQP_PORT);
        registryProps.setUsername(IntegrationTestSupport.HONO_USER);
        registryProps.setPassword(IntegrationTestSupport.HONO_PWD);
        honoDeviceRegistryClient = new HonoClientImpl(
                vertx,
                ConnectionFactory.newConnectionFactory(vertx, registryProps),
                registryProps);

        final ProtonClientOptions options = new ProtonClientOptions();

        // connect to AMQP messaging network
        final Future<HonoClient> downstreamTracker = downstreamClient.connect(options);

        // create sender
        final Future<MessageSender> senderTracker = honoClient
                .connect(options)
                .compose(connectedClient -> createProducer(TEST_TENANT_ID))
                .map(s -> {
                    sender = s;
                    return s;
                });

        // create registration client
        final Future<RegistrationClient> registrationClientTracker = honoDeviceRegistryClient
                .connect(options)
                .compose(connectedClient -> connectedClient.getOrCreateRegistrationClient(TEST_TENANT_ID))
                .map(c -> {
                    registrationClient = c;
                    return c;
                });

        CompositeFuture.all(downstreamTracker, senderTracker, registrationClientTracker).setHandler(ctx.asyncAssertSuccess(s -> {
            log.info("connections to Hono server, Hono device registry and AMQP messaging network established");
        }));
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
            log.debug("deregistering devices");
            registrationClient.deregister(DEVICE_ID).setHandler(r -> done.complete());
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
                log.info("error while disconnecting: ", r.cause());
            }
            shutdown.complete();
        });

        if (sender != null) {
            final Future<Void> regClientTracker = Future.future();
            registrationClient.close(regClientTracker.completer());
            regClientTracker.compose(r -> {
                final Future<Void> senderTracker = Future.future();
                sender.close(senderTracker.completer());
                return senderTracker;
            }).compose(r -> {
                final Future<Void> honoClientShutdownTracker = Future.future();
                honoClient.shutdown(honoClientShutdownTracker.completer());
                return honoClientShutdownTracker;
            }).compose(r -> {
                honoDeviceRegistryClient.shutdown(honoTracker.completer());
            }, honoTracker);
        } else {
            honoTracker.complete();
        }

        final Future<Void> receiverTracker = Future.future();
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
     * Verifies that a number of messages uploaded to Hono's Telemetry or Event API can be successfully
     * consumed via the AMQP Messaging Network.
     * 
     * @param ctx The test context.
     * @throws InterruptedException if test execution is interrupted.
     */
    @Test
    public void testSendingMessages(final TestContext ctx) throws InterruptedException {

        final Async setup = ctx.async();

        final AtomicReference<String> registrationAssertion = new AtomicReference<>();

        registrationClient.register(DEVICE_ID, null)
        .compose(ok -> registrationClient.assertRegistration(DEVICE_ID))
        .map(result -> {
            registrationAssertion.set(result.getString(RegistrationConstants.FIELD_ASSERTION));
            return null;
        }).setHandler(ok -> setup.complete());
        setup.await();

        final Function<Handler<Void>, Future<Void>> receiver = callback -> {
            return createConsumer(TEST_TENANT_ID, msg -> {
                assertMessageProperties(ctx, msg);
                assertAdditionalMessageProperties(ctx, msg);
                callback.handle(null);
            }).map(c -> {
                consumer = c;
                return null;
            });
        };

        doUploadMessages(ctx, receiver, payload -> {
            final Async sending = ctx.async();
            sender.send(
                    DEVICE_ID,
                    payload,
                    CONTENT_TYPE_TEXT_PLAIN,
                    registrationAssertion.get(),
                    creditAvailable -> sending.complete());
            sending.await();
        });
    }

    /**
     * Verifies that a client which is authorized to WRITE to resources for the DEFAULT_TENANT only,
     * is not allowed to write to a resource concerning another tenant than the DEFAULT_TENANT.
     *
     * @param ctx The test context
     */
    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testCreateSenderFailsForTenantWithoutAuthorization(final TestContext ctx) {

        createProducer("non-authorized").setHandler(
                ctx.asyncAssertFailure(failed -> log.debug("creation of sender failed: {}", failed.getMessage())
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

        createConsumer("non-authorized", message -> {}).setHandler(
                ctx.asyncAssertFailure(failed -> log.debug("creation of receiver failed: {}", failed.getMessage())
        ));
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
