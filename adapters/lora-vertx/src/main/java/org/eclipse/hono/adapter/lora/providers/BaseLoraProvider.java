/**
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.hono.adapter.lora.providers;

import java.util.Map;
import java.util.Objects;

import org.eclipse.hono.adapter.lora.LoraMessage;
import org.eclipse.hono.adapter.lora.LoraMessageType;
import org.eclipse.hono.adapter.lora.UplinkLoraMessage;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;

/**
 * A base class for implementing {@link LoraProvider}s.
 *
 */
abstract class BaseLoraProvider implements LoraProvider {

    @Override
    public LoraMessage getMessage(final Buffer body) {
        Objects.requireNonNull(body);
        try {
            final JsonObject requestBody = body.toJsonObject();
            final LoraMessageType type = extractMessageType(requestBody);
            switch (type) {
            case UPLINK:
                return createUplinkMessage(requestBody);
            default:
                throw new LoraProviderMalformedPayloadException(String.format("unsupported message type [%s]", type));
            }
        } catch (final DecodeException | IllegalArgumentException e) {
            throw new LoraProviderMalformedPayloadException("failed to decode request body", e);
        }
    }

    protected UplinkLoraMessage createUplinkMessage(final JsonObject requestBody) {
        final String devEui = extractDevEui(requestBody);
        final UplinkLoraMessage message = new UplinkLoraMessage(devEui);
        message.setPayload(extractPayload(requestBody));
        message.setNormalizedData(extractNormalizedData(requestBody));
        message.setAdditionalData(extractAdditionalData(requestBody));
        return message;
    }

    protected abstract String extractDevEui(JsonObject loraMessage);
    protected abstract Buffer extractPayload( JsonObject loraMessage);
    protected abstract LoraMessageType extractMessageType(JsonObject loraMessage);
    protected Map<String, Object> extractNormalizedData(final JsonObject loraMessage) {
        return Map.of();
    };
    protected JsonObject extractAdditionalData(final JsonObject loraMessage) {
        return null;
    };
}
