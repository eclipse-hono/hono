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

package org.eclipse.hono.util;

import static org.junit.Assert.*;

import org.junit.Test;

import io.vertx.core.json.JsonObject;


/**
 * Verifies behavior of {@link CredentialsObject}.
 *
 */
public class CredentialsObjectTest {

    /**
     * Verifies that credentials that do not contain any secrets are
     * detected as invalid.
     */
    @Test
    public void testHasValidCredentialsDetectsMissingSecrets() {
        final CredentialsObject creds = new CredentialsObject("tenant", "device", "x509-cert");
        assertFalse(creds.hasValidSecrets());
    }

    /**
     * Verifies that credentials that contains an empty set of secrets only are
     * considered valid.
     */
    @Test
    public void testHasValidCredentialsAcceptsEmptySecrets() {
        final CredentialsObject creds = new CredentialsObject("tenant", "device", "x509-cert");
        creds.addSecret(new JsonObject());
        assertTrue(creds.hasValidSecrets());
    }

    /**
     * Verifies that the SHA family of hash functions is supported.
     */
    @Test
    public void testGetHashedPasswordSupportsSHA() {

        final byte[] salt = new byte[] { 0x01, 0x02, 0x03 };
        final String[] hashFunctions = new String[] { "sha-256", "sha-512" };
        for (String hashFunction : hashFunctions) {
            assertNotNull(CredentialsObject.getHashedPassword(hashFunction, salt, "thesecretpwd"));
        }
    }
}
