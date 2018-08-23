/**
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hono.adapter.coap;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

import org.eclipse.hono.service.auth.device.AbstractDeviceCredentials;
import org.eclipse.hono.service.auth.device.DeviceCredentials;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.json.JsonObject;

/**
 * Helper class to represent the device identity based on pre shared key identity.
 */
public class PreSharedKeyDeviceIdentity implements DeviceCredentials {

    private static final Logger LOG = LoggerFactory.getLogger(PreSharedKeyDeviceIdentity.class);

    /**
     * Identity that the device wants to authenticate as.
     */
    private final String authId;
    /**
     * Tenant id the device belongs to.
     */
    private final String tenantId;
    /**
     * Precalculated hash.
     */
    private final int hash;

    /**
     * Create a new instance.
     * 
     * @param tenantId tenant id the device belongs to
     * @param authId Iidentity that the device wants to authenticate as
     */
    public PreSharedKeyDeviceIdentity(final String tenantId, final String authId) {
        this.tenantId = Objects.requireNonNull(tenantId);
        this.authId = Objects.requireNonNull(authId);
        this.hash = tenantId.hashCode() + 31 * authId.hashCode();
    }

    /**
     * Creates a new instance.
     *
     * @param identity The identity provided by the device using the pre shared key handshake.
     * @param separateRegex The regular expression to split identity for multi tenant.
     * @return The instance of the created object. Will be null if the identity is null, or the identity does not comply
     *         to the structure defined by the separateRegex.
     */
    public static final PreSharedKeyDeviceIdentity create(final String identity, final String separateRegex) {

        if (identity == null) {
            LOG.trace("username must not be null");
            return null;
        }

        if (separateRegex == null) {
            return new PreSharedKeyDeviceIdentity(Constants.DEFAULT_TENANT, identity);
        } else {
            // multi tenantId -> <userId><sep-regex><tenantId> (default)
            final String[] userComponents = identity.split(separateRegex, 2);
            if (userComponents.length != 2) {
                LOG.trace("username does not comply with expected pattern [<authId>@<tenantId>]", identity);
                return null;
            }
            return new PreSharedKeyDeviceIdentity(userComponents[1], userComponents[0]);
        }
    }

    /**
     * Gets the identity that the device wants to authenticate as.
     * <p>
     * This is either the value of the identity provided by the device (single tenant), or the <em>auth ID</em> part
     * parsed from the identity (multi tenant).
     * 
     * @return The identity.
     */
    public final String getAuthId() {
        return authId;
    }

    /**
     * Gets the tenant that the device claims to belong to.
     * <p>
     * This is either the {@link Constants#DEFAULT_TENANT} (single tenant) or the <em>tenant ID</em> part parsed from
     * the identity (multi tenant).
     * 
     * @return The tenant.
     */
    public final String getTenantId() {
        return tenantId;
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final PreSharedKeyDeviceIdentity other = (PreSharedKeyDeviceIdentity) obj;
        if (authId == null) {
            if (other.authId != null) {
                return false;
            }
        } else if (!authId.equals(other.authId)) {
            return false;
        }
        if (tenantId == null) {
            if (other.tenantId != null) {
                return false;
            }
        } else if (!tenantId.equals(other.tenantId)) {
            return false;
        }
        return true;
    }

    @Override
    public String getType() {
        return CredentialsConstants.SECRETS_TYPE_PRESHARED_KEY;
    }

    @Override
    public boolean validate(final CredentialsObject credentialsOnRecord) {
        // Identities doesn't validate
        return false;
    }

    /**
     * Extract pre-shared-key.
     * 
     * @param credentialsOnRecord credentials record form credentials service.
     * @return pre-shared-key
     */
    public byte[] getCredentialsSecret(final CredentialsObject credentialsOnRecord) {
        final SecretDeviceCredentials util = new SecretDeviceCredentials(this);
        util.validate(credentialsOnRecord);
        if (util.sharedKeys.isEmpty()) {
            return null;
        }
        return util.sharedKeys.get(0);
    }

    /**
     * Secret device credentials for pre-shared-key.
     * <p>
     * Use base class implementation to select credentials and extract the valid secret for pre-shared-key instead of
     * comparing it.
     */
    private static class SecretDeviceCredentials extends AbstractDeviceCredentials {

        /**
         * Extracted pre-shared-key.
         */
        private final List<byte[]> sharedKeys = new ArrayList<byte[]>();

        private SecretDeviceCredentials(final PreSharedKeyDeviceIdentity identity) {
            super(identity.getTenantId(), identity.getAuthId());
        }

        @Override
        public final String getType() {
            return CredentialsConstants.SECRETS_TYPE_PRESHARED_KEY;
        }

        @Override
        public boolean matchesCredentials(final JsonObject candidateSecret) {
            final String secretKeyBase64 = candidateSecret.getString(CredentialsConstants.FIELD_SECRETS_KEY);

            if (secretKeyBase64 != null) {
                final byte[] secret = Base64.getDecoder().decode(secretKeyBase64);
                sharedKeys.add(secret);
            }

            return false;
        }

    }

}
