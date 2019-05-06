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
import org.eclipse.hono.util.TenantObject;
import org.infinispan.protostream.annotations.ProtoDoc;
import org.infinispan.protostream.annotations.ProtoField;

/**
 * A custom class to be used as value in the backend key-value storage.
 * This store tenants details.
 *
 *  See {@link CacheTenantService CacheTenantService} class.
 */
@ProtoDoc("@Indexed")
public class RegistryTenantObject implements Serializable {

    private String tenantId;
    private String trustedCa;
    private String tenantObject;

    /**
     *  Constructor without arguments for the protobuilder.
     */
    public RegistryTenantObject() {
    }

    /**
     * Create a a RegistryTenantObject with the Tenant details.
     * @param tenant the tenant object, in a {@link org.eclipse.hono.util.TenantObject Hono TenantObject util class}.
     */
    public RegistryTenantObject(final TenantObject tenant) {
        this.tenantId = tenant.getTenantId();

        if (tenant.getTrustedCaSubjectDn() != null ){
            this.trustedCa = tenant.getTrustedCaSubjectDn().getName();
        } else {
            this.trustedCa = null;
        }

        this.tenantObject = JsonObject.mapFrom(tenant).encode();
    }

    @ProtoDoc("@Field")
    @ProtoField(number = 3, required = true)
    public String getTenantObject() {
        return tenantObject;
    }

    @ProtoDoc("@Field")
    @ProtoField(number = 1, required = true)
    public String getTenantId() {
        return tenantId;
    }

    // Matching TenantConstants.FIELD_PAYLOAD_TRUSTED_CA;
    @ProtoDoc("@Field")
    @ProtoField(number = 2)
    public String getTrustedCa() {
        return trustedCa;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public void setTrustedCa(String trustedCa) {
        this.trustedCa = trustedCa;
    }

    public void setTenantObject(String tenantObject) {
        this.tenantObject = tenantObject;
    }
}
