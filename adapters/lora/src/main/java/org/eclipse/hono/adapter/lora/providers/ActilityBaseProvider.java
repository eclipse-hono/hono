/*******************************************************************************
 * Copyright (c) 2020, 2023 Contributors to the Eclipse Foundation
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

import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.adapter.lora.LoraMessageType;
import org.eclipse.hono.adapter.lora.LoraMetaData;

import com.google.common.io.BaseEncoding;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;

/**
 * A base class for the actility {@link LoraProvider}s.
 */
abstract class ActilityBaseProvider extends JsonBasedLoraProvider {
    protected static final String FIELD_ACTILITY_CHANNEL = "Channel";
    protected static final String FIELD_ACTILITY_FPORT = "FPort";
    protected static final String FIELD_ACTILITY_DEV_ALTITUDE = "DevAlt";
    protected static final String FIELD_ACTILITY_DEV_LATITUDE = "DevLAT";
    protected static final String FIELD_ACTILITY_DEV_LONGITUDE = "DevLON";
    protected static final String FIELD_ACTILITY_FRAME_COUNT_UPLINK = "FCntUp";
    protected static final String FIELD_ACTILITY_LRR = "Lrr";
    protected static final String FIELD_ACTILITY_LRR_ID = "Lrrid";
    protected static final String FIELD_ACTILITY_LRR_LATITUDE = "LrrLAT";
    protected static final String FIELD_ACTILITY_LRR_LONGITUDE = "LrrLON";
    protected static final String FIELD_ACTILITY_LRR_RSSI = "LrrRSSI";
    protected static final String FIELD_ACTILITY_LRR_SNR = "LrrSNR";
    protected static final String FIELD_ACTILITY_LRRS = "Lrrs";
    protected static final String FIELD_ACTILITY_SPREADING_FACTOR = "SpFact";
    private static final String FIELD_ACTILITY_DEVICE_EUI = "DevEUI";
    private static final String FIELD_ACTILITY_ROOT_OBJECT = "DevEUI_uplink";
    private static final String FIELD_ACTILITY_PAYLOAD = "payload_hex";

    protected Optional<JsonObject> getRootObject(final JsonObject loraMessage) {
        return LoraUtils.getChildObject(loraMessage, FIELD_ACTILITY_ROOT_OBJECT, JsonObject.class);
    }

    @Override
    protected byte[] getDevEui(final JsonObject loraMessage) {

        Objects.requireNonNull(loraMessage);
        return getRootObject(loraMessage)
                .map(root -> root.getValue(FIELD_ACTILITY_DEVICE_EUI))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(LoraUtils::convertFromHexToBytes)
                .orElseThrow(() -> new LoraProviderMalformedPayloadException("message does not contain String valued device EUI property"));
    }

    @Override
    protected Buffer getPayload(final JsonObject loraMessage) {
        Objects.requireNonNull(loraMessage);
        return getRootObject(loraMessage)
                .map(root -> root.getValue(FIELD_ACTILITY_PAYLOAD))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(s -> Buffer.buffer(BaseEncoding.base16().decode(s.toUpperCase())))
                .orElseThrow(() -> new LoraProviderMalformedPayloadException("message does not contain String valued payload property"));
    }

    @Override
    protected LoraMessageType getMessageType(final JsonObject loraMessage) {
        Objects.requireNonNull(loraMessage);
        return getRootObject(loraMessage)
                .map(root -> LoraMessageType.UPLINK)
                .orElse(LoraMessageType.UNKNOWN);
    }

    /**
     * Gets the frequency corresponding to the channel ID used by Actility/ThingWork
     * as described in section 2.4 of the
     * <a href="https://partners.thingpark.com/sites/default/files/2017-11/AdvancedThingParkDeveloperGuide_V4.pdf">
     * Advanced Developer Guide</a>.
     *
     * @param logicalChannelId The channel ID.
     * @return The frequency in MHz or {@code null} if the identifier is unknown.
     */
    protected Double getFrequency(final String logicalChannelId) {
        switch (logicalChannelId) {
        case "LC1":
            return 868.1;
        case "LC2":
            return 868.3;
        case "LC3":
            return 868.5;
        case "RX2":
            return 869.525;
        default:
            return null;
        }
    }

    @Override
    protected LoraMetaData getMetaData(final JsonObject loraMessage) {

        Objects.requireNonNull(loraMessage);

        return getRootObject(loraMessage)
            .map(this::extractMetaData)
            .orElse(null);
    }

    protected abstract LoraMetaData extractMetaData(JsonObject rootObject);
}
