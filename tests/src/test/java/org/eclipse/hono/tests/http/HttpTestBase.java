/*******************************************************************************
 * Copyright (c) 2016, 2023 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.tests.http;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Stream;

import javax.security.auth.x500.X500Principal;

import org.eclipse.hono.application.client.DownstreamMessage;
import org.eclipse.hono.application.client.MessageConsumer;
import org.eclipse.hono.application.client.MessageContext;
import org.eclipse.hono.service.management.device.Device;
import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.service.metric.MetricsTags;
import org.eclipse.hono.tests.CommandEndpointConfiguration.SubscriberRole;
import org.eclipse.hono.tests.CrudHttpClient;
import org.eclipse.hono.tests.DownstreamMessageAssertions;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.tests.Tenants;
import org.eclipse.hono.util.Adapter;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.QoS;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.handler.codec.http.QueryStringEncoder;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.PemTrustOptions;
import io.vertx.core.net.SelfSignedCertificate;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.predicate.ResponsePredicate;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxTestContext;

/**
 * Base class for HTTP adapter integration tests.
 *
 */
public abstract class HttpTestBase {

    /**
     * The default password of devices.
     */
    protected static final String PWD = "secret";
    /**
     * The CORS <em>origin</em> address to use for sending messages.
     */
    protected static final String ORIGIN_URI = "http://hono.eclipse.org";

    /**
     * A helper for accessing the AMQP 1.0 Messaging Network and
     * for managing tenants/devices/credentials.
     */
    protected static IntegrationTestSupport helper;
    /**
     * The number of messages to send during a test run.
     */
    protected static final int MESSAGES_TO_SEND = 60;

    private static final String COMMAND_TO_SEND = "setBrightness";
    private static final String COMMAND_JSON_KEY = "brightness";

    private static final String ORIGIN_WILDCARD = "*";
    private static final long  TEST_TIMEOUT_MILLIS = 20000; // 20 seconds

    /**
     * The default options to use for creating HTTP clients.
     */
    private static HttpClientOptions defaultOptions;

    /**
     * A logger to be shared with subclasses.
     */
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * A client for connecting to the HTTP adapter.
     */
    protected CrudHttpClient httpClient;
    /**
     * A client for connecting to the HTTP adapter using a client certificate
     * for authentication.
     */
    protected CrudHttpClient httpClientWithClientCert;
    /**
     * The random tenant identifier created for each test case.
     */
    protected String tenantId;
    /**
     * The random device identifier created for each test case.
     */
    protected String deviceId;
    /**
     * The BASIC Auth header value created for the random device.
     */
    protected String authorization;
    /**
     * The self-signed certificate that a device may use for authentication.
     */
    protected SelfSignedCertificate deviceCert;

    private final Vertx vertx = Vertx.vertx();

    private long testStartTimeMillis;

    /**
     * Creates default client options.
     */
    @BeforeAll
    public static void init() {

        defaultOptions = new HttpClientOptions()
                .setDefaultHost(IntegrationTestSupport.HTTP_HOST)
                .setDefaultPort(IntegrationTestSupport.HTTPS_PORT)
                .setTrustOptions(new PemTrustOptions().addCertPath(IntegrationTestSupport.TRUST_STORE_PATH))
                .setVerifyHost(false)
                .setSsl(true)
                .setEnabledSecureTransportProtocols(Set.of(IntegrationTestSupport.TLS_VERSION_1_2));

    }

    /**
     * Sets up the fixture.
     *
     * @param testInfo Meta info about the test being run.
     * @param ctx The vert.x test context.
     */
    @BeforeEach
    public void setUp(final TestInfo testInfo, final VertxTestContext ctx) {

        testStartTimeMillis = System.currentTimeMillis();
        logger.info("running {}", testInfo.getDisplayName());
        helper = new IntegrationTestSupport(vertx);
        logger.info("using HTTP adapter [host: {}, http port: {}, https port: {}]",
                IntegrationTestSupport.HTTP_HOST,
                IntegrationTestSupport.HTTP_PORT,
                IntegrationTestSupport.HTTPS_PORT);

        deviceCert = SelfSignedCertificate.create(UUID.randomUUID().toString());
        httpClient = new CrudHttpClient(vertx, new HttpClientOptions(defaultOptions));
        httpClientWithClientCert = new CrudHttpClient(vertx, new HttpClientOptions(defaultOptions)
                .setKeyCertOptions(deviceCert.keyCertOptions()));

        tenantId = helper.getRandomTenantId();
        deviceId = helper.getRandomDeviceId(tenantId);
        authorization = getBasicAuth(tenantId, deviceId, PWD);
        helper.init().onComplete(ctx.succeedingThenComplete());
    }

    /**
     * Deletes all temporary objects from the Device Registry which
     * have been created during the last test execution.
     * Disconnects from the AMQP Messaging Network.
     *
     * @param ctx The vert.x test context.
     */
    @AfterEach
    public void deleteObjects(final VertxTestContext ctx) {

        helper.deleteObjects(ctx);
        if (deviceCert != null) {
            deviceCert.delete();
        }

    }

    /**
     * Disconnect helper.
     *
     * @param ctx The vert.x test context.
     */
    @AfterEach
    public void disconnect(final VertxTestContext ctx) {
        helper.disconnect().onComplete(ctx.succeedingThenComplete());
    }

    /**
     * Creates the endpoint configuration variants for Command &amp; Control scenarios.
     *
     * @return The configurations.
     */
    static Stream<HttpCommandEndpointConfiguration> commandAndControlVariants() {
        return Stream.of(
                new HttpCommandEndpointConfiguration(SubscriberRole.DEVICE),
                new HttpCommandEndpointConfiguration(SubscriberRole.GATEWAY_FOR_ALL_DEVICES),
                new HttpCommandEndpointConfiguration(SubscriberRole.GATEWAY_FOR_SINGLE_DEVICE)
                );
    }

    /**
     * Gets the (relative) URI of the endpoint to send requests to.
     *
     * @return The URI.
     */
    protected abstract String getEndpointUri();

    /**
     * Creates a test specific message consumer.
     *
     * @param tenantId        The tenant to create the consumer for.
     * @param messageConsumer The handler to invoke for every message received.
     * @return A future succeeding with the created consumer.
     */
    protected abstract Future<MessageConsumer> createConsumer(
            String tenantId,
            Handler<DownstreamMessage<? extends MessageContext>> messageConsumer);

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

        final String customContentType = "application/custom";
        final MultiMap requestHeaders = MultiMap.caseInsensitiveMultiMap()
                .add(HttpHeaders.CONTENT_TYPE, customContentType)
                .add(HttpHeaders.AUTHORIZATION, authorization)
                .add(HttpHeaders.ORIGIN, ORIGIN_URI);

        helper.registry
            .addDeviceForTenant(tenantId, new Tenant(), deviceId, PWD)
            .compose(response -> createConsumer(tenantId, msg -> {
                logger.trace("received {}", msg);
                ctx.verify(() -> {
                    DownstreamMessageAssertions.assertTelemetryApiProperties(msg);
                    DownstreamMessageAssertions.assertMessageContainsAdapterAndAddress(msg);
                    assertThat(msg.getContentType()).isEqualTo(customContentType);
                    assertThat(msg.getPayload()).isNull();
                    assertAdditionalMessageProperties(msg);
                });
                ctx.completeNow();
            }))
            .compose(consumer -> httpClient.create(
                    getEndpointUri(),
                    null,
                    requestHeaders,
                    ResponsePredicate.SC_ACCEPTED))
            .onFailure(ctx::failNow);
    }

    /**
     * Verifies that the adapter rejects empty messages that have no content type set with a 400 status code.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    @Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
    public void testUploadEmptyMessageWithoutContentTypeFails(final VertxTestContext ctx) {

        final MultiMap requestHeaders = MultiMap.caseInsensitiveMultiMap()
                .add(HttpHeaders.AUTHORIZATION, authorization)
                .add(HttpHeaders.ORIGIN, ORIGIN_URI);

        helper.registry
            .addDeviceForTenant(tenantId, new Tenant(), deviceId, PWD)
            .compose(response -> createConsumer(tenantId, msg -> {
                logger.trace("received {}", msg);
                ctx.failNow("downstream consumer should not have received message");
            }))
            .compose(consumer -> httpClient.create(
                    getEndpointUri(),
                    null,
                    requestHeaders,
                    ResponsePredicate.SC_BAD_REQUEST))
            .onComplete(ctx.succeedingThenComplete());

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

        final MultiMap requestHeaders = MultiMap.caseInsensitiveMultiMap()
                .add(HttpHeaders.AUTHORIZATION, authorization)
                .add(HttpHeaders.ORIGIN, ORIGIN_URI);

        helper.registry
            .addDeviceForTenant(tenantId, new Tenant(), deviceId, PWD)
            .compose(response -> createConsumer(tenantId, msg -> {
                logger.trace("received {}", msg);
                ctx.verify(() -> {
                    DownstreamMessageAssertions.assertTelemetryApiProperties(msg);
                    DownstreamMessageAssertions.assertMessageContainsAdapterAndAddress(msg);
                    assertThat(msg.getContentType()).isEqualTo(MessageHelper.CONTENT_TYPE_OCTET_STREAM);
                    assertThat(msg.getPayload().length()).isGreaterThan(0);
                    assertAdditionalMessageProperties(msg);
                });
                ctx.completeNow();
            }))
            .compose(consumer -> httpClient.create(
                    getEndpointUri(),
                    Buffer.buffer("some payload"),
                    requestHeaders,
                    ResponsePredicate.SC_ACCEPTED))
            .onFailure(ctx::failNow);

    }

    /**
     * Verifies that the adapter rejects non-empty messages that are marked as empty
     * notifications with a 400 status code.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    @Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
    public void testUploadNonEmptyMessageMarkedAsEmptyNotificationFails(final VertxTestContext ctx) {

        final MultiMap requestHeaders = MultiMap.caseInsensitiveMultiMap()
                .add(HttpHeaders.CONTENT_TYPE, EventConstants.CONTENT_TYPE_EMPTY_NOTIFICATION)
                .add(HttpHeaders.AUTHORIZATION, authorization)
                .add(HttpHeaders.ORIGIN, ORIGIN_URI);

        helper.registry
            .addDeviceForTenant(tenantId, new Tenant(), deviceId, PWD)
            .compose(response -> createConsumer(tenantId, msg -> {
                logger.trace("received {}", msg);
                ctx.failNow("downstream consumer should not have received message");
            }))
            .compose(consumer -> httpClient.create(
                    getEndpointUri(),
                    Buffer.buffer("some payload"),
                    requestHeaders,
                    ResponsePredicate.SC_BAD_REQUEST))
            .onComplete(ctx.succeedingThenComplete());

    }

    /**
     * Verifies that a number of messages uploaded to Hono's HTTP adapter
     * using HTTP Basic auth can be successfully consumed via the AMQP Messaging Network.
     *
     * @param ctx The test context.
     * @throws InterruptedException if the test fails.
     */
    @Test
    public void testUploadMessagesUsingBasicAuth(final VertxTestContext ctx) throws InterruptedException {

        final VertxTestContext setup = new VertxTestContext();
        final Tenant tenant = new Tenant();
        final MultiMap requestHeaders = MultiMap.caseInsensitiveMultiMap()
                .add(HttpHeaders.CONTENT_TYPE, "text/plain")
                .add(HttpHeaders.AUTHORIZATION, authorization)
                .add(HttpHeaders.ORIGIN, ORIGIN_URI);
        final MultiMap requestHeadersWithEncodedCredentials = MultiMap.caseInsensitiveMultiMap()
                .add(HttpHeaders.CONTENT_TYPE, "text/plain")
                .add(HttpHeaders.AUTHORIZATION, getBasicAuthWithEncodedCredentials(tenantId, deviceId, PWD))
                .add(HttpHeaders.ORIGIN, ORIGIN_URI);

        helper.registry
            .addDeviceForTenant(tenantId, tenant, deviceId, PWD)
            .onComplete(setup.succeedingThenComplete());

        assertThat(setup.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
        if (setup.failed()) {
            ctx.failNow(setup.causeOfFailure());
            return;
        }

        final var clientOptions = new HttpClientOptions(defaultOptions)
            .setEnabledSecureTransportProtocols(Set.of("TLSv1.3"));
        final var tls13BasedClient = new CrudHttpClient(vertx, new HttpClientOptions(clientOptions));
        testUploadMessages(ctx, tenantId,
                count -> {
                    return tls13BasedClient.create(
                            getEndpointUri(),
                            Buffer.buffer("hello " + count),
                            count % 2 == 0 ? requestHeaders : requestHeadersWithEncodedCredentials,
                                    ResponsePredicate.status(HttpURLConnection.HTTP_ACCEPTED))
                        .compose(this::verifyAccessControlExposedHeaders);
                });
    }

    /**
     * Verifies that a number of messages uploaded to the HTTP adapter via a gateway using HTTP Basic auth can be
     * successfully consumed via the AMQP Messaging Network.
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
        // use device ID with colon to verify proper decoding of device ID in request URI path
        final String deviceId = "test:" + this.deviceId;
        final Device device = new Device();
        device.setVia(Arrays.asList(gatewayOneId, gatewayTwoId));

        final VertxTestContext setup = new VertxTestContext();
        helper.registry.addDeviceForTenant(tenantId, tenant, gatewayOneId, PWD)
            .compose(ok -> helper.registry.addDeviceToTenant(tenantId, gatewayTwoId, PWD))
            .compose(ok -> helper.registry.registerDevice(tenantId, deviceId, device))
            .onComplete(setup.succeedingThenComplete());

        assertThat(setup.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
        if (setup.failed()) {
            ctx.failNow(setup.causeOfFailure());
            return;
        }

        final MultiMap requestHeadersOne = MultiMap.caseInsensitiveMultiMap()
                .add(HttpHeaders.CONTENT_TYPE, "text/plain")
                .add(HttpHeaders.AUTHORIZATION, getBasicAuth(tenantId, gatewayOneId, PWD))
                .add(HttpHeaders.ORIGIN, ORIGIN_URI);

        final MultiMap requestHeadersTwo = MultiMap.caseInsensitiveMultiMap()
                .add(HttpHeaders.CONTENT_TYPE, "text/plain")
                .add(HttpHeaders.AUTHORIZATION, getBasicAuth(tenantId, gatewayTwoId, PWD))
                .add(HttpHeaders.ORIGIN, ORIGIN_URI);

        final String encodedDeviceId = URLEncoder.encode(deviceId, StandardCharsets.UTF_8);
        final String uriOne = String.format("%s/%s/%s", getEndpointUri(), tenantId, encodedDeviceId);
        final String uriTwo = String.format("%s//%s", getEndpointUri(), encodedDeviceId);

        testUploadMessages(
                ctx,
                tenantId,
                count -> {
                    final MultiMap headers;
                    final String uri;
                    if ((count.intValue() & 1) == 0) {
                        headers = requestHeadersOne;
                        uri = uriOne;
                    } else {
                        headers = requestHeadersTwo;
                        uri = uriTwo;
                    }
                    // GW uses PUT when acting on behalf of a device
                    return httpClient.update(
                            uri,
                            Buffer.buffer("hello " + count),
                            headers,
                            ResponsePredicate.status(HttpURLConnection.HTTP_ACCEPTED));
                });
    }

    /**
     * Verifies that a number of messages uploaded to Hono's HTTP adapter using client certificate based authentication
     * can be successfully consumed via the AMQP Messaging Network.
     *
     * @param ctx The test context.
     * @throws InterruptedException if the test fails.
     */
    @Test
    public void testUploadMessagesUsingClientCertificate(final VertxTestContext ctx) throws InterruptedException {

        final VertxTestContext setup = new VertxTestContext();
        final MultiMap requestHeaders = MultiMap.caseInsensitiveMultiMap()
                .add(HttpHeaders.CONTENT_TYPE, "text/plain")
                .add(HttpHeaders.ORIGIN, ORIGIN_URI);

        helper.getCertificate(deviceCert.certificatePath())
           .compose(cert -> {

                final var tenant = Tenants.createTenantForTrustAnchor(cert);
                return helper.registry.addDeviceForTenant(tenantId, tenant, deviceId, cert);

            })
            .onComplete(setup.succeedingThenComplete());

        assertThat(setup.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
        if (setup.failed()) {
            ctx.failNow(setup.causeOfFailure());
            return;
        }

        testUploadMessages(ctx, tenantId, count -> {
            return httpClientWithClientCert.create(
                    getEndpointUri(),
                    Buffer.buffer("hello " + count),
                    requestHeaders,
                    ResponsePredicate.status(HttpURLConnection.HTTP_ACCEPTED))
                .compose(this::verifyAccessControlExposedHeaders);
        });
    }

    /**
     * Verifies that the adapter opens a connection if auto-provisioning is enabled for the device certificate.
     *
     * @param ctx The test context.
     * @throws InterruptedException if the test fails.
     */
    @Test
    public void testConnectSucceedsWithAutoProvisioning(final VertxTestContext ctx) throws InterruptedException {

        final VertxTestContext setup = new VertxTestContext();
        final MultiMap requestHeaders = MultiMap.caseInsensitiveMultiMap()
                .add(HttpHeaders.CONTENT_TYPE, "text/plain")
                .add(HttpHeaders.ORIGIN, ORIGIN_URI);

        helper.getCertificate(deviceCert.certificatePath())
            .compose(cert -> {
                // GIVEN a tenant configured for auto-provisioning
                final var tenant = Tenants.createTenantForTrustAnchor(cert);
                tenant.getTrustedCertificateAuthorities().get(0).setAutoProvisioningEnabled(true);
                return helper.registry.addTenant(tenantId, tenant);

            })
            .onComplete(setup.succeedingThenComplete());

        assertThat(setup.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
        if (setup.failed()) {
            ctx.failNow(setup.causeOfFailure());
            return;
        }

        testUploadMessages(
                ctx,
                tenantId,
                null,
                count -> httpClientWithClientCert.create(
                        getEndpointUri(),
                        Buffer.buffer("hello " + count),
                        requestHeaders,
                        ResponsePredicate.status(HttpURLConnection.HTTP_ACCEPTED))
                    .compose(this::verifyAccessControlExposedHeaders),
                5,
                null);
    }

    /**
     * Verifies that the adapter rejects connection attempts from an unknown device for which auto-provisioning is
     * disabled.
     *
     * @param ctx The test context.
     */
    @Test
    @Timeout(timeUnit = TimeUnit.SECONDS, value = 20)
    public void testConnectFailsIfAutoProvisioningIsDisabled(final VertxTestContext ctx) {

        // GIVEN a tenant configured with a trust anchor that does not allow auto-provisioning
        helper.getCertificate(deviceCert.certificatePath())
            .compose(cert -> {
                final var tenant = Tenants.createTenantForTrustAnchor(cert);
                tenant.getTrustedCertificateAuthorities().get(0).setAutoProvisioningEnabled(false);
                return helper.registry.addTenant(tenantId, tenant);
            })
            // WHEN a unknown device tries to connect to the adapter
            // using a client certificate with the trust anchor registered for the device's tenant
            .compose(ok -> {
                final MultiMap requestHeaders = MultiMap.caseInsensitiveMultiMap()
                        .add(HttpHeaders.CONTENT_TYPE, "text/plain")
                        .add(HttpHeaders.ORIGIN, ORIGIN_URI);
                return httpClientWithClientCert.create(
                        getEndpointUri(),
                        Buffer.buffer("hello"),
                        requestHeaders,
                        ResponsePredicate.status(HttpURLConnection.HTTP_UNAUTHORIZED));
            })
            // THEN the connection is refused
            .onComplete(ctx.succeedingThenComplete());
    }

    /**
     * Uploads messages to the HTTP endpoint.
     *
     * @param ctx The test context to run on.
     * @param tenantId The tenant that the device belongs to.
     * @param requestSender The test device that will publish the data.
     * @throws InterruptedException if the test is interrupted before it
     *              has finished.
     */
    protected void testUploadMessages(
            final VertxTestContext ctx,
            final String tenantId,
            final Function<Integer, Future<HttpResponse<Buffer>>> requestSender) throws InterruptedException {
        testUploadMessages(ctx, tenantId, null, requestSender);
    }

    /**
     * Uploads messages to the HTTP endpoint.
     *
     * @param ctx The test context to run on.
     * @param tenantId The tenant that the device belongs to.
     * @param messageConsumer Consumer that is invoked when a message was received.
     * @param requestSender The test device that will publish the data.
     * @throws InterruptedException if the test is interrupted before it
     *              has finished.
     */
    protected void testUploadMessages(
            final VertxTestContext ctx,
            final String tenantId,
            final Function<DownstreamMessage<? extends MessageContext>, Future<Void>> messageConsumer,
            final Function<Integer, Future<HttpResponse<Buffer>>> requestSender) throws InterruptedException {
        testUploadMessages(ctx, tenantId, messageConsumer, requestSender, MESSAGES_TO_SEND, null);
    }

    private QoS getExpectedQoS(final QoS qos) {
        if (qos != null) {
            return qos;
        }

        final MetricsTags.EndpointType endpointType = MetricsTags.EndpointType.fromString(getEndpointUri().replaceFirst("/", ""));

        switch (endpointType) {
            case EVENT:
                return QoS.AT_LEAST_ONCE;
            case TELEMETRY:
                return QoS.AT_MOST_ONCE;
            default:
                throw new IllegalArgumentException("Either QoS must be non-null or endpoint type must be telemetry or event!");
        }
    }

    /**
     * Uploads messages to the HTTP endpoint.
     *
     * @param ctx The test context to run on.
     * @param tenantId The tenant that the device belongs to.
     * @param messageConsumer Consumer that is invoked when a message was received.
     * @param requestSender The test device that will publish the data.
     * @param numberOfMessages The number of messages that are uploaded.
     * @param expectedQos The expected QoS level, may be {@code null} leading to expecting the default for event or telemetry.
     * @throws InterruptedException if the test is interrupted before it has finished.
     */
    protected void testUploadMessages(
            final VertxTestContext ctx,
            final String tenantId,
            final Function<DownstreamMessage<? extends MessageContext>, Future<Void>> messageConsumer,
            final Function<Integer, Future<HttpResponse<Buffer>>> requestSender,
            final int numberOfMessages,
            final QoS expectedQos) throws InterruptedException {

        final VertxTestContext messageSending = new VertxTestContext();
        final Checkpoint messageSent = messageSending.checkpoint(numberOfMessages);
        final Checkpoint messageReceived = messageSending.laxCheckpoint(numberOfMessages);
        final AtomicInteger receivedMessageCount = new AtomicInteger(0);

        final VertxTestContext setup = new VertxTestContext();

        createConsumer(tenantId, msg -> {
            logger.trace("received {}", msg);
            ctx.verify(() -> {
                DownstreamMessageAssertions.assertTelemetryApiProperties(msg);
                DownstreamMessageAssertions.assertMessageContainsAdapterAndAddress(msg);
                assertThat(msg.getQos()).isEqualTo(getExpectedQoS(expectedQos));
                assertAdditionalMessageProperties(msg);
            });
            Optional.ofNullable(messageConsumer)
                .map(consumer -> consumer.apply(msg))
                .orElseGet(Future::succeededFuture)
                .onComplete(attempt -> {
                    if (attempt.succeeded()) {
                        receivedMessageCount.incrementAndGet();
                        messageReceived.flag();
                    } else {
                        logger.error("failed to process message from device", attempt.cause());
                        messageSending.failNow(attempt.cause());
                    }
            });
            if (receivedMessageCount.get() % 20 == 0) {
                logger.info("messages received: {}", receivedMessageCount.get());
            }
        }).onComplete(setup.succeedingThenComplete());

        assertThat(setup.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
        if (setup.failed()) {
            ctx.failNow(setup.causeOfFailure());
            return;
        }

        final long start = System.currentTimeMillis();
        int messageCount = 0;

        while (messageCount < numberOfMessages && !messageSending.failed()) {

            messageCount++;
            final int currentMessage = messageCount;

            final CountDownLatch sending = new CountDownLatch(1);
            requestSender.apply(currentMessage)
                .compose(this::assertHttpResponse)
                .onComplete(attempt -> {
                    try {
                        if (attempt.succeeded()) {
                            logger.debug("sent message {}", currentMessage);
                            messageSent.flag();
                        } else {
                            logger.info("failed to send message {}: {}", currentMessage, attempt.cause().getMessage());
                            messageSending.failNow(attempt.cause());
                        }
                    } finally {
                        sending.countDown();
                    }
                });
            if (!sending.await(TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                ctx.failNow("timeout waiting for message %s to be sent".formatted(currentMessage));
            } else if (currentMessage % 20 == 0) {
                logger.info("messages sent: " + currentMessage);
            }
        }
        if (ctx.failed()) {
            return;
        }

        final long timeToWait = Math.max(TEST_TIMEOUT_MILLIS - 50 - (System.currentTimeMillis() - testStartTimeMillis),
                1);
        assertThat(messageSending.awaitCompletion(timeToWait, TimeUnit.MILLISECONDS)).isTrue();

        if (messageSending.failed()) {
            logger.error("test execution failed", messageSending.causeOfFailure());
            ctx.failNow(messageSending.causeOfFailure());
        } else {
            logger.info("successfully sent {} and received {} messages after {} milliseconds",
                    messageCount, receivedMessageCount.get(), System.currentTimeMillis() - start);
            ctx.completeNow();
        }
    }

    /**
     * Verifies that the adapter fails to authenticate a device if the device's client certificate's signature cannot be
     * validated using the trust anchor that is registered for the tenant that the device belongs to.
     *
     * @param ctx The vert.x test context.
     * @throws GeneralSecurityException if the tenant's trust anchor cannot be generated
     */
    @Test
    @Timeout(timeUnit = TimeUnit.SECONDS, value = 20)
    public void testUploadFailsForNonMatchingTrustAnchor(final VertxTestContext ctx) throws GeneralSecurityException {


        final KeyPair keyPair = helper.newEcKeyPair();

        // GIVEN a tenant configured with a trust anchor
        helper.getCertificate(deviceCert.certificatePath())
            .compose(cert -> {

                final Tenant tenant = Tenants.createTenantForTrustAnchor(
                        cert.getIssuerX500Principal().getName(X500Principal.RFC2253),
                        keyPair.getPublic().getEncoded(),
                        keyPair.getPublic().getAlgorithm());

                return helper.registry.addDeviceForTenant(tenantId, tenant, deviceId, cert);
            })
            // WHEN a device tries to upload data and authenticate with a client
            // certificate that has not been signed with the configured trusted CA
            .compose(ok -> {

                final MultiMap requestHeaders = MultiMap.caseInsensitiveMultiMap()
                        .add(HttpHeaders.CONTENT_TYPE, "text/plain")
                        .add(HttpHeaders.ORIGIN, ORIGIN_URI);
                return httpClientWithClientCert.create(
                        getEndpointUri(),
                        Buffer.buffer("hello"),
                        requestHeaders,
                        ResponsePredicate.status(HttpURLConnection.HTTP_UNAUTHORIZED));
            })
            // THEN the request fails with a 401
            .onComplete(ctx.succeedingThenComplete());
    }

    /**
     * Verifies that the adapter fails to authenticate a device that is providing
     * wrong credentials.
     *
     * @param ctx The vert.x test context.
     * @throws InterruptedException if the test fails.
     */
    @Test
    @Timeout(timeUnit = TimeUnit.SECONDS, value = 20)
    public void testUploadFailsForWrongCredentials(final VertxTestContext ctx) throws InterruptedException {

        final VertxTestContext setup = new VertxTestContext();
        final Tenant tenant = new Tenant();
        final MultiMap requestHeaders = MultiMap.caseInsensitiveMultiMap()
                .add(HttpHeaders.CONTENT_TYPE, "text/plain")
                .add(HttpHeaders.AUTHORIZATION, getBasicAuth(tenantId, deviceId, "wrong password"))
                .add(HttpHeaders.ORIGIN, ORIGIN_URI);

        // GIVEN a device
        helper.registry
                .addDeviceForTenant(tenantId, tenant, deviceId, PWD)
                .onComplete(setup.succeedingThenComplete());

        assertThat(setup.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
        if (setup.failed()) {
            ctx.failNow(setup.causeOfFailure());
            return;
        }

        // WHEN a device tries to upload data and authenticate using wrong credentials
        httpClient.create(
                getEndpointUri(),
                Buffer.buffer("hello"),
                requestHeaders,
                ResponsePredicate.status(HttpURLConnection.HTTP_UNAUTHORIZED))
                // THEN the request fails with a 401
                .onComplete(ctx.succeedingThenComplete());
    }

    /**
     * Verifies that the adapter fails to authenticate a device that is providing
     * credentials that contain a non-existing tenant.
     *
     * @param ctx The vert.x test context.
     * @throws InterruptedException if the test fails.
     */
    @Test
    @Timeout(timeUnit = TimeUnit.SECONDS, value = 20)
    public void testUploadFailsForCredentialsWithNonExistingTenant(final VertxTestContext ctx) throws InterruptedException {

        final VertxTestContext setup = new VertxTestContext();
        final Tenant tenant = new Tenant();
        final MultiMap requestHeaders = MultiMap.caseInsensitiveMultiMap()
                .add(HttpHeaders.CONTENT_TYPE, "text/plain")
                .add(HttpHeaders.AUTHORIZATION, getBasicAuth("nonExistingTenant", deviceId, PWD))
                .add(HttpHeaders.ORIGIN, ORIGIN_URI);

        // GIVEN a device
        helper.registry
                .addDeviceForTenant(tenantId, tenant, deviceId, PWD)
                .onComplete(setup.succeedingThenComplete());

        assertThat(setup.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
        if (setup.failed()) {
            ctx.failNow(setup.causeOfFailure());
            return;
        }

        // WHEN a device tries to upload data and authenticate using wrong credentials
        httpClient.create(
                getEndpointUri(),
                Buffer.buffer("hello"),
                requestHeaders,
                ResponsePredicate.status(HttpURLConnection.HTTP_UNAUTHORIZED))
                // THEN the request fails with a 401
                .onComplete(ctx.succeedingThenComplete());
    }

    /**
     * Verifies that the HTTP adapter rejects messages from a device that belongs to a tenant for which the HTTP adapter
     * has been disabled with a 403.
     *
     * @param ctx The test context
     */
    @Test
    @Timeout(timeUnit = TimeUnit.SECONDS, value = 20)
    public void testUploadMessageFailsForDisabledTenant(final VertxTestContext ctx) {

        // GIVEN a tenant for which the HTTP adapter is disabled
        final Tenant tenant = new Tenant();
        tenant.addAdapterConfig(new Adapter(Constants.PROTOCOL_ADAPTER_TYPE_HTTP).setEnabled(false));

        helper.registry
            .addDeviceForTenant(tenantId, tenant, deviceId, PWD)
            .compose(ok -> {

                // WHEN a device that belongs to the tenant uploads a message
                final MultiMap requestHeaders = MultiMap.caseInsensitiveMultiMap()
                        .add(HttpHeaders.CONTENT_TYPE, "text/plain")
                        .add(HttpHeaders.AUTHORIZATION, authorization);

                return httpClient.create(
                        getEndpointUri(),
                        Buffer.buffer("hello"),
                        requestHeaders,
                        ResponsePredicate.status(HttpURLConnection.HTTP_FORBIDDEN));
            })
            // THEN the message gets rejected by the HTTP adapter with a 403
            .onComplete(ctx.succeedingThenComplete());
    }

    /**
     * Verifies that the HTTP adapter rejects messages from a disabled device with a 404.
     *
     * @param ctx The test context
     */
    @Test
    @Timeout(timeUnit = TimeUnit.SECONDS, value = 20)
    public void testUploadMessageFailsForDisabledDevice(final VertxTestContext ctx) {

        // GIVEN a disabled device
        final Tenant tenant = new Tenant();
        final Device device = new Device().setEnabled(Boolean.FALSE);

        helper.registry
            .addDeviceForTenant(tenantId, tenant, deviceId, device, PWD)
            .compose(ok -> {

                // WHEN the device tries to upload a message
                final MultiMap requestHeaders = MultiMap.caseInsensitiveMultiMap()
                        .add(HttpHeaders.CONTENT_TYPE, "text/plain")
                        .add(HttpHeaders.AUTHORIZATION, authorization);

                return httpClient.create(
                        getEndpointUri(),
                        Buffer.buffer("hello"),
                        requestHeaders,
                        ResponsePredicate.status(HttpURLConnection.HTTP_NOT_FOUND));
            })
            // THEN the message gets rejected by the HTTP adapter with a 404
            .onComplete(ctx.succeedingThenComplete());
    }

    /**
     * Verifies that the HTTP adapter rejects messages from a disabled gateway for an enabled device with a 403.
     *
     * @param ctx The test context
     */
    @Test
    @Timeout(timeUnit = TimeUnit.SECONDS, value = 20)
    public void testUploadMessageFailsForDisabledGateway(final VertxTestContext ctx) {

        // GIVEN a device that is connected via a disabled gateway
        final Tenant tenant = new Tenant();

        final Device gateway = new Device().setEnabled(Boolean.FALSE);
        final String gatewayId = helper.getRandomDeviceId(tenantId);

        final Device device = new Device().setVia(Collections.singletonList(gatewayId));

        helper.registry
            .addDeviceForTenant(tenantId, tenant, gatewayId, gateway, PWD)
            .compose(ok -> helper.registry.registerDevice(tenantId, deviceId, device))
            .compose(ok -> {

                // WHEN the gateway tries to upload a message for the device
                final MultiMap requestHeaders = MultiMap.caseInsensitiveMultiMap()
                        .add(HttpHeaders.CONTENT_TYPE, "text/plain")
                        .add(HttpHeaders.AUTHORIZATION, getBasicAuth(tenantId, gatewayId, PWD));

                return httpClient.update(
                        String.format("%s/%s/%s", getEndpointUri(), tenantId, deviceId),
                        Buffer.buffer("hello"),
                        requestHeaders,
                        ResponsePredicate.status(HttpURLConnection.HTTP_FORBIDDEN));

            })
            // THEN the message gets rejected by the HTTP adapter with a 403
            .onComplete(ctx.succeedingThenComplete());
    }

    /**
     * Verifies that the HTTP adapter rejects messages from a gateway for an device that it is not authorized for with a
     * 403.
     *
     * @param ctx The test context
     */
    @Test
    @Timeout(timeUnit = TimeUnit.SECONDS, value = 20)
    public void testUploadMessageFailsForUnauthorizedGateway(final VertxTestContext ctx) {

        // GIVEN a device that is connected via gateway "not-the-created-gateway"
        final Tenant tenant = new Tenant();
        final String gatewayId = helper.getRandomDeviceId(tenantId);
        final Device deviceData = new Device();
        deviceData.setVia(Collections.singletonList("not-the-created-gateway"));

        helper.registry
            .addDeviceForTenant(tenantId, tenant, gatewayId, PWD)
            .compose(ok -> helper.registry.registerDevice(tenantId, deviceId, deviceData))
            .compose(ok -> {

                // WHEN another gateway tries to upload a message for the device
                final MultiMap requestHeaders = MultiMap.caseInsensitiveMultiMap()
                        .add(HttpHeaders.CONTENT_TYPE, "text/plain")
                        .add(HttpHeaders.AUTHORIZATION, getBasicAuth(tenantId, gatewayId, PWD));

                return httpClient.update(
                        String.format("%s/%s/%s", getEndpointUri(), tenantId, deviceId),
                        Buffer.buffer("hello"),
                        requestHeaders,
                        ResponsePredicate.status(HttpURLConnection.HTTP_FORBIDDEN))
                    .map(requestHeaders);
            })
            .compose(requestHeaders -> httpClient.update(
                        String.format("%s//%s", getEndpointUri(), deviceId),
                        Buffer.buffer("hello"),
                        requestHeaders,
                        ResponsePredicate.status(HttpURLConnection.HTTP_FORBIDDEN)))
            // THEN the message gets rejected by the HTTP adapter with a 403
            .onComplete(ctx.succeedingThenComplete());
    }

    /**
     * Verifies that an edge device is auto-provisioned if it connects via a gateway equipped with the corresponding
     * authority.
     *
     * @param ctx The test context.
     * @throws InterruptedException if the test fails.
     */
    @Test
    @Timeout(timeUnit = TimeUnit.SECONDS, value = 15)
    public void testAutoProvisioningViaGateway(final VertxTestContext ctx) throws InterruptedException {

        final Tenant tenant = new Tenant();
        final String gatewayId = helper.getRandomDeviceId(tenantId);
        final Device gateway = new Device()
                .setAuthorities(Collections.singleton(RegistryManagementConstants.AUTHORITY_AUTO_PROVISIONING_ENABLED));
        final String edgeDeviceId = helper.getRandomDeviceId(tenantId);
        final Promise<Void> provisioningNotificationReceived = Promise.promise();

        helper.createAutoProvisioningMessageConsumers(ctx, provisioningNotificationReceived, tenantId, edgeDeviceId)
                .compose(ok -> helper.registry.addDeviceForTenant(tenantId, tenant, gatewayId, gateway, PWD))
                .compose(ok -> {
                    final MultiMap requestHeaders = MultiMap.caseInsensitiveMultiMap()
                            .add(HttpHeaders.CONTENT_TYPE, "text/plain")
                            .add(HttpHeaders.AUTHORIZATION, getBasicAuth(tenantId, gatewayId, PWD))
                            .add(HttpHeaders.ORIGIN, ORIGIN_URI);

                    final String uri = String.format("%s/%s/%s", getEndpointUri(), tenantId, edgeDeviceId);

                    return httpClient.update(
                                uri,
                                Buffer.buffer("hello"),
                                requestHeaders,
                                ResponsePredicate.status(HttpURLConnection.HTTP_ACCEPTED));
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
     * Verifies that for two consecutive upload requests containing a TTD, sent in close succession so that the command
     * triggered by the first request isn't sent before the adapter has received the second upload request, the HTTP
     * adapter returns the command as response to the second upload request.
     *
     * @param ctx The test context.
     * @throws InterruptedException if the test is interrupted before having completed.
     */
    @Test
    @Timeout(timeUnit = TimeUnit.SECONDS, value = 20)
    public void testHandleConcurrentUploadWithTtd(final VertxTestContext ctx) throws InterruptedException {

        final Tenant tenant = new Tenant();

        final CountDownLatch firstMessageReceived = new CountDownLatch(1);
        final CountDownLatch secondMessageReceived = new CountDownLatch(1);

        // GIVEN a registered device
        final VertxTestContext setup = new VertxTestContext();
        helper.registry.addDeviceForTenant(tenantId, tenant, deviceId, PWD)
        .compose(ok -> createConsumer(tenantId, msg -> {
            logger.trace("received message: {}", msg);
            msg.getTimeUntilDisconnectNotification().ifPresent(notification -> {
                logger.debug("processing piggy backed message [ttd: {}]", notification.getTtd());
                ctx.verify(() -> {
                    assertThat(notification.getTenantId()).isEqualTo(tenantId);
                    assertThat(notification.getDeviceId()).isEqualTo(deviceId);
                });
            });
            switch (msg.getContentType()) {
            case "text/msg1":
                logger.debug("received first message");
                firstMessageReceived.countDown();
                break;
            case "text/msg2":
                logger.debug("received second message");
                secondMessageReceived.countDown();
                break;
            default:
                // nothing to do
            }
        })).compose(c -> {
            // We need to send an initial request with QoS 1 to trigger establishment
            // of the link required for processing requests in the HTTP adapter.
            // Otherwise, the second of the two consecutive upload requests
            // might fail immediately because the link has not been established yet.
            return httpClient.create(
                getEndpointUri(),
                Buffer.buffer("trigger msg"),
                MultiMap.caseInsensitiveMultiMap()
                    .add(HttpHeaders.CONTENT_TYPE, "application/trigger")
                    .add(HttpHeaders.AUTHORIZATION, authorization)
                    .add(HttpHeaders.ORIGIN, ORIGIN_URI)
                    .add(Constants.HEADER_QOS_LEVEL, "1"),
                ResponsePredicate.status(200, 300));
        }).onComplete(setup.succeedingThenComplete());

        assertThat(setup.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
        if (setup.failed()) {
            ctx.failNow(setup.causeOfFailure());
            return;
        }

        // WHEN the device sends a first upload request
        MultiMap requestHeaders = MultiMap.caseInsensitiveMultiMap()
                .add(HttpHeaders.CONTENT_TYPE, "text/msg1")
                .add(HttpHeaders.AUTHORIZATION, authorization)
                .add(HttpHeaders.ORIGIN, ORIGIN_URI)
                .add(Constants.HEADER_TIME_TILL_DISCONNECT, "10");

        final Future<HttpResponse<Buffer>> firstRequest = httpClient.create(
                getEndpointUri(),
                Buffer.buffer("hello one"),
                requestHeaders,
                ResponsePredicate.status(200, 300))
                .map(httpResponse -> {
                    logger.info("received response to first request");
                    return httpResponse;
                });
        logger.info("sent first request");
        if (!firstMessageReceived.await(5, TimeUnit.SECONDS)) {
            ctx.failNow(new IllegalStateException("first message not received in time"));
        }

        // followed by a second request
        requestHeaders = MultiMap.caseInsensitiveMultiMap()
                .add(HttpHeaders.CONTENT_TYPE, "text/msg2")
                .add(HttpHeaders.AUTHORIZATION, authorization)
                .add(HttpHeaders.ORIGIN, ORIGIN_URI)
                .add(Constants.HEADER_TIME_TILL_DISCONNECT, "5");
        final Future<HttpResponse<Buffer>> secondRequest = httpClient.create(
                getEndpointUri(),
                Buffer.buffer("hello two"),
                requestHeaders,
                ResponsePredicate.status(200, 300))
                .map(httpResponse -> {
                    logger.info("received response to second request");
                    return httpResponse;
                });
        logger.info("sent second request");
        // wait for messages having been received
        if (!secondMessageReceived.await(5, TimeUnit.SECONDS)) {
            ctx.failNow(new IllegalStateException("second message not received in time"));
        }
        // send command
        final JsonObject inputData = new JsonObject().put(COMMAND_JSON_KEY, (int) (Math.random() * 100));
        final Future<Void> commandSent = helper.sendOneWayCommand(tenantId, deviceId, COMMAND_TO_SEND,
                "application/json", inputData.toBuffer(), 3000);
        logger.info("sent one-way command to device");

        // THEN both requests succeed
        Future.all(commandSent, firstRequest, secondRequest)
        .onComplete(ctx.succeeding(ok -> {
            ctx.verify(() -> {
                // and the response to the second request contains a command
                assertThat(secondRequest.result().getHeader(Constants.HEADER_COMMAND)).isEqualTo(COMMAND_TO_SEND);

                // while the response to the first request is empty
                assertThat(firstRequest.result().getHeader(Constants.HEADER_COMMAND)).isNull();
            });
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that the HTTP adapter returns empty responses when sending consecutive requests
     * for uploading telemetry data or events with a TTD but no command is pending for the device.
     *
     * @param ctx The test context.
     * @throws InterruptedException if the test fails.
     */
    @Test
    public void testUploadMessagesWithTtdThatDoNotReplyWithCommand(final VertxTestContext ctx) throws InterruptedException {

        final VertxTestContext setup = new VertxTestContext();
        final Tenant tenant = new Tenant();
        final MultiMap requestHeaders = MultiMap.caseInsensitiveMultiMap()
                .add(HttpHeaders.CONTENT_TYPE, "text/plain")
                .add(HttpHeaders.AUTHORIZATION, authorization)
                .add(HttpHeaders.ORIGIN, ORIGIN_URI)
                .add(Constants.HEADER_TIME_TILL_DISCONNECT, "2");

        helper.registry.addDeviceForTenant(tenantId, tenant, deviceId, PWD).onComplete(setup.succeedingThenComplete());

        assertThat(setup.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
        if (setup.failed()) {
            ctx.failNow(setup.causeOfFailure());
            return;
        }

        testUploadMessages(ctx, tenantId,
                msg -> {
                    // do NOT send a command, but let the HTTP adapter's timer expire
                    logger.trace("received message");
                    return msg.getTimeUntilDisconnectNotification()
                    .map(notification -> {
                        ctx.verify(() -> {
                            assertThat(notification.getTtd()).isEqualTo(2);
                            assertThat(notification.getTenantId()).isEqualTo(tenantId);
                            assertThat(notification.getDeviceId()).isEqualTo(deviceId);
                        });
                        return Future.<Void>succeededFuture();
                    })
                    .orElseGet(() -> Future.<Void>succeededFuture());
                },
                count -> {
                    return httpClient.create(
                            getEndpointUri(),
                            Buffer.buffer("hello " + count),
                            requestHeaders,
                            ResponsePredicate.status(HttpURLConnection.HTTP_ACCEPTED))
                            .map(responseHeaders -> {
                                ctx.verify(() -> {
                                    // assert that the response does not contain a command nor a request ID nor a payload
                                    assertThat(responseHeaders.getHeader(Constants.HEADER_COMMAND)).isNull();
                                    assertThat(responseHeaders.getHeader(Constants.HEADER_COMMAND_REQUEST_ID)).isNull();
                                    assertThat(responseHeaders.getHeader(HttpHeaders.CONTENT_LENGTH.toString())).isEqualTo("0");
                                });
                                return responseHeaders;
                            });
                },
                5,
                null);
    }

    /**
     * Verifies that the HTTP adapter delivers a command to a device and accepts the corresponding
     * response from the device.
     *
     * @param endpointConfig The endpoints to use for sending/receiving commands.
     * @param ctx The test context.
     * @throws InterruptedException if the test fails.
     */
    @ParameterizedTest(name = IntegrationTestSupport.PARAMETERIZED_TEST_NAME_PATTERN)
    @MethodSource("commandAndControlVariants")
    public void testUploadMessagesWithTtdThatReplyWithCommand(
            final HttpCommandEndpointConfiguration endpointConfig,
            final VertxTestContext ctx) throws InterruptedException {

        final Tenant tenant = new Tenant();
        testUploadMessagesWithTtdThatReplyWithCommand(endpointConfig, tenant, ctx);
    }

    private void testUploadMessagesWithTtdThatReplyWithCommand(final HttpCommandEndpointConfiguration endpointConfig,
            final Tenant tenant, final VertxTestContext ctx) throws InterruptedException {
        final VertxTestContext setup = new VertxTestContext();

        final MultiMap requestHeaders = MultiMap.caseInsensitiveMultiMap()
                .add(HttpHeaders.CONTENT_TYPE, "text/plain")
                .add(HttpHeaders.AUTHORIZATION, authorization)
                .add(HttpHeaders.ORIGIN, ORIGIN_URI)
                .add(Constants.HEADER_TIME_TILL_DISCONNECT, "5");

        final MultiMap cmdResponseRequestHeaders = MultiMap.caseInsensitiveMultiMap()
                .add(HttpHeaders.CONTENT_TYPE, "text/plain")
                .add(HttpHeaders.AUTHORIZATION, authorization)
                .add(HttpHeaders.ORIGIN, ORIGIN_URI)
                .add(Constants.HEADER_COMMAND_RESPONSE_STATUS, "200");

        helper.registry
        .addDeviceForTenant(tenantId, tenant, deviceId, PWD)
        .onComplete(setup.succeedingThenComplete());

        assertThat(setup.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
        if (setup.failed()) {
            ctx.failNow(setup.causeOfFailure());
            return;
        }

        final String commandTargetDeviceId = endpointConfig.isSubscribeAsGateway()
                ? helper.setupGatewayDeviceBlocking(tenantId, deviceId, 5)
                : deviceId;
        final String subscribingDeviceId = endpointConfig.isSubscribeAsGatewayForSingleDevice() ? commandTargetDeviceId
                : deviceId;

        testUploadMessages(ctx, tenantId,
                msg -> {

                    return msg.getTimeUntilDisconnectNotification()
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
                                        notification.getMillisecondsUntilExpiry())
                                        .map(response -> {
                                            ctx.verify(() -> {
                                                DownstreamMessageAssertions.assertCommandAndControlApiProperties(
                                                        response, tenantId, commandTargetDeviceId);
                                                assertThat(response.getContentType()).isEqualTo("text/plain");
                                            });
                                            return (Void) null;
                                        });
                            })
                            .orElseGet(Future::succeededFuture);
                },
                count -> {
                    final Buffer buffer = Buffer.buffer("hello " + count);
                    final boolean useQueryParams = count % 2 == 0; // use ttd query param instead of header for half of the requests
                    return sendHttpRequestForGatewayOrDevice(buffer, requestHeaders, endpointConfig, commandTargetDeviceId, useQueryParams, true)
                            .map(httpResponse -> {

                                final String requestId = httpResponse.getHeader(Constants.HEADER_COMMAND_REQUEST_ID);

                                ctx.verify(() -> {
                                    // assert that the response contains a command
                                    assertWithMessage("response no. %s '%s' header", count, Constants.HEADER_COMMAND)
                                            .that(httpResponse.getHeader(Constants.HEADER_COMMAND))
                                            .isNotNull();
                                    assertThat(httpResponse.getHeader(Constants.HEADER_COMMAND)).isEqualTo(COMMAND_TO_SEND);
                                    assertThat(httpResponse.getHeader(HttpHeaders.CONTENT_TYPE.toString())).isEqualTo("application/json");
                                    assertThat(requestId).isNotNull();
                                    assertThat(httpResponse.getHeader(HttpHeaders.CONTENT_LENGTH.toString())).isNotEqualTo("0");
                                });
                                return requestId;

                            }).compose(receivedCommandRequestId -> {

                                // send a response to the command now
                                final String tenantIdToUseInResponseUri;
                                if (count % 2 == 0 && endpointConfig.isSubscribeAsGateway()) {
                                    tenantIdToUseInResponseUri = "";
                                } else {
                                    tenantIdToUseInResponseUri = tenantId;
                                }
                                final String responseUri = endpointConfig.getCommandResponseUri(
                                        tenantIdToUseInResponseUri,
                                        commandTargetDeviceId,
                                        receivedCommandRequestId);
                                logger.debug("sending response to command [uri: {}]", responseUri);

                                final Buffer body = Buffer.buffer("ok");
                                final Future<HttpResponse<Buffer>> result;
                                if (endpointConfig.isSubscribeAsGateway()) {
                                    // GW uses PUT when acting on behalf of a device
                                    result = httpClient.update(responseUri, body, cmdResponseRequestHeaders,
                                            ResponsePredicate.status(HttpURLConnection.HTTP_ACCEPTED));
                                } else {
                                    result = httpClient.create(responseUri, body, cmdResponseRequestHeaders,
                                            ResponsePredicate.status(HttpURLConnection.HTTP_ACCEPTED));
                                }
                                return result.recover(thr -> {
                                    // wrap exception, making clear it occurred when sending the command response,
                                    // not the preceding telemetry/event message
                                    final String msg = "Error sending command response: " + thr.getMessage();
                                    return Future.failedFuture(new RuntimeException(msg, thr));
                                });
                            });
                });
    }

    /**
     * Verifies that the HTTP adapter delivers a command to a device and accepts the corresponding
     * response from the device.
     *
     * @param endpointConfig The endpoints to use for sending/receiving commands.
     * @param ctx The test context.
     * @throws InterruptedException if the test fails.
     */
    @ParameterizedTest(name = IntegrationTestSupport.PARAMETERIZED_TEST_NAME_PATTERN)
    @MethodSource("commandAndControlVariants")
    public void testUploadMessagesWithTtdThatReplyWithOneWayCommand(
            final HttpCommandEndpointConfiguration endpointConfig,
            final VertxTestContext ctx) throws InterruptedException {

        final Tenant tenant = new Tenant();
        final VertxTestContext setup = new VertxTestContext();

        final MultiMap requestHeaders = MultiMap.caseInsensitiveMultiMap()
                .add(HttpHeaders.CONTENT_TYPE, "text/plain")
                .add(HttpHeaders.AUTHORIZATION, authorization)
                .add(HttpHeaders.ORIGIN, ORIGIN_URI)
                .add(Constants.HEADER_TIME_TILL_DISCONNECT, "4");

        helper.registry
        .addDeviceForTenant(tenantId, tenant, deviceId, PWD)
        .onComplete(setup.succeedingThenComplete());

        assertThat(setup.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
        if (setup.failed()) {
            ctx.failNow(setup.causeOfFailure());
            return;
        }

        final String commandTargetDeviceId = endpointConfig.isSubscribeAsGateway()
                ? helper.setupGatewayDeviceBlocking(tenantId, deviceId, 5)
                : deviceId;
        final String subscribingDeviceId = endpointConfig.isSubscribeAsGatewayForSingleDevice() ? commandTargetDeviceId
                : deviceId;

        testUploadMessages(ctx, tenantId,
                msg -> {
                    return msg.getTimeUntilDisconnectNotification()
                            .map(notification -> {

                                logger.trace("received piggy backed message [ttd: {}]: {}", notification.getTtd(), msg);
                                ctx.verify(() -> {
                                    assertThat(notification.getTenantId()).isEqualTo(tenantId);
                                    assertThat(notification.getDeviceId()).isEqualTo(subscribingDeviceId);
                                });
                                // now ready to send a command
                                final JsonObject inputData = new JsonObject().put(COMMAND_JSON_KEY, (int) (Math.random() * 100));
                                return helper.sendOneWayCommand(
                                        tenantId,
                                        commandTargetDeviceId,
                                        COMMAND_TO_SEND,
                                        "application/json",
                                        inputData.toBuffer(),
                                        notification.getMillisecondsUntilExpiry());
                            })
                            .orElseGet(Future::succeededFuture);
                },
                count -> {
                    final Buffer payload = Buffer.buffer("hello " + count);
                    final boolean useQueryParams = count % 2 == 0; // use ttd query param instead of header for half of the requests
                    return sendHttpRequestForGatewayOrDevice(payload, requestHeaders, endpointConfig, commandTargetDeviceId, useQueryParams, true)
                        .map(httpResponse -> {
                            ctx.verify(() -> {
                                // assert that the response contains a one-way command
                                assertWithMessage("response no. %s '%s' header", count, Constants.HEADER_COMMAND)
                                        .that(httpResponse.getHeader(Constants.HEADER_COMMAND))
                                        .isNotNull();
                                assertThat(httpResponse.getHeader(Constants.HEADER_COMMAND)).isEqualTo(COMMAND_TO_SEND);
                                assertThat(httpResponse.getHeader(HttpHeaders.CONTENT_TYPE.toString())).isEqualTo("application/json");
                                assertThat(httpResponse.getHeader(Constants.HEADER_COMMAND_REQUEST_ID)).isNull();
                            });
                            return httpResponse;
                        });
                });
    }

    private Future<HttpResponse<Buffer>> sendHttpRequestForGatewayOrDevice(
            final Buffer payload,
            final MultiMap requestHeaders,
            final HttpCommandEndpointConfiguration endpointConfig,
            final String requestDeviceId,
            final boolean useQueryParams) {

        final QueryStringEncoder uriEncoder = endpointConfig.isSubscribeAsGatewayForSingleDevice() ?
                new QueryStringEncoder(getEndpointUri() + "/" + tenantId + "/" + requestDeviceId) :
                new QueryStringEncoder(getEndpointUri());

        final MultiMap requestHeadersToUse;
        if (useQueryParams && (requestHeaders.contains(Constants.HEADER_TIME_TILL_DISCONNECT)
                || requestHeaders.contains(Constants.HEADER_TIME_TO_LIVE))) {
            requestHeadersToUse = MultiMap.caseInsensitiveMultiMap().addAll(requestHeaders);
            removeFromHeadersAndAddAsQueryParam(Constants.HEADER_TIME_TILL_DISCONNECT, requestHeadersToUse, uriEncoder);
            removeFromHeadersAndAddAsQueryParam(Constants.HEADER_TIME_TO_LIVE, requestHeadersToUse, uriEncoder);
        } else {
            requestHeadersToUse = requestHeaders;
        }
        if (endpointConfig.isSubscribeAsGatewayForSingleDevice()) {
            // GW uses PUT when acting on behalf of a device
            return httpClient.update(uriEncoder.toString(), payload, requestHeadersToUse,
                    ResponsePredicate.status(HttpURLConnection.HTTP_OK));
        } else {
            return httpClient.create(uriEncoder.toString(), payload, requestHeadersToUse,
                    ResponsePredicate.status(HttpURLConnection.HTTP_OK));
        }
    }

    private void removeFromHeadersAndAddAsQueryParam(final String header, final MultiMap headers,
            final QueryStringEncoder queryStringEncoder) {
        Optional.ofNullable(headers.get(header))
                .ifPresent(ttd -> {
                    queryStringEncoder.addParam(header, ttd);
                    headers.remove(header);
                });
    }

    private Future<HttpResponse<Buffer>> sendHttpRequestForGatewayOrDevice(
            final Buffer payload,
            final MultiMap requestHeaders,
            final HttpCommandEndpointConfiguration endpointConfig,
            final String requestDeviceId,
            final boolean useQueryParams,
            final boolean retry) {

        return sendHttpRequestForGatewayOrDevice(payload, requestHeaders, endpointConfig, requestDeviceId, useQueryParams)
                .recover(t -> {
                    if (retry) {
                        // we probably sent the request before the
                        // HTTP adapter was able to close the command
                        // consumer for the previous request
                        // wait a little and try again
                        final Promise<HttpResponse<Buffer>> retryResult = Promise.promise();
                        vertx.setTimer(300, timerId -> {
                            logger.info("re-trying request, failure was: {}", t.getMessage());
                            sendHttpRequestForGatewayOrDevice(payload, requestHeaders, endpointConfig, requestDeviceId, useQueryParams)
                                .onComplete(retryResult);
                        });
                        return retryResult.future();
                    } else {
                        return Future.failedFuture(t);
                    }
                });
    }

    private <T> Future<HttpResponse<T>> verifyAccessControlExposedHeaders(final HttpResponse<T> response) {
        final String exposedHeaders = response.getHeader(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS.toString());
        if (exposedHeaders != null
                && exposedHeaders.contains(Constants.HEADER_COMMAND)
                && exposedHeaders.contains(Constants.HEADER_COMMAND_REQUEST_ID)) {
            return Future.succeededFuture(response);
        } else {
            return Future.failedFuture(String.format("response did not contain expected %s header value",
                    HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS));
        }
    }

    private Future<Void> assertHttpResponse(final HttpResponse<Buffer> response) {

        final Promise<Void> result = Promise.promise();
        final String allowedOrigin = response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN.toString());
        final boolean hasValidOrigin = allowedOrigin != null
                && (allowedOrigin.equals(ORIGIN_WILDCARD) || allowedOrigin.equals(ORIGIN_URI));

        if (!hasValidOrigin) {
            result.fail(new IllegalArgumentException("response contains invalid allowed origin: " + allowedOrigin));
        } else {
            result.complete();
        }
        return result.future();
    }

    /**
     * Creates an HTTP Basic Authorization header value for a device.
     *
     * @param tenant The tenant that the device belongs to.
     * @param deviceId The device identifier.
     * @param password The device's password.
     * @return The header value.
     */
    protected static String getBasicAuth(final String tenant, final String deviceId, final String password) {

        final StringBuilder result = new StringBuilder("Basic ");
        final String username = IntegrationTestSupport.getUsername(deviceId, tenant);
        result.append(Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8)));
        return result.toString();
    }

    /**
     * Creates an HTTP Basic Authorization header value for a device.
     * <p>
     * The credentials are Base64 encoded in the Basic auth header's username segment.
     *
     * @param tenant The tenant that the device belongs to.
     * @param deviceId The device identifier.
     * @param password The device's password.
     * @return The header value.
     */
    protected static String getBasicAuthWithEncodedCredentials(final String tenant, final String deviceId, final String password) {

        final StringBuilder result = new StringBuilder("Basic ");
        final String username = IntegrationTestSupport.getUsername(deviceId, tenant) + ":" + password;
        final String encodedUsername = Base64.getEncoder().encodeToString(username.getBytes(StandardCharsets.UTF_8));
        result.append(Base64.getEncoder().encodeToString((encodedUsername + ":").getBytes(StandardCharsets.UTF_8)));
        return result.toString();
    }
}
