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
 * A container for the result returned by a Hono API that implements the request response pattern.
 *
 */
public class RequestResponseResult {

    private final int status;
    private final JsonObject payload;

    protected RequestResponseResult(final int status, final JsonObject payload) {
        this.status = status;
        this.payload = payload;
    }

    /**
     * @return the status
     */
    public final int getStatus() {
        return status;
    }

    /**
     * @return the payload
     */
    public final JsonObject getPayload() {
        return payload;
    }
}
