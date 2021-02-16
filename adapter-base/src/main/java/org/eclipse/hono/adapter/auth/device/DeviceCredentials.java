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

package org.eclipse.hono.adapter.auth.device;

import io.vertx.core.json.JsonObject;

/**
 * A wrapper around credentials provided by a device for authentication.
 *
 */
public interface DeviceCredentials {

    /**
     * Gets the type of credentials this instance represents.
     *
     * @return The type.
     */
    String getType();

    /**
     * Gets the identity that the device wants to authenticate as.
     *
     * @return The identity.
     */
    String getAuthId();

    /**
     * Gets the tenant that the device claims to belong to.
     *
     * @return The tenant.
     */
    String getTenantId();

    /**
     * Gets additional properties of the device.
     * <p>
     * An implementation can return a JSON object containing arbitrary properties.
     * <p>
     * The default implementation returns a new, empty JSON object.
     *
     * @return The properties representing the client context.
     */
    default JsonObject getClientContext() {
        return new JsonObject();
    }
}
