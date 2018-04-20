/**
 * Copyright (c) 2017, 2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.service.auth.device;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.Instant;

import org.eclipse.hono.util.CredentialsObject;
import org.junit.Test;

import io.vertx.core.json.JsonObject;


/**
 * Tests verifying behavior of {@link AbstractDeviceCredentialsTest}.
 *
 */
public class AbstractDeviceCredentialsTest {

    /**
     * Verifies that credentials validation fails if the credentials on record are disabled.
     */
    @Test
    public void testValidateFailsIfCredentialsAreDisabled() {

        final AbstractDeviceCredentials creds = getDeviceCredentials("type", "tenant", "identity", true);
        final CredentialsObject credentialsOnRecord = getCredentialsObject("type", "identity", "device", false)
                .addSecret(CredentialsObject.emptySecret(Instant.now().minusSeconds(120), null));
        assertFalse(creds.validate(credentialsOnRecord));
    }

    /**
     * Verifies that credentials validation fails if none of the secrets on record are
     * valid yet.
     */
    @Test
    public void testValidateFailsIfNoSecretsAreValidYet() {

        final AbstractDeviceCredentials creds = getDeviceCredentials("type", "tenant", "identity", true);
        final CredentialsObject credentialsOnRecord = getCredentialsObject("type", "identity", "device", true)
                .addSecret(CredentialsObject.emptySecret(Instant.now().plusSeconds(120), null));
        assertFalse(creds.validate(credentialsOnRecord));
    }

    /**
     * Verifies that credentials validation fails if none of the secrets on record are
     * valid any more.
     */
    @Test
    public void testValidateFailsIfNoSecretsAreValidAnymore() {

        final AbstractDeviceCredentials creds = getDeviceCredentials("type", "tenant", "identity", true);
        final CredentialsObject credentialsOnRecord = getCredentialsObject("type", "identity", "device", true)
                .addSecret(CredentialsObject.emptySecret(null, Instant.now().minusSeconds(120)));
        assertFalse(creds.validate(credentialsOnRecord));
    }

    /**
     * Verifies that credentials validation succeeds if at least one of the secrets on record is
     * valid and matches the credentials.
     */
    @Test
    public void testValidateSucceedsIfAnyValidSecretMatches() {

        final AbstractDeviceCredentials creds = getDeviceCredentials("type", "tenant", "identity", true);
        final CredentialsObject credentialsOnRecord = getCredentialsObject("type", "identity", "device", true)
                .addSecret(CredentialsObject.emptySecret(Instant.now().minusSeconds(120), Instant.now().plusSeconds(120)));
        assertTrue(creds.validate(credentialsOnRecord));
    }

    private static AbstractDeviceCredentials getDeviceCredentials(final String type, final String tenantId, final String authId, final boolean match) {
        return new AbstractDeviceCredentials(tenantId, authId) {

            @Override
            public String getType() {
                return type;
            }

            @Override
            public boolean matchesCredentials(final JsonObject candidateSecret) {
                return match;
            }
        };
    }

    private static CredentialsObject getCredentialsObject(final String type, final String authId, final String deviceId, final boolean enabled) {

        return new CredentialsObject()
                .setAuthId(authId)
                .setDeviceId(deviceId)
                .setType(type)
                .setEnabled(enabled);
    }
}
