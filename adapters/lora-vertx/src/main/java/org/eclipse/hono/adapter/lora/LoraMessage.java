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

import io.vertx.core.buffer.Buffer;

/**
 * A message exchanged between a Lora device and Hono's Lora adapter.
 *
 */
public interface LoraMessage {

    /**
     * Gets the global end-device ID in IEEE EUI64 address space that uniquely identifies the
     * end-device.
     * <p>
     * This property corresponds to the <em>DevEUI</em> defined in section 6.1.1.2 of the
     * LoRaWAN 1.1 Specification.
     *
     * @return The identifier.
     */
    byte[] getDevEUI();

    /**
     * Gets the global end-device ID in IEEE EUI64 address space that uniquely identifies the
     * end-device.
     * <p>
     * This property corresponds to the <em>DevEUI</em> defined in section 6.1.1.2 of the
     * LoRaWAN 1.1 Specification.
     *
     * @return The base16 (hex) encoded identifier.
     */
    String getDevEUIAsString();

    /**
     * Gets this message's type.
     *
     * @return The type.
     */
    LoraMessageType getType();

    /**
     * Gets the payload.
     *
     * @return The payload as raw bytes.
     */
    Buffer getPayload();
}
