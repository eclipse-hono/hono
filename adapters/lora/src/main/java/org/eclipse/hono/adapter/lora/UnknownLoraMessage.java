/**
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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
 * A Lora message that contains unknown data sent from an end-device to a Network Server.
 *
 */
public class UnknownLoraMessage implements LoraMessage {

    /**
     * {@inheritDoc}
     */
    @Override
    public final byte[] getDevEUI() {
        return new byte[0];
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final String getDevEUIAsString() {
        return "";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final LoraMessageType getType() {
        return LoraMessageType.UNKNOWN;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Buffer getPayload() {
        return Buffer.buffer();
    }
}
