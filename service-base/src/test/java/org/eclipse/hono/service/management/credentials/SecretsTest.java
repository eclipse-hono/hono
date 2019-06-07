/*******************************************************************************
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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


import static org.eclipse.hono.util.CredentialsConstants.FIELD_SECRETS;
import static org.eclipse.hono.util.RegistryManagementConstants.FIELD_AUTH_ID;
import static org.eclipse.hono.util.RegistryManagementConstants.FIELD_EXT;
import static org.eclipse.hono.util.RegistryManagementConstants.FIELD_SECRETS_COMMENT;
import static org.eclipse.hono.util.RegistryManagementConstants.FIELD_SECRETS_KEY;
import static org.eclipse.hono.util.RegistryManagementConstants.FIELD_SECRETS_PWD_HASH;
import static org.eclipse.hono.util.RegistryManagementConstants.FIELD_SECRETS_SALT;
import static org.eclipse.hono.util.RegistryManagementConstants.FIELD_TYPE;
import static org.eclipse.hono.util.RegistryManagementConstants.SECRETS_TYPE_X509_CERT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsMapContaining.hasEntry;
import static org.hamcrest.collection.IsMapWithSize.aMapWithSize;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.hamcrest.core.IsNull.nullValue;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import java.time.Instant;
import org.eclipse.hono.util.CredentialsConstants;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Verifies {@link CommonSecret} and others.
 */
public class SecretsTest {

    /**
     * Test encoding of a simple password secret.
     */
    @Test
    public void testEncodePasswordSecret1() {

        final PasswordSecret secret = new PasswordSecret();

        secret.setNotAfter(Instant.EPOCH);
        secret.setNotAfter(Instant.EPOCH.plusMillis(1));

        secret.setComment("setec astronomy");

        secret.setPasswordHash("2a5d81942494986ce6e23aadfa18cd426a1d7ab90629a0814d244c4cd82cc81f");
        secret.setSalt("abc");

        secret.setHashFunction(CredentialsConstants.HASH_FUNCTION_SHA256);

        final JsonObject json = JsonObject.mapFrom(secret);
        assertNotNull(json);

        assertEquals("abc", json.getString(FIELD_SECRETS_SALT));
        assertEquals("2a5d81942494986ce6e23aadfa18cd426a1d7ab90629a0814d244c4cd82cc81f",
                json.getString(FIELD_SECRETS_PWD_HASH));
        assertEquals("setec astronomy", json.getString(FIELD_SECRETS_COMMENT));
    }

    /**
     * Test encoding a psk secret.
     */
    @Test
    public void testEncodePskSecret() {
        final PskSecret secret = new PskSecret();
        secret.setKey(new byte[] { 1, 2, 3 });

        final JsonObject json = JsonObject.mapFrom(secret);
        assertNotNull(json);

        assertArrayEquals(new byte[] { 1, 2, 3 }, json.getBinary(FIELD_SECRETS_KEY));
    }

    /**
     * Test encoding a x509 credential.
     */
    @Test
    public void testEncodeX509Credential() {

        final X509CertificateSecret secret = new X509CertificateSecret();

        final X509CertificateCredential credential = new X509CertificateCredential();
        credential.setAuthId("auth1");

        credential.getSecrets().add(secret);

        final JsonObject json = JsonObject.mapFrom(credential);
        assertNotNull(json);
        assertThat(json.getString(FIELD_TYPE), is(SECRETS_TYPE_X509_CERT));
        assertThat(json.getJsonObject(FIELD_EXT), nullValue());
    }

    /**
     * Test decode an unknown type.
     */
    @Test
    public void testDecodeGeneric() {
        final CommonCredential credential = new JsonObject()
                .put(FIELD_TYPE, "foo")
                .put(FIELD_AUTH_ID, "authId1")
                .put(FIELD_SECRETS, new JsonArray()
                        .add(new JsonObject()
                                .put("quote", "setec astronomy")))
                .mapTo(CommonCredential.class);

        assertThat(credential, notNullValue());
        assertThat(credential, instanceOf(GenericCredential.class));

        assertThat(credential.getSecrets(), notNullValue());
        assertThat(credential.getSecrets().size(), is(1));
        assertThat(credential.getSecrets().get(0), instanceOf(GenericSecret.class));

        final var secret = (GenericSecret) credential.getSecrets().get(0);
        assertThat(secret.getAdditionalProperties(), notNullValue());
        assertThat(secret.getAdditionalProperties(), aMapWithSize(1));
        assertThat(secret.getAdditionalProperties(), hasEntry("quote", "setec astronomy"));

        assertThat(credential.getAuthId(), is("authId1"));
        assertThat(((GenericCredential) credential).getType(), is("foo"));

    }

}
