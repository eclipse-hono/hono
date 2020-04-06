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

package org.eclipse.hono.deviceregistry.service.credentials;

import java.util.Objects;

import org.eclipse.hono.deviceregistry.service.tenant.TenantKey;

import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;

/**
 * Provides a unique key for a <em>Credential</em> resource of Hono's
 * <a href="https://www.eclipse.org/hono/docs/api/management/">Device Registry Management API</a>.
 * It is used for storing and retrieving values from the backend storage and external systems.
 */
public final class CredentialKey {

    private final String tenantId;
    private final String authId;
    private final String type;


    /**
     * Creates a credential key.
     *
     * @param tenantId The tenant identifier.
     * @param authId The authentication identifier.
     * @param type The credential type.
     */
    public CredentialKey(final String tenantId, final String authId, final String type) {
        this.tenantId = tenantId;
        this.authId = authId;
        this.type = type;
    }

    /**
     * Gets the tenant identifier.
     *
     * @return The identifier or {@code null} if not set.
     */
    public String getTenantId() {
        return tenantId;
    }

    /**
     * Gets the authentication id of the credential.
     *
     * @return The authentication identifier or {@code null} if not set.
     */
    public String getAuthId() {
        return authId;
    }

    /**
     * Gets the type of the credential.
     *
     * @return The type or {@code null} if not set.
     */
    public String getType() {
        return type;
    }

    /**
     * Creates a credential key from tenant identifier, authentication identifier and type.
     *
     * @param tenantId The tenant identifier.
     * @param authId The authentication identifier.
     * @param type The credential type.
     * @throws NullPointerException if any of the parameters is {@code null}.
     * @return The credential key.
     */
    public static CredentialKey from(final String tenantId, final String authId, final String type) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(authId);
        Objects.requireNonNull(type);
        return new CredentialKey(tenantId, authId, type);
    }

    /**
     * Creates a credential key from tenant key, authentication identifier and type.
     *
     * @param tenantKey The tenant key.
     * @param authId The authentication identifier.
     * @param type The credential type.
     * @throws NullPointerException if any of the parameters is {@code null}.
     * @return The credential key.
     */
    public static CredentialKey from(final TenantKey tenantKey, final String authId, final String type) {
        Objects.requireNonNull(tenantKey);
        Objects.requireNonNull(authId);
        Objects.requireNonNull(type);
        return new CredentialKey(tenantKey.getTenantId(), authId, type);
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

    @Override
    public String toString() {
        return toStringHelper().toString();
    }

    private ToStringHelper toStringHelper() {
        return MoreObjects.toStringHelper(this)
                .add("tenantId", this.tenantId)
                .add("authId", this.authId)
                .add("type", this.type);
    }
}
