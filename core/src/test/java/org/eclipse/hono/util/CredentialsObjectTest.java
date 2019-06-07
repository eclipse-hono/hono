/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.util;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;

import org.junit.Test;

import io.vertx.core.json.JsonObject;


/**
 * Verifies behavior of {@link CredentialsObject}.
 *
 */
public class CredentialsObjectTest {

    /**
     * Verifies that the object can be successfully marshaled to JSON
     * and then unmarshaled back into an object.
     */
    @Test
    public void testMarshaling() {

        // GIVEN a credentials object with some additional custom data
        final CredentialsObject orig = CredentialsObject.fromClearTextPassword("4711", "my-device", "secret", null, null);
        orig.setProperty("client-id", "MQTT-client-4523653");
        orig.setEnabled(false);

        // WHEN marshaling the object to JSON
        final JsonObject json = JsonObject.mapFrom(orig);
        // and unmarshaling it back into an object
        final CredentialsObject unmarshaled = json.mapTo(CredentialsObject.class);

        // THEN all properties have the same value as in the original object
        assertThat(unmarshaled.getDeviceId(), is("4711"));
        assertThat(unmarshaled.getAuthId(), is("my-device"));
        assertThat(unmarshaled.getType(), is(CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD));
        assertFalse(unmarshaled.isEnabled());
        assertThat(unmarshaled.getProperty("client-id", String.class), is("MQTT-client-4523653"));
    }

    /**
     * Verifies that credentials that do not contain any secrets are
     * detected as invalid.
     */
    @Test(expected = IllegalStateException.class)
    public void testCheckSecretsDetectsMissingSecrets() {
        final CredentialsObject creds = new CredentialsObject("tenant", "device", "x509-cert");
        creds.checkSecrets();
    }

    /**
     * Verifies that credentials that contains an empty set of secrets only are
     * considered valid.
     */
    @Test
    public void testCheckSecretsAcceptsEmptySecrets() {
        final CredentialsObject creds = new CredentialsObject("tenant", "device", "x509-cert");
        creds.addSecret(new JsonObject());
        creds.checkSecrets();
    }

    /**
     * Verifies that credentials that contain a malformed not-before value are
     * detected as invalid.
     */
    @Test(expected = IllegalStateException.class)
    public void testCheckSecretsDetectsMalformedNotBefore() {
        final CredentialsObject creds = new CredentialsObject("tenant", "device", "x509-cert");
        creds.setType(CredentialsConstants.SECRETS_TYPE_PRESHARED_KEY);
        creds.addSecret(new JsonObject()
                .put(CredentialsConstants.FIELD_SECRETS_NOT_BEFORE, "malformed"));
        creds.checkSecrets();
    }

    /**
     * Verifies that credentials that contain a malformed not-before value are
     * detected as invalid.
     */
    @Test(expected = IllegalStateException.class)
    public void testCheckSecretsDetectsMalformedNotAfter() {
        final CredentialsObject creds = new CredentialsObject("tenant", "device", "x509-cert");
        creds.setType(CredentialsConstants.SECRETS_TYPE_PRESHARED_KEY);
        creds.addSecret(new JsonObject()
                .put(CredentialsConstants.FIELD_SECRETS_NOT_AFTER, "malformed"));
        creds.checkSecrets();
    }

    /**
     * Verifies that credentials that contain inconsistent values for not-before
     * and not-after are detected as invalid.
     */
    @Test(expected = IllegalStateException.class)
    public void testCheckSecretsDetectsInconsistentValidityPeriod() {
        final CredentialsObject creds = new CredentialsObject("tenant", "device", "x509-cert");
        creds.setType(CredentialsConstants.SECRETS_TYPE_PRESHARED_KEY);
        creds.addSecret(new JsonObject()
                .put(CredentialsConstants.FIELD_SECRETS_NOT_BEFORE, "2018-10-10T00:00:00+00:00")
                .put(CredentialsConstants.FIELD_SECRETS_NOT_AFTER, "2018-10-01T00:00:00+00:00"));
        creds.checkSecrets();
    }

    /**
     * Verifies that hashed-password credentials that do not contain the hash function name are
     * detected as invalid.
     */
    @Test(expected = IllegalStateException.class)
    public void testCheckSecretsDetectsMissingHashFunction() {
        final CredentialsObject creds = new CredentialsObject("tenant", "device", "x509-cert");
        creds.setType(CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD);
        creds.addSecret(new JsonObject().put(CredentialsConstants.FIELD_SECRETS_PWD_HASH, "hash"));
        creds.checkSecrets();
    }

    /**
     * Verifies that hashed-password credentials that do not contain the hash value are
     * detected as invalid.
     */
    @Test(expected = IllegalStateException.class)
    public void testCheckSecretsDetectsMissingHashValue() {
        final CredentialsObject creds = new CredentialsObject("tenant", "device", "x509-cert");
        creds.setType(CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD);
        creds.addSecret(new JsonObject().put(CredentialsConstants.FIELD_SECRETS_HASH_FUNCTION, CredentialsConstants.HASH_FUNCTION_SHA256));
        creds.checkSecrets();
    }
}
