/*******************************************************************************
 * Copyright (c) 2019, 2023 Contributors to the Eclipse Foundation
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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import javax.security.auth.x500.X500Principal;

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
import org.eclipse.californium.core.coap.option.MapBasedOptionRegistry;
import org.eclipse.californium.core.coap.option.StandardOptionRegistry;
import org.eclipse.californium.core.network.CoapEndpoint;
import org.eclipse.californium.core.network.EndpointManager;
import org.eclipse.californium.elements.config.Configuration;
import org.eclipse.californium.scandium.DTLSConnector;
import org.eclipse.californium.scandium.config.DtlsConfig;
import org.eclipse.californium.scandium.config.DtlsConnectorConfig;
import org.eclipse.californium.scandium.dtls.CertificateType;
import org.eclipse.californium.scandium.dtls.pskstore.AdvancedPskStore;
import org.eclipse.californium.scandium.dtls.pskstore.AdvancedSinglePskStore;
import org.eclipse.californium.scandium.dtls.x509.SingleCertificateProvider;
import org.eclipse.californium.scandium.dtls.x509.StaticNewAdvancedCertificateVerifier;
import org.eclipse.hono.adapter.coap.option.TimeOption;
import org.eclipse.hono.application.client.DownstreamMessage;
import org.eclipse.hono.application.client.MessageConsumer;
import org.eclipse.hono.application.client.MessageContext;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.client.util.StatusCodeMapper;
import org.eclipse.hono.config.KeyLoader;
import org.eclipse.hono.service.management.device.Device;
import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.tests.CommandEndpointConfiguration.SubscriberRole;
import org.eclipse.hono.tests.DownstreamMessageAssertions;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.tests.Tenants;
import org.eclipse.hono.util.Adapter;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.QoS;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.junit.jupiter.api.AfterEach;
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
import io.vertx.junit5.Checkpoint;
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
    protected static final String PATH_CA_CERT = "target/certs/default_tenant-cert.pem";
    protected static final String PATH_DEVICE_CERT = "target/certs/device-4711-cert.pem";
    protected static final String PATH_DEVICE_KEY = "target/certs/device-4711-key.pem";

    private static final String COMMAND_TO_SEND = "setDarkness";
    private static final String COMMAND_JSON_KEY = "darkness";

    /**
     * A logger to be shared with subclasses.
     */
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    protected final Vertx vertx = Vertx.vertx();

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
     * Creates the endpoint configuration variants for Command &amp; Control scenarios.
     *
     * @return The configurations.
     */
    static Stream<CoapCommandEndpointConfiguration> commandAndControlVariants() {
        return Stream.of(
                new CoapCommandEndpointConfiguration(SubscriberRole.DEVICE),
                new CoapCommandEndpointConfiguration(SubscriberRole.UNAUTHENTICATED_DEVICE),
                new CoapCommandEndpointConfiguration(SubscriberRole.GATEWAY_FOR_ALL_DEVICES),
                new CoapCommandEndpointConfiguration(SubscriberRole.GATEWAY_FOR_SINGLE_DEVICE)
                );
    }

    /**
     * Sets up the fixture.
     *
     * @param testInfo The test meta data.
     * @param ctx The vert.x test context.
     * @throws UnknownHostException if the CoAP adapter's host name cannot be resolved.
     * @throws IOException if the client endpoint for the coap scheme could not be created.
     */
    @BeforeEach
    public void setUp(final TestInfo testInfo, final VertxTestContext ctx) throws UnknownHostException, IOException {

        logger.info("running {}", testInfo.getDisplayName());
        helper = new IntegrationTestSupport(vertx);
        logger.info("using CoAP adapter [host: {}, coap port: {}, coaps port: {}]",
                IntegrationTestSupport.COAP_HOST,
                IntegrationTestSupport.COAP_PORT,
                IntegrationTestSupport.COAPS_PORT);
        coapAdapterSecureAddress = new InetSocketAddress(
                Inet4Address.getByName(IntegrationTestSupport.COAP_HOST),
                IntegrationTestSupport.COAPS_PORT);

        final var ep = CoapEndpoint.builder()
                .setConfiguration(new Configuration())
                .build();
        try {
            ep.start();
            logger.debug("created client endpoint for coap scheme");
            EndpointManager.getEndpointManager().setDefaultEndpoint(ep);
        } catch (IOException e) {
            logger.error("could not create client endpoint for coap scheme", e);
            throw e;
        }

        tenantId = helper.getRandomTenantId();
        deviceId = helper.getRandomDeviceId(tenantId);
        helper.init().onComplete(ctx.succeedingThenComplete());
    }

    /**
     * Deletes all temporary objects from the Device Registry which
     * have been created during the last test execution.
     *
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
     * @param ctx The Vert.x test context.
     */
    @AfterEach
    public void disconnect(final VertxTestContext ctx) {
        helper.disconnect().onComplete(ctx.succeedingThenComplete());
    }

    /**
     * Creates the client to use for uploading data to the insecure endpoint
     * of the CoAP adapter.
     *
     * @return The client.
     */
    protected CoapClient getCoapClient() {
        final CoapEndpoint.Builder builder = CoapEndpoint.builder();
        builder.setOptionRegistry(new MapBasedOptionRegistry(StandardOptionRegistry.getDefaultOptionRegistry(), TimeOption.DEFINITION));
        return new CoapClient().setEndpoint(builder.build());
    }

    /**
     * Creates the client to use for uploading data to the secure endpoint
     * of the CoAP adapter.
     *
     * @param keyLoader The loader for the device's private key and certificate.
     * @return The client.
     */
    protected CoapClient getCoapsClient(final KeyLoader keyLoader) {
        final Configuration configuration = new Configuration();
        final DtlsConnectorConfig.Builder dtlsConfig = DtlsConnectorConfig.builder(configuration);
        dtlsConfig.set(DtlsConfig.DTLS_USE_SERVER_NAME_INDICATION, true);
        dtlsConfig.setCertificateIdentityProvider(new SingleCertificateProvider(
                keyLoader.getPrivateKey(),
                keyLoader.getCertificateChain(),
                CertificateType.X_509));
        dtlsConfig.setAdvancedCertificateVerifier(StaticNewAdvancedCertificateVerifier.builder()
                .setTrustAllCertificates()
                .build());
        return getCoapsClient(dtlsConfig);
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
        return getCoapsClient(new AdvancedSinglePskStore(
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
    protected CoapClient getCoapsClient(final AdvancedPskStore pskStoreToUse) {
        final Configuration configuration = new Configuration();
        final DtlsConnectorConfig.Builder dtlsConfig = DtlsConnectorConfig.builder(configuration);
        dtlsConfig.setAdvancedPskStore(pskStoreToUse);
        return getCoapsClient(dtlsConfig);
    }

    /**
     * Creates the client to use for uploading data to the secure endpoint
     * of the CoAP adapter.
     *
     * @param dtlsConnectorConfig The configuration of the DTLS connector to use for connecting
     *                            to the adapter.
     * @return The client.
     */
    protected CoapClient getCoapsClient(final DtlsConnectorConfig.Builder dtlsConnectorConfig) {

        // listen on wildcard to support non-localhost docker daemons
        dtlsConnectorConfig.setAddress(new InetSocketAddress(0));
        dtlsConnectorConfig.set(DtlsConfig.DTLS_MAX_RETRANSMISSIONS, 1);
        // Disabled server certificate subject validation as according RFC7252 certificates with wildcards are not allowed.
        // See https://www.rfc-editor.org/rfc/rfc7252.html#section-9.1.3.3
        dtlsConnectorConfig.set(DtlsConfig.DTLS_VERIFY_SERVER_CERTIFICATES_SUBJECT, false);
        final DtlsConnectorConfig dtlsConfig = dtlsConnectorConfig.build();
        final CoapEndpoint.Builder builder = CoapEndpoint.builder();
        builder.setOptionRegistry(new MapBasedOptionRegistry(StandardOptionRegistry.getDefaultOptionRegistry(), TimeOption.DEFINITION));
        builder.setConfiguration(dtlsConfig.getConfiguration());
        builder.setConnector(new DTLSConnector(dtlsConfig));
        return new CoapClient().setEndpoint(builder.build());
    }

    /**
     * Creates a test specific message consumer.
     *
     * @param tenantId        The tenant to create the consumer for.
     * @param messageConsumer The handler to invoke for every message received.
     * @return A future succeeding with the created consumer.
     */
    protected abstract Future<MessageConsumer> createConsumer(
            String tenantId, Handler<DownstreamMessage<? extends MessageContext>> messageConsumer);

    /**
     * Gets the name of the resource that unauthenticated devices
     * or gateways should use for uploading data.
     *
     * @param tenant The tenant.
     * @param deviceId The device ID.
     * @return The resource name.
     */
    protected String getPutResource(final String tenant, final String deviceId) {
        return getPutResource(0, tenant, deviceId);
    }

    /**
     * Gets the name of the resource that unauthenticated devices
     * or gateways should use for uploading data.
     *
     * @param msgNo The sequence number of the message to send. This can for example be used to return alternating
     *              short and long resource names.
     * @param tenant The tenant.
     * @param deviceId The device ID.
     * @return The resource name.
     */
    protected abstract String getPutResource(int msgNo, String tenant, String deviceId);

    /**
     * Gets the name of the resource that authenticated devices
     * should use for uploading data.
     *
     * @return The resource name.
     */
    protected String getPostResource() {
        return getPostResource(0);
    }

    /**
     * Gets the name of the resource that authenticated devices
     * should use for uploading data.
     *
     * @param msgNo The sequence number of the message to send. This can for example be used to return alternating
     *              short and long resource names.
     * @return The resource name.
     */
    protected abstract String getPostResource(int msgNo);

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
                vertx.setTimer(1000, tid -> result.complete());
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

    private void assertResponseContainsOneWayCommand(
            final CoapCommandEndpointConfiguration endpointConfiguration,
            final OptionSet responseOptions,
            final String expectedCommand,
            final String tenantId,
            final String commandTargetDeviceId) {

        assertWithMessage("location-query option contains command name")
                .that(responseOptions.getLocationQuery()).contains(expectedCommand);
        assertThat(responseOptions.getContentFormat()).isEqualTo(MediaTypeRegistry.APPLICATION_JSON);
        assertThat(responseOptions.getLocationPath().size()).isAtLeast(1);
        assertWithMessage("location-path indicates one-way command")
            .that(responseOptions.getLocationPath().get(0))
            .isAnyOf(CommandConstants.COMMAND_ENDPOINT, CommandConstants.COMMAND_ENDPOINT_SHORT);

        if (endpointConfiguration.isSubscribeAsGateway()) {
            assertThat(responseOptions.getLocationPath()).hasSize(3);
            assertWithMessage("location-path option indicates target device")
                .that(responseOptions.getLocationPath().subList(1, 3))
                .containsExactly("", commandTargetDeviceId)
                .inOrder();
        } else {
            assertThat(responseOptions.getLocationPath()).hasSize(1);
        }
    }

    private void assertResponseContainsCommand(
            final CoapCommandEndpointConfiguration endpointConfiguration,
            final OptionSet responseOptions,
            final String expectedCommand,
            final String tenantId,
            final String commandTargetDeviceId) {

        final boolean isTenantRequiredInUri = endpointConfiguration.isSubscribeAsUnauthenticatedDevice();
        final boolean isDeviceRequiredInUri = endpointConfiguration.isSubscribeAsGateway() || isTenantRequiredInUri;

        final int requestIdIdx = isDeviceRequiredInUri ? 3 : 1;
        assertWithMessage("location-query option contains command name")
                .that(responseOptions.getLocationQuery()).contains(expectedCommand);
        assertThat(responseOptions.getContentFormat()).isEqualTo(MediaTypeRegistry.APPLICATION_JSON);
        assertThat(responseOptions.getLocationPath().size()).isAtLeast(1);
        assertWithMessage("location-path indicates request-response command")
            .that(responseOptions.getLocationPath().get(0))
            .isAnyOf(CommandConstants.COMMAND_RESPONSE_ENDPOINT, CommandConstants.COMMAND_RESPONSE_ENDPOINT_SHORT);

        if (isTenantRequiredInUri) {
            assertThat(responseOptions.getLocationPath().size()).isAtLeast(2);
            assertWithMessage("location-path contains target device's tenant ID")
                .that(responseOptions.getLocationPath().get(1))
                .isEqualTo(tenantId);
        }
        if (isDeviceRequiredInUri) {
            assertThat(responseOptions.getLocationPath().size()).isAtLeast(3);
            assertWithMessage("location-path contains target device ID")
                .that(responseOptions.getLocationPath().get(2))
                .isEqualTo(commandTargetDeviceId);
        }
        assertWithMessage("location-path contains request ID")
            .that(responseOptions.getLocationPath().get(requestIdIdx))
            .isNotNull();
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
        .onComplete(setup.succeedingThenComplete());
        ctx.verify(() -> assertThat(setup.awaitCompletion(5, TimeUnit.SECONDS)).isTrue());

        final CoapClient client = getCoapClient();
        testUploadMessages(ctx, tenantId,
                () -> warmUp(client, createCoapRequest(Code.PUT, getPutResource(tenantId, deviceId), 0)),
                count -> {
                    final Promise<CoapResponse> result = Promise.promise();
                    final Request request = createCoapRequest(Code.PUT, getPutResource(count, tenantId, deviceId), count);
                    client.advanced(getHandler(result), request);
                    return result.future();
                });
    }

    /**
     * Verifies that a number of messages uploaded to Hono's CoAP adapter using TLS_ECDSA based authentication can be
     * successfully consumed via the messaging infrastructure.
     *
     * @param ctx The test context.
     * @throws InterruptedException if the test fails.
     */
    @Test
    public void testUploadMessagesUsingClientCertificate(final VertxTestContext ctx) throws InterruptedException {

        final var clientCertLoader = KeyLoader.fromFiles(vertx, PATH_DEVICE_KEY, PATH_DEVICE_CERT);
        final var clientCert = (X509Certificate) clientCertLoader.getCertificateChain()[0];

        final VertxTestContext setup = new VertxTestContext();

        helper.getCertificate(PATH_CA_CERT)
            .compose(caCert -> {
                final var tenant = Tenants.createTenantForTrustAnchor(caCert);
                return helper.registry.addDeviceForTenant(tenantId, tenant, deviceId, clientCert);
            })
            .onComplete(setup.succeedingThenComplete());

        assertThat(setup.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
        if (setup.failed()) {
            ctx.failNow(setup.causeOfFailure());
            return;
        }

        final CoapClient client = getCoapsClient(clientCertLoader);

        testUploadMessages(ctx, tenantId,
                () -> warmUp(client, createCoapsRequest(Code.POST, getPostResource(), 0)),
                count -> {
                    final Promise<CoapResponse> result = Promise.promise();
                    final Request request = createCoapsRequest(Code.POST, getPostResource(count), count);
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
        .onComplete(setup.succeedingThenComplete());
        ctx.verify(() -> assertThat(setup.awaitCompletion(5, TimeUnit.SECONDS)).isTrue());

        final CoapClient client = getCoapsClient(deviceId, tenantId, SECRET);

        testUploadMessages(ctx, tenantId,
                () -> warmUp(client, createCoapsRequest(Code.POST, getPostResource(), 0)),
                count -> {
                    final Promise<CoapResponse> result = Promise.promise();
                    final Request request = createCoapsRequest(Code.POST, getPostResource(count), count);
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
        .onComplete(setup.succeedingThenComplete());
        ctx.verify(() -> assertThat(setup.awaitCompletion(5, TimeUnit.SECONDS)).isTrue());

        final CoapClient gatewayOne = getCoapsClient(gatewayOneId, tenantId, SECRET);
        final CoapClient gatewayTwo = getCoapsClient(gatewayTwoId, tenantId, SECRET);

        testUploadMessages(ctx, tenantId,
                () -> warmUp(gatewayOne, createCoapsRequest(Code.PUT, getPutResource(tenantId, deviceId), 0)),
                count -> {
                    final CoapClient client;
                    final String resource;
                    if (count % 2 == 0) {
                        client = gatewayOne;
                        resource = getPutResource(count, tenantId, deviceId);
                    } else {
                        client = gatewayTwo;
                        resource = getPutResource(count, "", deviceId);
                    }
                    final Promise<CoapResponse> result = Promise.promise();
                    final Request request = createCoapsRequest(Code.PUT, resource, count);
                    client.advanced(getHandler(result), request);
                    return result.future();
                });
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
                .compose(ok -> helper.registry.addPskDeviceForTenant(tenantId, tenant, gatewayId, gateway, SECRET))
                .compose(ok -> {
                    final Promise<CoapResponse> result = Promise.promise();
                    final Request request = createCoapsRequest(Code.PUT, getPutResource(tenantId, edgeDeviceId), 0);

                    final CoapClient client = getCoapsClient(gatewayId, tenantId, SECRET);
                    client.advanced(getHandler(result), request);

                    return result.future();
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
            final Supplier<Future<Void>> warmUp,
            final Function<Integer, Future<CoapResponse>> requestSender) throws InterruptedException {
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
            final Supplier<Future<Void>> warmUp,
            final Consumer<DownstreamMessage<? extends MessageContext>> messageConsumer,
            final Function<Integer, Future<CoapResponse>> requestSender) throws InterruptedException {
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
            final Supplier<Future<Void>> warmUp,
            final Consumer<DownstreamMessage<? extends MessageContext>> messageConsumer,
            final Function<Integer, Future<CoapResponse>> requestSender,
            final int numberOfMessages,
            final QoS expectedQos) throws InterruptedException {

        final CountDownLatch received = new CountDownLatch(numberOfMessages);

        final VertxTestContext setup = new VertxTestContext();
        createConsumer(tenantId, msg -> {
            ctx.verify(() -> {
                logger.trace("received {}", msg);
                DownstreamMessageAssertions.assertTelemetryApiProperties(msg);
                DownstreamMessageAssertions.assertMessageContainsAdapterAndAddress(msg);
                assertThat(msg.getQos()).isEqualTo(getExpectedQoS(expectedQos));
                assertAdditionalMessageProperties(msg);
                if (messageConsumer != null) {
                    messageConsumer.accept(msg);
                }
            });
            received.countDown();
            if (received.getCount() % 20 == 0) {
                logger.info("messages received: {}", numberOfMessages - received.getCount());
            }
        })
        .compose(ok -> Optional.ofNullable(warmUp).map(Supplier::get).orElseGet(Future::succeededFuture))
        .onComplete(setup.succeedingThenComplete());
        ctx.verify(() -> assertThat(setup.awaitCompletion(5, TimeUnit.SECONDS)).isTrue());

        final long start = System.currentTimeMillis();
        final AtomicInteger messageCount = new AtomicInteger(0);

        while (messageCount.get() < numberOfMessages && !ctx.failed()) {

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
                        sending.countDown();
                    });

            if (!sending.await(TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                ctx.failNow("timeout waiting for message %s to be sent".formatted(messageCount.get()));
            } else if (messageCount.get() % 20 == 0) {
                logger.info("messages sent: {}", messageCount.get());
            }
        }
        if (ctx.failed()) {
            return;
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
     * Verifies that the adapter fails to authenticate a device if the device's client certificate's signature cannot be
     * validated using the trust anchor that is registered for the tenant that the device belongs to.
     *
     * @param ctx The vert.x test context.
     * @throws GeneralSecurityException if the tenant's trust anchor cannot be generated
     */
    @Test
    @Timeout(timeUnit = TimeUnit.SECONDS, value = 20)
    public void testUploadFailsForNonMatchingTrustAnchor(final VertxTestContext ctx) throws GeneralSecurityException {

        final var keyLoader = KeyLoader.fromFiles(vertx, PATH_DEVICE_KEY, PATH_DEVICE_CERT);

        // GIVEN a tenant configured with a trust anchor

        final KeyPair keyPair = helper.newEcKeyPair();
        final var clientCert = (X509Certificate) keyLoader.getCertificateChain()[0];
        final Tenant tenant = Tenants.createTenantForTrustAnchor(
                clientCert.getIssuerX500Principal().getName(X500Principal.RFC2253),
                keyPair.getPublic().getEncoded(),
                keyPair.getPublic().getAlgorithm());

        helper.registry.addDeviceForTenant(tenantId, tenant, deviceId, clientCert)
            // WHEN a device tries to upload data and authenticate with a client
            // certificate that has not been signed with the configured trusted CA
            .compose(ok -> {

                final CoapClient client = getCoapsClient(keyLoader);
                final Promise<CoapResponse> result = Promise.promise();
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
            final Promise<CoapResponse> result = Promise.promise();
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
            final Promise<CoapResponse> result = Promise.promise();
            // THEN a FORBIDDEN response code is returned
            client.advanced(getHandler(result, ResponseCode.FORBIDDEN), createCoapsRequest(Code.POST, getPostResource(), 0));
            return result.future();
        })
        .onComplete(ctx.succeedingThenComplete());
    }

    /**
     * Verifies that the adapter forwards an empty message with a custom content format set in the request
     * to downstream consumers.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    @Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
    public void testUploadEmptyMessageWithCustomContentFormatSucceeds(final VertxTestContext ctx) {

        final int customContentType = MediaTypeRegistry.APPLICATION_OCTET_STREAM;

        helper.registry.addPskDeviceForTenant(tenantId, new Tenant(), deviceId, SECRET)
            .compose(response -> createConsumer(tenantId, msg -> {
                    logger.trace("received {}", msg);
                    ctx.verify(() -> {
                        DownstreamMessageAssertions.assertTelemetryApiProperties(msg);
                        DownstreamMessageAssertions.assertMessageContainsAdapterAndAddress(msg);
                        assertThat(msg.getContentType()).isEqualTo(MediaTypeRegistry.toString(customContentType));
                        assertThat(msg.getPayload().length()).isEqualTo(0);
                        assertAdditionalMessageProperties(msg);
                    });
                    ctx.completeNow();
            }))
            .compose(ok -> {

                final CoapClient client = getCoapsClient(deviceId, tenantId, SECRET);
                final Promise<CoapResponse> result = Promise.promise();
                final var request = createCoapsRequest(
                        Code.POST,
                        getMessageType(),
                        IntegrationTestSupport.COAP_HOST,
                        getPostResource(),
                        null);
                request.getOptions().setContentFormat(customContentType);
                client.advanced(getHandler(result, ResponseCode.CHANGED), request);
                return result.future();
            })
            .onFailure(ctx::failNow);

    }

    /**
     * Verifies that the adapter rejects empty messages that have no content format set and
     * that are not marked as empty notifications with a 4.00 response code.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    @Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
    public void testUploadEmptyMessageWithoutContentFormatFails(final VertxTestContext ctx) {

        helper.registry.addPskDeviceForTenant(tenantId, new Tenant(), deviceId, SECRET)
            .compose(response -> createConsumer(tenantId, msg -> {
                    logger.trace("received {}", msg);
                    ctx.failNow("downstream consumer should not have received message");
            }))
            .compose(ok -> {

                final CoapClient client = getCoapsClient(deviceId, tenantId, SECRET);
                final Promise<CoapResponse> result = Promise.promise();
                final var request = createCoapsRequest(
                        Code.POST,
                        getMessageType(),
                        getPostResource(),
                        null);
                request.getOptions().removeContentFormat();
                client.advanced(getHandler(result, ResponseCode.BAD_REQUEST), request);
                return result.future();
            })
            .onComplete(ctx.succeedingThenComplete());

    }

    /**
     * Verifies that the adapter forwards a non-empty message without any content format set in the request
     * to downstream consumers using the {@value MessageHelper#CONTENT_TYPE_OCTET_STREAM} content type.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    @Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
    public void testUploadNonEmptyMessageWithoutContentFormatSucceeds(final VertxTestContext ctx) {

        helper.registry.addPskDeviceForTenant(tenantId, new Tenant(), deviceId, SECRET)
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
            .compose(ok -> {

                final CoapClient client = getCoapsClient(deviceId, tenantId, SECRET);
                final Promise<CoapResponse> result = Promise.promise();
                final var request = createCoapsRequest(Code.POST, getPostResource(), 0);
                request.getOptions().removeContentFormat();
                client.advanced(getHandler(result, ResponseCode.CHANGED), request);
                return result.future();
            })
            .onFailure(ctx::failNow);

    }

    /**
     * Verifies that the adapter rejects non-empty messages that are marked as empty
     * notifications with a 4.00 response code.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    @Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
    public void testUploadNonEmptyMessageMarkedAsEmptyNotificationFails(final VertxTestContext ctx) {

        helper.registry.addPskDeviceForTenant(tenantId, new Tenant(), deviceId, SECRET)
            .compose(response -> createConsumer(tenantId, msg -> {
                    logger.trace("received {}", msg);
                    ctx.failNow("downstream consumer should not have received message");
            }))
            .compose(ok -> {

                final CoapClient client = getCoapsClient(deviceId, tenantId, SECRET);
                final Promise<CoapResponse> result = Promise.promise();
                final var request = createCoapsRequest(Code.POST, getPostResource(), 0);
                request.getOptions().removeContentFormat();
                request.getOptions().addUriQuery("empty");
                client.advanced(getHandler(result, ResponseCode.BAD_REQUEST), request);
                return result.future();
            })
            .onComplete(ctx.succeedingThenComplete());

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
            final Promise<CoapResponse> result = Promise.promise();
            // THEN a NOT_FOUND response code is returned
            client.advanced(getHandler(result, ResponseCode.NOT_FOUND), createCoapsRequest(Code.POST, getPostResource(), 0));
            return result.future();
        })
        .onComplete(ctx.succeedingThenComplete());
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
            final Promise<CoapResponse> result = Promise.promise();
            final CoapClient client = getCoapsClient(gatewayId, tenantId, SECRET);
            // THEN a FORBIDDEN response code is returned
            client.advanced(getHandler(result, ResponseCode.FORBIDDEN), createCoapsRequest(Code.PUT, getPutResource(tenantId, deviceId), 0));
            return result.future();
        })
        .onComplete(ctx.succeedingThenComplete());
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
            final Promise<CoapResponse> result = Promise.promise();
            final CoapClient client = getCoapsClient(gatewayId, tenantId, SECRET);
            // THEN a FORBIDDEN response code is returned
            client.advanced(getHandler(result, ResponseCode.FORBIDDEN),
                    createCoapsRequest(Code.PUT, getPutResource(tenantId, deviceId), 0));
            return result.future();
        })
        .onComplete(ctx.succeedingThenComplete());
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

        if (endpointConfig.isSubscribeAsUnauthenticatedDevice()) {
            helper.registry.addDeviceForTenant(tenantId, tenant, deviceId, SECRET).onComplete(setup.succeedingThenComplete());
        } else {
            helper.registry.addPskDeviceForTenant(tenantId, tenant, deviceId, SECRET).onComplete(setup.succeedingThenComplete());
        }
        ctx.verify(() -> assertThat(setup.awaitCompletion(5, TimeUnit.SECONDS)).isTrue());

        final String commandTargetDeviceId = endpointConfig.isSubscribeAsGateway()
                ? helper.setupGatewayDeviceBlocking(tenantId, deviceId, 5)
                : deviceId;
        final String subscribingDeviceId = endpointConfig.isSubscribeAsGatewayForSingleDevice() ? commandTargetDeviceId
                : deviceId;

        final CoapClient client = endpointConfig.isSubscribeAsUnauthenticatedDevice() ? getCoapClient()
                : getCoapsClient(deviceId, tenantId, SECRET);

        testUploadMessages(ctx, tenantId,
                () -> warmUp(
                        client,
                        endpointConfig.isSubscribeAsUnauthenticatedDevice() ? createCoapRequest(Code.POST, getPostResource(), 0)
                                : createCoapsRequest(Code.POST, getPostResource(), 0)),
                msg -> {

                    msg.getTimeUntilDisconnectNotification()
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
                                    return response;
                                });
                        });
                },
                count -> {
                    final Promise<CoapResponse> result = Promise.promise();
                    final Request request = createCoapsOrCoapRequest(endpointConfig, commandTargetDeviceId, count);
                    request.getOptions().addUriQuery(String.format("%s=%d", Constants.HEADER_TIME_TILL_DISCONNECT, 5));
                    client.advanced(getHandler(result, ResponseCode.CHANGED), request);
                    return result.future()
                            .map(response -> {
                                ctx.verify(() -> {
                                    assertResponseContainsCommand(
                                            endpointConfig,
                                            response.getOptions(),
                                            expectedCommand,
                                            tenantId,
                                            commandTargetDeviceId);
                                });
                                return response.getOptions().getLocationPathString();
                            })
                            .compose(locationPath -> {
                                // send a response to the command now
                                final String responseUri = "/" + locationPath;
                                logger.debug("sending response to command [uri: {}]", responseUri);

                                final Buffer body = Buffer.buffer("ok");
                                final Promise<CoapResponse> commandResponseResult = Promise.promise();
                                final Request commandResponseRequest = createCoapsOrCoapRequest(
                                        endpointConfig,
                                        responseUri,
                                        body.getBytes());
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
                                                    ? StatusCodeMapper.from(tenantId, ((ServiceInvocationException) thr).getErrorCode(), msg, thr)
                                                    : new RuntimeException(msg, thr));
                                        });
                            });
                });
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
        if (endpointConfig.isSubscribeAsUnauthenticatedDevice()) {
            helper.registry.addDeviceForTenant(tenantId, tenant, deviceId, SECRET).onComplete(setup.succeedingThenComplete());
        } else {
            helper.registry.addPskDeviceForTenant(tenantId, tenant, deviceId, SECRET).onComplete(setup.succeedingThenComplete());
        }
        ctx.verify(() -> assertThat(setup.awaitCompletion(5, TimeUnit.SECONDS)).isTrue());

        final CoapClient client = endpointConfig.isSubscribeAsUnauthenticatedDevice() ? getCoapClient()
                : getCoapsClient(deviceId, tenantId, SECRET);

        final String commandTargetDeviceId = endpointConfig.isSubscribeAsGateway()
                ? helper.setupGatewayDeviceBlocking(tenantId, deviceId, 5)
                : deviceId;
        final String subscribingDeviceId = endpointConfig.isSubscribeAsGatewayForSingleDevice() ? commandTargetDeviceId
                : deviceId;

        testUploadMessages(ctx, tenantId,
                () -> warmUp(
                        client,
                        endpointConfig.isSubscribeAsUnauthenticatedDevice() ? createCoapRequest(Code.POST, getPostResource(), 0)
                                : createCoapsRequest(Code.POST, getPostResource(), 0)),
                msg -> {
                    final Integer ttd = msg.getTimeTillDisconnect();
                    logger.debug("north-bound message received {}, ttd: {}", msg, ttd);
                    msg.getTimeUntilDisconnectNotification().ifPresent(notification -> {
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
                                notification.getMillisecondsUntilExpiry() / 2);
                    });
                },
                count -> {
                    final Promise<CoapResponse> result = Promise.promise();
                    final Request request = createCoapsOrCoapRequest(endpointConfig, commandTargetDeviceId, count);
                    request.getOptions().addUriQuery(String.format("%s=%d", Constants.HEADER_TIME_TILL_DISCONNECT, 4));
                    logger.debug("south-bound send {}", request);
                    client.advanced(getHandler(result, ResponseCode.CHANGED), request);
                    return result.future()
                            .map(response -> {
                                ctx.verify(() -> {
                                    assertResponseContainsOneWayCommand(
                                            endpointConfig,
                                            response.getOptions(),
                                            expectedCommand,
                                            tenantId,
                                            commandTargetDeviceId);
                                });
                                return response;
                            });
                });
    }

    /**
     * Ensures that a CoAP response contains a server time option.
     *
     * @param response The CoAP response.
     */
    private void assertResponseContainsServerTime(final CoapResponse response) {
        final var serverTimeOption = response.getOptions().getOtherOption(TimeOption.DEFINITION);
        assertThat(serverTimeOption).isNotNull();
        // Unit tests verify that the time value is correct but due to minute time differences in the
        // containers running integration tests it suffices to verify that the value is non zero.
        assertThat(serverTimeOption.getLongValue()).isGreaterThan(0L);
    }

    /**
     * Verify that the CoAP adapter responds with a time option in the response when request includes
     * a time option.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    @Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
    public void testServerTimeResponseWithRequestOption(final VertxTestContext ctx) {
        final Checkpoint checks = ctx.checkpoint(2);
        helper.registry.addPskDeviceForTenant(tenantId, new Tenant(), deviceId, SECRET)
                .compose(response -> createConsumer(tenantId, msg -> {
                    logger.trace("received {}", msg);
                    ctx.verify(() -> {
                        DownstreamMessageAssertions.assertTelemetryApiProperties(msg);
                        DownstreamMessageAssertions.assertMessageContainsAdapterAndAddress(msg);
                        assertThat(msg.getPayload().length()).isGreaterThan(0);
                        assertAdditionalMessageProperties(msg);
                    });
                    checks.flag();
                }))
                .compose(ok -> {
                    final CoapClient client = getCoapsClient(deviceId, tenantId, SECRET);
                    final Promise<CoapResponse> result = Promise.promise();
                    final var request = createCoapsRequest(Code.POST, getPostResource(), 0);
                    request.getOptions().addOption(new TimeOption(0));
                    client.advanced(getHandler(result, ResponseCode.CHANGED), request);
                    return result.future();
                })
                .onSuccess(res -> {
                    ctx.verify(() -> assertResponseContainsServerTime(res));
                    checks.flag();
                })
                .onFailure(ctx::failNow);
    }

    /**
     * Verify that the CoAP adapter responds with a time option in the response when request includes
     * the {@value TimeOption#QUERY_PARAMETER_NAME} query parameter.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    @Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
    public void testServerTimeResponseWithRequestParameter(final VertxTestContext ctx) {
        final Checkpoint checks = ctx.checkpoint(2);
        helper.registry.addPskDeviceForTenant(tenantId, new Tenant(), deviceId, SECRET)
                .compose(response -> createConsumer(tenantId, msg -> {
                    logger.trace("received {}", msg);
                    ctx.verify(() -> {
                        DownstreamMessageAssertions.assertTelemetryApiProperties(msg);
                        DownstreamMessageAssertions.assertMessageContainsAdapterAndAddress(msg);
                        assertThat(msg.getPayload().length()).isGreaterThan(0);
                        assertAdditionalMessageProperties(msg);
                    });
                    checks.flag();
                }))
                .compose(ok -> {
                    final CoapClient client = getCoapsClient(deviceId, tenantId, SECRET);
                    final Promise<CoapResponse> result = Promise.promise();
                    final var request = createCoapsRequest(Code.POST, getPostResource(), 0);
                    final URI uri = URI.create(request.getURI() + "?hono-time");
                    request.setURI(uri);
                    client.advanced(getHandler(result, ResponseCode.CHANGED), request);
                    return result.future();
                })
                .onSuccess(res -> {
                    ctx.verify(() -> assertResponseContainsServerTime(res));
                    checks.flag();
                })
                .onFailure(ctx::failNow);
    }

    /**
     * Verify that the CoAP adapter response does not include a time option when neither the time option nor the
     * query parameter are present in the request.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    @Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
    public void testNoServerTimeResponse(final VertxTestContext ctx) {

        final Checkpoint checks = ctx.checkpoint(2);
        helper.registry.addPskDeviceForTenant(tenantId, new Tenant(), deviceId, SECRET)
                .compose(response -> createConsumer(tenantId, msg -> {
                    logger.trace("received {}", msg);
                    ctx.verify(() -> {
                        DownstreamMessageAssertions.assertTelemetryApiProperties(msg);
                        DownstreamMessageAssertions.assertMessageContainsAdapterAndAddress(msg);

                        assertThat(msg.getPayload().length()).isGreaterThan(0);
                        assertAdditionalMessageProperties(msg);
                    });
                    //ctx.completeNow();
                    checks.flag();
                }))
                .compose(ok -> {
                    final CoapClient client = getCoapsClient(deviceId, tenantId, SECRET);
                    final Promise<CoapResponse> result = Promise.promise();
                    final var request = createCoapsRequest(Code.POST, getPostResource(), 0);
                    client.advanced(getHandler(result, ResponseCode.CHANGED), request);
                    return result.future();
                })
                .onSuccess(res -> {
                    ctx.verify(() -> {
                        assertThat(res.getOptions().hasOption(TimeOption.DEFINITION)).isFalse();
                    });
                    checks.flag();
                })
                .onFailure(ctx::failNow);
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
    public void testUploadMessagesWithTtdThatReplyWithOneWayCommandInSeparateResponse(
            final CoapCommandEndpointConfiguration endpointConfig,
            final VertxTestContext ctx) throws InterruptedException {

        final Tenant tenant = new Tenant();
        tenant.addAdapterConfig(new Adapter(Constants.PROTOCOL_ADAPTER_TYPE_COAP)
                .setEnabled(true)
                .putExtension("timeoutToAck", 0)
                .putExtension("deviceTriggeredTimeoutToAck", 30000));
        final String expectedCommand = String.format("%s=%s", Constants.HEADER_COMMAND, COMMAND_TO_SEND);

        final VertxTestContext setup = new VertxTestContext();
        if (endpointConfig.isSubscribeAsUnauthenticatedDevice()) {
            helper.registry.addDeviceForTenant(tenantId, tenant, deviceId, SECRET).onComplete(setup.succeedingThenComplete());
        } else {
            helper.registry.addPskDeviceForTenant(tenantId, tenant, deviceId, SECRET).onComplete(setup.succeedingThenComplete());
        }
        ctx.verify(() -> assertThat(setup.awaitCompletion(5, TimeUnit.SECONDS)).isTrue());

        final CoapClient client = endpointConfig.isSubscribeAsUnauthenticatedDevice() ? getCoapClient()
                : getCoapsClient(deviceId, tenantId, SECRET);

        final String commandTargetDeviceId = endpointConfig.isSubscribeAsGateway()
                ? helper.setupGatewayDeviceBlocking(tenantId, deviceId, 5)
                : deviceId;
        final String subscribingDeviceId = endpointConfig.isSubscribeAsGatewayForSingleDevice() ? commandTargetDeviceId
                : deviceId;

        testUploadMessages(ctx, tenantId,
                () -> warmUp(
                        client,
                        endpointConfig.isSubscribeAsUnauthenticatedDevice() ? createCoapRequest(Code.POST, getPostResource(), 0)
                                : createCoapsRequest(Code.POST, getPostResource(), 0)),
                msg -> {
                    final Integer ttd = msg.getTimeTillDisconnect();
                    logger.debug("north-bound message received {}, ttd: {}", msg, ttd);
                    msg.getTimeUntilDisconnectNotification().ifPresent(notification -> {
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
                                notification.getMillisecondsUntilExpiry() / 2);
                    });
                },
                count -> {
                    final Promise<CoapResponse> result = Promise.promise();
                    final Request request = createCoapsOrCoapRequest(endpointConfig, commandTargetDeviceId, count);
                    request.getOptions().addUriQuery(String.format("%s=%d", Constants.HEADER_TIME_TILL_DISCONNECT, 4));
                    logger.debug("south-bound send {}", request);
                    client.advanced(getHandler(result, ResponseCode.CHANGED), request);
                    return result.future()
                            .map(response -> {
                                ctx.verify(() -> {
                                    assertResponseContainsOneWayCommand(
                                            endpointConfig,
                                            response.getOptions(),
                                            expectedCommand,
                                            tenantId,
                                            commandTargetDeviceId);
                                    assertThat(response.advanced().getType()).isEqualTo(request.getType());
                                });
                                return response;
                            });
                });
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
    public void testUploadMessagesWithTtdAndPiggyThatReplyWithOneWayCommandInPiggyBackedResponse(
            final CoapCommandEndpointConfiguration endpointConfig,
            final VertxTestContext ctx) throws InterruptedException {

        final Tenant tenant = new Tenant();
        tenant.addAdapterConfig(new Adapter(Constants.PROTOCOL_ADAPTER_TYPE_COAP)
                .setEnabled(true)
                .putExtension("timeoutToAck", 0)
                .putExtension("deviceTriggeredTimeoutToAck", 30000));
        final String expectedCommand = String.format("%s=%s", Constants.HEADER_COMMAND, COMMAND_TO_SEND);

        final VertxTestContext setup = new VertxTestContext();
        if (endpointConfig.isSubscribeAsUnauthenticatedDevice()) {
            helper.registry.addDeviceForTenant(tenantId, tenant, deviceId, SECRET).onComplete(setup.succeedingThenComplete());
        } else {
            helper.registry.addPskDeviceForTenant(tenantId, tenant, deviceId, SECRET).onComplete(setup.succeedingThenComplete());
        }
        ctx.verify(() -> assertThat(setup.awaitCompletion(5, TimeUnit.SECONDS)).isTrue());

        final CoapClient client = endpointConfig.isSubscribeAsUnauthenticatedDevice() ? getCoapClient()
                : getCoapsClient(deviceId, tenantId, SECRET);

        final String commandTargetDeviceId = endpointConfig.isSubscribeAsGateway()
                ? helper.setupGatewayDeviceBlocking(tenantId, deviceId, 5)
                : deviceId;
        final String subscribingDeviceId = endpointConfig.isSubscribeAsGatewayForSingleDevice() ? commandTargetDeviceId
                : deviceId;

        testUploadMessages(ctx, tenantId,
                () -> warmUp(
                        client,
                        endpointConfig.isSubscribeAsUnauthenticatedDevice() ? createCoapRequest(Code.POST, getPostResource(), 0)
                                : createCoapsRequest(Code.POST, getPostResource(), 0)),
                msg -> {
                    final Integer ttd = msg.getTimeTillDisconnect();
                    logger.debug("north-bound message received {}, ttd: {}", msg, ttd);
                    msg.getTimeUntilDisconnectNotification().ifPresent(notification -> {
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
                                notification.getMillisecondsUntilExpiry() / 2);
                    });
                },
                count -> {
                    final Promise<CoapResponse> result = Promise.promise();
                    final Request request = createCoapsOrCoapRequest(endpointConfig, commandTargetDeviceId, count);
                    request.getOptions().addUriQuery(String.format("%s=%d", Constants.HEADER_TIME_TILL_DISCONNECT, 4));
                    request.getOptions().addUriQuery("piggy");
                    logger.debug("south-bound send {}", request);
                    client.advanced(getHandler(result, ResponseCode.CHANGED), request);
                    return result.future()
                            .map(response -> {
                                ctx.verify(() -> {
                                    assertResponseContainsOneWayCommand(
                                            endpointConfig,
                                            response.getOptions(),
                                            expectedCommand,
                                            tenantId,
                                            commandTargetDeviceId);
                                    final Type expectedResponseType = request.getType() == Type.NON ? Type.NON : Type.ACK;
                                    assertThat(response.advanced().getType()).isEqualTo(expectedResponseType);
                                });
                                return response;
                            });
                });
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

    /**
     * Performs additional checks on the response received in reply to a CoAP request.
     * <p>
     * This default implementation always returns a succeeded future.
     * Subclasses should override this method to implement reasonable checks.
     *
     * @param response The CoAP response.
     * @return A future indicating the outcome of the checks.
     */
    protected Future<Void> assertCoapResponse(final CoapResponse response) {
        return Future.succeededFuture();
    }

    /**
     * Gets a handler for CoAP responses.
     *
     * @param responseHandler The handler to invoke with the outcome of the request. The handler will be invoked with a
     *            succeeded result if the response contains a 2.04 (Changed) code. Otherwise it will be invoked with a
     *            result that is failed with a {@link CoapResultException}.
     * @return The handler.
     */
    protected final CoapHandler getHandler(final Handler<AsyncResult<CoapResponse>> responseHandler) {
        return getHandler(responseHandler, ResponseCode.CHANGED);
    }

    /**
     * Gets a handler for CoAP responses.
     *
     * @param responseHandler The handler to invoke with the outcome of the request. The handler will be invoked with a
     *            succeeded result if the response contains the expected code. Otherwise it will be invoked with a
     *            result that is failed with a {@link CoapResultException}.
     * @param expectedStatusCode The status code that is expected in the response.
     * @return The handler.
     */
    protected final CoapHandler getHandler(
            final Handler<AsyncResult<CoapResponse>> responseHandler,
            final ResponseCode expectedStatusCode) {

        return new CoapHandler() {

            @Override
            public void onLoad(final CoapResponse response) {
                if (response.getCode() == expectedStatusCode) {
                    logger.debug("=> received {}", Utils.prettyPrint(response));
                    responseHandler.handle(Future.succeededFuture(response));
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

        return getRequestUri("coap", IntegrationTestSupport.COAP_HOST, resource);
    }

    /**
     * Creates a URI for a resource that uses the <em>coaps</em> scheme.
     *
     * @param resource The resource path.
     * @return The URI.
     */
    protected final URI getCoapsRequestUri(final String resource) {
        return getCoapsRequestUri(IntegrationTestSupport.COAP_HOST, resource);
    }

    /**
     * Creates a URI for a resource that uses the <em>coaps</em> scheme.
     *
     * @param hostname The name of the host that the resource resides on.
     * @param resource The resource path.
     * @return The URI.
     */
    protected final URI getCoapsRequestUri(final String hostname, final String resource) {

        return getRequestUri("coaps", hostname, resource);
    }

    private URI getRequestUri(
            final String scheme,
            final String hostname,
            final String resource) {

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
            return new URI(scheme, null, hostname, port, resource, null, null);
        } catch (final URISyntaxException e) {
            // cannot happen
            return null;
        }
    }

    /**
     * Creates a CoAP request using either the <em>coaps</em> or <em>coap</em> scheme,
     * depending on the given endpoint configuration.
     *
     * @param endpointConfig The endpoint configuration.
     * @param requestDeviceId The identifier of the device to publish data for.
     * @param msgNo The message number.
     * @return The request to send.
     */
    protected Request createCoapsOrCoapRequest(
            final CoapCommandEndpointConfiguration endpointConfig,
            final String requestDeviceId,
            final int msgNo) {

        if (endpointConfig.isSubscribeAsGatewayForSingleDevice()) {
            return createCoapsRequest(Code.PUT, getMessageType(), getPutResource(tenantId, requestDeviceId), msgNo);
        } else if (endpointConfig.isSubscribeAsUnauthenticatedDevice()) {
            return createCoapRequest(Code.PUT, getMessageType(), getPutResource(tenantId, requestDeviceId), msgNo);
        } else {
            return createCoapsRequest(Code.POST, getMessageType(), getPostResource(), msgNo);
        }
    }

    /**
     * Creates a CoAP request for an endpoint configuration.
     * <p>
     * The request will use the {@code coap} scheme if the endpoint is configured for
     * an unauthenticated device. Otherwise, the {@code coaps} scheme is used.
     *
     * @param endpointConfig The endpoint configuration.
     * @param resource the resource path.
     * @param payload The payload to send in the request body.
     * @return The request to send.
     */
    protected Request createCoapsOrCoapRequest(
            final CoapCommandEndpointConfiguration endpointConfig,
            final String resource,
            final byte[] payload) {

        if (endpointConfig.isSubscribeAsGateway()) {
            // Gateway uses PUT when acting on behalf of a device
            return createCoapsRequest(Code.PUT, Type.CON, resource, payload);
        } else if (endpointConfig.isSubscribeAsUnauthenticatedDevice()) {
            return createCoapRequest(Code.PUT, Type.CON, resource, payload);
        } else {
            return createCoapsRequest(Code.POST, Type.CON, resource, payload);
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
     * @param payload The payload to send in the request body.
     * @return The request to send.
     */
    protected Request createCoapRequest(
            final Code code,
            final Type type,
            final String resource,
            final byte[] payload) {
        final Request request = new Request(code, type);
        request.setURI(getCoapRequestUri(resource));
        request.setPayload(payload);
        request.getOptions().setContentFormat(MediaTypeRegistry.TEXT_PLAIN);
        return request;
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
        return createCoapsRequest(code, type, IntegrationTestSupport.COAP_HOST, resource, payload);
    }

    /**
     * Creates a CoAP request using the <em>coaps</em> scheme.
     *
     * @param code The CoAP request code.
     * @param type The message type.
     * @param hostname The name of the host to send the request to.
     * @param resource the resource path.
     * @param payload The payload to send in the request body.
     * @return The request to send.
     */
    protected Request createCoapsRequest(
            final Code code,
            final Type type,
            final String hostname,
            final String resource,
            final byte[] payload) {
        final Request request = new Request(code, type);
        request.setURI(getCoapsRequestUri(hostname, resource));
        request.setPayload(payload);
        request.getOptions().setContentFormat(MediaTypeRegistry.TEXT_PLAIN);
        return request;
    }
}
