/*******************************************************************************
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.service.management.credentials;


import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.eclipse.hono.util.CredentialsConstants;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;

/**
 * Verifies behavior of {@link PskSecret}.
 */
public class PskSecretTest {

    /**
     * Test encoding a psk secret.
     */
    @Test
    public void testEncodePskSecret() {

        final PskSecret secret = new PskSecret();
        CommonSecretTest.addCommonProperties(secret);
        secret.setKey(new byte[] { 1, 2, 3 });

        final JsonObject json = JsonObject.mapFrom(secret);
        CommonSecretTest.assertCommonProperties(json);
        assertArrayEquals(new byte[] { 1, 2, 3 }, json.getBinary(CredentialsConstants.FIELD_SECRETS_KEY));
    }

    /**
     * Verifies that the key of an existing secret is not merged into an
     * updated secret if it contains a new key.
     */
    @Test
    public void testMergePropertiesUsesNewKey() {

        final PskSecret updatedSecret = new PskSecret();
        updatedSecret.setId("one");
        updatedSecret.setKey(new byte[] { 0x03, 0x04 });

        final PskSecret existingSecret = new PskSecret();
        existingSecret.setId("one");
        existingSecret.setKey(new byte[] { 0x01, 0x02 });

        updatedSecret.merge(existingSecret);

        assertThat(updatedSecret.getKey()).isEqualTo(new byte[] { 0x03, 0x04 });
    }

    /**
     * Verifies that the shared key of an existing secret
     * is merged into an updated secret if it contains an ID only.
     */
    @Test
    public void testMergePropertiesUsesExistingKey() {

        final PskSecret updatedSecret = new PskSecret();
        updatedSecret.setId("one");

        final PskSecret existingSecret = new PskSecret();
        existingSecret.setId("one");
        existingSecret.setKey(new byte[] { 0x01, 0x02 });

        updatedSecret.merge(existingSecret);

        assertThat(updatedSecret.getKey()).isEqualTo(new byte[] { 0x01, 0x02 });
    }
}
