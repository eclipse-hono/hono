/*******************************************************************************
 * Copyright (c) 2019, 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.deviceregistry.base.device;

import java.io.Serializable;
import java.util.Objects;

import org.eclipse.hono.deviceregistry.base.tenant.TenantHandle;

import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;

/**
 * A custom class to be used as key in the back-end key-value storage.
 * This uses the unique values of a credential to create a unique key to store the credentials
 * details.
 */
public final class CredentialKey implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String tenantId;
    private final String authId;
    private final String type;

    /**
     * Creates a new CredentialsKey. Used by CacheCredentialsService.
     *
     * @param tenantId the id of the tenant owning the registration key.
     * @param authId the auth-id used in the credential.
     * @param type the the type of the credential.
     */
    private CredentialKey(final String tenantId, final String authId, final String type) {
        this.tenantId = tenantId;
        this.authId = authId;
        this.type = type;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final CredentialKey that = (CredentialKey) o;
        return Objects.equals(this.tenantId, that.tenantId) &&
                Objects.equals(this.authId, that.authId) &&
                Objects.equals(this.type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, authId, type);
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getAuthId() {
        return authId;
    }

    public String getType() {
        return type;
    }

    protected ToStringHelper toStringHelper() {
        return MoreObjects.toStringHelper(this)
                .add("tenantId", this.tenantId)
                .add("authId", this.authId)
                .add("type", this.type);
    }

    @Override
    public String toString() {
        return toStringHelper().toString();
    }

    public static CredentialKey credentialKey(final String tenantId, final String authId, final String type) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(authId);
        Objects.requireNonNull(type);
        return new CredentialKey(tenantId, authId, type);
    }

    public static CredentialKey credentialKey(final TenantHandle tenantHandle, final String authId, final String type) {
        Objects.requireNonNull(tenantHandle);
        Objects.requireNonNull(authId);
        Objects.requireNonNull(type);
        return new CredentialKey(tenantHandle.getId(), authId, type);
    }
}
