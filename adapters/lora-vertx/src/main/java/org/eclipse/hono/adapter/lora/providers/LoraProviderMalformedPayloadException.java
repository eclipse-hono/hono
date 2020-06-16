/*******************************************************************************
 * Copyright (c) 2019, 2020 Contributors to the Eclipse Foundation
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

/**
 * Indicates that a message received from a LoRa adapter contains malformed payload.
 */
public class LoraProviderMalformedPayloadException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a new exception for a message.
     *
     * @param message The message describing the error.
     */
    public LoraProviderMalformedPayloadException(final String message) {
        this(message, null);
    }

    /**
     * Creates a new exception for a message and root cause.
     *
     * @param message The message describing the error.
     * @param cause The root cause for the error. (A {@code null} value is
     *         permitted, and indicates that the cause is nonexistent or
     *         unknown.)
     */
    public LoraProviderMalformedPayloadException(final String message, final Exception cause) {
        super(message, cause);
    }
}
