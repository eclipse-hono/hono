/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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
 * An Exception thrown when a malformed payload has to be processed.
 */
public class LoraProviderMalformedPayloadException extends RuntimeException {

    /**
     * Create the Exception with the given message and cause.
     *
     * @param message the Exception message
     * @param cause the Exception cause
     */
    public LoraProviderMalformedPayloadException(final String message, final Exception cause) {
        super(message, cause);
    }
}
