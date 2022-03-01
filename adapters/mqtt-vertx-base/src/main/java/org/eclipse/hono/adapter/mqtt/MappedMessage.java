/*******************************************************************************
 * Copyright (c) 2020, 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter.mqtt;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import io.vertx.core.buffer.Buffer;

/**
 * The result of mapping a message using a {@code MessageMapping} service.
 */
public final class MappedMessage {

    private final String targetDeviceId;
    private final Buffer payload;
    private final Map<String, String> additionalProperties = new HashMap<>();

    /**
     * Creates a new mapping result.
     *
     * @param targetDeviceId The identifier of the target device that the original message has been mapped to.
     * @param payload The payload that the original message has been mapped to.
     * @throws NullPointerException if targetAddress is {@code null}.
     */
    public MappedMessage(final String targetDeviceId, final Buffer payload) {
        this(targetDeviceId, payload, null);
    }

    /**
     * Creates a new mapping result.
     *
     * @param targetDeviceId The identifier of the target device that the original message has been mapped to.
     * @param payload The payload that the original message has been mapped to.
     * @param additionalProperties Extra properties that should be included with the mapped message.
     * @throws NullPointerException if targetAddress is {@code null}.
     */
    public MappedMessage(
            final String targetDeviceId,
            final Buffer payload,
            final Map<String, String> additionalProperties) {

        this.targetDeviceId = Objects.requireNonNull(targetDeviceId);
        this.payload = Optional.ofNullable(payload).orElseGet(Buffer::buffer);
        Optional.ofNullable(additionalProperties)
            .ifPresent(props -> this.additionalProperties.putAll(additionalProperties));
    }

    /**
     * Gets the identifier of the device that the message should be forwarded to.
     *
     * @return The device identifier.
     */
    public String getTargetDeviceId() {
        return targetDeviceId;
    }

    /**
     * Gets the mapped payload.
     *
     * @return The payload.
     */
    public Buffer getPayload() {
        return payload;
    }

    /**
     * Gets additional properties to be included with the mapped
     * message.
     *
     * @return The properties (may be empty).
     */
    public Map<String, String> getAdditionalProperties() {
        return additionalProperties;
    }
}
