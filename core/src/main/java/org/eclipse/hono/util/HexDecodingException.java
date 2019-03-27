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

package org.eclipse.hono.util;

/**
 * Exception thrown when something went wrong decoding a hex vaule.
 */
public class HexDecodingException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a new exception whith the given error message.
     *
     * @param message the error message
     */
    public HexDecodingException(final String message) {
        super(message);
    }
}
