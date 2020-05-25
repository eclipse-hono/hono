/*******************************************************************************
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.tests.coap;

/**
 * A dedicated exception for CoAP failures.
 *
 */
@SuppressWarnings("serial")
public class CoapResultException extends Exception {

    private final int errorCode;

    /**
     * Create a new instance with an error code.
     *
     * @param errorCode The error code.
     */
    public CoapResultException(final int errorCode) {
        super(Integer.toString(errorCode));
        this.errorCode = errorCode;
    }

    /**
     * Create a new instance with an error code and message.
     *
     * @param errorCode The error code.
     * @param message The message.
     */
    public CoapResultException(final int errorCode, final String message) {
        super(Integer.toString(errorCode) + ": " + message);
        this.errorCode = errorCode;
    }

    /**
     * Gets the error code.
     *
     * @return The code.
     */
    public final int getErrorCode() {
        return this.errorCode;
    }

}
