/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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

import static org.assertj.core.api.Assertions.assertThat;

import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import javax.security.auth.x500.X500Principal;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.service.management.device.Device;
import org.eclipse.hono.service.management.tenant.Adapter;
import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.service.management.tenant.TrustedCertificateAuthority;
import org.eclipse.hono.tests.CrudHttpClient;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.tests.Tenants;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.MessageHelper;
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

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.PemTrustOptions;
import io.vertx.core.net.SelfSignedCertificate;
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

    private static final String COMMAND_TO_SEND = "setBrightness";
    private static final String COMMAND_JSON_KEY = "brightness";

    private static final String ORIGIN_WILDCARD = "*";
    private static final Vertx VERTX = Vertx.vertx();
    private static final long  TEST_TIMEOUT_MILLIS = 20000; // 20 seconds
    private static final int MESSAGES_TO_SEND = 60;

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

    private long testStartTimeMillis;

    /**
     * Sets up clients.
     * 
     * @param ctx The vert.x test context.
     */
    @BeforeAll
    public static void init(final VertxTestContext ctx) {

        defaultOptions = new HttpClientOptions()
                .setDefaultHost(IntegrationTestSupport.HTTP_HOST)
                .setDefaultPort(IntegrationTestSupport.HTTPS_PORT)
                .setTrustOptions(new PemTrustOptions().addCertPath(IntegrationTestSupport.TRUST_STORE_PATH))
                .setVerifyHost(false)
                .setSsl(true);

        helper = new IntegrationTestSupport(VERTX);
        helper.init().setHandler(ctx.completing());
    }

    /**
     * Sets up the fixture.
     * 
     * @param testInfo Meta info about the test being run.
     */
    @BeforeEach
    public void setUp(final TestInfo testInfo) {

        testStartTimeMillis = System.currentTimeMillis();
        logger.info("running {}", testInfo.getDisplayName());
        logger.info("using HTTP adapter [host: {}, http port: {}, https port: {}]",
                IntegrationTestSupport.HTTP_HOST,
                IntegrationTestSupport.HTTP_PORT,
                IntegrationTestSupport.HTTPS_PORT);

        deviceCert = SelfSignedCertificate.create(UUID.randomUUID().toString());
        httpClient = new CrudHttpClient(VERTX, new HttpClientOptions(defaultOptions));
        httpClientWithClientCert = new CrudHttpClient(VERTX, new HttpClientOptions(defaultOptions)
                .setKeyCertOptions(deviceCert.keyCertOptions()));

        tenantId = helper.getRandomTenantId();
        deviceId = helper.getRandomDeviceId(tenantId);
        authorization = getBasicAuth(tenantId, deviceId, PWD);
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
        if (deviceCert != null) {
            deviceCert.delete();
        }
    }

    /**
     * Closes the AMQP 1.0 Messaging Network client.
     * 
     * @param ctx The vert.x test context.
     */
    @AfterAll
    public static void disconnect(final VertxTestContext ctx) {

        helper.disconnect().setHandler(ctx.completing());
    }

    /**
     * Creates the endpoint configuration variants for Command &amp; Control scenarios.
     * 
     * @return The configurations.
     */
    static Stream<HttpCommandEndpointConfiguration> commandAndControlVariants() {
        return Stream.of(
                new HttpCommandEndpointConfiguration(false, false, false),
                new HttpCommandEndpointConfiguration(false, false, true),
                new HttpCommandEndpointConfiguration(false, true, false),
                new HttpCommandEndpointConfiguration(false, true, true),

                // gateway devices are supported with north bound "command" endpoint only
                new HttpCommandEndpointConfiguration(true, false, false),
                new HttpCommandEndpointConfiguration(true, true, false)
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
    protected abstract Future<MessageConsumer> createConsumer(String tenantId, Consumer<Message> messageConsumer);

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

        helper.registry
        .addDeviceForTenant(tenantId, tenant, deviceId, PWD)
        .setHandler(setup.completing());

        assertThat(setup.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
        if (setup.failed()) {
            ctx.failNow(setup.causeOfFailure());
            return;
        }

        testUploadMessages(ctx, tenantId,
                count -> {
                    return httpClient.create(
                            getEndpointUri(),
                            Buffer.buffer("hello " + count),
                            requestHeaders,
                            response -> response.statusCode() == HttpURLConnection.HTTP_ACCEPTED
                                && hasAccessControlExposedHeaders(response.headers()));
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
        final Device device = new Device();
        device.setVia(Arrays.asList(gatewayOneId, gatewayTwoId));

        final VertxTestContext setup = new VertxTestContext();
        helper.registry.addDeviceForTenant(tenantId, tenant, gatewayOneId, PWD)
        .compose(ok -> helper.registry.addDeviceToTenant(tenantId, gatewayTwoId, PWD))
        .compose(ok -> helper.registry.registerDevice(tenantId, deviceId, device))
        .setHandler(setup.completing());

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

        final String uri = String.format("%s/%s/%s", getEndpointUri(), tenantId, deviceId);

        testUploadMessages(
                ctx,
                tenantId,
                count -> {
                    final MultiMap headers = (count.intValue() & 1) == 0 ? requestHeadersOne : requestHeadersTwo;
                    return httpClient.update( // GW uses PUT when acting on behalf of a device
                            uri,
                            Buffer.buffer("hello " + count),
                            headers,
                            status -> status == HttpURLConnection.HTTP_ACCEPTED);
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
        .setHandler(setup.completing());

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
                    response -> response.statusCode() == HttpURLConnection.HTTP_ACCEPTED
                            && hasAccessControlExposedHeaders(response.headers()));
        });
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
            final Function<Integer, Future<MultiMap>> requestSender) throws InterruptedException {
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
            final Consumer<Message> messageConsumer,
            final Function<Integer, Future<MultiMap>> requestSender) throws InterruptedException {
        testUploadMessages(ctx, tenantId, messageConsumer, requestSender, MESSAGES_TO_SEND);
    }

    /**
     * Uploads messages to the HTTP endpoint.
     *
     * @param ctx The test context to run on.
     * @param tenantId The tenant that the device belongs to.
     * @param messageConsumer Consumer that is invoked when a message was received.
     * @param requestSender The test device that will publish the data.
     * @param numberOfMessages The number of messages that are uploaded.
     * @throws InterruptedException if the test is interrupted before it has finished.
     */
    protected void testUploadMessages(
            final VertxTestContext ctx,
            final String tenantId,
            final Consumer<Message> messageConsumer,
            final Function<Integer, Future<MultiMap>> requestSender,
            final int numberOfMessages) throws InterruptedException {

        final CountDownLatch received = new CountDownLatch(numberOfMessages);
        final VertxTestContext setup = new VertxTestContext();

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
        }).setHandler(setup.completing());

        assertThat(setup.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
        if (setup.failed()) {
            ctx.failNow(setup.causeOfFailure());
            return;
        }

        final long start = System.currentTimeMillis();
        int messageCount = 0;

        while (messageCount < numberOfMessages) {

            final int currentMessage = messageCount;
            messageCount++;

            final CountDownLatch sending = new CountDownLatch(1);
            requestSender.apply(currentMessage).compose(this::assertHttpResponse).setHandler(attempt -> {
                try {
                    if (attempt.succeeded()) {
                        logger.debug("sent message {}", currentMessage);
                    } else {
                        logger.info("failed to send message {}: {}", currentMessage, attempt.cause().getMessage());
                        ctx.failNow(attempt.cause());
                    }
                } finally {
                    sending.countDown();
                }
            });

            if (currentMessage % 20 == 0) {
                logger.info("messages sent: " + currentMessage);
            }
            sending.await();
        }

        final long timeToWait = Math.max(TEST_TIMEOUT_MILLIS - 50 - (System.currentTimeMillis() - testStartTimeMillis),
                1);
        if (!received.await(timeToWait, TimeUnit.MILLISECONDS)) {
            logger.info("sent {} and received {} messages after {} milliseconds",
                    messageCount, numberOfMessages - received.getCount(), System.currentTimeMillis() - start);
            ctx.failNow(new IllegalStateException("did not receive all messages sent"));
        } else {
            logger.info("sent {} and received {} messages after {} milliseconds",
                    messageCount, numberOfMessages - received.getCount(), System.currentTimeMillis() - start);
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

        final Tenant tenant = new Tenant();

        final MultiMap requestHeaders = MultiMap.caseInsensitiveMultiMap()
                .add(HttpHeaders.CONTENT_TYPE, "text/plain")
                .add(HttpHeaders.ORIGIN, ORIGIN_URI);

        final KeyPair keyPair = IntegrationTestSupport.newEcKeyPair();

        // GIVEN a tenant configured with a trust anchor
        helper.getCertificate(deviceCert.certificatePath())
                .compose(cert -> {

                    final TrustedCertificateAuthority trustedCertificateAuthority = new TrustedCertificateAuthority();

                    trustedCertificateAuthority
                            .setSubjectDn(cert.getIssuerX500Principal().getName(X500Principal.RFC2253))
                            .setPublicKey(keyPair.getPublic().getEncoded())
                            .setKeyAlgorithm(keyPair.getPublic().getAlgorithm());

                    final var trustedAuthorities = new ArrayList<TrustedCertificateAuthority>();
                    trustedAuthorities.add(trustedCertificateAuthority);
                    tenant.setTrustedAuthorities(trustedAuthorities);

                    return helper.registry.addDeviceForTenant(tenantId, tenant, deviceId, cert);
                })
                // WHEN a device tries to upload data and authenticate with a client
                // certificate that has not been signed with the configured trusted CA
                .compose(ok -> httpClientWithClientCert.create(
                        getEndpointUri(),
                        Buffer.buffer("hello"),
                        requestHeaders,
                        response -> response.statusCode() == HttpURLConnection.HTTP_ACCEPTED))
                // THEN the request fails with a 401
                .setHandler(ctx.failing(t -> {
                    ctx.verify(() -> {
                        assertThat(t).isInstanceOf(ServiceInvocationException.class);
                        assertThat(((ServiceInvocationException) t).getErrorCode())
                                .isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);
                    });
                    ctx.completeNow();
                }));
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
                    response -> response.statusCode() == HttpURLConnection.HTTP_ACCEPTED)
                    .recover(HttpProtocolException::transformInto);

        })
        .setHandler(ctx.failing(t -> {

            // THEN the message gets rejected by the HTTP adapter with a 403
            logger.info("could not publish message for disabled tenant [{}]", tenantId);
            ctx.verify(() -> HttpProtocolException.assertProtocolError(HttpURLConnection.HTTP_FORBIDDEN, t));
            ctx.completeNow();
        }));
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
                    response -> response.statusCode() == HttpURLConnection.HTTP_ACCEPTED)
                    .recover(HttpProtocolException::transformInto);

        })
        .setHandler(ctx.failing(t -> {

            // THEN the message gets rejected by the HTTP adapter with a 404
            logger.info("could not publish message for disabled device [tenant-id: {}, device-id: {}]",
                    tenantId, deviceId);
            ctx.verify(() ->  HttpProtocolException.assertProtocolError(HttpURLConnection.HTTP_NOT_FOUND, t));
            ctx.completeNow();
        }));
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
                    statusCode -> statusCode == HttpURLConnection.HTTP_ACCEPTED)
                    .recover(HttpProtocolException::transformInto);

        })
        .setHandler(ctx.failing(t -> {

            // THEN the message gets rejected by the HTTP adapter with a 403
            logger.info("could not publish message for disabled gateway [tenant-id: {}, gateway-id: {}]",
                    tenantId, gatewayId);
            ctx.verify(() -> HttpProtocolException.assertProtocolError(HttpURLConnection.HTTP_FORBIDDEN, t));
            ctx.completeNow();
        }));
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
                    statusCode -> statusCode == HttpURLConnection.HTTP_ACCEPTED)
                    .recover(HttpProtocolException::transformInto);

        })
        .setHandler(ctx.failing(t -> {

            // THEN the message gets rejected by the HTTP adapter with a 403
            logger.info("could not publish message for unauthorized gateway [tenant-id: {}, gateway-id: {}]",
                    tenantId, gatewayId);
            ctx.verify(() -> HttpProtocolException.assertProtocolError(HttpURLConnection.HTTP_FORBIDDEN, t));
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that the HTTP adapter returns a command in response to an initial upload request containing a TTD
     * and immediately returns a 202 in response to consecutive upload requests which include a TTD value
     * and which are sent before the command response to the initial request has been received.
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
            TimeUntilDisconnectNotification.fromMessage(msg).ifPresent(notification -> {
                final Integer ttd = MessageHelper.getTimeUntilDisconnect(msg);
                logger.debug("processing piggy backed message [ttd: {}]", ttd);
                ctx.verify(() -> {
                    assertThat(notification.getTenantId()).isEqualTo(tenantId);
                    assertThat(notification.getDeviceId()).isEqualTo(deviceId);
                });
            });
            switch(msg.getContentType()) {
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
            // we need to send an initial request with QoS 1 to trigger establishment
            // of links required for processing requests in the HTTP adapter
            // otherwise, the second of the two consecutive upload requests
            // might fail immediately because the links have not been established yet
            return httpClient.create(
                getEndpointUri(),
                Buffer.buffer("trigger msg"),
                MultiMap.caseInsensitiveMultiMap()
                    .add(HttpHeaders.CONTENT_TYPE, "application/trigger")
                    .add(HttpHeaders.AUTHORIZATION, authorization)
                    .add(HttpHeaders.ORIGIN, ORIGIN_URI)
                    .add(Constants.HEADER_QOS_LEVEL, "1"),
                response -> response.statusCode() >= 200 && response.statusCode() < 300);
        }).setHandler(setup.completing());

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

        final Future<MultiMap> firstRequest = httpClient.create(
                getEndpointUri(),
                Buffer.buffer("hello one"),
                requestHeaders,
                response -> response.statusCode() >= 200 && response.statusCode() < 300)
                .map(headers -> {
                    logger.info("received response to first request");
                    return headers;
                });
        logger.info("sent first request");
        firstMessageReceived.await();

        // followed by a second request
        requestHeaders = MultiMap.caseInsensitiveMultiMap()
                .add(HttpHeaders.CONTENT_TYPE, "text/msg2")
                .add(HttpHeaders.AUTHORIZATION, authorization)
                .add(HttpHeaders.ORIGIN, ORIGIN_URI)
                .add(Constants.HEADER_TIME_TILL_DISCONNECT, "5");
        final Future<MultiMap> secondRequest = httpClient.create(
                getEndpointUri(),
                Buffer.buffer("hello two"),
                requestHeaders,
                response -> response.statusCode() >= 200 && response.statusCode() < 300)
                .map(headers -> {
                    logger.info("received response to second request");
                    return headers;
                });
        logger.info("sent second request");
        // wait for messages having been received
        secondMessageReceived.await();
        // send command
        final JsonObject inputData = new JsonObject().put(COMMAND_JSON_KEY, (int) (Math.random() * 100));
        final Future<Void> commandSent = helper.sendOneWayCommand(tenantId, deviceId, COMMAND_TO_SEND, "application/json", inputData.toBuffer(), null, 3000);
        logger.info("sent one-way command to device");

        // THEN both requests succeed
        CompositeFuture.all(commandSent, firstRequest, secondRequest)
        .setHandler(ctx.succeeding(ok -> {
            ctx.verify(() -> {
                // and the response to the first request contains a command
                assertThat(firstRequest.result().get(Constants.HEADER_COMMAND)).isEqualTo(COMMAND_TO_SEND);

                // while the response to the second request is empty
                assertThat(secondRequest.result().get(Constants.HEADER_COMMAND)).isNull();
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

        helper.registry.addDeviceForTenant(tenantId, tenant, deviceId, PWD).setHandler(setup.completing());

        assertThat(setup.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
        if (setup.failed()) {
            ctx.failNow(setup.causeOfFailure());
            return;
        }

        testUploadMessages(ctx, tenantId,
                msg -> {
                    logger.trace("received message");
                    TimeUntilDisconnectNotification.fromMessage(msg).ifPresent(notification -> {
                        ctx.verify(() -> {
                            assertThat(notification.getTtd()).isEqualTo(2);
                            assertThat(notification.getTenantId()).isEqualTo(tenantId);
                            assertThat(notification.getDeviceId()).isEqualTo(deviceId);
                        });
                    });
                    // do NOT send a command, but let the HTTP adapter's timer expire
                },
                count -> {
                    return httpClient.create(
                            getEndpointUri(),
                            Buffer.buffer("hello " + count),
                            requestHeaders,
                            response -> response.statusCode() == HttpURLConnection.HTTP_ACCEPTED)
                            .map(responseHeaders -> {
                                ctx.verify(() -> {
                                    // assert that the response does not contain a command nor a request ID nor a payload
                                    assertThat(responseHeaders.get(Constants.HEADER_COMMAND)).isNull();
                                    assertThat(responseHeaders.get(Constants.HEADER_COMMAND_REQUEST_ID)).isNull();
                                    assertThat(responseHeaders.get(HttpHeaders.CONTENT_LENGTH)).isEqualTo("0");
                                });
                                return responseHeaders;
                            });
                },
                5);
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
        final VertxTestContext setup = new VertxTestContext();

        final MultiMap requestHeaders = MultiMap.caseInsensitiveMultiMap()
                .add(HttpHeaders.CONTENT_TYPE, "text/plain")
                .add(HttpHeaders.AUTHORIZATION, authorization)
                .add(HttpHeaders.ORIGIN, ORIGIN_URI)
                .add(Constants.HEADER_TIME_TILL_DISCONNECT, "2");

        final MultiMap cmdResponseRequestHeaders = MultiMap.caseInsensitiveMultiMap()
                .add(HttpHeaders.CONTENT_TYPE, "text/plain")
                .add(HttpHeaders.AUTHORIZATION, authorization)
                .add(HttpHeaders.ORIGIN, ORIGIN_URI)
                .add(Constants.HEADER_COMMAND_RESPONSE_STATUS, "200");

        helper.registry
        .addDeviceForTenant(tenantId, tenant, deviceId, PWD)
        .setHandler(setup.completing());

        assertThat(setup.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
        if (setup.failed()) {
            ctx.failNow(setup.causeOfFailure());
            return;
        }

        final String commandTarget = helper.setupGatewayDeviceBlocking(tenantId, deviceId, endpointConfig.isGatewayDevice(), 5);

        testUploadMessages(ctx, tenantId,
                msg -> {

                    TimeUntilDisconnectNotification.fromMessage(msg).ifPresent(notification -> {
                        logger.trace("received piggy backed message [ttd: {}]: {}", notification.getTtd(), msg);
                        ctx.verify(() -> {
                            assertThat(notification.getTenantId()).isEqualTo(tenantId);
                            assertThat(notification.getDeviceId()).isEqualTo(deviceId);
                        });
                        // now ready to send a command
                        final JsonObject inputData = new JsonObject().put(COMMAND_JSON_KEY, (int) (Math.random() * 100));
                        helper.sendCommand(
                                tenantId,
                                commandTarget,
                                COMMAND_TO_SEND,
                                "application/json",
                                inputData.toBuffer(),
                                null,
                                notification.getMillisecondsUntilExpiry(),
                                endpointConfig.isLegacyNorthboundEndpoint())
                        .setHandler(ctx.succeeding(response -> {
                            ctx.verify(() -> {
                                assertThat(response.getContentType()).isEqualTo("text/plain");
                                assertThat(response.getApplicationProperty(MessageHelper.APP_PROPERTY_DEVICE_ID, String.class)).isEqualTo(deviceId);
                                assertThat(response.getApplicationProperty(MessageHelper.APP_PROPERTY_TENANT_ID, String.class)).isEqualTo(tenantId);
                            });
                        }));
                    });
                },
                count -> {
                    return httpClient.create(
                            getEndpointUri(),
                            Buffer.buffer("hello " + count),
                            requestHeaders,
                            response -> response.statusCode() == HttpURLConnection.HTTP_OK)
                            .map(responseHeaders -> {

                                final String requestId = responseHeaders.get(Constants.HEADER_COMMAND_REQUEST_ID);

                                ctx.verify(() -> {
                                    // assert that the response contains a command
                                    assertThat(responseHeaders.get(Constants.HEADER_COMMAND)).isEqualTo(COMMAND_TO_SEND);
                                    assertThat(responseHeaders.get(HttpHeaders.CONTENT_TYPE)).isEqualTo("application/json");
                                    assertThat(requestId).isNotNull();
                                    assertThat(responseHeaders.get(HttpHeaders.CONTENT_LENGTH)).isNotEqualTo("0");
                                });
                                return requestId;

                            }).compose(receivedCommandRequestId -> {

                                // send a response to the command now
                                final String responseUri = endpointConfig.getCommandResponseUri(receivedCommandRequestId);

                                logger.debug("posting response to command [uri: {}]", responseUri);

                                return httpClient.create(
                                        responseUri,
                                        Buffer.buffer("ok"),
                                        cmdResponseRequestHeaders,
                                        response -> response.statusCode() == HttpURLConnection.HTTP_ACCEPTED);
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
                .add(Constants.HEADER_TIME_TILL_DISCONNECT, "2");

        helper.registry
        .addDeviceForTenant(tenantId, tenant, deviceId, PWD)
        .setHandler(setup.completing());

        assertThat(setup.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
        if (setup.failed()) {
            ctx.failNow(setup.causeOfFailure());
            return;
        }

        final String commandTarget = helper.setupGatewayDeviceBlocking(tenantId, deviceId, endpointConfig.isGatewayDevice(), 5);

        testUploadMessages(ctx, tenantId,
                msg -> {
                    TimeUntilDisconnectNotification.fromMessage(msg).ifPresent(notification -> {

                        logger.trace("received piggy backed message [ttd: {}]: {}", notification.getTtd(), msg);
                        assertThat(notification.getTenantId()).isEqualTo(tenantId);
                        assertThat(notification.getDeviceId()).isEqualTo(deviceId);
                        // now ready to send a command
                        final JsonObject inputData = new JsonObject().put(COMMAND_JSON_KEY, (int) (Math.random() * 100));
                        helper.sendOneWayCommand(
                                tenantId,
                                commandTarget,
                                COMMAND_TO_SEND,
                                "application/json",
                                inputData.toBuffer(),
                                null,
                                notification.getMillisecondsUntilExpiry(),
                                endpointConfig.isLegacyNorthboundEndpoint())
                        .setHandler(ctx.succeeding());
                    });
                },
                count -> {
                    final Buffer payload = Buffer.buffer("hello " + count);
                    return httpClient.create(
                            getEndpointUri(),
                            payload,
                            requestHeaders,
                            response -> response.statusCode() == HttpURLConnection.HTTP_OK)
                    .recover(t -> {

                        // we probably sent the request before the
                        // HTTP adapter was able to close the command
                        // consumer for the previous request
                        // wait a little and try again
                        final Future<MultiMap> retryResult = Future.future();
                        VERTX.setTimer(100, retry -> {
                            logger.info("re-trying last request [{}]", count);
                            httpClient.create(
                                    getEndpointUri(),
                                    payload,
                                    requestHeaders,
                                    response -> response.statusCode() == HttpURLConnection.HTTP_OK)
                            .setHandler(retryResult);
                        });
                        return retryResult;
                    })
                    .map(responseHeaders -> {
                        ctx.verify(() -> {
                            // assert that the response contains a one-way command
                            assertThat(responseHeaders.get(Constants.HEADER_COMMAND)).isEqualTo(COMMAND_TO_SEND);
                            assertThat(responseHeaders.get(HttpHeaders.CONTENT_TYPE)).isEqualTo("application/json");
                            assertThat(responseHeaders.get(Constants.HEADER_COMMAND_REQUEST_ID)).isNull();
                        });
                        return responseHeaders;
                    });
                });
    }

    private void assertMessageProperties(final VertxTestContext ctx, final Message msg) {
        ctx.verify(() -> {
            assertThat(MessageHelper.getDeviceId(msg)).isNotNull();
            assertThat(MessageHelper.getTenantIdAnnotation(msg)).isNotNull();
            assertThat(MessageHelper.getDeviceIdAnnotation(msg)).isNotNull();
            assertThat(MessageHelper.getRegistrationAssertion(msg)).isNull();
            assertAdditionalMessageProperties(msg);
        });
    }

    private boolean hasAccessControlExposedHeaders(final MultiMap responseHeaders) {
        final String exposedHeaders = responseHeaders.get(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS);
        return exposedHeaders != null
                && exposedHeaders.contains(Constants.HEADER_COMMAND)
                && exposedHeaders.contains(Constants.HEADER_COMMAND_REQUEST_ID);
    }

    /**
     * Performs additional checks on a received message.
     * <p>
     * This default implementation does nothing. Subclasses should override this method to implement
     * reasonable checks.
     * 
     * @param msg The message to perform checks on.
     */
    protected void assertAdditionalMessageProperties(final Message msg) {
        // empty
    }

    private Future<?> assertHttpResponse(final MultiMap responseHeaders) {

        final Future<?> result = Future.future();
        final String allowedOrigin = responseHeaders.get(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);
        final boolean hasValidOrigin = allowedOrigin != null
                && (allowedOrigin.equals(ORIGIN_WILDCARD) || allowedOrigin.equals(ORIGIN_URI));

        if (!hasValidOrigin) {
            result.fail(new IllegalArgumentException("response contains invalid allowed origin: " + allowedOrigin));
        } else {
            result.complete();
        }
        return result;
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
