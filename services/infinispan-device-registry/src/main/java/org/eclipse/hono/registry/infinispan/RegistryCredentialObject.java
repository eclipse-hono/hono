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
package org.eclipse.hono.registry.infinispan;

import io.vertx.core.json.JsonObject;
import java.io.Serializable;
import org.eclipse.hono.util.CredentialsObject;
import org.infinispan.protostream.annotations.ProtoDoc;
import org.infinispan.protostream.annotations.ProtoField;

/**
 * A custom class to be used as value in the backend key-value storage.
 * This store credentials details.
 *
 *  See {@link CacheTenantService CacheTenantService} class.
 */
@ProtoDoc("@Indexed")
public class RegistryCredentialObject implements Serializable {

    private String tenantId;
    private String deviceId;
    private String originalJson;

    /**
     * Constructor without arguments for the protobuilder.
     */
    public RegistryCredentialObject(){
    }

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
        this.originalJson = originalJson.encode();
    }

    @ProtoDoc("@Field")
    @ProtoField(number = 3, required = true)
    public String getOriginalJson() {
        return originalJson;
    }

    @ProtoDoc("@Field")
    @ProtoField(number = 2, required = true)
    public String getDeviceId(){
        return deviceId;
    }

    @ProtoDoc("@Field")
    @ProtoField(number = 1, required = true)
    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(final String tenantId) {
        this.tenantId = tenantId;
    }

    public void setDeviceId(final String deviceId) {
        this.deviceId = deviceId;
    }

    public void setOriginalJson(final String originalJson) {
        this.originalJson = originalJson;
    }
}
