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

import java.util.Optional;
import java.util.Set;

import org.eclipse.hono.adapter.lora.GatewayInfo;
import org.eclipse.hono.adapter.lora.LoraMetaData;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * A LoRaWAN provider with API for Actility Wireless.
 */
@ApplicationScoped
public class ActilityWirelessProvider extends ActilityBaseProvider {

    private static final String FIELD_ACTILITY_ADR = "ADRbit";

    @Override
    public String getProviderName() {
        return "actilityWireless";
    }

    @Override
    public Set<String> pathPrefixes() {
        return Set.of("/actility", "/actilityWireless");
    }

    @Override
    protected LoraMetaData extractMetaData(final JsonObject rootObject) {

        final LoraMetaData data = new LoraMetaData();

        LoraUtils.getChildObject(rootObject, FIELD_ACTILITY_SPREADING_FACTOR, String.class)
            .ifPresent(s -> data.setSpreadingFactor(Integer.valueOf(s)));
        LoraUtils.getChildObject(rootObject, FIELD_ACTILITY_FPORT, String.class)
            .ifPresent(s -> data.setFunctionPort(Integer.valueOf(s)));
        LoraUtils.getChildObject(rootObject, FIELD_ACTILITY_FRAME_COUNT_UPLINK, String.class)
            .ifPresent(s -> data.setFrameCount(Integer.valueOf(s)));
        LoraUtils.getChildObject(rootObject, FIELD_ACTILITY_ADR, String.class)
            .ifPresent(s -> data.setAdaptiveDataRateEnabled(s.equals("1") ? Boolean.TRUE : Boolean.FALSE));
        LoraUtils.getChildObject(rootObject, FIELD_ACTILITY_CHANNEL, String.class)
            .map(this::getFrequency)
            .ifPresent(data::setFrequency);
        Optional.ofNullable(LoraUtils.newLocationFromString(
                LoraUtils.getChildObject(rootObject, FIELD_ACTILITY_DEV_LONGITUDE, String.class),
                LoraUtils.getChildObject(rootObject, FIELD_ACTILITY_DEV_LATITUDE, String.class),
                LoraUtils.getChildObject(rootObject, FIELD_ACTILITY_DEV_ALTITUDE, String.class)))
                    .ifPresent(data::setLocation);

        LoraUtils.getChildObject(rootObject, FIELD_ACTILITY_LRRS, JsonObject.class)
            .map(lrrs -> lrrs.getValue(FIELD_ACTILITY_LRR))
            .filter(JsonArray.class::isInstance)
            .map(JsonArray.class::cast)
            .ifPresent(lrrList -> {
                final Optional<String> gwId = LoraUtils.getChildObject(rootObject, FIELD_ACTILITY_LRR_ID, String.class);
                lrrList.stream()
                    .filter(JsonObject.class::isInstance)
                    .map(JsonObject.class::cast)
                    .map(this::extractGatewayInfo)
                    .forEach(gateway -> {
                        Optional.ofNullable(gateway.getGatewayId())
                            .ifPresent(s -> gwId.ifPresent(id -> {
                                if (id.equals(s)) {
                                    Optional.ofNullable(LoraUtils.newLocationFromString(
                                            LoraUtils.getChildObject(rootObject, FIELD_ACTILITY_LRR_LONGITUDE, String.class),
                                            LoraUtils.getChildObject(rootObject, FIELD_ACTILITY_LRR_LATITUDE, String.class),
                                            Optional.empty()))
                                        .ifPresent(gateway::setLocation);
                                }
                            }));
                        data.addGatewayInfo(gateway);
                    });
            });
        return data;
    }

    private GatewayInfo extractGatewayInfo(final JsonObject lrr) {
        final GatewayInfo gateway = new GatewayInfo();
        LoraUtils.getChildObject(lrr, FIELD_ACTILITY_LRR_ID, String.class)
            .ifPresent(gateway::setGatewayId);
        LoraUtils.getChildObject(lrr, FIELD_ACTILITY_LRR_RSSI, String.class)
            .ifPresent(s -> gateway.setRssi(Double.valueOf(s).intValue()));
        LoraUtils.getChildObject(lrr, FIELD_ACTILITY_LRR_SNR, String.class)
            .ifPresent(s -> gateway.setSnr(Double.valueOf(s)));
        return gateway;
    }
}
