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
package org.eclipse.hono.adapter.limiting;

/**
 * Indicates that the auto-configuration of the connection limit failed.
 *
 */
public class ConnectionLimitAutoConfigException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a new exception for a detail message.
     *
     * @param msg The detail message.
     */
    public ConnectionLimitAutoConfigException(final String msg) {
        super(msg);
    }
}
