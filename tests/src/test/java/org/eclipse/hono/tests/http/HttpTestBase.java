/**
 * Copyright (c) 2016, 2018 Bosch Software Innovations GmbH.
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

package org.eclipse.hono.tests.http;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.tests.CrudHttpClient;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.TenantConstants;
import org.eclipse.hono.util.TenantObject;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
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

    /**
     * The CORS <em>origin</em> address to use for sending messages.
     */
    private static final String ORIGIN_URI = "http://hono.eclipse.org";
    private static final String ORIGIN_WILDCARD = "*";
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpTestBase.class);
    private static final Vertx VERTX = Vertx.vertx();
    private static final long  TEST_TIMEOUT = 15000; // ms

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

    private SelfSignedCertificate deviceCert;
    /**
     * A client for connecting to the HTTP adapter.
     */
    private CrudHttpClient httpClient;
    /**
     * A client for connecting to the HTTP adapter using a client certificate
     * for authentication.
     */
    private CrudHttpClient httpClientWithClientCert;

    /**
     * Sets up clients.
     * 
     * @param ctx The vert.x test context.
     */
    @BeforeClass
    public static void init(final TestContext ctx) {

        helper = new IntegrationTestSupport(VERTX);
        helper.init(ctx);

        LOGGER.debug("using HTTP adapter [host: {}, http port: {}, https port: {}]",
                IntegrationTestSupport.HTTP_HOST,
                IntegrationTestSupport.HTTP_PORT,
                IntegrationTestSupport.HTTPS_PORT);

        defaultOptions = new HttpClientOptions()
           .setDefaultHost(IntegrationTestSupport.HTTP_HOST)
           .setDefaultPort(IntegrationTestSupport.HTTPS_PORT)
           .setTrustOptions(new PemTrustOptions().addCertPath(IntegrationTestSupport.TRUST_STORE_PATH))
           .setSsl(true);
    }

    private static Future<Buffer> loadFile(final String path) {

        final Future<Buffer> result = Future.future();
        VERTX.fileSystem().readFile(path, result.completer());
        return result;
    }

    private static Future<X509Certificate> getCertificate(final String path) {

        return loadFile(path).map(buffer -> {
            try (InputStream is = new ByteArrayInputStream(buffer.getBytes())) {
                final CertificateFactory factory = CertificateFactory.getInstance("X.509");
                return (X509Certificate) factory.generateCertificate(is);
            } catch (Exception e) {
                throw new IllegalArgumentException("file cannot be parsed into X.509 certificate");
            }
        });
    }

    /**
     * Sets up the fixture.
     */
    @Before
    public void setUp() {

        deviceCert = SelfSignedCertificate.create(UUID.randomUUID().toString());
        httpClient = new CrudHttpClient(VERTX, new HttpClientOptions(defaultOptions));
        httpClientWithClientCert = new CrudHttpClient(VERTX, new HttpClientOptions(defaultOptions)
                .setKeyCertOptions(deviceCert.keyCertOptions()));
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
        final String tenantId = helper.getRandomTenantId();
        final String deviceId = helper.getRandomDeviceId(tenantId);
        final String password = "secret";
        final TenantObject tenant = TenantObject.from(tenantId, true);
        final MultiMap requestHeaders = MultiMap.caseInsensitiveMultiMap()
                .add(HttpHeaders.CONTENT_TYPE, "binary/octet-stream")
                .add(HttpHeaders.AUTHORIZATION, getBasicAuth(tenantId, deviceId, password))
                .add(HttpHeaders.ORIGIN, ORIGIN_URI);

        helper.registry.addDeviceForTenant(tenant, deviceId, password).setHandler(ctx.asyncAssertSuccess(ok -> setup.complete()));
        setup.await();

        testUploadMessages(ctx, tenantId, count -> {
            return httpClient.create(
                    getEndpointUri(),
                    Buffer.buffer("hello " + count),
                    requestHeaders,
                    statusCode -> statusCode == HttpURLConnection.HTTP_ACCEPTED);
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
        final String tenantId = helper.getRandomTenantId();
        final String deviceId = helper.getRandomDeviceId(tenantId);
        final MultiMap requestHeaders = MultiMap.caseInsensitiveMultiMap()
                .add(HttpHeaders.CONTENT_TYPE, "binary/octet-stream")
                .add(HttpHeaders.ORIGIN, ORIGIN_URI);

        getCertificate(deviceCert.certificatePath()).compose(cert -> {
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
                    statusCode -> statusCode == HttpURLConnection.HTTP_ACCEPTED);
        });
    }

    private void testUploadMessages(
            final TestContext ctx,
            final String tenantId,
            final Function<Integer, Future<MultiMap>> requestSender) throws InterruptedException {

        final int messagesToSend = 60;
        final CountDownLatch received = new CountDownLatch(messagesToSend);
        final Async setup = ctx.async();

        createConsumer(tenantId, msg -> {
            LOGGER.trace("received {}", msg);
            assertMessageProperties(ctx, msg);
            assertAdditionalMessageProperties(ctx, msg);
            received.countDown();
            if (received.getCount() % 20 == 0) {
                LOGGER.info("messages received: {}", messagesToSend - received.getCount());
            }
        }).setHandler(ctx.asyncAssertSuccess(ok -> setup.complete()));

        setup.await();
        final long start = System.currentTimeMillis();
        final AtomicInteger messageCount = new AtomicInteger(0);

        while (messageCount.get() < messagesToSend) {

            final Async sending = ctx.async();
            requestSender.apply(messageCount.getAndIncrement()).compose(this::assertHttpResponse).setHandler(attempt -> {
                if (attempt.succeeded()) {
                    LOGGER.debug("sent message {}", messageCount.get());
                } else {
                    LOGGER.debug("failed to send message {}: {}", messageCount.get(), attempt.cause().getMessage());
                    ctx.fail(attempt.cause());
                }
                sending.complete();
            });

            if (messageCount.get() % 20 == 0) {
                LOGGER.info("messages sent: " + messageCount.get());
            }
            sending.await();
        }

        long timeToWait = Math.max(TEST_TIMEOUT - 1000, Math.round(messagesToSend * 1.2));
        if (!received.await(timeToWait, TimeUnit.MILLISECONDS)) {
            LOGGER.info("sent {} and received {} messages after {} milliseconds",
                    messageCount, messagesToSend - received.getCount(), System.currentTimeMillis() - start);
            ctx.fail("did not receive all messages sent");
        } else {
            LOGGER.info("sent {} and received {} messages after {} milliseconds",
                    messageCount, messagesToSend - received.getCount(), System.currentTimeMillis() - start);
        }
    }

    /**
     * Verifies that the HTTP adapter rejects messages from a device
     * that belongs to a tenant for which the HTTP adapter has been disabled.
     *
     * @param ctx The test context
     */
    @Test
    public void testUploadMessageFailsForDisabledTenant(final TestContext ctx) {

        // GIVEN a tenant for which the HTTP adapter is disabled
        final String tenantId = helper.getRandomTenantId();
        final String deviceId = helper.getRandomDeviceId(tenantId);
        final String password = "secret";
        final MultiMap requestHeaders = MultiMap.caseInsensitiveMultiMap()
                .add(HttpHeaders.CONTENT_TYPE, "binary/octet-stream")
                .add(HttpHeaders.AUTHORIZATION, getBasicAuth(tenantId, deviceId, password));

        final JsonObject adapterDetailsHttp = new JsonObject()
                .put(TenantConstants.FIELD_ADAPTERS_TYPE, Constants.PROTOCOL_ADAPTER_TYPE_HTTP)
                .put(TenantConstants.FIELD_ENABLED, Boolean.FALSE);
        final TenantObject tenant = TenantObject.from(tenantId, true);
        tenant.addAdapterConfiguration(adapterDetailsHttp);

        helper.registry.addDeviceForTenant(tenant, deviceId, password).compose(ok -> {
            // WHEN a device that belongs to the tenant uploads a message
            return httpClient.create(
                    getEndpointUri(),
                    Buffer.buffer("hello"),
                    requestHeaders,
                    statusCode -> statusCode == HttpURLConnection.HTTP_OK);
        }).setHandler(ctx.asyncAssertFailure(t -> {
            // THEN the message gets rejected by the HTTP adapter
            LOGGER.info("could not publish message for disabled tenant [{}]", tenantId);
            ctx.assertEquals(HttpURLConnection.HTTP_FORBIDDEN, ((ServiceInvocationException) t).getErrorCode());
        }));
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

    private Future<Void> assertHttpResponse(final MultiMap responseHeaders) {

        final Future<Void> result = Future.future();
        final String allowedOrigin = responseHeaders.get(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);
        final boolean hasValidOrigin = allowedOrigin != null
                && (allowedOrigin.equals(ORIGIN_WILDCARD) || allowedOrigin.equals(ORIGIN_URI));


        if (!hasValidOrigin) {
            result.fail(new IllegalArgumentException("response contains invalid allowed origin: " + allowedOrigin));
        } else {
            result.complete();;
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
    private static String getBasicAuth(final String tenant, final String deviceId, final String password) {

        final StringBuilder result = new StringBuilder("Basic ");
        final String username = IntegrationTestSupport.getUsername(deviceId, tenant);
        result.append(Base64.getEncoder().encodeToString(new StringBuilder(username).append(":").append(password)
                .toString().getBytes(StandardCharsets.UTF_8)));
        return result.toString();
    }

}
