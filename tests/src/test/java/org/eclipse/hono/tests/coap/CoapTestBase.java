/*******************************************************************************
 * Copyright (c) 2019, 2020 Contributors to the Eclipse Foundation
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

import static org.assertj.core.api.Assertions.assertThat;

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
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.apache.qpid.proton.message.Message;
import org.assertj.core.data.Index;
import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapHandler;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.Utils;
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
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.service.management.device.Device;
import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.tests.CommandEndpointConfiguration.SubscriberRole;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.util.Adapter;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.QoS;
import org.eclipse.hono.util.TimeUntilDisconnectNotification;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxTestContext;

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

    protected static final int MESSAGES_TO_SEND = 60;

    private static final Vertx VERTX = Vertx.vertx();

    private static final String COMMAND_TO_SEND = "setDarkness";
    private static final String COMMAND_JSON_KEY = "darkness";

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
    @BeforeAll
    public static void init(final VertxTestContext ctx) {

        helper = new IntegrationTestSupport(VERTX);
        helper.init().onComplete(ctx.completing());
    }

    /**
     * Creates the endpoint configuration variants for Command &amp; Control scenarios.
     *
     * @return The configurations.
     */
    static Stream<CoapCommandEndpointConfiguration> commandAndControlVariants() {
        return Stream.of(
                new CoapCommandEndpointConfiguration(SubscriberRole.DEVICE),
                new CoapCommandEndpointConfiguration(SubscriberRole.GATEWAY_FOR_ALL_DEVICES),
                new CoapCommandEndpointConfiguration(SubscriberRole.GATEWAY_FOR_SINGLE_DEVICE)
                );
    }

    /**
     * Sets up the fixture.
     *
     * @param testInfo The test meta data.
     * @throws UnknownHostException if the CoAP adapter's host name cannot be resolved.
     */
    @BeforeEach
    public void setUp(final TestInfo testInfo) throws UnknownHostException {

        logger.info("running {}", testInfo.getDisplayName());
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
    @AfterEach
    public void deleteObjects(final VertxTestContext ctx) {

        helper.deleteObjects(ctx);
    }

    /**
     * Closes the AMQP 1.0 Messaging Network client.
     *
     * @param ctx The vert.x test context.
     */
    @AfterAll
    public static void disconnect(final VertxTestContext ctx) {

        helper.disconnect().onComplete(ctx.completing());
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

    /**
     * Asserts the status code of a failed CoAP request.
     *
     * @param ctx The test context to verify the status for.
     * @param expectedStatus The expected status.
     * @param t The exception to verify.
     */
    protected static void assertStatus(final VertxTestContext ctx, final int expectedStatus, final Throwable t) {
        ctx.verify(() -> {
            assertThat(t).isInstanceOf(CoapResultException.class);
            assertThat(((CoapResultException) t).getErrorCode()).isEqualTo(expectedStatus);
        });
    }

    /**
     * Verifies that a number of messages uploaded to Hono's CoAP adapter
     * can be successfully consumed via the AMQP Messaging Network.
     *
     * @param ctx The test context.
     * @throws InterruptedException if the test fails.
     */
    @Test
    public void testUploadMessagesAnonymously(final VertxTestContext ctx) throws InterruptedException {

        final Tenant tenant = new Tenant();

        final VertxTestContext setup = new VertxTestContext();
        helper.registry.addDeviceForTenant(tenantId, tenant, deviceId, SECRET)
        .onComplete(setup.completing());
        ctx.verify(() -> assertThat(setup.awaitCompletion(5, TimeUnit.SECONDS)).isTrue());

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
    public void testUploadMessagesUsingPsk(final VertxTestContext ctx) throws InterruptedException {

        final Tenant tenant = new Tenant();

        final VertxTestContext setup = new VertxTestContext();
        helper.registry.addPskDeviceForTenant(tenantId, tenant, deviceId, SECRET)
        .onComplete(setup.completing());
        ctx.verify(() -> assertThat(setup.awaitCompletion(5, TimeUnit.SECONDS)).isTrue());

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
    public void testUploadMessagesViaGateway(final VertxTestContext ctx) throws InterruptedException {

        // GIVEN a device that is connected via two gateways
        final Tenant tenant = new Tenant();
        final String gatewayOneId = helper.getRandomDeviceId(tenantId);
        final String gatewayTwoId = helper.getRandomDeviceId(tenantId);
        final Device deviceData = new Device();
        deviceData.setVia(Arrays.asList(gatewayOneId, gatewayTwoId));

        final VertxTestContext setup = new VertxTestContext();
        helper.registry.addPskDeviceForTenant(tenantId, tenant, gatewayOneId, SECRET)
        .compose(ok -> helper.registry.addPskDeviceToTenant(tenantId, gatewayTwoId, SECRET))
        .compose(ok -> helper.registry.registerDevice(tenantId, deviceId, deviceData))
        .onComplete(setup.completing());
        ctx.verify(() -> assertThat(setup.awaitCompletion(5, TimeUnit.SECONDS)).isTrue());

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
            final VertxTestContext ctx,
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
            final VertxTestContext ctx,
            final String tenantId,
            final Supplier<Future<?>> warmUp,
            final Consumer<Message> messageConsumer,
            final Function<Integer, Future<OptionSet>> requestSender) throws InterruptedException {
        testUploadMessages(ctx, tenantId, warmUp, messageConsumer, requestSender, MESSAGES_TO_SEND, null);
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
     * @param expectedQos The expected QoS level, may be {@code null} leading to expecting the default for event or telemetry.
     * @throws InterruptedException if the test is interrupted before it has finished.
     */
    protected void testUploadMessages(
            final VertxTestContext ctx,
            final String tenantId,
            final Supplier<Future<?>> warmUp,
            final Consumer<Message> messageConsumer,
            final Function<Integer, Future<OptionSet>> requestSender,
            final int numberOfMessages,
            final QoS expectedQos) throws InterruptedException {

        final CountDownLatch received = new CountDownLatch(numberOfMessages);

        final VertxTestContext setup = new VertxTestContext();
        createConsumer(tenantId, msg -> {
            logger.trace("received {}", msg);
            assertMessageProperties(ctx, msg);
            assertQosLevel(ctx, msg, getExpectedQoS(expectedQos));
            if (messageConsumer != null) {
                messageConsumer.accept(msg);
            }
            received.countDown();
            if (received.getCount() % 20 == 0) {
                logger.info("messages received: {}", numberOfMessages - received.getCount());
            }
        })
        .compose(ok -> Optional.ofNullable(warmUp).map(w -> w.get()).orElse(Future.succeededFuture()))
        .onComplete(setup.completing());
        ctx.verify(() -> assertThat(setup.awaitCompletion(5, TimeUnit.SECONDS)).isTrue());

        final long start = System.currentTimeMillis();
        final AtomicInteger messageCount = new AtomicInteger(0);

        while (messageCount.get() < numberOfMessages) {

            final CountDownLatch sending = new CountDownLatch(1);
            requestSender.apply(messageCount.getAndIncrement()).compose(this::assertCoapResponse)
                    .onComplete(attempt -> {
                        if (attempt.succeeded()) {
                            logger.debug("sent message {}", messageCount.get());
                        } else {
                            logger.info("failed to send message {}: {}", messageCount.get(),
                                    attempt.cause().getMessage());
                            ctx.failNow(attempt.cause());
                        }
                        sending.countDown();;
                    });

            if (messageCount.get() % 20 == 0) {
                logger.info("messages sent: {}", messageCount.get());
            }
            sending.await();
        }

        final long timeToWait = Math.max(TEST_TIMEOUT_MILLIS - 1000, Math.round(numberOfMessages * 20));
        if (received.await(timeToWait, TimeUnit.MILLISECONDS)) {
            logger.info("sent {} and received {} messages after {} milliseconds",
                    messageCount, numberOfMessages - received.getCount(), System.currentTimeMillis() - start);
            ctx.completeNow();
        } else {
            logger.info("sent {} and received {} messages after {} milliseconds",
                    messageCount, numberOfMessages - received.getCount(), System.currentTimeMillis() - start);
            ctx.failNow(new AssertionError("did not receive all messages sent"));
        }
    }

    /**
     * Verifies that the adapter fails to authenticate a device if the shared key registered
     * for the device does not match the key used by the device in the DTLS handshake.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    @Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
    public void testUploadFailsForNonMatchingSharedKey(final VertxTestContext ctx) {

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
        })
        .onComplete(ctx.failing(t -> {
            // THEN the request fails because the DTLS handshake cannot be completed
            assertStatus(ctx, HttpURLConnection.HTTP_UNAVAILABLE, t);
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that the CoAP adapter rejects messages from a device that belongs to a tenant for which the CoAP adapter
     * has been disabled.
     *
     * @param ctx The test context
     */
    @Test
    @Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
    public void testUploadMessageFailsForDisabledTenant(final VertxTestContext ctx) {

        // GIVEN a tenant for which the CoAP adapter is disabled
        final Tenant tenant = new Tenant();
        tenant.addAdapterConfig(new Adapter(Constants.PROTOCOL_ADAPTER_TYPE_COAP).setEnabled(false));

        helper.registry.addPskDeviceForTenant(tenantId, tenant, deviceId, SECRET)
        .compose(ok -> {

            // WHEN a device that belongs to the tenant uploads a message
            final CoapClient client = getCoapsClient(deviceId, tenantId, SECRET);
            final Promise<OptionSet> result = Promise.promise();
            client.advanced(getHandler(result, ResponseCode.FORBIDDEN), createCoapsRequest(Code.POST, getPostResource(), 0));
            return result.future();
        })
        .onComplete(ctx.completing());
    }

    /**
     * Verifies that the CoAP adapter rejects messages from a disabled device.
     *
     * @param ctx The test context
     */
    @Test
    @Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
    public void testUploadMessageFailsForDisabledDevice(final VertxTestContext ctx) {

        // GIVEN a disabled device
        final Tenant tenant = new Tenant();
        final Device deviceData = new Device();
        deviceData.setEnabled(false);

        helper.registry.addPskDeviceForTenant(tenantId, tenant, deviceId, deviceData, SECRET)
        .compose(ok -> {

            // WHEN the device tries to upload a message
            final CoapClient client = getCoapsClient(deviceId, tenantId, SECRET);
            final Promise<OptionSet> result = Promise.promise();
            client.advanced(getHandler(result, ResponseCode.NOT_FOUND), createCoapsRequest(Code.POST, getPostResource(), 0));
            return result.future();
        })
        .onComplete(ctx.completing());
    }

    /**
     * Verifies that the CoAP adapter rejects messages from a disabled gateway
     * for an enabled device with a 403.
     *
     * @param ctx The test context
     */
    @Test
    @Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
    public void testUploadMessageFailsForDisabledGateway(final VertxTestContext ctx) {

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
            client.advanced(getHandler(result, ResponseCode.FORBIDDEN), createCoapsRequest(Code.PUT, getPutResource(tenantId, deviceId), 0));
            return result.future();
        })
        .onComplete(ctx.completing());
    }

    /**
     * Verifies that the CoAP adapter rejects messages from a gateway for a device that it is not authorized for with a
     * 403.
     *
     * @param ctx The test context
     */
    @Test
    @Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
    public void testUploadMessageFailsForUnauthorizedGateway(final VertxTestContext ctx) {

        // GIVEN a device that is connected via gateway "not-the-created-gateway"
        final Tenant tenant = new Tenant();
        final String gatewayId = helper.getRandomDeviceId(tenantId);
        final Device deviceData = new Device();
        deviceData.setVia(Collections.singletonList("not-the-created-gateway"));

        helper.registry.addPskDeviceForTenant(tenantId, tenant, gatewayId, SECRET)
        .compose(ok -> helper.registry.registerDevice(tenantId, deviceId, deviceData))
        .compose(ok -> {

            // WHEN another gateway tries to upload a message for the device
            final Promise<OptionSet> result = Promise.promise();
            final CoapClient client = getCoapsClient(gatewayId, tenantId, SECRET);
            client.advanced(getHandler(result, ResponseCode.FORBIDDEN),
                    createCoapsRequest(Code.PUT, getPutResource(tenantId, deviceId), 0));
            return result.future();
        })
        .onComplete(ctx.completing());
    }

    /**
     * Verifies that the CoAP adapter delivers a command to a device and accepts
     * the corresponding response from the device.
     *
     * @param endpointConfig The endpoints to use for sending/receiving commands.
     * @param ctx The test context.
     * @throws InterruptedException if the test fails.
     */
    @ParameterizedTest(name = IntegrationTestSupport.PARAMETERIZED_TEST_NAME_PATTERN)
    @MethodSource("commandAndControlVariants")
    public void testUploadMessagesWithTtdThatReplyWithCommand(
            final CoapCommandEndpointConfiguration endpointConfig,
            final VertxTestContext ctx) throws InterruptedException {

        final Tenant tenant = new Tenant();
        testUploadMessagesWithTtdThatReplyWithCommand(endpointConfig, tenant, ctx);
    }

    private void testUploadMessagesWithTtdThatReplyWithCommand(
            final CoapCommandEndpointConfiguration endpointConfig,
            final Tenant tenant, final VertxTestContext ctx) throws InterruptedException {

        final String expectedCommand = String.format("%s=%s", Constants.HEADER_COMMAND, COMMAND_TO_SEND);

        final VertxTestContext setup = new VertxTestContext();

        helper.registry.addPskDeviceForTenant(tenantId, tenant, deviceId, SECRET).onComplete(setup.completing());
        ctx.verify(() -> assertThat(setup.awaitCompletion(5, TimeUnit.SECONDS)).isTrue());

        final String commandTargetDeviceId = endpointConfig.isSubscribeAsGateway()
                ? helper.setupGatewayDeviceBlocking(tenantId, deviceId, 5)
                : deviceId;
        final String subscribingDeviceId = endpointConfig.isSubscribeAsGatewayForSingleDevice() ? commandTargetDeviceId
                : deviceId;

        final CoapClient client = getCoapsClient(deviceId, tenantId, SECRET);
        final AtomicInteger counter = new AtomicInteger();

        testUploadMessages(ctx, tenantId,
                () -> warmUp(client, createCoapsRequest(Code.POST, getPostResource(), 0)),
                msg -> {

                    TimeUntilDisconnectNotification.fromMessage(msg)
                        .map(notification -> {
                            logger.trace("received piggy backed message [ttd: {}]: {}", notification.getTtd(), msg);
                            ctx.verify(() -> {
                                assertThat(notification.getTenantId()).isEqualTo(tenantId);
                                assertThat(notification.getDeviceId()).isEqualTo(subscribingDeviceId);
                            });
                            // now ready to send a command
                            final JsonObject inputData = new JsonObject().put(COMMAND_JSON_KEY, (int) (Math.random() * 100));
                            return helper.sendCommand(
                                    tenantId,
                                    commandTargetDeviceId,
                                    COMMAND_TO_SEND,
                                    "application/json",
                                    inputData.toBuffer(),
                                    // set "forceCommandRerouting" message property so that half the command are rerouted via the AMQP network
                                    IntegrationTestSupport.newCommandMessageProperties(() -> counter.getAndIncrement() >= MESSAGES_TO_SEND / 2),
                                    notification.getMillisecondsUntilExpiry())
                                    .map(response -> {
                                        ctx.verify(() -> {
                                            assertThat(response.getContentType()).isEqualTo("text/plain");
                                            assertThat(response.getApplicationProperty(MessageHelper.APP_PROPERTY_DEVICE_ID, String.class)).isEqualTo(commandTargetDeviceId);
                                            assertThat(response.getApplicationProperty(MessageHelper.APP_PROPERTY_TENANT_ID, String.class)).isEqualTo(tenantId);
                                        });
                                        return response;
                                    });
                        });
                },
                count -> {
                    final Promise<OptionSet> result = Promise.promise();
                    final Request request = createCoapsRequest(endpointConfig, commandTargetDeviceId, count);
                    request.getOptions().addUriQuery(String.format("%s=%d", Constants.HEADER_TIME_TILL_DISCONNECT, 5));
                    client.advanced(getHandler(result, ResponseCode.CHANGED), request);
                    return result.future()
                            .map(responseOptions -> {
                                ctx.verify(() -> {
                                    assertResponseContainsCommand(
                                            endpointConfig,
                                            responseOptions,
                                            expectedCommand,
                                            tenantId,
                                            commandTargetDeviceId);
                                });
                                final List<String> locationPath = responseOptions.getLocationPath();
                                return locationPath.get(locationPath.size() - 1);
                            })
                            .compose(receivedCommandRequestId -> {
                                // send a response to the command now
                                final String responseUri = endpointConfig.getCommandResponseUri(tenantId, commandTargetDeviceId, receivedCommandRequestId);
                                logger.debug("sending response to command [uri: {}]", responseUri);

                                final Buffer body = Buffer.buffer("ok");
                                final Promise<OptionSet> commandResponseResult = Promise.promise();
                                final Request commandResponseRequest;
                                if (endpointConfig.isSubscribeAsGateway()) {
                                    // GW uses PUT when acting on behalf of a device
                                    commandResponseRequest = createCoapsRequest(Code.PUT, Type.CON, responseUri, body.getBytes());
                                } else {
                                    commandResponseRequest = createCoapsRequest(Code.POST, Type.CON, responseUri, body.getBytes());
                                }
                                commandResponseRequest.getOptions()
                                    .setContentFormat(MediaTypeRegistry.TEXT_PLAIN)
                                    .addUriQuery(String.format("%s=%d", Constants.HEADER_COMMAND_RESPONSE_STATUS, 200));
                                client.advanced(getHandler(commandResponseResult, ResponseCode.CHANGED), commandResponseRequest);
                                return commandResponseResult.future()
                                        .recover(thr -> {
                                            // wrap exception, making clear it occurred when sending the command response,
                                            // not the preceding telemetry/event message
                                            final String msg = "Error sending command response: " + thr.getMessage();
                                            return Future.failedFuture(thr instanceof ServiceInvocationException
                                                    ? new ServiceInvocationException(((ServiceInvocationException) thr).getErrorCode(), msg, thr)
                                                    : new RuntimeException(msg, thr));
                                        });
                            });
                });
    }

    private void assertResponseContainsCommand(
            final CoapCommandEndpointConfiguration endpointConfiguration,
            final OptionSet responseOptions,
            final String expectedCommand,
            final String tenantId,
            final String commandTargetDeviceId) {

        assertThat(responseOptions.getLocationQuery())
            .as("location query must contain parameter [%s]", expectedCommand)
            .contains(expectedCommand);
        assertThat(responseOptions.getContentFormat()).isEqualTo(MediaTypeRegistry.APPLICATION_JSON);
        int idx = 0;
        assertThat(responseOptions.getLocationPath()).contains(CommandConstants.COMMAND_RESPONSE_ENDPOINT, Index.atIndex(idx++));
        if (endpointConfiguration.isSubscribeAsGateway()) {
            assertThat(responseOptions.getLocationPath()).contains(tenantId, Index.atIndex(idx++));
            assertThat(responseOptions.getLocationPath()).contains(commandTargetDeviceId, Index.atIndex(idx++));
        }
        // request ID
        assertThat(responseOptions.getLocationPath().get(idx))
            .as("location path must contain command request ID")
            .isNotNull();
    }

    /**
     * Verifies that the CoAP adapter delivers a one-way command to a device.
     *
     * @param endpointConfig The endpoints to use for sending/receiving commands.
     * @param ctx The test context
     * @throws InterruptedException if the test fails.
     */
    @ParameterizedTest(name = IntegrationTestSupport.PARAMETERIZED_TEST_NAME_PATTERN)
    @MethodSource("commandAndControlVariants")
    @Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
    public void testUploadMessagesWithTtdThatReplyWithOneWayCommand(
            final CoapCommandEndpointConfiguration endpointConfig,
            final VertxTestContext ctx) throws InterruptedException {

        final Tenant tenant = new Tenant();
        final String expectedCommand = String.format("%s=%s", Constants.HEADER_COMMAND, COMMAND_TO_SEND);

        final VertxTestContext setup = new VertxTestContext();
        helper.registry.addPskDeviceForTenant(tenantId, tenant, deviceId, SECRET).onComplete(setup.completing());
        ctx.verify(() -> assertThat(setup.awaitCompletion(5, TimeUnit.SECONDS)).isTrue());

        final CoapClient client = getCoapsClient(deviceId, tenantId, SECRET);
        final AtomicInteger counter = new AtomicInteger();

        final String commandTargetDeviceId = endpointConfig.isSubscribeAsGateway()
                ? helper.setupGatewayDeviceBlocking(tenantId, deviceId, 5)
                : deviceId;
        final String subscribingDeviceId = endpointConfig.isSubscribeAsGatewayForSingleDevice() ? commandTargetDeviceId
                : deviceId;

        testUploadMessages(ctx, tenantId,
                () -> warmUp(client, createCoapsRequest(Code.POST, getPostResource(), 0)),
                msg -> {
                    final Integer ttd = MessageHelper.getTimeUntilDisconnect(msg);
                    logger.debug("north-bound-cmd received {}, ttd: {}", msg, ttd);
                    TimeUntilDisconnectNotification.fromMessage(msg).ifPresent(notification -> {
                        ctx.verify(() -> {
                            assertThat(notification.getTenantId()).isEqualTo(tenantId);
                            assertThat(notification.getDeviceId()).isEqualTo(subscribingDeviceId);
                        });
                        logger.debug("send one-way-command");
                        final JsonObject inputData = new JsonObject().put(COMMAND_JSON_KEY, (int) (Math.random() * 100));
                        helper.sendOneWayCommand(
                                tenantId,
                                commandTargetDeviceId,
                                COMMAND_TO_SEND,
                                "application/json",
                                inputData.toBuffer(),
                                // set "forceCommandRerouting" message property so that half the command are rerouted via the AMQP network
                                IntegrationTestSupport.newCommandMessageProperties(() -> counter.getAndIncrement() >= MESSAGES_TO_SEND / 2),
                                notification.getMillisecondsUntilExpiry() / 2);
                    });
                },
                count -> {
                    final Promise<OptionSet> result = Promise.promise();
                    final Request request = createCoapsRequest(endpointConfig, commandTargetDeviceId, count);
                    request.getOptions().addUriQuery(String.format("%s=%d", Constants.HEADER_TIME_TILL_DISCONNECT, 4));
                    logger.debug("south-bound send {}", request);
                    client.advanced(getHandler(result, ResponseCode.CHANGED), request);
                    return result.future()
                            .map(responseOptions -> {
                                ctx.verify(() -> {
                                    assertResponseContainsOneWayCommand(
                                            endpointConfig,
                                            responseOptions,
                                            expectedCommand,
                                            tenantId,
                                            commandTargetDeviceId);
                                });
                                return responseOptions;
                            });
                });
    }

    private void assertResponseContainsOneWayCommand(
            final CoapCommandEndpointConfiguration endpointConfiguration,
            final OptionSet responseOptions,
            final String expectedCommand,
            final String tenantId,
            final String commandTargetDeviceId) {

        assertThat(responseOptions.getLocationQuery())
            .as("response doesn't contain command")
            .contains(expectedCommand);
        assertThat(responseOptions.getContentFormat()).isEqualTo(MediaTypeRegistry.APPLICATION_JSON);
        assertThat(responseOptions.getLocationPath()).contains(CommandConstants.COMMAND_ENDPOINT, Index.atIndex(0));
        if (endpointConfiguration.isSubscribeAsGateway()) {
            assertThat(responseOptions.getLocationPath()).contains(tenantId, Index.atIndex(1));
            assertThat(responseOptions.getLocationPath()).contains(commandTargetDeviceId, Index.atIndex(2));
        }
    }

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

    private QoS getExpectedQoS(final QoS qos) {
        if (qos != null) {
            return qos;
        }

        switch (getMessageType()) {
            case CON:
                return QoS.AT_LEAST_ONCE;
            case NON:
                return QoS.AT_MOST_ONCE;
            default:
                throw new IllegalArgumentException("Either QoS must be non-null or message type must be CON or NON!");
        }
    }

    private void  assertQosLevel(final VertxTestContext ctx, final Message msg, final QoS expectedQos) {
        ctx.verify(() -> assertThat(MessageHelper.getQoS(msg)).isEqualTo(expectedQos.ordinal()));
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
    protected void assertAdditionalMessageProperties(final VertxTestContext ctx, final Message msg) {
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
                    logger.debug("=> received {}", Utils.prettyPrint(response));
                    responseHandler.handle(Future.succeededFuture(response.getOptions()));
                } else {
                    logger.warn("expected {} => received {}", expectedStatusCode, Utils.prettyPrint(response));
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
        switch (scheme) {
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
     * @param endpointConfig The endpoint configuration.
     * @param requestDeviceId The identifier of the device to publish data for.
     * @param msgNo The message number.
     * @return The request to send.
     */
    protected Request createCoapsRequest(
            final CoapCommandEndpointConfiguration endpointConfig,
            final String requestDeviceId,
            final int msgNo) {

        if (endpointConfig.isSubscribeAsGatewayForSingleDevice()) {
            return createCoapsRequest(Code.PUT, getMessageType(), getPutResource(tenantId, requestDeviceId), msgNo);
        }
        return createCoapsRequest(Code.POST, getMessageType(), getPostResource(), msgNo);
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
