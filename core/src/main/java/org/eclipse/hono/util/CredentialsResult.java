/**
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.util;

/**
 * A container for the result returned by Hono's credentials API.
 *
 * @param <T> denotes the concrete type of the payload that is part of the result
 */
public final class CredentialsResult<T> extends RequestResponseResult<T> {
    private CredentialsResult(final int status, final T payload) {
        super(status, payload);
    }

    public static CredentialsResult from(final int status) {
        return new CredentialsResult(status, null);
    }

    public static <T> CredentialsResult<T> from(final int status, final T payload) {
        return new CredentialsResult<>(status, payload);
    }
}
