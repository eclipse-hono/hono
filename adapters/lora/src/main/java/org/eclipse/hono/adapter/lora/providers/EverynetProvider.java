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

import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.eclipse.hono.adapter.lora.GatewayInfo;
import org.eclipse.hono.adapter.lora.LoraMessageType;
import org.eclipse.hono.adapter.lora.LoraMetaData;
import org.eclipse.hono.service.http.HttpUtils;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * A LoRaWAN provider with API for Everynet.
 * <p>
 * This provider supports uplink messages as described in the
 * <a href="https://ns.docs.everynet.io/data-api/uplink-message.html">
 * Everynet API</a>
 */
@ApplicationScoped
public class EverynetProvider extends JsonBasedLoraProvider {

    private static final String FIELD_EVERYNET_ALTITUDE = "alt";
    private static final String FIELD_EVERYNET_BANDWIDTH = "bandwidth";
    private static final String FIELD_EVERYNET_CHANNEL = "channel";
    private static final String FIELD_EVERYNET_CODERATE = "coderate";
    private static final String FIELD_EVERYNET_DEVICE_EUI = "device";
    private static final String FIELD_EVERYNET_FRAME_COUNT = "counter_up";
    private static final String FIELD_EVERYNET_FREQUENCY = "freq";
    private static final String FIELD_EVERYNET_LATITUDE = "lat";
    private static final String FIELD_EVERYNET_LONGITUDE = "lng";
    private static final String FIELD_EVERYNET_PAYLOAD = "payload";
    private static final String FIELD_EVERYNET_PORT = "port";
    private static final String FIELD_EVERYNET_RSSI = "rssi";
    private static final String FIELD_EVERYNET_SNR = "snr";
    private static final String FIELD_EVERYNET_SPREADING_FACTOR = "spreading";
    private static final String FIELD_EVERYNET_TYPE = "type";

    private static final String OBJECT_EVERYNET_GPS = "gps";
    private static final String OBJECT_EVERYNET_HARDWARE = "hardware";
    private static final String OBJECT_EVERYNET_PARAMS = "params";
    private static final String OBJECT_EVERYNET_META = "meta";
    private static final String OBJECT_EVERYNET_MODULATION = "modulation";
    private static final String OBJECT_EVERYNET_RADIO = "radio";

    @Override
    public String getProviderName() {
        return "everynet";
    }

    @Override
    public Set<String> pathPrefixes() {
        return Set.of("/everynet");
    }

    private Optional<JsonObject> getMetaObject(final JsonObject loraMessage) {
        return LoraUtils.getChildObject(loraMessage, OBJECT_EVERYNET_META, JsonObject.class);
    }

    private Optional<JsonObject> getParamsObject(final JsonObject loraMessage) {
        return LoraUtils.getChildObject(loraMessage, OBJECT_EVERYNET_PARAMS, JsonObject.class);
    }

    /**
     * {@inheritDoc}
     *
     * @return Always {@value HttpUtils#CONTENT_TYPE_JSON}.
     */
    @Override
    public String acceptedContentType() {
        return HttpUtils.CONTENT_TYPE_JSON;
    }

    /**
     * {@inheritDoc}
     *
     * @return Always {@link HttpMethod#POST}.
     */
    @Override
    public HttpMethod acceptedHttpMethod() {
        return HttpMethod.POST;
    }

    @Override
    protected byte[] getDevEui(final JsonObject loraMessage) {

        Objects.requireNonNull(loraMessage);

        return getMetaObject(loraMessage)
                .map(meta -> meta.getValue(FIELD_EVERYNET_DEVICE_EUI))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(LoraUtils::convertFromHexToBytes)
                .orElseThrow(() -> new LoraProviderMalformedPayloadException("message does not contain String valued device ID property"));
    }

    @Override
    protected Buffer getPayload(final JsonObject loraMessage) {

        Objects.requireNonNull(loraMessage);
        return getParamsObject(loraMessage)
                .map(params -> params.getValue(FIELD_EVERYNET_PAYLOAD))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(s -> Buffer.buffer(Base64.getDecoder().decode(s)))
                .orElseThrow(() -> new LoraProviderMalformedPayloadException("message does not contain Base64 encoded payload property"));
    }

    @Override
    protected LoraMessageType getMessageType(final JsonObject loraMessage) {
        Objects.requireNonNull(loraMessage);
        return LoraUtils.getChildObject(loraMessage, FIELD_EVERYNET_TYPE, String.class)
                .filter("uplink"::equals)
                .map(s -> LoraMessageType.UPLINK)
                .orElse(LoraMessageType.UNKNOWN);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected LoraMetaData getMetaData(final JsonObject loraMessage) {

        Objects.requireNonNull(loraMessage);

        final LoraMetaData metaData = new LoraMetaData();
        getParamsObject(loraMessage)
            .map(params -> {
                LoraUtils.getChildObject(params, FIELD_EVERYNET_PORT, Integer.class)
                    .ifPresent(metaData::setFunctionPort);
                LoraUtils.getChildObject(params, FIELD_EVERYNET_FRAME_COUNT, Integer.class)
                    .ifPresent(metaData::setFrameCount);
                return params.getValue(OBJECT_EVERYNET_RADIO);
            })
            .filter(JsonObject.class::isInstance)
            .map(JsonObject.class::cast)
            .ifPresent(radio -> {
                LoraUtils.getChildObject(radio, FIELD_EVERYNET_FREQUENCY, Double.class)
                    .ifPresent(metaData::setFrequency);

                LoraUtils.getChildObject(radio, OBJECT_EVERYNET_MODULATION, JsonObject.class)
                    .ifPresent(modulation -> {
                        LoraUtils.getChildObject(modulation, FIELD_EVERYNET_SPREADING_FACTOR, Integer.class)
                            .ifPresent(metaData::setSpreadingFactor);
                        LoraUtils.getChildObject(modulation, FIELD_EVERYNET_BANDWIDTH, Integer.class)
                            .map(v -> v / 1000)
                            .ifPresent(metaData::setBandwidth);
                        LoraUtils.getChildObject(modulation, FIELD_EVERYNET_CODERATE, String.class)
                            .ifPresent(metaData::setCodingRate);
                    });

                LoraUtils.getChildObject(radio, OBJECT_EVERYNET_HARDWARE, JsonObject.class)
                    .ifPresent(hardware -> {
                        final GatewayInfo gwInfo = new GatewayInfo();
                        LoraUtils.getChildObject(hardware, FIELD_EVERYNET_CHANNEL, Integer.class)
                            .ifPresent(gwInfo::setChannel);
                        LoraUtils.getChildObject(hardware, FIELD_EVERYNET_RSSI, Integer.class)
                            .ifPresent(gwInfo::setRssi);
                        LoraUtils.getChildObject(hardware, FIELD_EVERYNET_SNR, Double.class)
                            .ifPresent(gwInfo::setSnr);
                        LoraUtils.getChildObject(hardware, OBJECT_EVERYNET_GPS, JsonObject.class)
                            .map(gps -> LoraUtils.newLocation(
                                    LoraUtils.getChildObject(gps, FIELD_EVERYNET_LONGITUDE, Double.class),
                                    LoraUtils.getChildObject(gps, FIELD_EVERYNET_LATITUDE, Double.class),
                                    LoraUtils.getChildObject(gps, FIELD_EVERYNET_ALTITUDE, Double.class)))
                            .ifPresent(gwInfo::setLocation);
                        metaData.addGatewayInfo(gwInfo);
                    });
            });

        return metaData;
    }
}
