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

import static org.eclipse.hono.util.RegistryManagementConstants.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * Verifies {@link CommonCredential} and others.
 */
public class CredentialsTest {

    /**
     * Test encoding of a simple password credential.
     */
    @Test
    public void testEncodePasswordCredential() {

        final PasswordSecret secret = new PasswordSecret();

        secret.setNotBefore(Instant.EPOCH);
        secret.setNotAfter(Instant.EPOCH.plusMillis(1));

        secret.setPasswordHash("2a5d81942494986ce6e23aadfa18cd426a1d7ab90629a0814d244c4cd82cc81f");
        secret.setSalt("abc");

        secret.setHashFunction(HASH_FUNCTION_SHA256);

        final PasswordCredential credential = new PasswordCredential();
        credential.setAuthId("foo");
        credential.setComment("setec astronomy");
        credential.setSecrets(Collections.singletonList(secret));

        final JsonObject jsonCredential = JsonObject.mapFrom(credential);
        assertNotNull(jsonCredential);
        assertEquals("hashed-password", jsonCredential.getString(FIELD_TYPE));
        assertEquals(1, jsonCredential.getJsonArray(FIELD_SECRETS).size());

        final JsonObject jsonSecret = jsonCredential.getJsonArray(FIELD_SECRETS).getJsonObject(0);

        assertEquals("foo", jsonCredential.getString(FIELD_AUTH_ID));
        assertEquals("setec astronomy", jsonCredential.getString("comment"));

        assertEquals("abc", jsonSecret.getString(FIELD_SECRETS_SALT));
        assertEquals("2a5d81942494986ce6e23aadfa18cd426a1d7ab90629a0814d244c4cd82cc81f",
                jsonSecret.getString(FIELD_SECRETS_PWD_HASH));
    }

    /**
     * Test encoding a psk secret.
     */
    @Test
    public void testEncodePskCredential() {
        final PskCredential credential = new PskCredential();

        final JsonObject json = JsonObject.mapFrom(credential);
        assertNotNull(json);

        assertEquals("psk", json.getString(FIELD_TYPE));

        final CommonCredential decodedCredential = Json.decodeValue(Json.encodeToBuffer(credential), CommonCredential.class);
        assertTrue(decodedCredential instanceof PskCredential);
    }

    /**
     * Test encoding a x509 secret.
     */
    @Test
    public void testEncodeX509Credential() {
        final X509CertificateCredential credential = new X509CertificateCredential();

        final JsonObject json = JsonObject.mapFrom(credential);
        assertNotNull(json);

        assertEquals("x509-cert", json.getString(FIELD_TYPE));
    }

    /**
     * Test encoding an array of secrets.
     */
    @Test
    public void testEncodeArray() {
        testEncodeMany(credentials -> credentials.toArray(CommonCredential[]::new));
    }

    /**
     * Test encoding an aray of secrets.
     *
     * @param provider credentials provider.
     */
    protected void testEncodeMany(final Function<List<CommonCredential>, Object> provider) {
        final List<CommonCredential> credentials = new ArrayList<>();

        final PskCredential credential = new PskCredential();
        credential.setAuthId("auth");
        final PskSecret secret = new PskSecret();
        secret.setKey("foo".getBytes(StandardCharsets.UTF_8));
        credential.setSecrets(Collections.singletonList(secret));
        credentials.add(credential);

        final String json = Json.encode(provider.apply(credentials));

        final JsonArray array = new JsonArray(json);
        for (final Object o : array) {
            assertTrue(o instanceof JsonObject);
            assertNotNull(((JsonObject) o).getString("type"));
        }
    }

    /**
     * Test the decoding of a Json Object to a simple password credential.
     */
    @Test
    public void testDecodePasswordCredential() {

        final JsonObject jsonCredential = new JsonObject()
                .put(FIELD_TYPE, SECRETS_TYPE_HASHED_PASSWORD)
                .put(FIELD_AUTH_ID, "foo")
                .put(FIELD_ENABLED, true)
                .put(FIELD_SECRETS, new JsonArray()
                        .add(new JsonObject()
                            .put(FIELD_ENABLED, true)
                            .put(FIELD_SECRETS_NOT_BEFORE, Instant.EPOCH)
                            .put(FIELD_SECRETS_NOT_AFTER, Instant.EPOCH.plusMillis(1))
                            .put(FIELD_SECRETS_HASH_FUNCTION, HASH_FUNCTION_SHA256)
                            .put(FIELD_SECRETS_PWD_HASH, "2a5d81942494986ce6e23aadfa18cd426a1d7ab90629a0814d244c4cd82cc81f")
                            .put(FIELD_SECRETS_SALT, "abc")
                            .put(FIELD_SECRETS_COMMENT, "setec astronomy")
                        )
                );

        final PasswordCredential credential = jsonCredential.mapTo(PasswordCredential.class);

        assertNotNull(credential);
        assertEquals("foo", credential.getAuthId());
        assertTrue(credential.getEnabled());
        assertEquals(1, credential.getSecrets().size());

        final PasswordSecret secret = credential.getSecrets().get(0);

        assertEquals("setec astronomy", secret.getComment());
        assertEquals("abc", secret.getSalt());
        assertEquals(HASH_FUNCTION_SHA256, secret.getHashFunction());
        assertEquals("2a5d81942494986ce6e23aadfa18cd426a1d7ab90629a0814d244c4cd82cc81f", secret.getPasswordHash());
    }
}
