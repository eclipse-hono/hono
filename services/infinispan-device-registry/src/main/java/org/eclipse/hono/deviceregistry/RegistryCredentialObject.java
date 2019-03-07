/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.deviceregistry;

import io.vertx.core.json.JsonObject;
import org.eclipse.hono.util.CredentialsObject;

/**
 * A custom class to be used as value in the backend key-value storage.
 * This store credentials details.
 *
 *  See {@link org.eclipse.hono.deviceregistry.CacheTenantService CacheTenantService} class.
 */
public class RegistryCredentialObject {

    private final String tenantId;
    private final String deviceId;
    private final JsonObject originalJson;

    /**
     * Create a a RegistryCredentialObject with the credentials details.
     *
     * @param honoCredential the credential object, in a {@link org.eclipse.hono.util.CredentialsObject Hono CredentialsObject util class}.
     * @param tenantId the tenant ID associated with the credential.
     * @param originalJson the raw JSON object contained in the original creation request.
     */
    public RegistryCredentialObject(final CredentialsObject honoCredential, final String tenantId, final JsonObject originalJson){
        this.tenantId = tenantId;
        this.deviceId = honoCredential.getDeviceId();
        this.originalJson = originalJson;
    }

    public JsonObject getOriginalJson() {
        return originalJson;
    }

    public String getDeviceId(){
        return deviceId;
    }
}
