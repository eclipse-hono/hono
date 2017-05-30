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

import io.vertx.core.json.JsonObject;

/**
 * A container for the result returned by Hono's credentials API.
 *
 */
public final class CredentialsResult extends RequestResponseResult {
    private CredentialsResult(final int status, final JsonObject payload) {
        super(status, payload);
    }

    public static CredentialsResult from(final int status) {
        return new CredentialsResult(status, null);
    }

    public static CredentialsResult from(final int status, final JsonObject payload) {
        return new CredentialsResult(status, payload);
    }
}
