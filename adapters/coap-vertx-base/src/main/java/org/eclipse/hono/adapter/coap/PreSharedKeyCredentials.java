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

import java.util.Arrays;
import java.util.Base64;

import org.eclipse.hono.service.auth.device.AbstractDeviceCredentials;
import org.eclipse.hono.util.CredentialsConstants;

import io.vertx.core.json.JsonObject;

/**
 * A class providing methods for verifying credentials using the content of
 * {@link CredentialsConstants#FIELD_SECRETS_KEY}.
 * <p>
 * This class is used, when the device sends the secret key in the URI. Therefore the secret key, isn't longer secret.
 * This may be changed therefore in the future to a different hashed password using then a different implementation of
 * the {@link AbstractDeviceCredentials}.
 */
public class PreSharedKeyCredentials extends AbstractPreSharedKeyCredentials {

    /**
     * Provided secret key.
     */
    private byte[] secretKey;

    /**
     * Create new instance to verify provided credentials with credentials from credentials service.
     * 
     * @param tenantId tenant id
     * @param authId identity that the device wants to authenticate as
     * @param secretKeyBase64 shared key base64 encoded
     */
    public PreSharedKeyCredentials(final String tenantId, final String authId, final String secretKeyBase64) {
        super(tenantId, authId);
        this.secretKey = Base64.getDecoder().decode(secretKeyBase64);
    }

    @Override
    public boolean matchesCredentials(final JsonObject candidateSecret) {
        final String secretKeyBase64 = candidateSecret.getString(CredentialsConstants.FIELD_SECRETS_KEY);

        if (secretKeyBase64 != null) {
            final byte[] secret = Base64.getDecoder().decode(secretKeyBase64);
            if (Arrays.equals(secretKey, secret)) {
                return true;
            }
        }

        return false;
    }
}
