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

import java.util.Optional;
import java.util.Set;

import org.eclipse.hono.adapter.lora.GatewayInfo;
import org.eclipse.hono.adapter.lora.LoraMetaData;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * A LoRaWAN provider with API for Actility Enterprise.
 */
@ApplicationScoped
public class ActilityEnterpriseProvider extends ActilityBaseProvider {

    @Override
    public String getProviderName() {
        return "actilityEnterprise";
    }

    @Override
    public Set<String> pathPrefixes() {
        return Set.of("/actilityEnterprise");
    }

    @Override
    protected LoraMetaData extractMetaData(final JsonObject rootObject) {

        final LoraMetaData data = new LoraMetaData();

        LoraUtils.getChildObject(rootObject, FIELD_ACTILITY_SPREADING_FACTOR, Integer.class)
            .ifPresent(i -> data.setSpreadingFactor(i));
        LoraUtils.getChildObject(rootObject, FIELD_ACTILITY_FPORT, Integer.class)
            .ifPresent(i -> data.setFunctionPort(i));
        LoraUtils.getChildObject(rootObject, FIELD_ACTILITY_FRAME_COUNT_UPLINK, Integer.class)
            .ifPresent(i -> data.setFrameCount(i));
        LoraUtils.getChildObject(rootObject, FIELD_ACTILITY_CHANNEL, String.class)
            .map(this::getFrequency)
            .ifPresent(data::setFrequency);
        Optional.ofNullable(LoraUtils.newLocation(
                LoraUtils.getChildObject(rootObject, FIELD_ACTILITY_DEV_LONGITUDE, Double.class),
                LoraUtils.getChildObject(rootObject, FIELD_ACTILITY_DEV_LATITUDE, Double.class),
                LoraUtils.getChildObject(rootObject, FIELD_ACTILITY_DEV_ALTITUDE, Double.class)))
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
                                    Optional.ofNullable(LoraUtils.newLocation(
                                            LoraUtils.getChildObject(rootObject, FIELD_ACTILITY_LRR_LONGITUDE, Double.class),
                                            LoraUtils.getChildObject(rootObject, FIELD_ACTILITY_LRR_LATITUDE, Double.class),
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
        LoraUtils.getChildObject(lrr, FIELD_ACTILITY_LRR_RSSI, Double.class)
            .ifPresent(d -> gateway.setRssi(d.intValue()));
        LoraUtils.getChildObject(lrr, FIELD_ACTILITY_LRR_SNR, Double.class)
            .ifPresent(d -> gateway.setSnr(d));
        return gateway;
    }
}
