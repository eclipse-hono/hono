/**
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
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
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.hono.service.auth.device.AbstractDeviceCredentials;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsObject;
import org.junit.Test;


/**
 * Tests verifying behavior of {@link AbstractDeviceCredentialsTest}.
 *
 */
public class AbstractDeviceCredentialsTest {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    /**
     * Verifies that credentials validation fails if the credentials on record are disabled.
     */
    @Test
    public void testValidateFailsIfCredentialsAreDisabled() {
        AbstractDeviceCredentials creds = getDeviceCredentials("type", "identity", true);
        CredentialsObject credentialsOnRecord = getCredentialsObject("type", "identity", "device", false);
        credentialsOnRecord.addSecret(getSecret(Instant.now().minusSeconds(120), null));
        assertFalse(creds.validate(credentialsOnRecord));
    }

    /**
     * Verifies that credentials validation fails if none of the secrets on record are
     * valid yet.
     */
    @Test
    public void testValidateFailsIfNoSecretsAreValidYet() {
        AbstractDeviceCredentials creds = getDeviceCredentials("type", "identity", true);
        CredentialsObject credentialsOnRecord = getCredentialsObject("type", "identity", "device", true);
        credentialsOnRecord.addSecret(getSecret(Instant.now().plusSeconds(120), null));
        assertFalse(creds.validate(credentialsOnRecord));
    }

    /**
     * Verifies that credentials validation fails if none of the secrets on record are
     * valid any more.
     */
    @Test
    public void testValidateFailsIfNoSecretsAreValidAnymore() {
        AbstractDeviceCredentials creds = getDeviceCredentials("type", "identity", true);
        CredentialsObject credentialsOnRecord = getCredentialsObject("type", "identity", "device", true);
        credentialsOnRecord.addSecret(getSecret(null, Instant.now().minusSeconds(120)));
        assertFalse(creds.validate(credentialsOnRecord));
    }

    /**
     * Verifies that credentials validation succeeds if at least one of the secrets on record is
     * valid and matches the credentials.
     */
    @Test
    public void testValidateSucceedsIfAnyValidSecretMatches() {
        AbstractDeviceCredentials creds = getDeviceCredentials("type", "identity", true);
        CredentialsObject credentialsOnRecord = getCredentialsObject("type", "identity", "device", true);
        credentialsOnRecord.addSecret(getSecret(Instant.now().minusSeconds(120), Instant.now().plusSeconds(120)));
        assertTrue(creds.validate(credentialsOnRecord));
    }

    private static AbstractDeviceCredentials getDeviceCredentials(final String type, final String authId, final boolean match) {
        return new AbstractDeviceCredentials() {

            @Override
            public String getType() {
                return type;
            }

            @Override
            public String getTenantId() {
                return "tenant";
            }

            @Override
            public String getAuthId() {
                return authId;
            }

            @Override
            public boolean matchesCredentials(Map<String, String> candidateSecret) {
                return match;
            }
        };
    }

    private static CredentialsObject getCredentialsObject(final String type, final String authId, final String deviceId, final boolean enabled) {

        CredentialsObject result = new CredentialsObject();
        result.setAuthId(authId);
        result.setDeviceId(deviceId);
        result.setType(type);
        result.setEnabled(enabled);
        return result;
    }

    private static Map<String, String> getSecret(final Instant notBefore, final Instant notAfter) {
        Map<String, String> secret = new HashMap<>();
        if (notBefore != null) {
            secret.put(CredentialsConstants.FIELD_SECRETS_NOT_BEFORE, DATE_TIME_FORMATTER.format(notBefore.atOffset(ZoneOffset.UTC)));
        }
        if (notAfter != null) {
            secret.put(CredentialsConstants.FIELD_SECRETS_NOT_AFTER, DATE_TIME_FORMATTER.format(notAfter.atOffset(ZoneOffset.UTC)));
        }
        return secret;
    }
}
