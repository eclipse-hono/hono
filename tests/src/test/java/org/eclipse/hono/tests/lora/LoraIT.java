/*******************************************************************************
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.tests.lora;

import static com.google.common.truth.Truth.assertThat;

import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.util.Base64;
import java.util.List;
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
import org.eclipse.hono.tests.CrudHttpClient;
import org.eclipse.hono.tests.DownstreamMessageAssertions;
import org.eclipse.hono.tests.EnabledIfProtocolAdaptersAreRunning;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.tests.Tenants;
import org.eclipse.hono.util.Adapter;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.QoS;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.net.PemTrustOptions;
import io.vertx.core.net.SelfSignedCertificate;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.predicate.ResponsePredicate;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Base class for Lora adapter integration tests.
 *
 */
@EnabledIfProtocolAdaptersAreRunning(loraAdapter = true)
@ExtendWith(VertxExtension.class)
@Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
public class LoraIT {

    /**
     * The default password of devices.
     */
    private static final String PWD = "secret";

    /**
     * A helper for accessing the messaging infrastructure and
     * for managing tenants/devices/credentials.
     */
    private static IntegrationTestSupport helper;
    /**
     * The number of messages to send during a test run.
     */
    private static final int MESSAGES_TO_SEND = 1;

    private static final long  TEST_TIMEOUT_MILLIS = 20000; // 20 seconds
    private static final String LORA_DEVICE_ID = "0102030405060708";
    private static final String ENDPOINT_URI_LORIOT = "/loriot";
    private static final Vertx VERTX = Vertx.vertx();

    private static Buffer loriotRequestBody;

    /**
     * The default options to use for creating HTTP clients.
     */
    private static HttpClientOptions defaultOptions;

    /**
     * A logger to be shared with subclasses.
     */
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * A client for connecting to the Lora adapter.
     */
    protected CrudHttpClient httpClient;
    /**
     * A client for connecting to the Lora adapter using a client certificate
     * for authentication.
     */
    protected CrudHttpClient httpClientWithClientCert;
    /**
     * The random tenant identifier created for each test case.
     */
    protected String tenantId;
    /**
     * The (random) gateway identifier representing the Lora Network Server.
     */
    protected String gatewayId;
    /**
     * The device identifier representing the Lora device.
     */
    protected String deviceId;
    /**
     * The BASIC Auth header value used by the Lora Network Server to authenticate to the Lora adapter.
     */
    protected String authorization;
    /**
     * The self-signed certificate that the Lora Network Server uses for authenticating to the Lora adapter.
     */
    protected SelfSignedCertificate clientCert;


    private long testStartTimeMillis;
    private Device loraDeviceData;

    /**
     * Creates default client options.
     */
    @BeforeAll
    public static void init() {

        defaultOptions = new HttpClientOptions()
                .setDefaultHost(IntegrationTestSupport.LORA_HOST)
                .setDefaultPort(IntegrationTestSupport.LORA_SECURE_PORT)
                .setTrustOptions(new PemTrustOptions().addCertPath(IntegrationTestSupport.TRUST_STORE_PATH))
                .setVerifyHost(false)
                .setSsl(true)
                .setEnabledSecureTransportProtocols(Set.of(IntegrationTestSupport.TLS_VERSION_1_2));
        loriotRequestBody = VERTX.fileSystem().readFileBlocking("lora/payload/loriot.uplink.json");
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
        helper = new IntegrationTestSupport(VERTX);
        logger.info("using Lora adapter [host: {}, http port: {}, https port: {}]",
                IntegrationTestSupport.LORA_HOST,
                IntegrationTestSupport.LORA_PORT,
                IntegrationTestSupport.LORA_SECURE_PORT);

        clientCert = SelfSignedCertificate.create(UUID.randomUUID().toString());
        httpClient = new CrudHttpClient(VERTX, new HttpClientOptions(defaultOptions));
        httpClientWithClientCert = new CrudHttpClient(VERTX, new HttpClientOptions(defaultOptions)
                .setKeyCertOptions(clientCert.keyCertOptions()));

        tenantId = helper.getRandomTenantId();
        gatewayId = helper.getRandomDeviceId(tenantId);
        loraDeviceData = new Device().setVia(List.of(gatewayId));
        helper.addDeviceIdForRemoval(tenantId, LORA_DEVICE_ID);
        authorization = getBasicAuth(tenantId, gatewayId, PWD);
        helper.init().onComplete(ctx.succeedingThenComplete());
    }

    /**
     * Deletes all temporary objects from the Device Registry which
     * have been created during the last test execution.
     *
     * @param ctx The vert.x test context.
     */
    @AfterEach
    public void deleteObjects(final VertxTestContext ctx) {

        helper.deleteObjects(ctx);
        if (clientCert != null) {
            clientCert.delete();
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

    static Stream<LoraRequest> loraRequests() {
        final String template = "lora/payload/%s";
        return Stream.of(
                LoraRequest.from(ENDPOINT_URI_LORIOT, template.formatted("loriot.uplink.json")),
                LoraRequest.from("/chirpstack", template.formatted("chirpStack.uplink.json")),
                LoraRequest.from("/firefly", template.formatted("firefly.uplink.json")));
    }

    /**
     * Creates a test specific message consumer.
     *
     * @param tenantId        The tenant to create the consumer for.
     * @param messageConsumer The handler to invoke for every message received.
     * @return A future succeeding with the created consumer.
     */
    private Future<MessageConsumer> createConsumer(
            final String tenantId,
            final Handler<DownstreamMessage<? extends MessageContext>> messageConsumer) {
        return helper.applicationClient.createTelemetryConsumer(
                tenantId,
                messageConsumer::handle,
                close -> {});
    }

    /**
     * Verifies that a number of messages uploaded to Hono's Lora adapter
     * using HTTP Basic auth can be successfully consumed via the messaging infrastructure.
     *
     * @param loraRequest The request to send to the Lora adapter.
     * @param ctx The test context.
     * @throws InterruptedException if the test fails.
     */
    @ParameterizedTest(name = IntegrationTestSupport.PARAMETERIZED_TEST_NAME_PATTERN)
    @MethodSource(value = "loraRequests")
    public void testUploadMessagesUsingBasicAuth(
            final LoraRequest loraRequest,
            final VertxTestContext ctx) throws InterruptedException {

        final VertxTestContext setup = new VertxTestContext();
        final Tenant tenant = new Tenant();
        final MultiMap requestHeaders = MultiMap.caseInsensitiveMultiMap()
                .add(HttpHeaders.CONTENT_TYPE, loraRequest.contentType)
                .add(HttpHeaders.AUTHORIZATION, authorization);

        helper.registry
            .addDeviceForTenant(tenantId, tenant, gatewayId, PWD)
            .compose(ok -> helper.registry.registerDevice(tenantId, LORA_DEVICE_ID, loraDeviceData))
            .onComplete(setup.succeedingThenComplete());

        assertThat(setup.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
        if (setup.failed()) {
            ctx.failNow(setup.causeOfFailure());
            return;
        }

        final var requestBody = VERTX.fileSystem().readFileBlocking(loraRequest.requestBodyPath);
        logger.trace("sending payload [URI: {}]:{}{}", loraRequest.endpointUri,
                System.lineSeparator(), requestBody.toJsonObject().encodePrettily());
        testUploadMessages(
                ctx,
                tenantId,
                count -> httpClient.create(
                            loraRequest.endpointUri,
                            requestBody,
                            requestHeaders,
                            ResponsePredicate.status(HttpURLConnection.HTTP_ACCEPTED)));
    }

    /**
     * Verifies that a number of messages uploaded to Hono's Lora adapter using client certificate
     * based authentication can be successfully consumed via the messaging infrastructure.
     *
     * @param loraRequest The request to send to the Lora adapter.
     * @param ctx The test context.
     * @throws InterruptedException if the test fails.
     */
    @ParameterizedTest(name = IntegrationTestSupport.PARAMETERIZED_TEST_NAME_PATTERN)
    @MethodSource(value = "loraRequests")
    public void testUploadMessagesUsingClientCertificate(
            final LoraRequest loraRequest,
            final VertxTestContext ctx) throws InterruptedException {

        final VertxTestContext setup = new VertxTestContext();
        final MultiMap requestHeaders = MultiMap.caseInsensitiveMultiMap()
                .add(HttpHeaders.CONTENT_TYPE, loraRequest.contentType);

        helper.getCertificate(clientCert.certificatePath())
            .compose(cert -> {
                final var tenant = Tenants.createTenantForTrustAnchor(cert);
                return helper.registry.addDeviceForTenant(tenantId, tenant, gatewayId, cert);
             })
            .compose(ok -> helper.registry.registerDevice(tenantId, LORA_DEVICE_ID, loraDeviceData))
            .onComplete(setup.succeedingThenComplete());

        assertThat(setup.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
        if (setup.failed()) {
            ctx.failNow(setup.causeOfFailure());
            return;
        }

        final var requestBody = VERTX.fileSystem().readFileBlocking(loraRequest.requestBodyPath);
        logger.trace("sending payload [URI: {}]:{}{}", loraRequest.endpointUri,
                System.lineSeparator(), requestBody.toJsonObject().encodePrettily());
        testUploadMessages(
                ctx,
                tenantId,
                count -> httpClientWithClientCert.create(
                    loraRequest.endpointUri,
                    requestBody,
                    requestHeaders,
                    ResponsePredicate.status(HttpURLConnection.HTTP_ACCEPTED)));
    }

    /**
     * Verifies that a Lora Network Server can successfully upload a message for a device that does
     * not exist yet if the LNS is authorized to auto-provision devices.
     *
     * @param ctx The test context.
     */
    @Test
    public void testUploadMessageSucceedsForAutoProvisionedDevice(final VertxTestContext ctx) {

        final MultiMap requestHeaders = MultiMap.caseInsensitiveMultiMap()
                .add(HttpHeaders.CONTENT_TYPE, "application/json")
                .add(HttpHeaders.AUTHORIZATION, authorization);
        final Checkpoint messageReceived = ctx.checkpoint(1);

        helper.getCertificate(clientCert.certificatePath())
            .compose(cert -> {
                // GIVEN a tenant configured for auto-provisioning
                final var tenant = Tenants.createTenantForTrustAnchor(cert);
                tenant.getTrustedCertificateAuthorities().get(0).setAutoProvisioningEnabled(true);
                return helper.registry.addTenant(tenantId, tenant);

            })
            .compose(ok -> {
                // and a Lora Network Server gateway that has authority to auto-provision devices
                final var lnsData = new Device().setAuthorities(Set.of("auto-provisioning-enabled"));
                return helper.registry.addDeviceToTenant(tenantId, gatewayId, lnsData, PWD);
            })
            .compose(ok -> helper.applicationClient.createTelemetryConsumer(
                        tenantId,
                        msg -> {
                            ctx.verify(() -> DownstreamMessageAssertions.assertLoraMetaDataPresent(msg));
                            messageReceived.flag();
                        },
                        t -> {}))
            .onSuccess(ok -> httpClient.create(
                        ENDPOINT_URI_LORIOT,
                        loriotRequestBody,
                        requestHeaders,
                        ResponsePredicate.status(HttpURLConnection.HTTP_ACCEPTED)));
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
        testUploadMessages(ctx, tenantId, null, requestSender, MESSAGES_TO_SEND, null);
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
                assertThat(msg.getQos() == expectedQos);
                DownstreamMessageAssertions.assertLoraMetaDataPresent(msg);
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
     * Verifies that the adapter fails to authenticate a Lora Network Server if the client certificate's
     * signature cannot be validated using the trust anchor that is registered for the tenant that the
     * LNS belongs to.
     *
     * @param ctx The vert.x test context.
     * @throws GeneralSecurityException if the tenant's trust anchor cannot be generated
     */
    @Test
    @Timeout(timeUnit = TimeUnit.SECONDS, value = 20)
    public void testUploadFailsForNonMatchingTrustAnchor(final VertxTestContext ctx) throws GeneralSecurityException {


        final KeyPair keyPair = helper.newEcKeyPair();

        // GIVEN a tenant configured with a trust anchor
        helper.getCertificate(clientCert.certificatePath())
            .compose(cert -> {

                final Tenant tenant = Tenants.createTenantForTrustAnchor(
                        cert.getIssuerX500Principal().getName(X500Principal.RFC2253),
                        keyPair.getPublic().getEncoded(),
                        keyPair.getPublic().getAlgorithm());

                return helper.registry.addDeviceForTenant(tenantId, tenant, gatewayId, cert);
            })
            // WHEN a device tries to upload data and authenticate with a client
            // certificate that has not been signed with the configured trusted CA
            .compose(ok -> {

                final MultiMap requestHeaders = MultiMap.caseInsensitiveMultiMap()
                        .add(HttpHeaders.CONTENT_TYPE, "application/json");
                return httpClientWithClientCert.create(
                        ENDPOINT_URI_LORIOT,
                        loriotRequestBody,
                        requestHeaders,
                        ResponsePredicate.status(HttpURLConnection.HTTP_UNAUTHORIZED));
            })
            // THEN the request fails with a 401
            .onComplete(ctx.succeedingThenComplete());
    }

    /**
     * Verifies that the adapter fails to authenticate a Lora Network Server that is providing
     * wrong credentials.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    @Timeout(timeUnit = TimeUnit.SECONDS, value = 20)
    public void testUploadFailsForWrongCredentials(final VertxTestContext ctx) {

        final Tenant tenant = new Tenant();

        // GIVEN a device
        helper.registry
                .addDeviceForTenant(tenantId, tenant, gatewayId, PWD)
        // WHEN a Lora Network Server tries to upload data and authenticate using wrong credentials
                .compose(ok -> {
                    final MultiMap requestHeaders = MultiMap.caseInsensitiveMultiMap()
                            .add(HttpHeaders.CONTENT_TYPE, "application/json")
                            .add(HttpHeaders.AUTHORIZATION, getBasicAuth(tenantId, gatewayId, "wrong password"));
                    return httpClient.create(
                            ENDPOINT_URI_LORIOT,
                            loriotRequestBody,
                            requestHeaders,
                            ResponsePredicate.status(HttpURLConnection.HTTP_UNAUTHORIZED));
                })
                // THEN the request fails with a 401
                .onComplete(ctx.succeedingThenComplete());
    }

    /**
     * Verifies that the adapter fails to authenticate a Lora Network Server that is providing
     * credentials that contain a non-existing tenant.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    @Timeout(timeUnit = TimeUnit.SECONDS, value = 20)
    public void testUploadFailsForCredentialsWithNonExistingTenant(final VertxTestContext ctx) {

        final Tenant tenant = new Tenant();

        // GIVEN a device
        helper.registry
                .addDeviceForTenant(tenantId, tenant, gatewayId, PWD)
                // WHEN a Lora Network Server tries to upload data and authenticate using wrong credentials
                .compose(ok -> {
                    final MultiMap requestHeaders = MultiMap.caseInsensitiveMultiMap()
                            .add(HttpHeaders.CONTENT_TYPE, "application/json")
                            .add(HttpHeaders.AUTHORIZATION, getBasicAuth("nonExistingTenant", gatewayId, PWD));
                    return httpClient.create(
                            ENDPOINT_URI_LORIOT,
                            loriotRequestBody,
                            requestHeaders,
                            ResponsePredicate.status(HttpURLConnection.HTTP_UNAUTHORIZED));
                })
                // THEN the request fails with a 401
                .onComplete(ctx.succeedingThenComplete());
    }

    /**
     * Verifies that the adapter rejects messages from a Lora Network Server that belongs to a tenant for
     * which the Lora adapter has been disabled.
     *
     * @param ctx The test context
     */
    @Test
    @Timeout(timeUnit = TimeUnit.SECONDS, value = 20)
    public void testUploadMessageFailsForDisabledAdapter(final VertxTestContext ctx) {

        // GIVEN a tenant for which the Lora adapter is disabled
        final Tenant tenant = new Tenant();
        tenant.addAdapterConfig(new Adapter(Constants.PROTOCOL_ADAPTER_TYPE_LORA).setEnabled(false));

        helper.registry
            .addDeviceForTenant(tenantId, tenant, gatewayId, PWD)
            .compose(ok -> {

                // WHEN a Lora Network Server that belongs to the tenant uploads a message
                final MultiMap requestHeaders = MultiMap.caseInsensitiveMultiMap()
                        .add(HttpHeaders.CONTENT_TYPE, "application/json")
                        .add(HttpHeaders.AUTHORIZATION, authorization);

                return httpClient.create(
                        ENDPOINT_URI_LORIOT,
                        loriotRequestBody,
                        requestHeaders,
                        ResponsePredicate.status(HttpURLConnection.HTTP_FORBIDDEN));
            })
            // THEN the message gets rejected by the Lora adapter with a 403
            .onComplete(ctx.succeedingThenComplete());
    }

    /**
     * Verifies that the adapter rejects messages originating from a disabled device.
     *
     * @param ctx The test context
     */
    @Test
    @Timeout(timeUnit = TimeUnit.SECONDS, value = 20)
    public void testUploadMessageFailsForDisabledDevice(final VertxTestContext ctx) {

        // GIVEN a disabled device
        final Tenant tenant = new Tenant();

        helper.registry
            .addDeviceForTenant(tenantId, tenant, gatewayId, PWD)
            .compose(ok -> helper.registry.registerDevice(tenantId, LORA_DEVICE_ID, loraDeviceData.setEnabled(false)))
            .compose(ok -> {

                // WHEN the device tries to upload a message
                final MultiMap requestHeaders = MultiMap.caseInsensitiveMultiMap()
                        .add(HttpHeaders.CONTENT_TYPE, "application/json")
                        .add(HttpHeaders.AUTHORIZATION, authorization);

                return httpClient.create(
                        ENDPOINT_URI_LORIOT,
                        loriotRequestBody,
                        requestHeaders,
                        // TODO does not feel right to get back a 404 here because the
                        // resource actually exists, it is the payload that contains the
                        // offending data, so a 400 might be more appropriate
                        ResponsePredicate.status(HttpURLConnection.HTTP_NOT_FOUND));
            })
            // THEN the message gets rejected by the Lora adapter with a 404
            .onComplete(ctx.succeedingThenComplete());
    }

    /**
     * Verifies that an attempt to upload a message for a non-existing device fails if the Lora Network Server
     * is not authorized to auto-provision devices.
     *
     * @param ctx The test context.
     */
    @Test
    @Timeout(timeUnit = TimeUnit.SECONDS, value = 20)
    public void testUploadMessageFailsForNonExistingDevice(final VertxTestContext ctx) {

        // GIVEN a Lora Network Server that is not authorized to auto-provision devices
        final Tenant tenant = new Tenant();

        helper.registry
            .addDeviceForTenant(tenantId, tenant, gatewayId, PWD)
            .compose(ok -> {

                // WHEN the LNS tries to upload a message for a non-existing device
                final MultiMap requestHeaders = MultiMap.caseInsensitiveMultiMap()
                        .add(HttpHeaders.CONTENT_TYPE, "application/json")
                        .add(HttpHeaders.AUTHORIZATION, authorization);

                return httpClient.create(
                        ENDPOINT_URI_LORIOT,
                        loriotRequestBody,
                        requestHeaders,
                        // TODO does not feel right to get back a 404 here because the
                        // resource actually exists, it is the payload that contains the
                        // offending data, so a 400 might be more appropriate
                        ResponsePredicate.status(HttpURLConnection.HTTP_NOT_FOUND));
            })
            // THEN the message gets rejected by the adapter with a 404
            .onComplete(ctx.succeedingThenComplete());
    }

    /**
     * Verifies that the adapter rejects messages from a disabled Lora Network Server for an enabled
     * device.
     *
     * @param ctx The test context
     */
    @Test
    @Timeout(timeUnit = TimeUnit.SECONDS, value = 20)
    public void testUploadMessageFailsForDisabledLoraNetworkServer(final VertxTestContext ctx) {

        // GIVEN a device that is connected via a disabled Lora Network Server
        final Tenant tenant = new Tenant();
        final Device lnsData = new Device().setEnabled(Boolean.FALSE);

        helper.registry
            .addDeviceForTenant(tenantId, tenant, gatewayId, lnsData, PWD)
            .compose(ok -> helper.registry.registerDevice(tenantId, LORA_DEVICE_ID, loraDeviceData))
            .compose(ok -> {

                // WHEN the Lora Network Server tries to upload a message for the device
                final MultiMap requestHeaders = MultiMap.caseInsensitiveMultiMap()
                        .add(HttpHeaders.CONTENT_TYPE, "application/json")
                        .add(HttpHeaders.AUTHORIZATION, authorization);

                return httpClient.create(
                        ENDPOINT_URI_LORIOT,
                        loriotRequestBody,
                        requestHeaders,
                        ResponsePredicate.status(HttpURLConnection.HTTP_FORBIDDEN));

            })
            // THEN the message gets rejected by the adapter with a 403
            .onComplete(ctx.succeedingThenComplete());
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
}
