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

import java.util.Objects;

import org.eclipse.hono.service.auth.device.CredentialsApiAuthProvider;
import org.eclipse.hono.service.auth.device.DeviceCredentials;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsObject;
import org.springframework.beans.factory.annotation.Autowired;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

/**
 * Authentication provider supporting pre-shared-key handshake.
 * 
 * Extends the {@link CredentialsApiAuthProvider} with a public
 * {@link #getCredentialsForDevice(PreSharedKeyTenantIdentity)} to provided the stored secret key for the device. In
 * difference to the authentication model supported direct by the {@link CredentialsApiAuthProvider}, which compares a
 * already available key with the stored one, a pre-shared-key handshake requires to access the secret key to generate a
 * proper finish message.
 */
public final class PreSharedKeyCredentialsProvider extends CredentialsApiAuthProvider {

    /**
     * Creates a new provider for a given configuration.
     * 
     * @param vertx The vertx instance to use.
     * @throws NullPointerException if any of the params is {@code null}.
     */
    @Autowired
    public PreSharedKeyCredentialsProvider(final Vertx vertx) {
        super(vertx);
    }

    /**
     * Retrieves credentials from the credentials service.
     * 
     * Uses {@link #getCredentialsForDevice(DeviceCredentials)} and offers public access.
     * 
     * @param tenantIdentity The tenant and identity provided by the device in the pre-shared-key handshake.
     * @return A future containing the credentials on record as retrieved from Hono's <em>Credentials</em> API.
     * @throws NullPointerException if tenantIdentity is {@code null}.
     */
    public final Future<CredentialsObject> getCredentialsForDevice(final PreSharedKeyTenantIdentity tenantIdentity) {
        Objects.requireNonNull(tenantIdentity);

        // convert pre-shared-key identity into hono DeviceCredentials
        final DeviceCredentials credentials = new DeviceCredentials() {

            @Override
            public boolean validate(final CredentialsObject credentialsOnRecord) {
                // dummy, not used for pre-shared-key
                return false;
            }

            @Override
            public String getType() {
                return CredentialsConstants.SECRETS_TYPE_PRESHARED_KEY;
            }

            @Override
            public String getTenantId() {
                return tenantIdentity.getTenantId();
            }

            @Override
            public String getAuthId() {
                return tenantIdentity.getAuthId();
            }
        };

        return getCredentialsForDevice(credentials);
    }

    @Override
    protected DeviceCredentials getCredentials(final JsonObject authInfo) {
        // dummy, not used for pre-shared-key
        return null;
    }

}
