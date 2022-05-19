/**
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

import io.vertx.core.json.JsonObject;

/**
 * Contains a command, modified by the lora provider, to send to the lorawan network server.
 *
 */
public class LoraCommand {
    private final JsonObject payload;
    private final String uri;

    /**
     * Creates a new LoraCommand.
     *
     * @param payload The actual payload JSON, formatted according to the specific lora provider.
     * @param uri The full URI to which the command should be sent.
     * @throws NullPointerException if payload or uri are {@code null}.
     */
    public LoraCommand(final JsonObject payload, final String uri) {
        this.payload = Objects.requireNonNull(payload);
        this.uri = Objects.requireNonNull(uri);
    }

    /**
     * Gets the payload JSON.
     *
     * @return The payload.
     */
    public JsonObject getPayload() {
        return payload;
    }

    /**
     * Gets the URI to which the command should be sent.
     *
     * @return the URI.
     */
    public String getUri() {
        return uri;
    }
}
