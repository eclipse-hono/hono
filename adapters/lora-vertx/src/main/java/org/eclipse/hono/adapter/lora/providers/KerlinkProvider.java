/*******************************************************************************
 * Copyright (c) 2019, 2019 Contributors to the Eclipse Foundation
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

import static java.net.HttpURLConnection.HTTP_UNAUTHORIZED;
import static org.eclipse.hono.adapter.lora.LoraConstants.*;
import static org.eclipse.hono.util.Constants.JSON_FIELD_DEVICE_ID;
import static org.eclipse.hono.util.Constants.JSON_FIELD_TENANT_ID;
import static org.eclipse.hono.util.RequestResponseApiConstants.FIELD_PAYLOAD_DEVICE_ID;

import java.text.MessageFormat;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import org.eclipse.hono.adapter.lora.LoraConstants;
import org.eclipse.hono.adapter.lora.impl.LoraProtocolAdapter;
import org.eclipse.hono.cache.ExpiringValueCache;
import org.eclipse.hono.client.Command;
import org.eclipse.hono.service.cache.SpringBasedExpiringValueCache;
import org.eclipse.hono.util.CredentialsObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

/**
 * A LoRaWAN provider with API for Kerlink.
 */
@Component
public class KerlinkProvider implements LoraProvider {

    static final String FIELD_KERLINK_CLUSTER_ID = "cluster-id";
    static final String FIELD_KERLINK_CUSTOMER_ID = "customer-id";

    private static final Logger LOG = LoggerFactory.getLogger(LoraProtocolAdapter.class);

    private static final String HEADER_CONTENT_TYPE_KERLINK_JSON = "application/vnd.kerlink.iot-v1+json";
    private static final String HEADER_BEARER_TOKEN = "Bearer";

    // Cached tokens will be invalidated already earlier than required to avoid edge cases.
    private static final int DEFAULT_DOWNLINK_TOKEN_PREEMPTIVE_INVALIDATION_TIME_IN_MS = 30_000;

    private static final String API_PATH_GET_TOKEN = "/oss/application/login";
    private static final String API_PATH_TX_MESSAGE = "/oss/application/customers/{0}/clusters/{1}/endpoints/{2}/txMessages";

    private static final String FIELD_UPLINK_DEVICE_EUI = "devEui";
    private static final String FIELD_UPLINK_USER_DATA = "userdata";
    private static final String FIELD_UPLINK_PAYLOAD = "payload";

    private static final String FIELD_DOWNLINK_PORT = "port";
    private static final String FIELD_DOWNLINK_PAYLOAD = "payload";
    private static final String FIELD_DOWNLINK_CONTENT_TYPE = "contentType";
    private static final String FIELD_DOWNLINK_ACK = "ack";

    private static final String VALUE_DOWNLINK_CONTENT_TYPE_HEXA = "HEXA";

    private static final String FIELD_KERLINK_AUTH_LOGIN = "login";

    private static final String FIELD_KERLINK_AUTH_PASSWORD = "password";

    private static final String FIELD_KERLINK_EXPIRY_DATE = "expiredDate";
    private static final String FIELD_KERLINK_TOKEN = "token";

    private final CacheManager cacheManager;

    private final ExpiringValueCache<String, String> sessionsCache;
    private int tokenPreemptiveInvalidationTimeInMs = DEFAULT_DOWNLINK_TOKEN_PREEMPTIVE_INVALIDATION_TIME_IN_MS;

    private final WebClient webClient;

    /**
     * Creates a Kerlink provider with the given vertx instance and cache manager.
     *
     * @param vertx the vertx instance this provider should run on
     * @param cacheManager the cache manager this provider should use
     */
    @Autowired
    public KerlinkProvider(final Vertx vertx, final CacheManager cacheManager) {
        this.cacheManager = cacheManager;

        sessionsCache = new SpringBasedExpiringValueCache<>(cacheManager.getCache(KerlinkProvider.class.getName()));

        final WebClientOptions options = new WebClientOptions();
        options.setTrustAll(true);

        this.webClient = WebClient.create(vertx, options);
    }

    @Override
    public String getProviderName() {
        return "kerlink";
    }

    @Override
    public String pathPrefix() {
        return "/kerlink/rxmessage";
    }

    @Override
    public String acceptedContentType() {
        return HEADER_CONTENT_TYPE_KERLINK_JSON;
    }

    @Override
    public String extractDeviceId(final JsonObject loraMessage) {
        return loraMessage.getString(FIELD_UPLINK_DEVICE_EUI);
    }

    @Override
    public String extractPayload(final JsonObject loraMessage) {
        return loraMessage.getJsonObject(FIELD_UPLINK_USER_DATA, new JsonObject()).getString(FIELD_UPLINK_PAYLOAD);
    }

    @Override
    public Future<Void> sendDownlinkCommand(final JsonObject gatewayDevice, final CredentialsObject gatewayCredential,
            final String targetDeviceId, final Command loraCommand) {
        LOG.info("Send downlink command for device '{}' using gateway '{}'", targetDeviceId,
                gatewayDevice.getString(FIELD_PAYLOAD_DEVICE_ID));

        if (!isValidDownlinkKerlinkGateway(gatewayDevice)) {
            LOG.info(
                    "Can't send downlink command for device '{}' using gateway '{}' because of invalid gateway configuration.",
                    targetDeviceId, gatewayDevice.getString(FIELD_PAYLOAD_DEVICE_ID));
            return Future.failedFuture(new LoraProviderDownlinkException("LoRa configuration is not valid."));
        }

        final Future<String> apiToken = getApiTokenFromCacheOrIssueNewFromLoraProvider(gatewayDevice,
                gatewayCredential);

        return apiToken.compose(token -> {
            LOG.info("Sending downlink command via rest api for device '{}' using gateway '{}' and resolved token",
                    targetDeviceId, gatewayDevice.getString(FIELD_PAYLOAD_DEVICE_ID));

            final JsonObject loraPayload = loraCommand.getPayload().toJsonObject();

            final String payloadBase64 = loraPayload.getString(LoraConstants.FIELD_LORA_DOWNLINK_PAYLOAD);

            final String payloadHex = LoraUtils.convertFromBase64ToHex(payloadBase64);
            return sendDownlinkViaRest(token, gatewayDevice, targetDeviceId, payloadHex);
        });
    }

    private Future<Void> sendDownlinkViaRest(final String bearerToken, final JsonObject gatewayDevice,
            final String targetDevice, final String payloadHexa) {
        LOG.debug("Invoking downlink rest api for device '{}'", targetDevice);

        final Future<Void> result = Future.future();

        final String targetUri = getDownlinkRequestUri(gatewayDevice, targetDevice);

        final JsonObject loraProperties = LoraUtils.getLoraConfigFromLoraGatewayDevice(gatewayDevice);
        final int port = loraProperties.getInteger(FIELD_LORA_DEVICE_PORT);

        final JsonObject txMessage = new JsonObject();
        txMessage.put(FIELD_DOWNLINK_PORT, port);
        txMessage.put(FIELD_DOWNLINK_PAYLOAD, payloadHexa);
        txMessage.put(FIELD_DOWNLINK_CONTENT_TYPE, VALUE_DOWNLINK_CONTENT_TYPE_HEXA);
        txMessage.put(FIELD_DOWNLINK_ACK, false);

        webClient.postAbs(targetUri).putHeader("content-type", HEADER_CONTENT_TYPE_KERLINK_JSON)
                .putHeader("Authorization", HEADER_BEARER_TOKEN + " " + bearerToken)
                .sendJsonObject(txMessage, response -> {
                    if (response.succeeded() && LoraUtils.isHttpSuccessStatusCode(response.result().statusCode())) {
                        LOG.debug("downlink rest api call for device '{}' was successful.", targetDevice);
                        result.complete();
                    } else if (response.succeeded() && response.result().statusCode() == HTTP_UNAUTHORIZED) {
                        LOG.debug(
                                "downlink rest api call for device '{}' failed because it was unauthorized. Response Body: '{}'",
                                targetDevice, response.result().bodyAsString());
                        invalidateCacheForGatewayDevice(gatewayDevice);
                        result.fail(new LoraProviderDownlinkException(
                                "Error invoking downlink provider api. Request was unauthorized."));
                    } else if (response.succeeded()) {
                        LOG.debug(
                                "Downlink rest api call for device '{}' returned unexpected status '{}'. Response Body: '{}'",
                                targetDevice, response.result().statusCode(), response.result().bodyAsString());
                        result.fail(new LoraProviderDownlinkException(
                                "Error invoking downlink provider api. Response Code of provider api was: "
                                        + response.result().statusCode()));
                    } else {
                        LOG.debug("Error invoking downlink rest api for device '{}'", targetDevice, response.cause());
                        result.fail(new LoraProviderDownlinkException("Error invoking downlink provider api.",
                                response.cause()));
                    }
                });

        return result;
    }

    private String getDownlinkRequestUri(final JsonObject gatewayDevice, final String targetDevice) {
        final String hostName = LoraUtils.getNormalizedProviderUrlFromGatewayDevice(gatewayDevice);
        final JsonObject vendorProperties = LoraUtils.getLoraConfigFromLoraGatewayDevice(gatewayDevice)
                .getJsonObject(FIELD_LORA_VENDOR_PROPERTIES);
        final int customerId = vendorProperties.getInteger(FIELD_KERLINK_CUSTOMER_ID);
        final int clusterId = vendorProperties.getInteger(FIELD_KERLINK_CLUSTER_ID);

        final String txUrlPath = MessageFormat.format(API_PATH_TX_MESSAGE, customerId, clusterId, targetDevice);
        final String targetUrl = hostName + txUrlPath;

        LOG.debug("Invoking downlink rest api using url '{}' for device '{}'", targetUrl, targetDevice);

        return targetUrl;
    }

    private Future<String> getApiTokenFromCacheOrIssueNewFromLoraProvider(final JsonObject gatewayDevice,
            final CredentialsObject gatewayCredentials) {
        LOG.debug("A bearer token for gateway device '{}' with auth-id '{}' was requested",
                gatewayDevice.getString(FIELD_PAYLOAD_DEVICE_ID), gatewayCredentials.getAuthId());

        final String bearerToken = getCachedTokenForGatewayDevice(gatewayDevice);

        if (StringUtils.isEmpty(bearerToken)) {
            LOG.debug("No bearer token for gateway device '{}' and auth-id '{}' in cache. Will request a new one",
                    gatewayDevice.getString(FIELD_PAYLOAD_DEVICE_ID), gatewayCredentials.getAuthId());

            return getApiTokenFromLoraProvider(gatewayDevice, gatewayCredentials).compose(apiResponse -> {
                LOG.debug("Got bearer token for gateway device '{}' and auth-id '{}'.",
                        gatewayDevice.getString(FIELD_PAYLOAD_DEVICE_ID), gatewayCredentials.getAuthId());

                final String token = apiResponse.getString(FIELD_KERLINK_TOKEN);
                final Long tokenExpiryString = apiResponse.getLong(FIELD_KERLINK_EXPIRY_DATE);
                final Instant tokenExpiry = Instant.ofEpochMilli(tokenExpiryString)
                        .minusMillis(getTokenPreemptiveInvalidationTimeInMs());

                if (Instant.now().isBefore(tokenExpiry)) {
                    putTokenForGatewayDeviceToCache(gatewayDevice, token, tokenExpiry);
                }

                return Future.succeededFuture(token);
            });
        } else {
            LOG.debug("Bearer token for gateway device '{}' and auth-id '{}' is in cache.",
                    gatewayDevice.getString(FIELD_PAYLOAD_DEVICE_ID), gatewayCredentials.getAuthId());
            return Future.succeededFuture(bearerToken);
        }
    }

    private Future<JsonObject> getApiTokenFromLoraProvider(final JsonObject gatewayDevice,
            final CredentialsObject gatewayCredentials) {
        final List<JsonObject> currentlyValidSecrets = gatewayCredentials.getCandidateSecrets();

        LOG.debug("Got a total of {} valid secrets for gateway device '{}' and auth-id '{}'",
                currentlyValidSecrets.size(), gatewayDevice.getString(FIELD_PAYLOAD_DEVICE_ID),
                gatewayCredentials.getAuthId());

        // For now we didn't implement support for multiple valid secrets at the same time.
        final JsonObject currentSecret = currentlyValidSecrets.get(0);

        return requestApiTokenWithSecret(gatewayDevice, currentSecret);
    }

    private Future<JsonObject> requestApiTokenWithSecret(final JsonObject gatewayDevice, final JsonObject secret) {
        final Future<JsonObject> result = Future.future();

        final String loginUri = LoraUtils.getNormalizedProviderUrlFromGatewayDevice(gatewayDevice) + API_PATH_GET_TOKEN;

        final String passwordBase64 = secret.getString(FIELD_LORA_CREDENTIAL_KEY);
        final String password = new String(Base64.getDecoder().decode(passwordBase64));

        final JsonObject loginRequestPayload = new JsonObject();
        loginRequestPayload.put(FIELD_KERLINK_AUTH_LOGIN, secret.getString(FIELD_LORA_CREDENTIAL_IDENTITY));
        loginRequestPayload.put(FIELD_KERLINK_AUTH_PASSWORD, password);

        LOG.debug("Going to obtain token for gateway device '{}' using url: '{}'",
                gatewayDevice.getString(FIELD_PAYLOAD_DEVICE_ID), loginUri);

        webClient.postAbs(loginUri).putHeader("content-type", HEADER_CONTENT_TYPE_KERLINK_JSON)
                .sendJsonObject(loginRequestPayload, response -> {
                    if (response.succeeded() && validateTokenResponse(response.result())) {
                        result.complete(response.result().bodyAsJsonObject());
                    } else {
                        LOG.debug("Error obtaining token for gateway device '{}' using url: '{}'",
                                gatewayDevice.getString(FIELD_PAYLOAD_DEVICE_ID), loginUri);
                        result.fail(new LoraProviderDownlinkException("Could not get authentication token for provider",
                                response.cause()));
                    }
                });
        return result;
    }

    private String getCachedTokenForGatewayDevice(final JsonObject gatewayDevice) {
        final String cacheId = getCacheIdForGatewayDevice(gatewayDevice);
        return sessionsCache.get(cacheId);
    }

    private void putTokenForGatewayDeviceToCache(final JsonObject gatewayDevice, final String token,
            final Instant expiryDate) {
        final String cacheId = getCacheIdForGatewayDevice(gatewayDevice);
        LOG.debug("Going to put token to cache with id '{}'", cacheId);
        sessionsCache.put(cacheId, token, expiryDate);
    }

    private void invalidateCacheForGatewayDevice(final JsonObject gatewayDevice) {
        final String cacheId = getCacheIdForGatewayDevice(gatewayDevice);
        LOG.debug("Invalidating item in cache with id '{}'", cacheId);
        // Ugly to directly remove it from the underlaying cache, but Hono cache does not implement evict method yet.
        cacheManager.getCache(KerlinkProvider.class.getName()).evict(cacheId);
    }

    private String getCacheIdForGatewayDevice(final JsonObject gatwayDevice) {
        return gatwayDevice.getString(JSON_FIELD_TENANT_ID) + "_" + gatwayDevice.getString(JSON_FIELD_DEVICE_ID) + "_"
                + LoraUtils.getLoraConfigFromLoraGatewayDevice(gatwayDevice).getString(FIELD_AUTH_ID);
    }

    private boolean isValidDownlinkKerlinkGateway(final JsonObject gatewayDevice) {
        final JsonObject loraConfig = LoraUtils.getLoraConfigFromLoraGatewayDevice(gatewayDevice);
        if (loraConfig == null) {
            return false;
        }

        final JsonObject vendorProperties = loraConfig.getJsonObject(FIELD_LORA_VENDOR_PROPERTIES);
        if (vendorProperties == null) {
            return false;
        }

        if (vendorProperties.getInteger(FIELD_KERLINK_CUSTOMER_ID) == null) {
            return false;
        }

        if (vendorProperties.getInteger(FIELD_KERLINK_CLUSTER_ID) == null) {
            return false;
        }

        return true;
    }

    private boolean validateTokenResponse(final HttpResponse<Buffer> response) {
        if (!LoraUtils.isHttpSuccessStatusCode(response.statusCode())) {
            LOG.debug("Received non success status code: '{}' from api.", response.statusCode());
            return false;
        }

        final JsonObject apiResponse;

        try {
            apiResponse = response.bodyAsJsonObject();
        } catch (final DecodeException e) {
            LOG.debug("Received non json object from api with data.", e);
            return false;
        }

        final String token;
        try {
            token = apiResponse.getString(FIELD_KERLINK_TOKEN);
        } catch (final ClassCastException e) {
            LOG.debug("Received token with invalid syntax from api.");
            return false;
        }

        if (StringUtils.isEmpty(token)) {
            LOG.debug("Received token with invalid syntax from api.");
            return false;
        }

        final Long expiryDate;
        try {
            expiryDate = apiResponse.getLong(FIELD_KERLINK_EXPIRY_DATE);
        } catch (final ClassCastException e) {
            LOG.debug("Received expiry date with invalid syntax from api.");
            return false;
        }

        if (expiryDate == null) {
            LOG.debug("Received token without expiryDate from api.");
            return false;
        }

        return true;
    }

    int getTokenPreemptiveInvalidationTimeInMs() {
        return tokenPreemptiveInvalidationTimeInMs;
    }

    void setTokenPreemptiveInvalidationTimeInMs(final int time) {
        this.tokenPreemptiveInvalidationTimeInMs = time;
    }
}
