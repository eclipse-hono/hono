/*******************************************************************************
 * Copyright (c) 2022, 2023 Contributors to the Eclipse Foundation
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

import org.eclipse.hono.adapter.lora.LoraMessageType;
import org.eclipse.hono.adapter.lora.LoraMetaData;
import org.eclipse.hono.util.MessageHelper;

import com.google.common.io.BaseEncoding;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * A LoRaWAN provider with API for LiveObjectsProvider.
 * <p>
 * This provider supports messages as described by the
 * <a href="https://liveobjects.orange-business.com/doc/html/lo_manual_v2.html#_lora_output">Live Objects API</a>.
 *
 */
@ApplicationScoped
public class LiveObjectsProvider extends JsonBasedLoraProvider {

    private static final String FIELD_LIVE_OBJECTS_ALTITUDE = "alt";
    private static final String FIELD_LIVE_OBJECTS_DEV_EUI = "devEUI";
    private static final String FIELD_LIVE_OBJECTS_FUNCTION_PORT = "port";
    private static final String FIELD_LIVE_OBJECTS_FRAME_COUNT = "fcnt";
    private static final String FIELD_LIVE_OBJECTS_FREQUENCY = "frequency";
    private static final String FIELD_LIVE_OBJECTS_LATITUDE = "lat";
    private static final String FIELD_LIVE_OBJECTS_LOCATION = "location";
    private static final String FIELD_LIVE_OBJECTS_LONGITUDE = "lon";
    private static final String FIELD_LIVE_OBJECTS_LORA = "lora";
    private static final String FIELD_LIVE_OBJECTS_MESSAGE_TYPE = "messageType";
    private static final String FIELD_LIVE_OBJECTS_METADATA = "metadata";
    private static final String FIELD_LIVE_OBJECTS_NETWORK = "network";
    private static final String FIELD_LIVE_OBJECTS_PAYLOAD = "payload";
    private static final String FIELD_LIVE_OBJECTS_SPREADING_FACTOR = "sf";
    private static final String FIELD_LIVE_OBJECTS_VALUE = "value";

    private static final String LIVE_OBJECTS_CONFIRMED_DATA_UP = "CONFIRMED_DATA_UP";
    private static final String LIVE_OBJECTS_UNCONFIRMED_DATA_UP = "UNCONFIRMED_DATA_UP";

    @Override
    public String getProviderName() {
        return "liveObjects";
    }

    @Override
    public Set<String> pathPrefixes() {
        return Set.of("/liveObjects");
    }

    @Override
    protected byte[] getDevEui(final JsonObject loraMessage) {
        Objects.requireNonNull(loraMessage);
        return getLoraObject(loraMessage)
                .flatMap(lora -> LoraUtils.getChildObject(lora, FIELD_LIVE_OBJECTS_DEV_EUI, String.class))
                .map(LoraUtils::convertFromHexToBytes)
                .orElseThrow(() -> new LoraProviderMalformedPayloadException("message does not contain device ID property"));
    }

    private Optional<JsonObject> getLoraObject(final JsonObject loraMessage) {
        return LoraUtils.getChildObject(loraMessage, FIELD_LIVE_OBJECTS_METADATA, JsonObject.class)
            .flatMap(metadata -> LoraUtils.getChildObject(metadata, FIELD_LIVE_OBJECTS_NETWORK, JsonObject.class))
            .flatMap(network -> LoraUtils.getChildObject(network, FIELD_LIVE_OBJECTS_LORA, JsonObject.class));
    }

    @Override
    protected Buffer getPayload(final JsonObject loraMessage) {
        Objects.requireNonNull(loraMessage);
        return LoraUtils.getChildObject(loraMessage, FIELD_LIVE_OBJECTS_VALUE, JsonObject.class)
                .flatMap(value -> LoraUtils.getChildObject(value, FIELD_LIVE_OBJECTS_PAYLOAD, String.class))
                .map(s -> Buffer.buffer(BaseEncoding.base16().decode(s.toUpperCase())))
                .orElseThrow(() -> new LoraProviderMalformedPayloadException("message does not contain HEX encoded payload property"));
    }

    @Override
    protected LoraMessageType getMessageType(final JsonObject loraMessage) {
        Objects.requireNonNull(loraMessage);
        return getLoraObject(loraMessage)
            .flatMap(lora -> LoraUtils.getChildObject(lora, FIELD_LIVE_OBJECTS_MESSAGE_TYPE, String.class))
            .map(messageType -> {
                switch (messageType) {
                    case LIVE_OBJECTS_CONFIRMED_DATA_UP:
                    case LIVE_OBJECTS_UNCONFIRMED_DATA_UP:
                        return LoraMessageType.UPLINK;
                    default:
                        return LoraMessageType.UNKNOWN;
                }
            })
            .orElse(LoraMessageType.UNKNOWN);
    }

    @Override
    protected LoraMetaData getMetaData(final JsonObject loraMessage) {

        Objects.requireNonNull(loraMessage);

        final LoraMetaData data = new LoraMetaData();

        final Optional<JsonObject> loraObject = getLoraObject(loraMessage);
        loraObject.ifPresent(lora -> {
            LoraUtils.getChildObject(lora, FIELD_LIVE_OBJECTS_FUNCTION_PORT, Integer.class)
                .ifPresent(data::setFunctionPort);
            LoraUtils.getChildObject(lora, FIELD_LIVE_OBJECTS_FRAME_COUNT, Integer.class)
                .ifPresent(data::setFrameCount);
            LoraUtils.getChildObject(lora, FIELD_LIVE_OBJECTS_FREQUENCY, Double.class)
                .ifPresent(data::setFrequency);
            LoraUtils.getChildObject(lora, FIELD_LIVE_OBJECTS_SPREADING_FACTOR, Integer.class)
                .ifPresent(data::setSpreadingFactor);
            LoraUtils.getChildObject(lora, FIELD_LIVE_OBJECTS_LOCATION, JsonObject.class)
                .ifPresent(location -> {
                    Optional.ofNullable(LoraUtils.newLocation(
                            LoraUtils.getChildObject(location, FIELD_LIVE_OBJECTS_LONGITUDE, Double.class),
                            LoraUtils.getChildObject(location, FIELD_LIVE_OBJECTS_LATITUDE, Double.class),
                            LoraUtils.getChildObject(location, FIELD_LIVE_OBJECTS_ALTITUDE, Double.class)))
                        .ifPresent(data::setLocation);
                });

        });

        return data;
    }

    @Override
    public Map<String, String> getDefaultHeaders() {
        return Map.of(
            HttpHeaders.CONTENT_TYPE.toString(), MessageHelper.CONTENT_TYPE_APPLICATION_JSON,
            HttpHeaders.ACCEPT.toString(), MessageHelper.CONTENT_TYPE_APPLICATION_JSON
        );
    }
}
