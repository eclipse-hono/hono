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

package org.eclipse.hono.adapter.lora.providers;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.hono.adapter.lora.GatewayInfo;
import org.eclipse.hono.adapter.lora.LoraMessageType;
import org.eclipse.hono.adapter.lora.LoraMetaData;
import org.eclipse.hono.util.MessageHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.BaseEncoding;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * A LoRaWAN provider with API for Loriot.
 * <p>
 * This provider only supports <a href="https://docs.loriot.io/display/LNS/Gateway+Information">
 * Gateway Information</a> messages.
 */
@ApplicationScoped
public class LoriotProvider extends JsonBasedLoraProvider {

    private static final Logger LOG = LoggerFactory.getLogger(LoriotProvider.class);
    private static final Pattern PATTERN_DATA_RATE = Pattern.compile("^SF(\\d+) BW(\\d+) (.+)$");

    private static final String FIELD_LORIOT_DATARATE = "dr";
    private static final String FIELD_LORIOT_EUI = "EUI";
    private static final String FIELD_LORIOT_FRAME_COUNT = "fcnt";
    private static final String FIELD_LORIOT_FREQUENCY = "freq";
    private static final String FIELD_LORIOT_FUNCTION_PORT = "port";
    private static final String FIELD_LORIOT_GATEWAY_EUI = "gweui";
    private static final String FIELD_LORIOT_LATITUDE = "lat";
    private static final String FIELD_LORIOT_LONGITUDE = "lon";
    private static final String FIELD_LORIOT_MESSAGE_TYPE = "cmd";
    private static final String FIELD_LORIOT_PAYLOAD = "data";
    private static final String FIELD_LORIOT_RSSI = "rssi";
    private static final String FIELD_LORIOT_SNR = "snr";

    private static final String COMMAND_FIELD_LORIOT_CMD = "cmd";
    private static final String COMMAND_FIELD_LORIOT_CONFIRMED = "confirmed";
    private static final String COMMAND_FIELD_LORIOT_DATA = "data";
    private static final String COMMAND_FIELD_LORIOT_EUI = "EUI";
    private static final String COMMAND_FIELD_LORIOT_PORT = "port";
    private static final String COMMAND_VALUE_LORIOT_TX = "tx";

    private static final String OBJECTS_LORIOT_GATEWAYS = "gws";

    private static final String MESSAGE_TYPE_UPLINK = "gw";

    @Override
    public String getProviderName() {
        return "loriot";
    }

    @Override
    public Set<String> pathPrefixes() {
        return Set.of("/loriot");
    }

    @Override
    protected byte[] getDevEui(final JsonObject loraMessage) {

        Objects.requireNonNull(loraMessage);
        return LoraUtils.getChildObject(loraMessage, FIELD_LORIOT_EUI, String.class)
                .map(LoraUtils::convertFromHexToBytes)
                .orElseThrow(() -> new LoraProviderMalformedPayloadException("message does not contain String valued device ID property"));
    }

    @Override
    protected Buffer getPayload(final JsonObject loraMessage) {

        Objects.requireNonNull(loraMessage);
        return LoraUtils.getChildObject(loraMessage, FIELD_LORIOT_PAYLOAD, String.class)
                .map(s -> Buffer.buffer(BaseEncoding.base16().decode(s.toUpperCase())))
                .orElseThrow(() -> new LoraProviderMalformedPayloadException("message does not contain String valued payload property"));
    }

    @Override
    protected LoraMessageType getMessageType(final JsonObject loraMessage) {

        Objects.requireNonNull(loraMessage);
        return LoraUtils.getChildObject(loraMessage, FIELD_LORIOT_MESSAGE_TYPE, String.class)
                .filter(MESSAGE_TYPE_UPLINK::equals)
                .map(s -> LoraMessageType.UPLINK)
                .orElse(LoraMessageType.UNKNOWN);
    }

    @Override
    protected LoraMetaData getMetaData(final JsonObject loraMessage) {

        Objects.requireNonNull(loraMessage);

        final LoraMetaData data = new LoraMetaData();

        LoraUtils.getChildObject(loraMessage, FIELD_LORIOT_FUNCTION_PORT, Integer.class)
            .ifPresent(data::setFunctionPort);
        LoraUtils.getChildObject(loraMessage, FIELD_LORIOT_FRAME_COUNT, Integer.class)
            .ifPresent(data::setFrameCount);
        LoraUtils.getChildObject(loraMessage, FIELD_LORIOT_FREQUENCY, Double.class)
            .map(f -> f / 1_000_000)
            .ifPresent(data::setFrequency);

        LoraUtils.getChildObject(loraMessage, FIELD_LORIOT_DATARATE, String.class)
            .ifPresent(dataRate -> {
                final Matcher matcher = PATTERN_DATA_RATE.matcher(dataRate);
                if (matcher.matches()) {
                    data.setSpreadingFactor(Integer.parseInt(matcher.group(1)));
                    data.setBandwidth(Integer.parseInt(matcher.group(2)));
                    data.setCodingRate(matcher.group(3));
                } else {
                    LOG.debug("invalid data rate [{}]", dataRate);
                }
            });

        LoraUtils.getChildObject(loraMessage, OBJECTS_LORIOT_GATEWAYS, JsonArray.class)
            .ifPresent(gws -> {
                gws.stream()
                    .filter(JsonObject.class::isInstance)
                    .map(JsonObject.class::cast)
                    .forEach(gw -> {
                        final GatewayInfo gwInfo = new GatewayInfo();
                        LoraUtils.getChildObject(gw, FIELD_LORIOT_GATEWAY_EUI, String.class)
                            .ifPresent(gwInfo::setGatewayId);
                        LoraUtils.getChildObject(gw, FIELD_LORIOT_RSSI, Integer.class)
                            .ifPresent(gwInfo::setRssi);
                        LoraUtils.getChildObject(gw, FIELD_LORIOT_SNR, Double.class)
                            .ifPresent(gwInfo::setSnr);
                        Optional.ofNullable(LoraUtils.newLocation(
                                LoraUtils.getChildObject(gw, FIELD_LORIOT_LONGITUDE, Double.class),
                                LoraUtils.getChildObject(gw, FIELD_LORIOT_LATITUDE, Double.class),
                                Optional.empty()))
                            .ifPresent(gwInfo::setLocation);
                        data.addGatewayInfo(gwInfo);
                    });
            });

        return data;
    }

    @Override
    protected JsonObject getCommandPayload(final Buffer payload, final String deviceId, final String subject) {
        final JsonObject json = new JsonObject();
        json.put(COMMAND_FIELD_LORIOT_CMD, COMMAND_VALUE_LORIOT_TX);
        json.put(COMMAND_FIELD_LORIOT_CONFIRMED, false);
        json.put(COMMAND_FIELD_LORIOT_DATA, BaseEncoding.base16().encode(payload.getBytes()));
        json.put(COMMAND_FIELD_LORIOT_EUI, deviceId);
        try {
            json.put(COMMAND_FIELD_LORIOT_PORT, Integer.parseInt(subject));
        } catch (final NumberFormatException ignored) {
            // port is not mandatory
        }
        return json;
    }

    @Override
    public Map<String, String> getDefaultHeaders() {
        return Map.of(
            HttpHeaders.CONTENT_TYPE.toString(), MessageHelper.CONTENT_TYPE_APPLICATION_JSON,
            HttpHeaders.ACCEPT.toString(), MessageHelper.CONTENT_TYPE_TEXT_PLAIN
            );
    }
}
