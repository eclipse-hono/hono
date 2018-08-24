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

package org.eclipse.hono.tests.http;

import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.security.auth.x500.X500Principal;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.tests.CrudHttpClient;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.TenantConstants;
import org.eclipse.hono.util.TenantObject;
import org.eclipse.hono.util.TimeUntilDisconnectNotification;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.rules.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.PemTrustOptions;
import io.vertx.core.net.SelfSignedCertificate;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;

/**
 * Base class for HTTP adapter integration tests.
 *
 */
public abstract class HttpTestBase {

    private static final String COMMAND_TO_SEND = "setBrightness";
    private static final String COMMAND_JSON_KEY = "brightness";
    private static final String COMMAND_RESPONSE_URI_TEMPLATE = "/control/res/%s";

    private static final String ORIGIN_WILDCARD = "*";
    private static final Vertx VERTX = Vertx.vertx();
    private static final long  TEST_TIMEOUT = 10000; // ms
    private static final int MESSAGES_TO_SEND = 60;

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
     * The default options to use for creating HTTP clients.
     */
    private static HttpClientOptions defaultOptions;

    /**
     * Time out each test after five seconds.
     */
    @Rule
    public final Timeout timeout = Timeout.millis(TEST_TIMEOUT);
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

    /**
     * Sets up clients.
     * 
     * @param ctx The vert.x test context.
     */
    @BeforeClass
    public static void init(final TestContext ctx) {

        helper = new IntegrationTestSupport(VERTX);
        helper.init(ctx);

        defaultOptions = new HttpClientOptions()
           .setDefaultHost(IntegrationTestSupport.HTTP_HOST)
           .setDefaultPort(IntegrationTestSupport.HTTPS_PORT)
           .setTrustOptions(new PemTrustOptions().addCertPath(IntegrationTestSupport.TRUST_STORE_PATH))
           .setVerifyHost(false)
           .setSsl(true);
    }

    /**
     * Sets up the fixture.
     */
    @Before
    public void setUp() {

        logger.info("running {}", testName.getMethodName());
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
    @After
    public void deleteObjects(final TestContext ctx) {

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
    @AfterClass
    public static void disconnect(final TestContext ctx) {

        helper.disconnect(ctx);
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
    public void testUploadMessagesUsingBasicAuth(final TestContext ctx) throws InterruptedException {

        final Async setup = ctx.async();
        final TenantObject tenant = TenantObject.from(tenantId, true);
        final MultiMap requestHeaders = MultiMap.caseInsensitiveMultiMap()
                .add(HttpHeaders.CONTENT_TYPE, "text/plain")
                .add(HttpHeaders.AUTHORIZATION, authorization)
                .add(HttpHeaders.ORIGIN, ORIGIN_URI);

        helper.registry.addDeviceForTenant(tenant, deviceId, PWD).setHandler(ctx.asyncAssertSuccess(ok -> setup.complete()));
        setup.await();

        testUploadMessages(ctx, tenantId,
                count -> {
                    return httpClient.create(
                            getEndpointUri(),
                            Buffer.buffer("hello " + count),
                            requestHeaders,
                            response -> response.statusCode() == HttpURLConnection.HTTP_ACCEPTED);
                });
    }

    /**
     * Verifies that a number of messages uploaded to the HTTP adapter via a gateway
     * using HTTP Basic auth can be successfully consumed via the AMQP Messaging Network.
     * 
     * @param ctx The test context.
     * @throws InterruptedException if the test fails.
     */
    @Test
    public void testUploadMessagesViaGateway(final TestContext ctx) throws InterruptedException {

        // GIVEN a device that is connected via a gateway
        final TenantObject tenant = TenantObject.from(tenantId, true);
        final String gatewayId = helper.getRandomDeviceId(tenantId);
        final JsonObject deviceData = new JsonObject()
                .put("via", gatewayId);

        final Async setup = ctx.async();
        helper.registry.addDeviceForTenant(tenant, gatewayId, PWD)
        .compose(ok -> helper.registry.registerDevice(tenantId, deviceId, deviceData))
        .setHandler(ctx.asyncAssertSuccess(ok -> setup.complete()));
        setup.await();

        final MultiMap requestHeaders = MultiMap.caseInsensitiveMultiMap()
                .add(HttpHeaders.CONTENT_TYPE, "text/plain")
                .add(HttpHeaders.AUTHORIZATION, getBasicAuth(tenantId, gatewayId, PWD))
                .add(HttpHeaders.ORIGIN, ORIGIN_URI);

        testUploadMessages(ctx, tenantId,
                count -> {
                    return httpClient.create(
                            getEndpointUri(),
                            Buffer.buffer("hello " + count),
                            requestHeaders,
                            response -> response.statusCode() == HttpURLConnection.HTTP_ACCEPTED);
                });
    }

    /**
     * Verifies that a number of messages uploaded to Hono's HTTP adapter
     * using client certificate based authentication can be successfully
     * consumed via the AMQP Messaging Network.
     * 
     * @param ctx The test context.
     * @throws InterruptedException if the test fails.
     */
    @Test
    public void testUploadMessagesUsingClientCertificate(final TestContext ctx) throws InterruptedException {

        final Async setup = ctx.async();
        final MultiMap requestHeaders = MultiMap.caseInsensitiveMultiMap()
                .add(HttpHeaders.CONTENT_TYPE, "text/plain")
                .add(HttpHeaders.ORIGIN, ORIGIN_URI);

        helper.getCertificate(deviceCert.certificatePath()).compose(cert -> {
            final TenantObject tenant = TenantObject.from(tenantId, true);
            tenant.setTrustAnchor(cert.getPublicKey(), cert.getSubjectX500Principal());
            return helper.registry.addDeviceForTenant(tenant, deviceId, cert);
        }).setHandler(ctx.asyncAssertSuccess(ok -> setup.complete()));

        setup.await();

        testUploadMessages(ctx, tenantId, count -> {
            return httpClientWithClientCert.create(
                    getEndpointUri(),
                    Buffer.buffer("hello " + count),
                    requestHeaders,
                    response -> response.statusCode() == HttpURLConnection.HTTP_ACCEPTED);
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
            final TestContext ctx,
            final String tenantId,
            final Function<Integer, Future<MultiMap>> requestSender) throws InterruptedException {
        this.testUploadMessages(ctx, tenantId, null, requestSender);
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
            final TestContext ctx,
            final String tenantId,
            final Consumer<Message> messageConsumer,
            final Function<Integer, Future<MultiMap>> requestSender) throws InterruptedException {
        this.testUploadMessages(ctx, tenantId, messageConsumer, requestSender, MESSAGES_TO_SEND);
    }

    /**
     * Uploads messages to the HTTP endpoint.
     *
     * @param ctx The test context to run on.
     * @param tenantId The tenant that the device belongs to.
     * @param messageConsumer Consumer that is invoked when a message was received.
     * @param requestSender The test device that will publish the data.
     * @param numberOfMessages The number of messages that are uploaded.
     * @throws InterruptedException if the test is interrupted before it
     *              has finished.
     */
    protected void testUploadMessages(
            final TestContext ctx,
            final String tenantId,
            final Consumer<Message> messageConsumer,
            final Function<Integer, Future<MultiMap>> requestSender,
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
        }).setHandler(ctx.asyncAssertSuccess(ok -> setup.complete()));

        setup.await();
        final long start = System.currentTimeMillis();
        final AtomicInteger messageCount = new AtomicInteger(0);

        while (messageCount.get() < numberOfMessages) {

            final Async sending = ctx.async();
            requestSender.apply(messageCount.getAndIncrement()).compose(this::assertHttpResponse).setHandler(attempt -> {
                if (attempt.succeeded()) {
                    logger.debug("sent message {}", messageCount.get());
                } else {
                    logger.info("failed to send message {}: {}", messageCount.get(), attempt.cause().getMessage());
                    ctx.fail(attempt.cause());
                }
                sending.complete();
            });

            if (messageCount.get() % 20 == 0) {
                logger.info("messages sent: " + messageCount.get());
            }
            sending.await();
        }

        final long timeToWait = Math.max(TEST_TIMEOUT - 1000, Math.round(numberOfMessages * 20));
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
     * Verifies that the adapter fails to authorize a device using a client certificate
     * if the public key that is registered for the tenant that the device belongs to can
     * not be parsed into a trust anchor.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testUploadFailsForMalformedCaPublicKey(final TestContext ctx) {

        final Async setup = ctx.async();
        final TenantObject tenant = TenantObject.from(tenantId, true);
        final MultiMap requestHeaders = MultiMap.caseInsensitiveMultiMap()
                .add(HttpHeaders.CONTENT_TYPE, "text/plain")
                .add(HttpHeaders.ORIGIN, ORIGIN_URI);

        // GIVEN a tenant configured with an invalid Base64 encoding of the
        // trust anchor public key
        helper.getCertificate(deviceCert.certificatePath())
        .compose(cert -> {
            tenant.setProperty(
                    TenantConstants.FIELD_PAYLOAD_TRUSTED_CA,
                    new JsonObject()
                        .put(TenantConstants.FIELD_PAYLOAD_SUBJECT_DN, cert.getIssuerX500Principal().getName(X500Principal.RFC2253))
                        .put(TenantConstants.FIELD_PAYLOAD_PUBLIC_KEY, "notBase64"));
            return helper.registry.addDeviceForTenant(tenant, deviceId, cert);
        })
        .setHandler(ctx.asyncAssertSuccess(ok -> setup.complete()));
        setup.await();

        // WHEN a device tries to upload data and authenticate with a client
        // certificate that has been signed with the configured trusted CA
        httpClientWithClientCert.create(
                getEndpointUri(),
                Buffer.buffer("hello"),
                requestHeaders,
                response -> response.statusCode() == HttpURLConnection.HTTP_ACCEPTED).setHandler(ctx.asyncAssertFailure(t -> {
                    // THEN the request fails with a 401
                    ctx.assertTrue(t instanceof ServiceInvocationException);
                    ctx.assertEquals(HttpURLConnection.HTTP_UNAUTHORIZED, ((ServiceInvocationException) t).getErrorCode());
                }));
    }

    /**
     * Verifies that the adapter fails to authenticate a device if the device's client
     * certificate's signature cannot be validated using the trust anchor that is registered
     * for the tenant that the device belongs to.
     * 
     * @param ctx The vert.x test context.
     * @throws GeneralSecurityException if the tenant's trust anchor cannot be generated
     */
    @Test
    public void testUploadFailsForNonMatchingTrustAnchor(final TestContext ctx) throws GeneralSecurityException {

        final Async setup = ctx.async();
        final TenantObject tenant = TenantObject.from(tenantId, true);
        final MultiMap requestHeaders = MultiMap.caseInsensitiveMultiMap()
                .add(HttpHeaders.CONTENT_TYPE, "text/plain")
                .add(HttpHeaders.ORIGIN, ORIGIN_URI);

        final KeyPair keyPair = helper.newEcKeyPair();

        // GIVEN a tenant configured with a trust anchor
        helper.getCertificate(deviceCert.certificatePath())
        .compose(cert -> {
            tenant.setProperty(
                    TenantConstants.FIELD_PAYLOAD_TRUSTED_CA,
                    new JsonObject()
                        .put(TenantConstants.FIELD_PAYLOAD_SUBJECT_DN, cert.getIssuerX500Principal().getName(X500Principal.RFC2253))
                        .put(TenantConstants.FIELD_ADAPTERS_TYPE, "EC")
                        .put(TenantConstants.FIELD_PAYLOAD_PUBLIC_KEY, Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded())));
            return helper.registry.addDeviceForTenant(tenant, deviceId, cert);
        })
        .setHandler(ctx.asyncAssertSuccess(ok -> setup.complete()));
        setup.await();

        // WHEN a device tries to upload data and authenticate with a client
        // certificate that has not been signed with the configured trusted CA
        httpClientWithClientCert.create(
                getEndpointUri(),
                Buffer.buffer("hello"),
                requestHeaders,
                response -> response.statusCode() == HttpURLConnection.HTTP_ACCEPTED).setHandler(ctx.asyncAssertFailure(t -> {
                    // THEN the request fails with a 401
                    ctx.assertTrue(t instanceof ServiceInvocationException);
                    ctx.assertEquals(HttpURLConnection.HTTP_UNAUTHORIZED, ((ServiceInvocationException) t).getErrorCode());
                }));
    }

    /**
     * Verifies that the HTTP adapter rejects messages from a device
     * that belongs to a tenant for which the HTTP adapter has been disabled
     * with a 403.
     *
     * @param ctx The test context
     */
    @Test
    public void testUploadMessageFailsForDisabledTenant(final TestContext ctx) {

        // GIVEN a tenant for which the HTTP adapter is disabled
        final JsonObject adapterDetailsHttp = new JsonObject()
                .put(TenantConstants.FIELD_ADAPTERS_TYPE, Constants.PROTOCOL_ADAPTER_TYPE_HTTP)
                .put(TenantConstants.FIELD_ENABLED, Boolean.FALSE);
        final TenantObject tenant = TenantObject.from(tenantId, true);
        tenant.addAdapterConfiguration(adapterDetailsHttp);

        helper.registry.addDeviceForTenant(tenant, deviceId, PWD).compose(ok -> {

            // WHEN a device that belongs to the tenant uploads a message
            final MultiMap requestHeaders = MultiMap.caseInsensitiveMultiMap()
                    .add(HttpHeaders.CONTENT_TYPE, "test/plain")
                    .add(HttpHeaders.AUTHORIZATION, authorization);

            return httpClient.create(
                    getEndpointUri(),
                    Buffer.buffer("hello"),
                    requestHeaders,
                    response -> response.statusCode() == HttpURLConnection.HTTP_ACCEPTED);

        }).setHandler(ctx.asyncAssertFailure(t -> {

            // THEN the message gets rejected by the HTTP adapter with a 403
            logger.info("could not publish message for disabled tenant [{}]", tenantId);
            ctx.assertEquals(HttpURLConnection.HTTP_FORBIDDEN, ((ServiceInvocationException) t).getErrorCode());
        }));
    }

    /**
     * Verifies that the HTTP adapter rejects messages from a disabled device
     * with a 404.
     *
     * @param ctx The test context
     */
    @Test
    public void testUploadMessageFailsForDisabledDevice(final TestContext ctx) {

        // GIVEN a disabled device
        final TenantObject tenant = TenantObject.from(tenantId, true);
        final JsonObject deviceData = new JsonObject()
                .put(RegistrationConstants.FIELD_ENABLED, Boolean.FALSE);

        helper.registry.addDeviceForTenant(tenant, deviceId, deviceData, PWD).compose(ok -> {

            // WHEN the device tries to upload a message
            final MultiMap requestHeaders = MultiMap.caseInsensitiveMultiMap()
                    .add(HttpHeaders.CONTENT_TYPE, "text/plain")
                    .add(HttpHeaders.AUTHORIZATION, authorization);

            return httpClient.create(
                    getEndpointUri(),
                    Buffer.buffer("hello"),
                    requestHeaders,
                    response -> response.statusCode() == HttpURLConnection.HTTP_ACCEPTED);

        }).setHandler(ctx.asyncAssertFailure(t -> {

            // THEN the message gets rejected by the HTTP adapter with a 404
            logger.info("could not publish message for disabled device [tenant-id: {}, device-id: {}]",
                    tenantId, deviceId);
            ctx.assertEquals(HttpURLConnection.HTTP_NOT_FOUND, ((ServiceInvocationException) t).getErrorCode());
        }));
    }

    /**
     * Verifies that the HTTP adapter rejects messages from a disabled gateway
     * for an enabled device with a 403.
     *
     * @param ctx The test context
     */
    @Test
    public void testUploadMessageFailsForDisabledGateway(final TestContext ctx) {

        // GIVEN a device that is connected via a disabled gateway
        final TenantObject tenant = TenantObject.from(tenantId, true);
        final String gatewayId = helper.getRandomDeviceId(tenantId);
        final JsonObject gatewayData = new JsonObject()
                .put(RegistrationConstants.FIELD_ENABLED, Boolean.FALSE);
        final JsonObject deviceData = new JsonObject()
                .put("via", gatewayId);

        helper.registry.addDeviceForTenant(tenant, gatewayId, gatewayData, PWD)
        .compose(ok -> helper.registry.registerDevice(tenantId, deviceId, deviceData))
        .compose(ok -> {

            // WHEN the gateway tries to upload a message for the device
            final MultiMap requestHeaders = MultiMap.caseInsensitiveMultiMap()
                    .add(HttpHeaders.CONTENT_TYPE, "text/plain")
                    .add(HttpHeaders.AUTHORIZATION, getBasicAuth(tenantId, gatewayId, PWD));

            return httpClient.update(
                    String.format("%s/%s/%s", getEndpointUri(), tenantId, deviceId),
                    Buffer.buffer("hello"),
                    requestHeaders,
                    statusCode -> statusCode == HttpURLConnection.HTTP_ACCEPTED);

        }).setHandler(ctx.asyncAssertFailure(t -> {

            // THEN the message gets rejected by the HTTP adapter with a 403
            logger.info("could not publish message for disabled gateway [tenant-id: {}, gateway-id: {}]",
                    tenantId, gatewayId);
            ctx.assertEquals(HttpURLConnection.HTTP_FORBIDDEN, ((ServiceInvocationException) t).getErrorCode());
        }));
    }

    /**
     * Verifies that the HTTP adapter rejects messages from a gateway
     * for an device that it is not authorized for with a 403.
     *
     * @param ctx The test context
     */
    @Test
    public void testUploadMessageFailsForUnauthorizedGateway(final TestContext ctx) {

        // GIVEN a device that is connected via gateway "not-the-created-gateway"
        final TenantObject tenant = TenantObject.from(tenantId, true);
        final String gatewayId = helper.getRandomDeviceId(tenantId);
        final JsonObject deviceData = new JsonObject()
                .put("via", "not-the-created-gateway");

        helper.registry.addDeviceForTenant(tenant, gatewayId, PWD)
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
                    statusCode -> statusCode == HttpURLConnection.HTTP_ACCEPTED);

        }).setHandler(ctx.asyncAssertFailure(t -> {

            // THEN the message gets rejected by the HTTP adapter with a 403
            logger.info("could not publish message for unauthorized gateway [tenant-id: {}, gateway-id: {}]",
                    tenantId, gatewayId);
            ctx.assertEquals(HttpURLConnection.HTTP_FORBIDDEN, ((ServiceInvocationException) t).getErrorCode());
        }));
    }

    /**
     * Verifies that the HTTP adapter returns empty responses when uploading
     * telemetry data or events with a TTD but no command is pending for the device.
     * 
     * @param ctx The test context.
     * @throws InterruptedException if the test fails.
     */
    @Test
    public void testUploadMessagesWithTtdThatDoNotReplyWithCommand(final TestContext ctx) throws InterruptedException {

        final Async setup = ctx.async();
        final TenantObject tenant = TenantObject.from(tenantId, true);
        final MultiMap requestHeaders = MultiMap.caseInsensitiveMultiMap()
                .add(HttpHeaders.CONTENT_TYPE, "text/plain")
                .add(HttpHeaders.AUTHORIZATION, authorization)
                .add(HttpHeaders.ORIGIN, ORIGIN_URI)
                .add(Constants.HEADER_TIME_TIL_DISCONNECT, "1");

        helper.registry.addDeviceForTenant(tenant, deviceId, PWD).setHandler(ctx.asyncAssertSuccess(ok -> setup.complete()));
        setup.await();

        testUploadMessages(ctx, tenantId,
                msg -> {
                    final Integer ttd = MessageHelper.getTimeUntilDisconnect(msg);
                    logger.trace("received telemetry message [ttd: {}]", ttd);
                    ctx.assertNotNull(ttd);
                    final Optional<TimeUntilDisconnectNotification> notificationOpt = TimeUntilDisconnectNotification.fromMessage(msg);
                    ctx.assertTrue(notificationOpt.isPresent());
                    final TimeUntilDisconnectNotification notification = notificationOpt.get();
                    ctx.assertEquals(tenantId, notification.getTenantId());
                    ctx.assertEquals(deviceId, notification.getDeviceId());
                    // do NOT send a command, but let the HTTP adapter's timer expire
                },
                count -> {
                    return httpClient.create(
                            getEndpointUri(),
                            Buffer.buffer("hello " + count),
                            requestHeaders,
                            response -> response.statusCode() == HttpURLConnection.HTTP_ACCEPTED).map(responseHeaders -> {

                                // assert that the response does not contain a command nor a request ID nor a payload
                                ctx.assertNull(responseHeaders.get(Constants.HEADER_COMMAND));
                                ctx.assertNull(responseHeaders.get(Constants.HEADER_COMMAND_REQUEST_ID));
                                ctx.assertEquals(responseHeaders.get(HttpHeaders.CONTENT_LENGTH), "0");
                                return responseHeaders;
                            });
                },
                3);
    }

    /**
     * Verifies that the HTTP adapter delivers a command to a device and accepts the corresponding
     * response from the device.
     * 
     * @param ctx The test context.
     * @throws InterruptedException if the test fails.
     */
    @Test
    public void testUploadMessagesWithTtdThatReplyWithCommand(final TestContext ctx) throws InterruptedException {

        final Async setup = ctx.async();
        final TenantObject tenant = TenantObject.from(tenantId, true);

        final MultiMap requestHeaders = MultiMap.caseInsensitiveMultiMap()
                .add(HttpHeaders.CONTENT_TYPE, "text/plain")
                .add(HttpHeaders.AUTHORIZATION, authorization)
                .add(HttpHeaders.ORIGIN, ORIGIN_URI)
                .add(Constants.HEADER_TIME_TIL_DISCONNECT, "2");

        final MultiMap cmdResponseRequestHeaders = MultiMap.caseInsensitiveMultiMap()
                .add(HttpHeaders.CONTENT_TYPE, "text/plain")
                .add(HttpHeaders.AUTHORIZATION, authorization)
                .add(HttpHeaders.ORIGIN, ORIGIN_URI)
                .add(Constants.HEADER_COMMAND_RESPONSE_STATUS, "200");

        helper.registry.addDeviceForTenant(tenant, deviceId, PWD).setHandler(ctx.asyncAssertSuccess(ok -> setup.complete()));
        setup.await();

        testUploadMessages(ctx, tenantId,
                msg -> {
                    final Integer ttd = MessageHelper.getTimeUntilDisconnect(msg);
                    logger.trace("piggy backed telemetry message received: {}, ttd = {}", msg, ttd);
                    final Optional<TimeUntilDisconnectNotification> notificationOpt = TimeUntilDisconnectNotification.fromMessage(msg);
                    ctx.assertTrue(notificationOpt.isPresent());
                    final TimeUntilDisconnectNotification notification = notificationOpt.get();
                    ctx.assertEquals(tenantId, notification.getTenantId());
                    ctx.assertEquals(deviceId, notification.getDeviceId());
                    // now ready to send a command
                    final JsonObject inputData = new JsonObject().put(COMMAND_JSON_KEY, (int) (Math.random() * 100));
                    helper
                        .sendCommand(notification, COMMAND_TO_SEND, "application/json", inputData.toBuffer())
                        .setHandler(ctx.asyncAssertSuccess(response -> {
                            ctx.assertEquals("text/plain", response.getContentType());
                        }));
                },
                count -> {
                    return httpClient.create(
                            getEndpointUri(),
                            Buffer.buffer("hello " + count),
                            requestHeaders,
                            response -> response.statusCode() == HttpURLConnection.HTTP_OK).map(responseHeaders -> {

                                // assert that the response contains a command
                                ctx.assertEquals(COMMAND_TO_SEND, responseHeaders.get(Constants.HEADER_COMMAND));
                                ctx.assertEquals("application/json", responseHeaders.get(HttpHeaders.CONTENT_TYPE));
                                final String requestId = responseHeaders.get(Constants.HEADER_COMMAND_REQUEST_ID);
                                ctx.assertNotNull(requestId);
                                ctx.assertNotEquals(responseHeaders.get(HttpHeaders.CONTENT_LENGTH), "0");
                                return requestId;

                            }).compose(receivedCommandRequestId -> {

                                // send a response to the command now
                                final String responseUri = getCommandResponseUri(receivedCommandRequestId);

                                logger.debug("posting response to command [uri: {}]", responseUri);

                                return httpClient.create(
                                        responseUri,
                                        Buffer.buffer("ok"),
                                        cmdResponseRequestHeaders,
                                        response -> response.statusCode() == HttpURLConnection.HTTP_ACCEPTED);
                            });
                });
    }

    private static String getCommandResponseUri(final String commandRequestId) {
        return String.format(COMMAND_RESPONSE_URI_TEMPLATE, commandRequestId);
    }

    private void assertMessageProperties(final TestContext ctx, final Message msg) {
        ctx.assertNotNull(MessageHelper.getDeviceId(msg));
        ctx.assertNotNull(MessageHelper.getTenantIdAnnotation(msg));
        ctx.assertNotNull(MessageHelper.getDeviceIdAnnotation(msg));
        ctx.assertNull(MessageHelper.getRegistrationAssertion(msg));
        assertAdditionalMessageProperties(ctx, msg);
    }

    /**
     * Performs additional checks on a received message.
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

    private Future<Void> assertHttpResponse(final MultiMap responseHeaders) {

        final Future<Void> result = Future.future();
        final String allowedOrigin = responseHeaders.get(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);
        final boolean hasValidOrigin = allowedOrigin != null
                && (allowedOrigin.equals(ORIGIN_WILDCARD) || allowedOrigin.equals(ORIGIN_URI));


        if (hasValidOrigin) {
            result.complete();
        } else {
            result.fail(new IllegalArgumentException("response contains invalid allowed origin: " + allowedOrigin));
        }
        return result;
    }

    /**
     * Creates an HTTP Basic auth header value for a device.
     * 
     * @param tenant The tenant that the device belongs to.
     * @param deviceId The device identifier.
     * @param password The device's password.
     * @return The header value.
     */
    protected static String getBasicAuth(final String tenant, final String deviceId, final String password) {

        final StringBuilder result = new StringBuilder("Basic ");
        final String username = IntegrationTestSupport.getUsername(deviceId, tenant);
        result.append(Base64.getEncoder().encodeToString(new StringBuilder(username).append(":").append(password)
                .toString().getBytes(StandardCharsets.UTF_8)));
        return result.toString();
    }

}
