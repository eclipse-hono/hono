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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.hono.adapter.lora.LoraConstants;
import org.eclipse.hono.adapter.lora.LoraMessageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.google.common.io.BaseEncoding;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * A LoRaWAN provider with API for Loriot.
 * <p>
 * This provider only supports <a href="https://docs.loriot.io/display/LNS/Gateway+Information">
 * Gateway Information</a> messages.
 */
@Component
public class LoriotProvider extends BaseLoraProvider {

    private static final Logger LOG = LoggerFactory.getLogger(LoriotProvider.class);
    private static final Pattern PATTERN_DATA_RATE = Pattern.compile("^SF(\\d+) BW(\\d+) (.+)$");
    private static final String FIELD_LORIOT_EUI = "EUI";
    private static final String FIELD_LORIOT_PAYLOAD = "data";

    private static final String FIELD_LORIOT_MESSAGE_TYPE_UPLINK = "gw";
    private static final String FIELD_LORIOT_MESSAGE_TYPE = "cmd";
    private static final String FIELD_LORIOT_DATARATE = "dr";
    private static final String FIELD_LORIOT_FUNCTION_PORT = "port";
    private static final String FIELD_LORIOT_FRAME_COUNT = "fcnt";
    private static final String FIELD_LORIOT_FREQUENCY = "freq";
    private static final String FIELD_LORIOT_GATEWAYS = "gws";
    private static final String FIELD_LORIOT_GATEWAY_EUI = "gweui";
    private static final String FIELD_LORIOT_RSSI = "rssi";
    private static final String FIELD_LORIOT_SNR = "snr";

    @Override
    public String getProviderName() {
        return "loriot";
    }

    @Override
    public String pathPrefix() {
        return "/loriot";
    }

    @Override
    protected String extractDevEui(final JsonObject loraMessage) {

        Objects.requireNonNull(loraMessage);
        return LoraUtils.getChildObject(loraMessage, FIELD_LORIOT_EUI, String.class)
                .orElseThrow(() -> new LoraProviderMalformedPayloadException("message does not contain String valued device ID property"));
    }

    @Override
    protected Buffer extractPayload(final JsonObject loraMessage) {

        Objects.requireNonNull(loraMessage);
        return LoraUtils.getChildObject(loraMessage, FIELD_LORIOT_PAYLOAD, String.class)
                .map(s -> Buffer.buffer(BaseEncoding.base16().decode(s.toUpperCase())))
                .orElseThrow(() -> new LoraProviderMalformedPayloadException("message does not contain String valued payload property"));
    }

    @Override
    protected LoraMessageType extractMessageType(final JsonObject loraMessage) {

        Objects.requireNonNull(loraMessage);
        return LoraUtils.getChildObject(loraMessage, FIELD_LORIOT_MESSAGE_TYPE, String.class)
                .map(s -> FIELD_LORIOT_MESSAGE_TYPE_UPLINK.equals(s) ? LoraMessageType.UPLINK : LoraMessageType.UNKNOWN)
                .orElse(LoraMessageType.UNKNOWN);
    }

    @Override
    protected Map<String, Object> extractNormalizedData(final JsonObject loraMessage) {

        Objects.requireNonNull(loraMessage);

        final Map<String, Object> data = new HashMap<>();

        LoraUtils.addNormalizedValue(
                loraMessage,
                FIELD_LORIOT_FUNCTION_PORT,
                Integer.class,
                LoraConstants.APP_PROPERTY_FUNCTION_PORT,
                v -> v,
                data);
        LoraUtils.addNormalizedValue(
                loraMessage,
                FIELD_LORIOT_FRAME_COUNT,
                Integer.class,
                LoraConstants.FRAME_COUNT,
                v -> v,
                data);
        LoraUtils.addNormalizedValue(
                loraMessage,
                FIELD_LORIOT_FREQUENCY,
                Double.class,
                LoraConstants.FREQUENCY,
                v -> v,
                data);

        LoraUtils.getChildObject(loraMessage, FIELD_LORIOT_DATARATE, String.class)
            .ifPresent(dataRate -> {
                data.put(LoraConstants.DATA_RATE, dataRate);
                final Matcher matcher = PATTERN_DATA_RATE.matcher(dataRate);
                if (matcher.matches()) {
                    data.put(LoraConstants.APP_PROPERTY_SPREADING_FACTOR, Integer.parseInt(matcher.group(1)));
                    data.put(LoraConstants.APP_PROPERTY_BANDWIDTH, Integer.parseInt(matcher.group(2)));
                    data.put(LoraConstants.CODING_RATE, matcher.group(3));
                } else {
                    LOG.debug("invalid data rate [{}]", dataRate);
                }
            });

        LoraUtils.getChildObject(loraMessage, FIELD_LORIOT_GATEWAYS, JsonObject.class)
            .map(gateways -> gateways.getValue(FIELD_LORIOT_GATEWAYS))
            .filter(JsonArray.class::isInstance)
            .map(JsonArray.class::cast)
            .ifPresent(gws -> {
                final JsonArray normalizedGatways = gws.stream()
                        .filter(JsonObject.class::isInstance)
                        .map(JsonObject.class::cast)
                        .map(gw -> {
                            final JsonObject normalizedGatway = new JsonObject();
                            LoraUtils.getChildObject(gw, FIELD_LORIOT_GATEWAY_EUI, String.class)
                                .ifPresent(v -> normalizedGatway.put(LoraConstants.GATEWAY_ID, v));
                            LoraUtils.getChildObject(gw, FIELD_LORIOT_RSSI, Integer.class)
                                .ifPresent(v -> normalizedGatway.put(LoraConstants.APP_PROPERTY_RSS, v));
                            LoraUtils.getChildObject(gw, FIELD_LORIOT_SNR, Double.class)
                                .ifPresent(v -> normalizedGatway.put(LoraConstants.APP_PROPERTY_SNR, v));
                            return normalizedGatway;
                        })
                        .collect(JsonArray::new, JsonArray::add, JsonArray::addAll);
                data.put(LoraConstants.GATEWAYS, normalizedGatways.toString());
            });

        return data;
    }

    @Override
    protected JsonObject extractAdditionalData(final JsonObject loraMessage) {
        return null;
    }
}
