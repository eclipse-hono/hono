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

package org.eclipse.hono.adapter.lora.providers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;

import java.time.Instant;
import java.util.Base64;

import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.adapter.lora.LoraConstants;
import org.eclipse.hono.adapter.lora.LoraMessageType;
import org.eclipse.hono.client.Command;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.CredentialsObject;
import org.eclipse.hono.util.RegistrationConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.http.Fault;
import com.google.common.base.Charsets;

import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Verifies the behavior of {@link KerlinkProvider}.
 */
@ExtendWith(VertxExtension.class)
public class KerlinkProviderTest {

    private static final Logger LOG = LoggerFactory.getLogger(KerlinkProviderTest.class);

    private static final String KERLINK_APPLICATION_TYPE = "application/vnd.kerlink.iot-v1+json";

    private static final String TEST_KERLINK_API_USER = "kerlinkApiUser";
    private static final String TEST_KERLINK_API_PASSWORD = "kerlinkApiPassword";

    private static final String KERLINK_URL_TOKEN = "/oss/application/login";
    private static final String KERLINK_URL_DOWNLINK = "/oss/application/customers/.*/clusters/.*/endpoints/.*/txMessages";

    private final Vertx vertx = Vertx.vertx();
    private WireMockServer kerlink;
    private KerlinkProvider provider;

    /**
     * Sets up the fixture.
     * 
     * @param testInfo The test meta data.
     */
    @BeforeEach
    public void before(final TestInfo testInfo) {

        LOG.info("running test: {}", testInfo.getDisplayName());
        kerlink = new WireMockServer(WireMockConfiguration.wireMockConfig().bindAddress(Constants.LOOPBACK_DEVICE_ADDRESS).dynamicPort());
        kerlink.start();
        provider = new KerlinkProvider(vertx, new ConcurrentMapCacheManager());
        // Use very small value here to avoid long running unit tests.
        provider.setTokenPreemptiveInvalidationTimeInMs(100);
    }

    /**
     * Stops the Kerling mock server.
     */
    @AfterEach
    public void stopKerlinkMock() {
        if (kerlink != null) {
            kerlink.stop();
        }
    }

    /**
     * Verifies that the extraction of the device id from a message is successful.
     */
    @Test
    public void extractDeviceIdFromLoraMessage() {
        final JsonObject loraMessage = LoraTestUtil.loadTestFile("kerlink.uplink");
        final String deviceId = provider.extractDeviceId(loraMessage);

        assertEquals("myBumluxDevice", deviceId);
    }

    /**
     * Verifies the extraction of a payload from a message is successful.
     */
    @Test
    public void extractPayloadFromLoraMessage() {
        final JsonObject loraMessage = LoraTestUtil.loadTestFile("kerlink.uplink");
        final String payload = provider.extractPayload(loraMessage);

        assertEquals("YnVtbHV4", payload);
    }

    /**
     * Verifies that the extracted message type matches uplink.
     */
    @Test
    public void extractTypeFromLoraUplinkMessage() {
        final JsonObject loraMessage = LoraTestUtil.loadTestFile("kerlink.uplink");
        final LoraMessageType type = provider.extractMessageType(loraMessage);
        assertEquals(LoraMessageType.UPLINK, type);
    }

    /**
     * Verifies that sending a downlink command is successful.
     *
     * @param context The helper to use for running async tests on vertx.
     */
    @Test
    public void sendingDownlinkCommandIsSuccessful(final VertxTestContext context) {

        stubSuccessfulTokenRequest();
        stubSuccessfulDownlinkRequest();

        final JsonObject loraGatewayDevice = getValidGatewayDevice();
        final CredentialsObject gatewayCredential = getValidGatewayCredential();

        final String targetDeviceId = "myTestDevice";
        final Command command = getValidDownlinkCommand();

        provider.sendDownlinkCommand(loraGatewayDevice, gatewayCredential, targetDeviceId, command)
                .setHandler(context.succeeding(ok -> {

                    final JsonObject expectedBody = new JsonObject();
                    expectedBody.put("login", TEST_KERLINK_API_USER);
                    expectedBody.put("password", TEST_KERLINK_API_PASSWORD);

                    context.verify(() -> {
                        kerlink.verify(postRequestedFor(urlEqualTo("/oss/application/login"))
                                .withRequestBody(equalToJson(expectedBody.encode())));
                    });
                    context.completeNow();
                }));
    }

    /**
     * Verifies that sending a downlink fails when LoRa config is missing.
     *
     * @param context The helper to use for running async tests on vertx.
     */
    @Test
    public void sendingDownlinkFailsOnMissingLoraConfig(final VertxTestContext context) {
        final JsonObject loraGatewayDevice = getValidGatewayDevice();
        loraGatewayDevice.getJsonObject(RegistrationConstants.FIELD_DATA).remove(LoraConstants.FIELD_LORA_CONFIG);

        expectValidationFailureForGateway(context, loraGatewayDevice);
    }

    /**
     * Verifies that sending a downlink fails when vendor properties are missing.
     *
     * @param context The helper to use for running async tests on vertx.
     */
    @Test
    public void sendingDownlinkFailsOnMissingVendorProperties(final VertxTestContext context) {
        final JsonObject loraGatewayDevice = getValidGatewayDevice();
        final JsonObject loraConfig = LoraUtils.getLoraConfigFromLoraGatewayDevice(loraGatewayDevice);
        loraConfig.remove(LoraConstants.FIELD_LORA_VENDOR_PROPERTIES);
        expectValidationFailureForGateway(context, loraGatewayDevice);
    }

    /**
     * Verifies that sending a downlink fails when the cluster id is missing.
     *
     * @param context The helper to use for running async tests on vertx.
     */
    @Test
    public void sendingDownlinkFailsOnMissingClusterId(final VertxTestContext context) {
        final JsonObject loraGatewayDevice = getValidGatewayDevice();
        LoraUtils.getLoraConfigFromLoraGatewayDevice(loraGatewayDevice)
                .getJsonObject(LoraConstants.FIELD_LORA_VENDOR_PROPERTIES)
                .remove(KerlinkProvider.FIELD_KERLINK_CLUSTER_ID);

        expectValidationFailureForGateway(context, loraGatewayDevice);
    }

    /**
     * Verifies that sending a downlink fails when the customer id is missing.
     *
     * @param context The helper to use for running async tests on vertx.
     */
    @Test
    public void sendingDownlinkFailsOnMissingCustomerId(final VertxTestContext context) {
        final JsonObject loraGatewayDevice = getValidGatewayDevice();
        LoraUtils.getLoraConfigFromLoraGatewayDevice(loraGatewayDevice)
                .getJsonObject(LoraConstants.FIELD_LORA_VENDOR_PROPERTIES)
                .remove(KerlinkProvider.FIELD_KERLINK_CUSTOMER_ID);

        expectValidationFailureForGateway(context, loraGatewayDevice);
    }

    private void expectValidationFailureForGateway(final VertxTestContext context, final JsonObject loraGatewayDevice) {

        final CredentialsObject gatewayCredential = getValidGatewayCredential();

        final String targetDeviceId = "myTestDevice";
        final Command command = getValidDownlinkCommand();

        provider.sendDownlinkCommand(loraGatewayDevice, gatewayCredential, targetDeviceId, command)
                .setHandler(context.failing(t -> {
                    context.completeNow();
                }));
    }

    /**
     * Verifies that a token is renewed after the token has expired.
     *
     * @param context The helper to use for running async tests on vertx.
     */
    @Test
    public void tokenIsRenewedAfterTokenExpiry(final VertxTestContext context) {

        stubSuccessfulTokenRequest(Instant.now().plusMillis(250));
        stubSuccessfulDownlinkRequest();

        final JsonObject loraGatewayDevice = getValidGatewayDevice();
        final CredentialsObject gatewayCredential = getValidGatewayCredential();

        final String targetDeviceId = "myTestDevice";
        final Command command = getValidDownlinkCommand();

        provider.sendDownlinkCommand(loraGatewayDevice, gatewayCredential, targetDeviceId, command)
            .compose(ok -> {
                final Promise<Void> secondResponse = Promise.promise();
                vertx.setTimer(500, nextRequest -> {
                    provider.sendDownlinkCommand(loraGatewayDevice, gatewayCredential, targetDeviceId, command)
                        .setHandler(secondResponse);
                });
                return secondResponse.future();
            })
            .setHandler(context.succeeding(ok -> {
                context.verify(() -> kerlink.verify(2, postRequestedFor(urlEqualTo("/oss/application/login"))));
                context.completeNow();
            }));
    }

    /**
     * Verifies that a token is reused from cache while it is still valid.
     *
     * @param context The helper to use for running async tests on vertx.
     */
    @Test
    public void tokenIsReusedFromCacheWhileValid(final VertxTestContext context) {

        stubSuccessfulTokenRequest();
        stubSuccessfulDownlinkRequest();

        final JsonObject loraGatewayDevice = getValidGatewayDevice();
        final CredentialsObject gatewayCredential = getValidGatewayCredential();

        final String targetDeviceId = "myTestDevice";
        final Command command = getValidDownlinkCommand();

        provider.sendDownlinkCommand(loraGatewayDevice, gatewayCredential, targetDeviceId, command)
            .compose(ok -> {
                final Promise<Void> secondResponse = Promise.promise();
                vertx.setTimer(250, nextRequest ->
                    provider.sendDownlinkCommand(loraGatewayDevice, gatewayCredential, targetDeviceId, command)
                        .setHandler(secondResponse));
                return secondResponse.future();
            })
            .setHandler(context.succeeding(ok -> {
                context.verify(() -> kerlink.verify(1, postRequestedFor(urlEqualTo("/oss/application/login"))));
                context.completeNow();
            }));
    }

    /**
     * Verifies that a token is invalidated after an unauthorized downlink request.
     *
     * @param context The helper to use for running async tests on vertx.
     */
    @Test
    public void tokenIsInvalidatedOnApiUnauthorized(final VertxTestContext context) {

        stubSuccessfulTokenRequest();
        stubUnauthorizedDownlinkRequest();

        final JsonObject loraGatewayDevice = getValidGatewayDevice();
        final CredentialsObject gatewayCredential = getValidGatewayCredential();

        final String targetDeviceId = "myTestDevice";
        final Command command = getValidDownlinkCommand();

        final Checkpoint firstResponseFailure = context.checkpoint();
        provider.sendDownlinkCommand(loraGatewayDevice, gatewayCredential, targetDeviceId, command)
            .recover(t -> {
                firstResponseFailure.flag();
                return provider.sendDownlinkCommand(loraGatewayDevice, gatewayCredential, targetDeviceId, command);
            })
            .setHandler(context.failing(t -> {
                context.verify(() -> kerlink.verify(2, postRequestedFor(urlEqualTo("/oss/application/login"))));
                context.completeNow();
            }));
    }

    /**
     * Verifies that a request fails on invalid token response.
     *
     * @param context The helper to use for running async tests on vertx.
     */
    @Test
    public void failureOnInvalidTokenResponse(final VertxTestContext context) {

        stubInvalidResponseOnTokenRequest();

        final JsonObject loraGatewayDevice = getValidGatewayDevice();
        final CredentialsObject gatewayCredential = getValidGatewayCredential();

        final String targetDeviceId = "myTestDevice";
        final Command command = getValidDownlinkCommand();

        provider.sendDownlinkCommand(loraGatewayDevice, gatewayCredential, targetDeviceId, command)
                .setHandler(context.failing(t -> context.completeNow()));
    }

    /**
     * Verifies that a request fails on invalid token request.
     *
     * @param context The helper to use for running async tests on vertx.
     */
    @Test
    public void failureOnTokenRequest(final VertxTestContext context) {

        stubFailureOnTokenRequest();

        final JsonObject loraGatewayDevice = getValidGatewayDevice();
        final CredentialsObject gatewayCredential = getValidGatewayCredential();

        final String targetDeviceId = "myTestDevice";
        final Command command = getValidDownlinkCommand();

        provider.sendDownlinkCommand(loraGatewayDevice, gatewayCredential, targetDeviceId, command)
                .setHandler(context.failing(t -> context.completeNow()));
    }

    /**
     * Verifies that a failed downlink request is handled properly.
     *
     * @param context The helper to use for running async tests on vertx.
     */
    @Test
    public void failureOnDownlinkRequest(final VertxTestContext context) {

        stubSuccessfulTokenRequest();
        stubFailureOnDownlinkRequest();

        final JsonObject loraGatewayDevice = getValidGatewayDevice();
        final CredentialsObject gatewayCredential = getValidGatewayCredential();

        final String targetDeviceId = "myTestDevice";
        final Command command = getValidDownlinkCommand();

        provider.sendDownlinkCommand(loraGatewayDevice, gatewayCredential, targetDeviceId, command)
                .setHandler(context.failing(t -> context.completeNow()));
    }

    /**
     * Verifies that a connection fault on a downlink request is handled properly.
     *
     * @param context The helper to use for running async tests on vertx.
     */
    @Test
    public void faultOnDownlinkRequest(final VertxTestContext context) {

        stubSuccessfulTokenRequest();
        stubConnectionFaultOnDownlinkRequest();

        final JsonObject loraGatewayDevice = getValidGatewayDevice();
        final CredentialsObject gatewayCredential = getValidGatewayCredential();

        final String targetDeviceId = "myTestDevice";
        final Command command = getValidDownlinkCommand();

        provider.sendDownlinkCommand(loraGatewayDevice, gatewayCredential, targetDeviceId, command)
                .setHandler(context.failing(t -> context.completeNow()));
    }

    private CredentialsObject getValidGatewayCredential() {
        final JsonObject secret = new JsonObject();
        secret.put("identity", TEST_KERLINK_API_USER);
        secret.put("key", Base64.getEncoder().encodeToString(TEST_KERLINK_API_PASSWORD.getBytes(Charsets.UTF_8)));

        final CredentialsObject gatewayCredential = new CredentialsObject();
        gatewayCredential.setAuthId("lora-secret");
        gatewayCredential.addSecret(secret);

        return gatewayCredential;
    }

    private JsonObject getValidGatewayDevice() {
        final JsonObject loraVendorProperties = new JsonObject();
        loraVendorProperties.put("cluster-id", 23);
        loraVendorProperties.put("customer-id", 4);

        final JsonObject loraNetworkServerData = new JsonObject();
        loraNetworkServerData.put("provider", "kerlink");
        loraNetworkServerData.put("auth-id", "lora-secret");
        loraNetworkServerData.put("url", String.format("http://%s:%d", Constants.LOOPBACK_DEVICE_ADDRESS, kerlink.port()));
        loraNetworkServerData.put("vendor-properties", loraVendorProperties);
        loraNetworkServerData.put("lora-port", 23);

        final JsonObject loraGatewayData = new JsonObject();
        loraGatewayData.put("lora-network-server", loraNetworkServerData);

        final JsonObject loraGatewayDevice = new JsonObject();
        loraGatewayDevice.put("tenant-id", "test-tenant");
        loraGatewayDevice.put("device-id", "bumlux");
        loraGatewayDevice.put("enabled", true);
        loraGatewayDevice.put("data", loraGatewayData);

        return loraGatewayDevice;
    }

    private Command getValidDownlinkCommand() {
        final Message message = Message.Factory.create();
        message.setAddress(String.format("%s/%s/%s",
                CommandConstants.COMMAND_ENDPOINT, "bumlux", "bumlux"));
        message.setSubject("subject");
        message.setCorrelationId("correlation_id");
        message.setReplyTo(CommandConstants.NORTHBOUND_COMMAND_RESPONSE_ENDPOINT + "/bumlux/replyId");

        final JsonObject payload = new JsonObject();
        payload.put(LoraConstants.FIELD_LORA_DOWNLINK_PAYLOAD, "bumlux".getBytes(Charsets.UTF_8));

        message.setBody(new AmqpValue(payload.encode()));

        return Command.from(message, "bumlux", "bumlux");
    }

    private void stubSuccessfulTokenRequest() {
        final Instant tokenExpiryTime = Instant.now().plusSeconds(60);
        stubSuccessfulTokenRequest(tokenExpiryTime);
    }

    private void stubSuccessfulTokenRequest(final Instant tokenExpiryTime) {

        final JsonObject result = new JsonObject();
        result.put("expiredDate", tokenExpiryTime.toEpochMilli());
        result.put("tokenType", "Bearer");
        result.put("token", "ThisIsAveryLongBearerTokenUsedByKerlink");

        kerlink.stubFor(post(urlEqualTo(KERLINK_URL_TOKEN))
                .withHeader("Content-Type", equalTo(KERLINK_APPLICATION_TYPE))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", KERLINK_APPLICATION_TYPE)
                        .withBody(result.encodePrettily())));
    }

    private void stubFailureOnTokenRequest() {

        kerlink.stubFor(post(urlEqualTo(KERLINK_URL_TOKEN))
                .withHeader("Content-Type", equalTo(KERLINK_APPLICATION_TYPE))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", KERLINK_APPLICATION_TYPE)));
    }

    private void stubInvalidResponseOnTokenRequest() {

        kerlink.stubFor(post(urlEqualTo(KERLINK_URL_TOKEN))
                .withHeader("Content-Type", equalTo(KERLINK_APPLICATION_TYPE))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", KERLINK_APPLICATION_TYPE)
                        .withBody("Here should be JSON, but instead it's some text we're for sure not expecting.")));
    }

    private void stubSuccessfulDownlinkRequest() {

        kerlink.stubFor(post(urlPathMatching(KERLINK_URL_DOWNLINK))
                .withHeader("Content-Type", equalTo(KERLINK_APPLICATION_TYPE))
                .willReturn(aResponse()
                        .withStatus(201)));
    }

    private void stubUnauthorizedDownlinkRequest() {

        kerlink.stubFor(post(urlPathMatching(KERLINK_URL_DOWNLINK))
                .withHeader("Content-Type", equalTo(KERLINK_APPLICATION_TYPE))
                .willReturn(aResponse()
                        .withStatus(401)));
    }

    private void stubFailureOnDownlinkRequest() {

        kerlink.stubFor(post(urlPathMatching(KERLINK_URL_DOWNLINK))
                .withHeader("Content-Type", equalTo(KERLINK_APPLICATION_TYPE))
                .willReturn(aResponse()
                        .withStatus(500).withBody("Something went really wrong.")));
    }

    private void stubConnectionFaultOnDownlinkRequest() {

        kerlink.stubFor(post(urlPathMatching(KERLINK_URL_DOWNLINK))
                .withHeader("Content-Type", equalTo(KERLINK_APPLICATION_TYPE))
                .willReturn(aResponse().withFault(Fault.MALFORMED_RESPONSE_CHUNK)));
    }
}
