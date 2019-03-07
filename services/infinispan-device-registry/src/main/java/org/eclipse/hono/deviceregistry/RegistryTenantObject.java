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

import org.eclipse.hono.util.TenantObject;

/**
 * A custom class to be used as value in the backend key-value storage.
 * This store tenants details.
 *
 *  See {@link org.eclipse.hono.deviceregistry.CacheTenantService CacheTenantService} class.
 */
public class RegistryTenantObject {

    //TODO add infinispan anotations
    private final String tenantId;
    // Matching TenantConstants.FIELD_PAYLOAD_TRUSTED_CA;
    private String trustedCa;

    private TenantObject tenantObject;


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

        this.tenantObject = tenant;
    }

    public TenantObject getTenantObject() {
        return tenantObject;
    }
}
