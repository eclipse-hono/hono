/*******************************************************************************
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.tests.coap;

import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertThat;

import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.qpid.proton.message.Message;
import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapHandler;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.coap.CoAP.Code;
import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.coap.CoAP.Type;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.coap.OptionSet;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.core.network.CoapEndpoint;
import org.eclipse.californium.core.network.config.NetworkConfig;
import org.eclipse.californium.scandium.DTLSConnector;
import org.eclipse.californium.scandium.config.DtlsConnectorConfig;
import org.eclipse.californium.scandium.dtls.pskstore.PskStore;
import org.eclipse.californium.scandium.dtls.pskstore.StaticPskStore;
import org.eclipse.hono.client.MessageConsumer;

import org.eclipse.hono.service.management.credentials.GenericCredential;
import org.eclipse.hono.service.management.device.Device;
import org.eclipse.hono.service.management.tenant.Adapter;
import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.MessageHelper;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.rules.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;

/**
 * Base class for CoAP adapter integration tests.
 *
 */
public abstract class CoapTestBase {

    /**
     * The default password of devices.
     */
    protected static final String SECRET = "secret";

        /**
     * A helper for accessing the AMQP 1.0 Messaging Network and
     * for managing tenants/devices/credentials.
     */
    protected static IntegrationTestSupport helper;
    /**
     * The period of time in milliseconds after which test cases should time out.
     */
    protected static final long TEST_TIMEOUT_MILLIS = 20000; // 20 seconds

    private static final Vertx VERTX = Vertx.vertx();

    private static final int MESSAGES_TO_SEND = 60;

    /**
     * Time out each test after 20 seconds.
     */
    @Rule
    public final Timeout timeout = Timeout.millis(TEST_TIMEOUT_MILLIS);
    /**
     * Provide test name to unit tests.
     */
    @Rule
    public final TestName testName = new TestName();

    /**
     * A logger to be shared with subclasses.
     */
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * The IP address and port of the CoAP adapter's secure endpoint.
     */
    protected InetSocketAddress coapAdapterSecureAddress;
    /**
     * The random tenant identifier created for each test case.
     */
    protected String tenantId;
    /**
     * The random device identifier created for each test case.
     */
    protected String deviceId;

    /**
     * Sets up clients.
     * 
     * @param ctx The vert.x test context.
     */
    @BeforeClass
    public static void init(final TestContext ctx) {

        helper = new IntegrationTestSupport(VERTX);
        helper.init(ctx);
    }

    /**
     * Sets up the fixture.
     * 
     * @throws UnknownHostException if the CoAP adapter's host name cannot be resolved.
     */
    @Before
    public void setUp() throws UnknownHostException {

        logger.info("running {}", testName.getMethodName());
        logger.info("using CoAP adapter [host: {}, coap port: {}, coaps port: {}]",
                IntegrationTestSupport.COAP_HOST,
                IntegrationTestSupport.COAP_PORT,
                IntegrationTestSupport.COAPS_PORT);
        coapAdapterSecureAddress = new InetSocketAddress(Inet4Address.getByName(IntegrationTestSupport.COAP_HOST), IntegrationTestSupport.COAPS_PORT);

        tenantId = helper.getRandomTenantId();
        deviceId = helper.getRandomDeviceId(tenantId);
    }

    /**
     * Deletes all temporary objects from the Device Registry which
     * have been created during the last test execution.
     * 
     * @param ctx The vert.x context.
     */
    @After
    public void deleteObjects(final TestContext ctx) {

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
    }

    /**
     * Creates the client to use for uploading data to the insecure endpoint
     * of the CoAP adapter.
     * 
     * @return The client.
     */
    protected CoapClient getCoapClient() {
        return new CoapClient();
    }

    /**
     * Creates the client to use for uploading data to the secure endpoint
     * of the CoAP adapter.
     * 
     * @param deviceId The device to add a shared secret for.
     * @param tenant The tenant that the device belongs to.
     * @param sharedSecret The secret shared with the CoAP server.
     * @return The client.
     */
    protected CoapClient getCoapsClient(final String deviceId, final String tenant, final String sharedSecret) {
        return getCoapsClient(new StaticPskStore(
                IntegrationTestSupport.getUsername(deviceId, tenant),
                sharedSecret.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Creates the client to use for uploading data to the secure endpoint
     * of the CoAP adapter.
     * 
     * @param pskStoreToUse The store to retrieve shared secrets from.
     * @return The client.
     */
    protected CoapClient getCoapsClient(final PskStore pskStoreToUse) {

        final DtlsConnectorConfig.Builder dtlsConfig = new DtlsConnectorConfig.Builder();
        dtlsConfig.setAddress(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
        dtlsConfig.setPskStore(pskStoreToUse);
        dtlsConfig.setMaxRetransmissions(1);
        final CoapEndpoint.Builder builder = new CoapEndpoint.Builder();
        builder.setNetworkConfig(NetworkConfig.createStandardWithoutFile());
        builder.setConnector(new DTLSConnector(dtlsConfig.build()));
        return new CoapClient().setEndpoint(builder.build());
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
     * Gets the name of the resource that unauthenticated devices
     * or gateways should use for uploading data.
     * 
     * @param tenant The tenant.
     * @param deviceId The device ID.
     * @return The resource name.
     */
    protected abstract String getPutResource(String tenant, String deviceId);

    /**
     * Gets the name of the resource that authenticated devices
     * should use for uploading data.
     * 
     * @return The resource name.
     */
    protected abstract String getPostResource();

    /**
     * Gets the CoAP message type to use for requests to the adapter.
     * 
     * @return The type.
     */
    protected abstract Type getMessageType();

    /**
     * Triggers the establishment of a downstream sender
     * for a tenant so that subsequent messages will be
     * more likely to be forwarded.
     * 
     * @param client The CoAP client to use for sending the request.
     * @param request The request to send.
     * @return A succeeded future.
     */
    protected final Future<Void> warmUp(final CoapClient client, final Request request) {

        logger.debug("sending request to trigger CoAP adapter's downstream message sender");
        final Promise<Void> result = Promise.promise();
        client.advanced(new CoapHandler() {

            @Override
            public void onLoad(final CoapResponse response) {
                waitForWarmUp();
            }

            @Override
            public void onError() {
                waitForWarmUp();
            }

            private void waitForWarmUp() {
                VERTX.setTimer(1000, tid -> result.complete());
            }
        }, request);
        return result.future();
    }

    private static void assertStatus(final TestContext ctx, final int expectedStatus, final Throwable t) {
        ctx.verify(v -> {
            assertThat(t, instanceOf(CoapResultException.class));
        });
        ctx.assertEquals(expectedStatus, ((CoapResultException) t).getErrorCode());
    }

    /**
     * Verifies that a number of messages uploaded to Hono's CoAP adapter
     * can be successfully consumed via the AMQP Messaging Network.
     * 
     * @param ctx The test context.
     * @throws InterruptedException if the test fails.
     */
    @Test
    public void testUploadMessagesAnonymously(final TestContext ctx) throws InterruptedException {

        final Async setup = ctx.async();
        final Tenant tenant = new Tenant();

        helper.registry
                .addDeviceForTenant(tenantId, tenant, deviceId, SECRET)
            .setHandler(ctx.asyncAssertSuccess(ok -> setup.complete()));
        setup.await();

        final CoapClient client = getCoapClient();
        testUploadMessages(ctx, tenantId,
                () -> warmUp(client, createCoapRequest(Code.PUT, getPutResource(tenantId, deviceId), 0)),
                count -> {
                    final Promise<OptionSet> result = Promise.promise();
                    final Request request = createCoapRequest(Code.PUT, getPutResource(tenantId, deviceId), count);
                    client.advanced(getHandler(result), request);
                    return result.future();
                });
    }

    /**
     * Verifies that a number of messages uploaded to Hono's CoAP adapter using TLS_PSK based authentication can be
     * successfully consumed via the AMQP Messaging Network.
     * 
     * @param ctx The test context.
     * @throws InterruptedException if the test fails.
     */
    @Test
    public void testUploadMessagesUsingPsk(final TestContext ctx) throws InterruptedException {

        final Async setup = ctx.async();
        final Tenant tenant = new Tenant();

        helper.registry
                .addPskDeviceForTenant(tenantId, tenant, deviceId, SECRET)
                .setHandler(ctx.asyncAssertSuccess(ok -> setup.complete()));
        setup.await();

        final CoapClient client = getCoapsClient(deviceId, tenantId, SECRET);

        testUploadMessages(ctx, tenantId,
                () -> warmUp(client, createCoapsRequest(Code.POST, getPostResource(), 0)),
                count -> {
                    final Promise<OptionSet> result = Promise.promise();
                    final Request request = createCoapsRequest(Code.POST, getPostResource(), count);
                    client.advanced(getHandler(result), request);
                    return result.future();
                });
    }

    /**
     * Verifies that a number of messages uploaded to the CoAP adapter via a gateway
     * using TLS_PSK can be successfully consumed via the AMQP Messaging Network.
     * 
     * @param ctx The test context.
     * @throws InterruptedException if the test fails.
     */
    @Test
    public void testUploadMessagesViaGateway(final TestContext ctx) throws InterruptedException {

        // GIVEN a device that is connected via two gateways
        final Tenant tenant = new Tenant();
        final String gatewayOneId = helper.getRandomDeviceId(tenantId);
        final String gatewayTwoId = helper.getRandomDeviceId(tenantId);
        final Device deviceData = new Device();
        deviceData.setVia(Arrays.asList(gatewayOneId, gatewayTwoId));

        final Async setup = ctx.async();
        helper.registry.addPskDeviceForTenant(tenantId, tenant, gatewayOneId, SECRET)
        .compose(ok -> helper.registry.addPskDeviceToTenant(tenantId, gatewayTwoId, SECRET))
        .compose(ok -> helper.registry.registerDevice(tenantId, deviceId, deviceData))
        .setHandler(ctx.asyncAssertSuccess(ok -> setup.complete()));
        setup.await();

        final CoapClient gatewayOne = getCoapsClient(gatewayOneId, tenantId, SECRET);
        final CoapClient gatewayTwo = getCoapsClient(gatewayTwoId, tenantId, SECRET);

        testUploadMessages(ctx, tenantId,
                () -> warmUp(gatewayOne, createCoapsRequest(Code.PUT, getPutResource(tenantId, deviceId), 0)),
                count -> {
                    final CoapClient client = (count.intValue() & 1) == 0 ? gatewayOne : gatewayTwo;
                    final Promise<OptionSet> result = Promise.promise();
                    final Request request = createCoapsRequest(Code.PUT, getPutResource(tenantId, deviceId), count);
                    client.advanced(getHandler(result), request);
                    return result.future();
                });
    }

    /**
     * Uploads messages to the CoAP endpoint.
     *
     * @param ctx The test context to run on.
     * @param tenantId The tenant that the device belongs to.
     * @param warmUp A sender of messages used to warm up the adapter before
     *               running the test itself or {@code null} if no warm up should
     *               be performed. 
     * @param requestSender The test device that will publish the data.
     * @throws InterruptedException if the test is interrupted before it
     *              has finished.
     */
    protected void testUploadMessages(
            final TestContext ctx,
            final String tenantId,
            final Supplier<Future<?>> warmUp,
            final Function<Integer, Future<OptionSet>> requestSender) throws InterruptedException {
        testUploadMessages(ctx, tenantId, warmUp, null, requestSender);
    }

    /**
     * Uploads messages to the CoAP endpoint.
     *
     * @param ctx The test context to run on.
     * @param tenantId The tenant that the device belongs to.
     * @param messageConsumer Consumer that is invoked when a message was received.
     * @param warmUp A sender of messages used to warm up the adapter before
     *               running the test itself or {@code null} if no warm up should
     *               be performed. 
     * @param requestSender The test device that will publish the data.
     * @throws InterruptedException if the test is interrupted before it
     *              has finished.
     */
    protected void testUploadMessages(
            final TestContext ctx,
            final String tenantId,
            final Supplier<Future<?>> warmUp,
            final Consumer<Message> messageConsumer,
            final Function<Integer, Future<OptionSet>> requestSender) throws InterruptedException {
        testUploadMessages(ctx, tenantId, warmUp, messageConsumer, requestSender, MESSAGES_TO_SEND);
    }

    /**
     * Uploads messages to the CoAP endpoint.
     *
     * @param ctx The test context to run on.
     * @param tenantId The tenant that the device belongs to.
     * @param warmUp A sender of messages used to warm up the adapter before running the test itself or {@code null} if
     *            no warm up should be performed.
     * @param messageConsumer Consumer that is invoked when a message was received.
     * @param requestSender The test device that will publish the data.
     * @param numberOfMessages The number of messages that are uploaded.
     * @throws InterruptedException if the test is interrupted before it has finished.
     */
    protected void testUploadMessages(
            final TestContext ctx,
            final String tenantId,
            final Supplier<Future<?>> warmUp,
            final Consumer<Message> messageConsumer,
            final Function<Integer, Future<OptionSet>> requestSender,
            final int numberOfMessages) throws InterruptedException {

        final CountDownLatch received = new CountDownLatch(numberOfMessages);
        final Async setup = ctx.async();

        createConsumer(tenantId, msg -> {
            logger.trace("received {}", msg);
            assertMessageProperties(ctx, msg);
            if (messageConsumer != null) {
                messageConsumer.accept(msg);
            }
            received.countDown();
            if (received.getCount() % 20 == 0) {
                logger.info("messages received: {}", numberOfMessages - received.getCount());
            }
        })
                .compose(ok -> Optional.ofNullable(warmUp).map(w -> w.get()).orElse(Future.succeededFuture()))
                .setHandler(ctx.asyncAssertSuccess(ok -> setup.complete()));

        setup.await();
        final long start = System.currentTimeMillis();
        final AtomicInteger messageCount = new AtomicInteger(0);

        while (messageCount.get() < numberOfMessages) {

            final Async sending = ctx.async();
            requestSender.apply(messageCount.getAndIncrement()).compose(this::assertCoapResponse)
                    .setHandler(attempt -> {
                        if (attempt.succeeded()) {
                            logger.debug("sent message {}", messageCount.get());
                        } else {
                            logger.info("failed to send message {}: {}", messageCount.get(),
                                    attempt.cause().getMessage());
                            ctx.fail(attempt.cause());
                        }
                        sending.complete();
                    });

            if (messageCount.get() % 20 == 0) {
                logger.info("messages sent: {}", messageCount.get());
            }
            sending.await();
        }

        final long timeToWait = Math.max(TEST_TIMEOUT_MILLIS - 1000, Math.round(numberOfMessages * 20));
        if (!received.await(timeToWait, TimeUnit.MILLISECONDS)) {
            logger.info("sent {} and received {} messages after {} milliseconds",
                    messageCount, numberOfMessages - received.getCount(), System.currentTimeMillis() - start);
            ctx.fail("did not receive all messages sent");
        } else {
            logger.info("sent {} and received {} messages after {} milliseconds",
                    messageCount, numberOfMessages - received.getCount(), System.currentTimeMillis() - start);
        }
    }

    /**
     * Verifies that the adapter fails to authorize a device using TLS_PSK
     * if the shared key that is registered for the device cannot be decoded
     * into a byte array.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    @Ignore("No possibility to add a malfored key anymore")
    public void testUploadFailsForMalformedSharedSecret(final TestContext ctx) {

        final Async setup = ctx.async();
        final Tenant tenant = new Tenant();

        // GIVEN a device for which an invalid shared key has been configured
        final GenericCredential credential = new GenericCredential();
        credential.setAuthId(deviceId);
        credential.setType(CredentialsConstants.SECRETS_TYPE_PRESHARED_KEY);
        credential.getAdditionalProperties().put(CredentialsConstants.FIELD_SECRETS_KEY, "notBase64");

        helper.registry.addTenant(tenantId, tenant)
        .compose(ok -> helper.registry.registerDevice(tenantId, deviceId))
                .compose(ok -> helper.registry.addCredentials(tenantId, deviceId, Collections.singleton(credential)))
        .setHandler(ctx.asyncAssertSuccess(ok -> setup.complete()));
        setup.await();

        // WHEN a device tries to upload data and authenticate using the PSK
        // identity for which the server has a malformed shared secret only
        final CoapClient client = getCoapsClient(deviceId, tenantId, SECRET);
        final Promise<OptionSet> result = Promise.promise();
        client.advanced(getHandler(result), createCoapsRequest(Code.POST, getPostResource(), 0));
        result.future().setHandler(ctx.asyncAssertFailure(t -> {
            // THEN the request fails because the DTLS handshake cannot be completed
            assertStatus(ctx, HttpURLConnection.HTTP_UNAVAILABLE, t);
        }));
    }

    /**
     * Verifies that the adapter fails to authenticate a device if the shared key registered
     * for the device does not match the key used by the device in the DTLS handshake.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testUploadFailsForNonMatchingSharedKey(final TestContext ctx) {

        final Tenant tenant = new Tenant();

        // GIVEN a device for which PSK credentials have been registered
        helper.registry.addPskDeviceForTenant(tenantId, tenant, deviceId, "NOT" + SECRET)
        .compose(ok -> {
            // WHEN a device tries to upload data and authenticate using the PSK
            // identity for which the server has a different shared secret on record
            final CoapClient client = getCoapsClient(deviceId, tenantId, SECRET);
            final Promise<OptionSet> result = Promise.promise();
            client.advanced(getHandler(result), createCoapsRequest(Code.POST, getPostResource(), 0));
            return result.future();
        }).setHandler(ctx.asyncAssertFailure(t -> {
            // THEN the request fails because the DTLS handshake cannot be completed
            assertStatus(ctx, HttpURLConnection.HTTP_UNAVAILABLE, t);
        }));
    }

    /**
     * Verifies that the CoAP adapter rejects messages from a device that belongs to a tenant for which the CoAP adapter
     * has been disabled.
     *
     * @param ctx The test context
     */
    @Test
    public void testUploadMessageFailsForDisabledTenant(final TestContext ctx) {

        // GIVEN a tenant for which the CoAP adapter is disabled
        final Tenant tenant = new Tenant();
        tenant.addAdapterConfig(new Adapter(Constants.PROTOCOL_ADAPTER_TYPE_COAP).setEnabled(false));

        helper.registry.addPskDeviceForTenant(tenantId, tenant, deviceId, SECRET)
                .compose(ok -> {

                    // WHEN a device that belongs to the tenant uploads a message
                    final CoapClient client = getCoapsClient(deviceId, tenantId, SECRET);
                    final Promise<OptionSet> result = Promise.promise();
                    client.advanced(getHandler(result), createCoapsRequest(Code.POST, getPostResource(), 0));
                    return result.future();
                }).setHandler(ctx.asyncAssertFailure(t -> {
                    // THEN the request fails with a 403
                    assertStatus(ctx, HttpURLConnection.HTTP_FORBIDDEN, t);
                }));
    }

    /**
     * Verifies that the CoAP adapter rejects messages from a disabled device.
     *
     * @param ctx The test context
     */
    @Test
    public void testUploadMessageFailsForDisabledDevice(final TestContext ctx) {

        // GIVEN a disabled device
        final Tenant tenant = new Tenant();
        final Device deviceData = new Device();
        deviceData.setEnabled(false);

        helper.registry.addPskDeviceForTenant(tenantId, tenant, deviceId, deviceData, SECRET)
        .compose(ok -> {

            // WHEN the device tries to upload a message
            final CoapClient client = getCoapsClient(deviceId, tenantId, SECRET);
            final Promise<OptionSet> result = Promise.promise();
            client.advanced(getHandler(result), createCoapsRequest(Code.POST, getPostResource(), 0));
            return result.future();
        }).setHandler(ctx.asyncAssertFailure(t -> {

            // THEN the request fails because the DTLS handshake cannot be completed
            logger.info("could not publish message for disabled device [tenant-id: {}, device-id: {}]",
                    tenantId, deviceId);
                    ctx.assertEquals(HttpURLConnection.HTTP_NOT_FOUND, ((CoapResultException) t).getErrorCode());
                    assertStatus(ctx, HttpURLConnection.HTTP_NOT_FOUND, t);
        }));
    }

    /**
     * Verifies that the CoAP adapter rejects messages from a disabled gateway
     * for an enabled device with a 403.
     *
     * @param ctx The test context
     */
    @Test
    public void testUploadMessageFailsForDisabledGateway(final TestContext ctx) {

        // GIVEN a device that is connected via a disabled gateway
        final Tenant tenant = new Tenant();
        final String gatewayId = helper.getRandomDeviceId(tenantId);
        final Device gatewayData = new Device();
        gatewayData.setEnabled(false);
        final Device deviceData = new Device();
        deviceData.setVia(Collections.singletonList(gatewayId));

        helper.registry.addPskDeviceForTenant(tenantId, tenant, gatewayId, gatewayData, SECRET)
        .compose(ok -> helper.registry.registerDevice(tenantId, deviceId, deviceData))
        .compose(ok -> {

            // WHEN the gateway tries to upload a message for the device
            final Promise<OptionSet> result = Promise.promise();
            final CoapClient client = getCoapsClient(gatewayId, tenantId, SECRET);
            client.advanced(getHandler(result), createCoapsRequest(Code.PUT, getPutResource(tenantId, deviceId), 0));
            return result.future();

        }).setHandler(ctx.asyncAssertFailure(t -> {

            // THEN the message gets rejected by the CoAP adapter with a 403
            logger.info("could not publish message for disabled gateway [tenant-id: {}, gateway-id: {}]",
                    tenantId, gatewayId);
                    assertStatus(ctx, HttpURLConnection.HTTP_FORBIDDEN, t);
        }));
    }

    /**
     * Verifies that the CoAP adapter rejects messages from a gateway for a device that it is not authorized for with a
     * 403.
     *
     * @param ctx The test context
     */
    @Test
    public void testUploadMessageFailsForUnauthorizedGateway(final TestContext ctx) {

        // GIVEN a device that is connected via gateway "not-the-created-gateway"
        final Tenant tenant = new Tenant();
        final String gatewayId = helper.getRandomDeviceId(tenantId);
        final Device deviceData = new Device();
        deviceData.setVia(Collections.singletonList("not-the-created-gateway"));

        helper.registry
                .addPskDeviceForTenant(tenantId, tenant, gatewayId, SECRET)
                .compose(ok -> helper.registry.registerDevice(tenantId, deviceId, deviceData))
                .compose(ok -> {

                    // WHEN another gateway tries to upload a message for the device
                    final Promise<OptionSet> result = Promise.promise();
                    final CoapClient client = getCoapsClient(gatewayId, tenantId, SECRET);
                    client.advanced(getHandler(result),
                            createCoapsRequest(Code.PUT, getPutResource(tenantId, deviceId), 0));
                    return result.future();

                })
                .setHandler(ctx.asyncAssertFailure(t -> {

                    // THEN the message gets rejected by the HTTP adapter with a 403
                    logger.info("could not publish message for unauthorized gateway [tenant-id: {}, gateway-id: {}]",
                            tenantId, gatewayId);
                    assertStatus(ctx, HttpURLConnection.HTTP_FORBIDDEN, t);
                }));
    }

    private void assertMessageProperties(final TestContext ctx, final Message msg) {
        ctx.assertNotNull(MessageHelper.getDeviceId(msg));
        ctx.assertNotNull(MessageHelper.getTenantIdAnnotation(msg));
        ctx.assertNotNull(MessageHelper.getDeviceIdAnnotation(msg));
        ctx.assertNull(MessageHelper.getRegistrationAssertion(msg));
        assertAdditionalMessageProperties(ctx, msg);
    }

    /**
     * Performs additional checks on messages received by a downstream consumer.
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

    /**
     * Performs additional checks on the response received in reply to a CoAP request.
     * <p>
     * This default implementation always returns a succeeded future.
     * Subclasses should override this method to implement reasonable checks.
     * 
     * @param responseOptions The CoAP options from the response.
     * @return A future indicating the outcome of the checks.
     */
    protected Future<?> assertCoapResponse(final OptionSet responseOptions) {
        return Future.succeededFuture();
    }

    /**
     * Gets a handler for CoAP responses.
     * 
     * @param responseHandler The handler to invoke with the outcome of the request. the handler will be invoked with a
     *            succeeded result if the response contains a 2.04 (Changed) code. Otherwise it will be invoked with a
     *            result that is failed with a {@link CoapResultException}.
     * @return The handler.
     */
    protected final CoapHandler getHandler(final Handler<AsyncResult<OptionSet>> responseHandler) {
        return getHandler(responseHandler, ResponseCode.CHANGED);
    }

    /**
     * Gets a handler for CoAP responses.
     * 
     * @param responseHandler The handler to invoke with the outcome of the request. the handler will be invoked with a
     *            succeeded result if the response contains the expected code. Otherwise it will be invoked with a
     *            result that is failed with a {@link CoapResultException}.
     * @param expectedStatusCode The status code that is expected in the response.
     * @return The handler.
     */
    protected final CoapHandler getHandler(final Handler<AsyncResult<OptionSet>> responseHandler, final ResponseCode expectedStatusCode) {
        return new CoapHandler() {

            @Override
            public void onLoad(final CoapResponse response) {
                if (response.getCode() == expectedStatusCode) {
                    responseHandler.handle(Future.succeededFuture(response.getOptions()));
                } else {
                    responseHandler.handle(Future.failedFuture(
                            new CoapResultException(toHttpStatusCode(response.getCode()), response.getResponseText())));
                }
            }

            @Override
            public void onError() {
                responseHandler
                        .handle(Future.failedFuture(new CoapResultException(HttpURLConnection.HTTP_UNAVAILABLE)));
            }
        };
    }

    /**
     * Sends some (optional) messages before uploading the batch of
     * real test messages.
     * 
     * @param client The CoAP client to use for sending the messages.
     * @return A succeeded future upon completion.
     */
    protected Future<Void> sendWarmUpMessages(final CoapClient client) {
        return Future.succeededFuture();
    }

    private static int toHttpStatusCode(final ResponseCode responseCode) {
        int result = 0;
        result += responseCode.codeClass * 100;
        result += responseCode.codeDetail;
        return result;
    }

    /**
     * Creates a URI for a resource that uses the <em>coap</em> scheme.
     * 
     * @param resource The resource path.
     * @return The URI.
     */
    protected final URI getCoapRequestUri(final String resource) {

        return getRequestUri("coap", resource);
    }

    /**
     * Creates a URI for a resource that uses the <em>coaps</em> scheme.
     * 
     * @param resource The resource path.
     * @return The URI.
     */
    protected final URI getCoapsRequestUri(final String resource) {

        return getRequestUri("coaps", resource);
    }

    private URI getRequestUri(final String scheme, final String resource) {

        final int port;
        switch(scheme) {
        case "coap": 
            port = IntegrationTestSupport.COAP_PORT;
            break;
        case "coaps": 
            port = IntegrationTestSupport.COAPS_PORT;
            break;
        default:
            throw new IllegalArgumentException();
        }
        try {
            return new URI(scheme, null, IntegrationTestSupport.COAP_HOST, port, resource, null, null);
        } catch (final URISyntaxException e) {
            // cannot happen
            return null;
        }
    }

    /**
     * Creates a CoAP request using the <em>coap</em> scheme.
     * 
     * @param code The CoAP request code.
     * @param resource the resource path.
     * @param msgNo The message number.
     * @return The request to send.
     */
    protected Request createCoapRequest(
            final Code code,
            final String resource,
            final int msgNo) {

        return createCoapRequest(code, getMessageType(), resource, msgNo);
    }

    /**
     * Creates a CoAP request using the <em>coap</em> scheme.
     * 
     * @param code The CoAP request code.
     * @param type The message type.
     * @param resource the resource path.
     * @param msgNo The message number.
     * @return The request to send.
     */
    protected Request createCoapRequest(
            final Code code,
            final Type type,
            final String resource,
            final int msgNo) {
        final Request request = new Request(code, type);
        request.setURI(getCoapRequestUri(resource));
        request.setPayload("hello " + msgNo);
        request.getOptions().setContentFormat(MediaTypeRegistry.TEXT_PLAIN);
        return request;
    }

    /**
     * Creates a CoAP request using the <em>coaps</em> scheme.
     * 
     * @param code The CoAP request code.
     * @param resource the resource path.
     * @param msgNo The message number.
     * @return The request to send.
     */
    protected Request createCoapsRequest(
            final Code code,
            final String resource,
            final int msgNo) {

        return createCoapsRequest(code, getMessageType(), resource, msgNo);
    }

    /**
     * Creates a CoAP request using the <em>coaps</em> scheme.
     * 
     * @param code The CoAP request code.
     * @param type The message type.
     * @param resource the resource path.
     * @param msgNo The message number.
     * @return The request to send.
     */
    protected Request createCoapsRequest(
            final Code code,
            final Type type,
            final String resource,
            final int msgNo) {

        final String payload = "hello " + msgNo;
        return createCoapsRequest(code, type, resource, payload.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Creates a CoAP request using the <em>coaps</em> scheme.
     * 
     * @param code The CoAP request code.
     * @param type The message type.
     * @param resource the resource path.
     * @param payload The payload to send in the request body.
     * @return The request to send.
     */
    protected Request createCoapsRequest(
            final Code code,
            final Type type,
            final String resource,
            final byte[] payload) {
        final Request request = new Request(code, type);
        request.setURI(getCoapsRequestUri(resource));
        request.setPayload(payload);
        request.getOptions().setContentFormat(MediaTypeRegistry.TEXT_PLAIN);
        return request;
    }
}
