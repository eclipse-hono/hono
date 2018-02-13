/**
 * Copyright (c) 2016,2017 Bosch Software Innovations GmbH.
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
 * A container for the result returned by Hono's tenant API.
 *
 */
public final class TenantResult extends RequestResponseResult<JsonObject> {
    private TenantResult(final int status, final JsonObject payload) {
        super(status, payload);
    }

    public static TenantResult from(final int status) {
        return new TenantResult(status, null);
    }

    public static TenantResult from(final int status, final JsonObject payload) {
        return new TenantResult(status, payload);
    }

    public static TenantResult from(final int status, final String payloadString) {
        if (payloadString != null) {
            return new TenantResult(status, new JsonObject(payloadString));
        } else {
            return new TenantResult(status, null);
        }
    }
}