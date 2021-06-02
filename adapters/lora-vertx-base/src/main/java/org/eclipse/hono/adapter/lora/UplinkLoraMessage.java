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


package org.eclipse.hono.adapter.lora;

import java.util.Objects;

import com.google.common.io.BaseEncoding;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;


/**
 * A Lora message that contains data sent from an end-device to a Network Server.
 *
 */
public class UplinkLoraMessage implements LoraMessage {

    private final byte[] devEui;
    private final String devEuiAsString;
    private Buffer payload;
    private LoraMetaData metaData = null;
    private JsonObject additionalData;

    /**
     * Creates a new message for a device identifier.
     *
     * @param devEui The identifier.
     * @throws NullPointerException if devEui is {@code null}.
     * @throws IllegalArgumentException if devEui is not 8 bytes in length.
     */
    public UplinkLoraMessage(final byte[] devEui) {
        Objects.requireNonNull(devEui);
        if (devEui.length != 8) {
            throw new IllegalArgumentException("devEUI must be 64 bits long");
        }
        this.devEui = devEui;
        this.devEuiAsString = BaseEncoding.base16().encode(devEui);
    }

    /**
     * Creates a new message for a device identifier.
     *
     * @param devEui The identifier.
     * @throws NullPointerException if devEui is {@code null}.
     * @throws IllegalArgumentException if devEui is not base16 encoded.
     */
    public UplinkLoraMessage(final String devEui) {
        Objects.requireNonNull(devEui);
        if (devEui.length() != 16) {
            throw new IllegalArgumentException("devEUI must be 64 bits long");
        }
        this.devEuiAsString = devEui;
        this.devEui = BaseEncoding.base16().decode(devEui.toUpperCase());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final byte[] getDevEUI() {
        return devEui;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final String getDevEUIAsString() {
        return devEuiAsString;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final LoraMessageType getType() {
        return LoraMessageType.UPLINK;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Buffer getPayload() {
        return payload;
    }

    /**
     * Sets the message payload.
     *
     * @param payload The raw data bytes.
     */
    public final void setPayload(final Buffer payload) {
        this.payload = Objects.requireNonNull(payload);
    }

    /**
     * Gets the meta data contained in this message.
     *
     * @return The meta data.
     */
    public final LoraMetaData getMetaData() {
        return metaData;
    }

    /**
     * Sets the meta data contained in this message.
     *
     * @param data The meta data.
     */
    public final void setMetaData(final LoraMetaData data) {
        this.metaData = data;
    }

    /**
     * Gets additional data contained in this message.
     * <p>
     * The data returned might be included as application properties in the
     * downstream AMQP message.
     *
     * @return The additional data or {@code null}.
     */
    public final JsonObject getAdditionalData() {
        return additionalData;
    }

    /**
     * Sets additional data contained in this message.
     *
     * @param data The additional data or {@code null}.
     */
    public final void setAdditionalData(final JsonObject data) {
        this.additionalData = data;
    }
}
