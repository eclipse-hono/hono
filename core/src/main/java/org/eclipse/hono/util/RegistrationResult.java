/**
 * Copyright (c) 2016 Bosch Software Innovations GmbH.
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
 * A container for the result returned by Hono's registration API.
 *
 */
public final class RegistrationResult {

    private final int status;
    private final JsonObject payload;

    private RegistrationResult(final int status, final JsonObject payload) {
        this.status = status;
        this.payload = payload;
    }

    public static RegistrationResult from(final int status) {
        return new RegistrationResult(status, null);
    }

    public static RegistrationResult from(final int status, final JsonObject payload) {
        return new RegistrationResult(status, payload);
    }

    /**
     * @return the status
     */
    public int getStatus() {
        return status;
    }

    /**
     * @return the payload
     */
    public JsonObject getPayload() {
        return payload;
    }

}
