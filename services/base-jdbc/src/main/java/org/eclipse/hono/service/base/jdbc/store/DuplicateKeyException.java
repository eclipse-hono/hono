/*******************************************************************************
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
 *******************************************************************************/

package org.eclipse.hono.service.base.jdbc.store;

/**
 * An exception indicating a duplicate key condition.
 */
public class DuplicateKeyException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Create a new instance without root cause.
     */
    public DuplicateKeyException() {
    }

    /**
     * Create a new instance with root cause.
     * @param cause The root cause.
     */
    public DuplicateKeyException(final Throwable cause) {
        super(cause);
    }
}
