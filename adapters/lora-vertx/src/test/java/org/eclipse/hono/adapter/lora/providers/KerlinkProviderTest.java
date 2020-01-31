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

import static org.junit.Assert.assertEquals;
import static com.github.tomakehurst.wiremock.client.WireMock.*;

import java.time.Instant;
import java.util.Base64;

import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.adapter.lora.LoraConstants;
import org.eclipse.hono.adapter.lora.LoraMessageType;
import org.eclipse.hono.client.Command;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.CredentialsObject;
import org.eclipse.hono.util.RegistrationConstants;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.common.base.Charsets;

import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

/**
 * Verifies the behavior of {@link KerlinkProvider}.
 */
@RunWith(VertxUnitRunner.class)
public class KerlinkProviderTest {

    private static final String KERLINK_APPLICATION_TYPE = "application/vnd.kerlink.iot-v1+json";

    private static final String TEST_KERLINK_API_USER = "kerlinkApiUser";
    private static final String TEST_KERLINK_API_PASSWORD = "kerlinkApiPassword";

    private static final String KERLINK_URL_TOKEN = "/oss/application/login";
    private static final String KERLINK_URL_DOWNLINK = "/oss/application/customers/.*/clusters/.*/endpoints/.*/txMessages";

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(WireMockConfiguration.wireMockConfig().dynamicPort());

    private KerlinkProvider provider;


    private final Vertx vertx = Vertx.vertx();

    /**
     * Sets up the fixture.
     */
    @Before
    public void before() {
        provider = new KerlinkProvider(vertx, new ConcurrentMapCacheManager());
        // Use very small value here to avoid long running unit tests.
        provider.setTokenPreemptiveInvalidationTimeInMs(100);
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
    public void sendingDownlinkCommandIsSuccessful(final TestContext context) {

        stubSuccessfulTokenRequest();
        stubSuccessfulDownlinkRequest();

        final JsonObject loraGatewayDevice = getValidGatewayDevice();
        final CredentialsObject gatewayCredential = getValidGatewayCredential();

        final String targetDeviceId = "myTestDevice";
        final Command command = getValidDownlinkCommand();

        provider.sendDownlinkCommand(loraGatewayDevice, gatewayCredential, targetDeviceId, command)
                .setHandler(context.asyncAssertSuccess(ok -> {

                    final JsonObject expectedBody = new JsonObject();
                    expectedBody.put("login", TEST_KERLINK_API_USER);
                    expectedBody.put("password", TEST_KERLINK_API_PASSWORD);

                    context.verify(v -> {
                        verify(postRequestedFor(urlEqualTo("/oss/application/login"))
                                .withRequestBody(equalToJson(expectedBody.encode())));
                    });
                }));
    }

    /**
     * Verifies that sending a downlink fails when LoRa config is missing.
     *
     * @param context The helper to use for running async tests on vertx.
     */
    @Test
    public void sendingDownlinkFailsOnMissingLoraConfig(final TestContext context) {
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
    public void sendingDownlinkFailsOnMissingVendorProperties(final TestContext context) {
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
    public void sendingDownlinkFailsOnMissingClusterId(final TestContext context) {
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
    public void sendingDownlinkFailsOnMissingCustomerId(final TestContext context) {
        final JsonObject loraGatewayDevice = getValidGatewayDevice();
        LoraUtils.getLoraConfigFromLoraGatewayDevice(loraGatewayDevice)
                .getJsonObject(LoraConstants.FIELD_LORA_VENDOR_PROPERTIES)
                .remove(KerlinkProvider.FIELD_KERLINK_CUSTOMER_ID);

        expectValidationFailureForGateway(context, loraGatewayDevice);
    }

    private void expectValidationFailureForGateway(final TestContext context, final JsonObject loraGatewayDevice) {
        final Async async = context.async();

        final CredentialsObject gatewayCredential = getValidGatewayCredential();

        final String targetDeviceId = "myTestDevice";
        final Command command = getValidDownlinkCommand();

        provider.sendDownlinkCommand(loraGatewayDevice, gatewayCredential, targetDeviceId, command)
                .setHandler(downlinkResult -> {
                    context.assertTrue(downlinkResult.failed());

                    async.complete();
                });
    }

    /**
     * Verifies that a token is renewed after the token has expired.
     *
     * @param context The helper to use for running async tests on vertx.
     */
    @Test
    public void tokenIsRenewedAfterTokenExpiry(final TestContext context) {

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
            .setHandler(context.asyncAssertSuccess(ok -> {
                context.verify(v -> WireMock.verify(2, postRequestedFor(urlEqualTo("/oss/application/login"))));
            }));
    }

    /**
     * Verifies that a token is reused from cache while it is still valid.
     *
     * @param context The helper to use for running async tests on vertx.
     */
    @Test
    public void tokenIsReusedFromCacheWhileValid(final TestContext context) {

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
            .setHandler(context.asyncAssertSuccess(ok -> {
                context.verify(v -> WireMock.verify(1, postRequestedFor(urlEqualTo("/oss/application/login"))));
            }));
    }

    /**
     * Verifies that a token is invalidated after an unauthorized downlink request.
     *
     * @param context The helper to use for running async tests on vertx.
     */
    @Test
    public void tokenIsInvalidatedOnApiUnauthorized(final TestContext context) {

        stubSuccessfulTokenRequest();
        stubUnauthorizedDownlinkRequest();

        final JsonObject loraGatewayDevice = getValidGatewayDevice();
        final CredentialsObject gatewayCredential = getValidGatewayCredential();

        final String targetDeviceId = "myTestDevice";
        final Command command = getValidDownlinkCommand();

        final Async firstResponseFailure = context.async();
        provider.sendDownlinkCommand(loraGatewayDevice, gatewayCredential, targetDeviceId, command)
            .recover(t -> {
                firstResponseFailure.complete();
                return provider.sendDownlinkCommand(loraGatewayDevice, gatewayCredential, targetDeviceId, command);
            })
            .setHandler(context.asyncAssertFailure(t -> {
                context.verify(v -> WireMock.verify(2, postRequestedFor(urlEqualTo("/oss/application/login"))));
            }));
    }

    /**
     * Verifies that a request fails on invalid token response.
     *
     * @param context The helper to use for running async tests on vertx.
     */
    @Test
    public void failureOnInvalidTokenResponse(final TestContext context) {

        stubInvalidResponseOnTokenRequest();

        final JsonObject loraGatewayDevice = getValidGatewayDevice();
        final CredentialsObject gatewayCredential = getValidGatewayCredential();

        final String targetDeviceId = "myTestDevice";
        final Command command = getValidDownlinkCommand();

        provider.sendDownlinkCommand(loraGatewayDevice, gatewayCredential, targetDeviceId, command)
                .setHandler(context.asyncAssertFailure());
    }

    /**
     * Verifies that a request fails on invalid token request.
     *
     * @param context The helper to use for running async tests on vertx.
     */
    @Test
    public void failureOnTokenRequest(final TestContext context) {

        stubFailureOnTokenRequest();

        final JsonObject loraGatewayDevice = getValidGatewayDevice();
        final CredentialsObject gatewayCredential = getValidGatewayCredential();

        final String targetDeviceId = "myTestDevice";
        final Command command = getValidDownlinkCommand();

        provider.sendDownlinkCommand(loraGatewayDevice, gatewayCredential, targetDeviceId, command)
                .setHandler(context.asyncAssertFailure());
    }

    /**
     * Verifies that a failed downlink request is handled properly.
     *
     * @param context The helper to use for running async tests on vertx.
     */
    @Test
    public void failureOnDownlinkRequest(final TestContext context) {

        stubSuccessfulTokenRequest();
        stubFailureOnDownlinkRequest();

        final JsonObject loraGatewayDevice = getValidGatewayDevice();
        final CredentialsObject gatewayCredential = getValidGatewayCredential();

        final String targetDeviceId = "myTestDevice";
        final Command command = getValidDownlinkCommand();

        provider.sendDownlinkCommand(loraGatewayDevice, gatewayCredential, targetDeviceId, command)
                .setHandler(context.asyncAssertFailure());
    }

    /**
     * Verifies that a connection fault on a downlink request is handled properly.
     *
     * @param context The helper to use for running async tests on vertx.
     */
    @Test
    public void faultOnDownlinkRequest(final TestContext context) {

        stubSuccessfulTokenRequest();
        stubConnectionFaultOnDownlinkRequest();

        final JsonObject loraGatewayDevice = getValidGatewayDevice();
        final CredentialsObject gatewayCredential = getValidGatewayCredential();

        final String targetDeviceId = "myTestDevice";
        final Command command = getValidDownlinkCommand();

        provider.sendDownlinkCommand(loraGatewayDevice, gatewayCredential, targetDeviceId, command)
                .setHandler(context.asyncAssertFailure());
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
        loraNetworkServerData.put("url", "http://localhost:" + wireMockRule.port());
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
        message.setReplyTo(CommandConstants.NORTHBOUND_COMMAND_RESPONSE_ENDPOINT + "/bumlux");

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

        stubFor(post(urlEqualTo(KERLINK_URL_TOKEN))
                .withHeader("Content-Type", equalTo(KERLINK_APPLICATION_TYPE))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", KERLINK_APPLICATION_TYPE)
                        .withBody(result.encodePrettily())));
    }

    private void stubFailureOnTokenRequest() {
        stubFor(post(urlEqualTo(KERLINK_URL_TOKEN))
                .withHeader("Content-Type", equalTo(KERLINK_APPLICATION_TYPE))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", KERLINK_APPLICATION_TYPE)));
    }

    private void stubInvalidResponseOnTokenRequest() {
        stubFor(post(urlEqualTo(KERLINK_URL_TOKEN))
                .withHeader("Content-Type", equalTo(KERLINK_APPLICATION_TYPE))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", KERLINK_APPLICATION_TYPE)
                        .withBody("Here should be JSON, but instead it's some text we're for sure not expecting.")));
    }

    private void stubSuccessfulDownlinkRequest() {
        stubFor(post(urlPathMatching(KERLINK_URL_DOWNLINK))
                .withHeader("Content-Type", equalTo(KERLINK_APPLICATION_TYPE))
                .willReturn(aResponse()
                        .withStatus(201)));
    }

    private void stubUnauthorizedDownlinkRequest() {
        stubFor(post(urlPathMatching(KERLINK_URL_DOWNLINK))
                .withHeader("Content-Type", equalTo(KERLINK_APPLICATION_TYPE))
                .willReturn(aResponse()
                        .withStatus(401)));
    }

    private void stubFailureOnDownlinkRequest() {
        stubFor(post(urlPathMatching(KERLINK_URL_DOWNLINK))
                .withHeader("Content-Type", equalTo(KERLINK_APPLICATION_TYPE))
                .willReturn(aResponse()
                        .withStatus(500).withBody("Something went really wrong.")));
    }

    private void stubConnectionFaultOnDownlinkRequest() {
        stubFor(post(urlPathMatching(KERLINK_URL_DOWNLINK))
                .withHeader("Content-Type", equalTo(KERLINK_APPLICATION_TYPE))
                .willReturn(aResponse().withFault(Fault.MALFORMED_RESPONSE_CHUNK)));
    }
}
