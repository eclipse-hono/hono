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

import java.util.Objects;

import org.eclipse.hono.adapter.lora.LoraMessage;
import org.eclipse.hono.adapter.lora.LoraMessageType;
import org.eclipse.hono.adapter.lora.LoraMetaData;
import org.eclipse.hono.adapter.lora.UplinkLoraMessage;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

/**
 * A base class for implementing {@link LoraProvider}s
 * that are using JSON messages in their external API.
 *
 */
abstract class JsonBasedLoraProvider implements LoraProvider {

    @Override
    public LoraMessage getMessage(final RoutingContext ctx) {
        Objects.requireNonNull(ctx);
        try {
            final Buffer requestBody = ctx.getBody();
            final JsonObject message = requestBody.toJsonObject();
            final LoraMessageType type = getMessageType(message);
            switch (type) {
            case UPLINK:
                return createUplinkMessage(ctx.request(), message);
            default:
                throw new LoraProviderMalformedPayloadException(String.format("unsupported message type [%s]", type));
            }
        } catch (final RuntimeException e) {
            // catch generic exception in order to also cover any (runtime) exceptions
            // thrown by overridden methods
            throw new LoraProviderMalformedPayloadException("failed to decode request body", e);
        }
    }

    /**
     * Gets the type of a Lora message.
     *
     * @param loraMessage The message.
     * @return The type.
     */
    protected abstract LoraMessageType getMessageType(JsonObject loraMessage);

    /**
     * Gets the device EUI from an uplink message.
     *
     * @param uplinkMessage The message.
     * @return The device EUI.
     * @throws RuntimeException if the EUI cannot be extracted.
     */
    protected abstract String getDevEui(JsonObject uplinkMessage);

    /**
     * Gets the payload from an uplink message.
     *
     * @param uplinkMessage The message.
     * @return The raw bytes sent by the device.
     * @throws RuntimeException if the EUI cannot be extracted.
     */
    protected abstract Buffer getPayload(JsonObject uplinkMessage);

    /**
     * Gets meta data contained in an uplink message.
     * <p>
     * This default implementation returns {@code null}.
     *
     * @param uplinkMessage The uplink message.
     * @return The meta data or {@code null} if no meta data is available.
     * @throws RuntimeException if the meta data cannot be parsed.
     */
    protected LoraMetaData getMetaData(final JsonObject uplinkMessage) {
        return null;
    }

    /**
     * Gets any data contained in an uplink message in addition to the device EUI,
     * payload and meta data.
     * <p>
     * This default implementation returns the message itself.
     *
     * @param uplinkMessage The uplink message.
     * @return The additional data or {@code null} if no additional data is available.
     * @throws RuntimeException if the additional data cannot be parsed.
     */
    protected JsonObject getAdditionalData(final JsonObject uplinkMessage) {
        return uplinkMessage;
    }

    /**
     * Creates an object representation of a Lora uplink message.
     * <p>
     * This method uses the {@link #getDevEui(JsonObject)}, {@link #getPayload(JsonObject)},
     * {@link #getMetaData(JsonObject)} and {@link #getAdditionalData(JsonObject)}
     * methods to extract relevant information from the request body to add
     * to the returned message.
     *
     * @param request The request sent by the provider's Network Server.
     * @param requestBody The JSON object contained in the request's body.
     * @return The message.
     * @throws RuntimeException if the message cannot be parsed.
     */
    protected UplinkLoraMessage createUplinkMessage(final HttpServerRequest request, final JsonObject requestBody) {

        Objects.requireNonNull(requestBody);

        final String devEui = getDevEui(requestBody);
        final UplinkLoraMessage message = new UplinkLoraMessage(devEui);
        message.setPayload(getPayload(requestBody));
        message.setMetaData(getMetaData(requestBody));
        message.setAdditionalData(getAdditionalData(requestBody));
        return message;
    }
}
