/**
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 1.0 which is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * SPDX-License-Identifier: EPL-1.0
 */
package org.eclipse.hono.adapter.coap;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.eclipse.hono.service.auth.device.AbstractDeviceCredentials;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsObject;

import io.vertx.core.json.JsonObject;

/**
 * A base class providing utility methods for verifying or extract credentials for pre-shared-key authentication.
 */
public abstract class AbstractPreSharedKeyCredentials extends AbstractDeviceCredentials {

    /**
     * Device identity based on the pre-shared-key identity.
     */
    private PreSharedKeyTenantIdentity identity;

    /**
     * Create new instance.
     * 
     * @param tenantId tenant id
     * @param authId identity that the device wants to authenticate as
     */
    public AbstractPreSharedKeyCredentials(final String tenantId, final String authId) {
        this.identity = new PreSharedKeyTenantIdentity(tenantId, authId);
    }

    /**
     * Create new instance.
     * 
     * @param identity device identity based on the pre-shared-key identity.
     */
    public AbstractPreSharedKeyCredentials(final PreSharedKeyTenantIdentity identity) {
        this.identity = identity;
    }

    @Override
    public final String getType() {
        return CredentialsConstants.SECRETS_TYPE_PRESHARED_KEY;
    }

    @Override
    public final String getAuthId() {
        return identity.getAuthId();
    }

    @Override
    public final String getTenantId() {
        return identity.getTenantId();
    }

    /**
     * Extract pre-shared-key.
     * 
     * @param identity device identity
     * @param credentialsOnRecord credentials record form credentials service.
     * @return pre-shared-key
     */
    public static byte[] getCredentialsSecret(final PreSharedKeyTenantIdentity identity,
            final CredentialsObject credentialsOnRecord) {
        CredentialsUtil util = new CredentialsUtil(identity);
        util.validate(credentialsOnRecord);
        if (util.sharedKeys.isEmpty()) {
            return null;
        }
        return util.sharedKeys.get(0);
    }

    /**
     * Pre-shared-key extract utility.
     * <p>
     * Use base class implementation to select credentials and extract the valid pre-shared-keys.
     */
    private static class CredentialsUtil extends AbstractPreSharedKeyCredentials {

        /**
         * Extracted pre-shared-key.
         */
        public final List<byte[]> sharedKeys = new ArrayList<byte[]>();

        public CredentialsUtil(final PreSharedKeyTenantIdentity identity) {
            super(identity);
        }

        @Override
        public boolean matchesCredentials(final JsonObject candidateSecret) {
            String secretKeyBase64 = candidateSecret.getString(CredentialsConstants.FIELD_SECRETS_KEY);

            if (secretKeyBase64 != null) {
                final byte[] secret = Base64.getDecoder().decode(secretKeyBase64);
                sharedKeys.add(secret);
            }

            return false;
        }

    }

}
