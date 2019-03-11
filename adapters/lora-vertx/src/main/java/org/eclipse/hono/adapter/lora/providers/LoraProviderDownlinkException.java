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
 * An Exception thrown when something went wrong during processing of a downlink message.
 */
public class LoraProviderDownlinkException extends RuntimeException {

    /**
     * Creates the exception with a failure message.
     *
     * @param message the failure message
     */
    public LoraProviderDownlinkException(final String message) {
        super(message);
    }

    /**
     * Creates the exception with a failure message and a failure cause.
     *
     * @param message the failure message
     * @param cause the cause of the failure
     */
    public LoraProviderDownlinkException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
