/*******************************************************************************
 * Copyright (c) 2019, 2020 Contributors to the Eclipse Foundation
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import org.eclipse.hono.deviceregistry.util.DeviceRegistryUtils;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

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

        secret.setNotBefore(Instant.EPOCH.truncatedTo(ChronoUnit.SECONDS));
        secret.setNotAfter(Instant.EPOCH.plusSeconds(1).truncatedTo(ChronoUnit.SECONDS));

        secret.setPasswordHash("2a5d81942494986ce6e23aadfa18cd426a1d7ab90629a0814d244c4cd82cc81f");
        secret.setSalt("abc");

        secret.setHashFunction(RegistryManagementConstants.HASH_FUNCTION_SHA256);

        final PasswordCredential credential = new PasswordCredential();
        credential.setAuthId("foo");
        credential.setComment("setec astronomy");
        credential.setSecrets(Collections.singletonList(secret));

        final JsonObject jsonCredential = JsonObject.mapFrom(credential);
        assertNotNull(jsonCredential);
        assertEquals("hashed-password", jsonCredential.getString(RegistryManagementConstants.FIELD_TYPE));
        assertEquals(1, jsonCredential.getJsonArray(RegistryManagementConstants.FIELD_SECRETS).size());

        final JsonObject jsonSecret = jsonCredential.getJsonArray(RegistryManagementConstants.FIELD_SECRETS).getJsonObject(0);

        assertEquals("foo", jsonCredential.getString(RegistryManagementConstants.FIELD_AUTH_ID));
        assertEquals("setec astronomy", jsonCredential.getString(RegistryManagementConstants.FIELD_SECRETS_COMMENT));

        assertEquals("abc", jsonSecret.getString(RegistryManagementConstants.FIELD_SECRETS_SALT));
        assertEquals("2a5d81942494986ce6e23aadfa18cd426a1d7ab90629a0814d244c4cd82cc81f",
                jsonSecret.getString(RegistryManagementConstants.FIELD_SECRETS_PWD_HASH));
    }

    /**
     * Test encoding a psk secret.
     */
    @Test
    public void testEncodePskCredential() {
        final PskCredential credential = new PskCredential();

        final JsonObject json = JsonObject.mapFrom(credential);
        assertNotNull(json);
        assertNull(json.getJsonArray(RegistryManagementConstants.FIELD_SECRETS));

        assertEquals("psk", json.getString(RegistryManagementConstants.FIELD_TYPE));

        final CommonCredential decodedCredential = json.mapTo(CommonCredential.class);
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
        assertNull(json.getJsonArray(RegistryManagementConstants.FIELD_SECRETS));

        assertEquals("x509-cert", json.getString(RegistryManagementConstants.FIELD_TYPE));
    }

    /**
     * Test encoding a generic secret.
     */
    @Test
    public void testEncodeGenericCredential() {
        final GenericCredential credential = new GenericCredential();
        credential.setType("custom");

        final JsonObject json = JsonObject.mapFrom(credential);
        assertThat(json).isNotNull();
        assertThat(json.getJsonArray(RegistryManagementConstants.FIELD_SECRETS)).isNull();

        assertThat(json.getString(RegistryManagementConstants.FIELD_TYPE)).isEqualTo("custom");
        final CommonCredential decodedCredential = json.mapTo(CommonCredential.class);
        assertThat(decodedCredential).isInstanceOf(GenericCredential.class);

    }

    /**
     * Test encoding an array of secrets.
     */
    @Test
    public void testEncodeArray() {
        testEncodeMany(credentials -> credentials.toArray(CommonCredential[]::new));
    }

    /**
     * Test encoding an array of secrets.
     *
     * @param provider credentials provider.
     */
    protected void testEncodeMany(final Function<List<CommonCredential>, Object> provider) {
        final List<CommonCredential> credentials = new ArrayList<>();

        final PskCredential credential = new PskCredential();
        credential.setAuthId(RegistryManagementConstants.FIELD_AUTH_ID);
        final PskSecret secret = new PskSecret();
        secret.setKey("foo".getBytes(StandardCharsets.UTF_8));
        credential.setSecrets(Collections.singletonList(secret));
        credentials.add(credential);

        final String json = Json.encode(provider.apply(credentials));

        final JsonArray array = new JsonArray(json);
        for (final Object o : array) {
            assertTrue(o instanceof JsonObject);
            assertNotNull(((JsonObject) o).getString(RegistryManagementConstants.FIELD_TYPE));
            assertNotNull(((JsonObject) o).getString(RegistryManagementConstants.FIELD_AUTH_ID));
            assertNotNull(((JsonObject) o).getJsonArray(RegistryManagementConstants.FIELD_SECRETS));
        }
    }

    /**
     * Test the decoding of a Json Object to a simple password credential.
     */
    @Test
    public void testDecodePasswordCredential() {

        final JsonObject jsonCredential = new JsonObject()
                .put(RegistryManagementConstants.FIELD_TYPE, RegistryManagementConstants.SECRETS_TYPE_HASHED_PASSWORD)
                .put(RegistryManagementConstants.FIELD_AUTH_ID, "foo")
                .put(RegistryManagementConstants.FIELD_ENABLED, true)
                .put(RegistryManagementConstants.FIELD_SECRETS, new JsonArray()
                        .add(new JsonObject()
                                .put(RegistryManagementConstants.FIELD_ENABLED, true)
                                .put(RegistryManagementConstants.FIELD_SECRETS_NOT_BEFORE,
                                        Instant.EPOCH.truncatedTo(ChronoUnit.SECONDS))
                                .put(RegistryManagementConstants.FIELD_SECRETS_NOT_AFTER,
                                        Instant.EPOCH.plusSeconds(1).truncatedTo(ChronoUnit.SECONDS))
                                .put(RegistryManagementConstants.FIELD_SECRETS_HASH_FUNCTION, RegistryManagementConstants.HASH_FUNCTION_SHA256)
                                .put(RegistryManagementConstants.FIELD_SECRETS_PWD_HASH,
                                        "2a5d81942494986ce6e23aadfa18cd426a1d7ab90629a0814d244c4cd82cc81f")
                                .put(RegistryManagementConstants.FIELD_SECRETS_SALT, "abc")
                                .put(RegistryManagementConstants.FIELD_SECRETS_COMMENT, "setec astronomy")));

        final PasswordCredential credential = jsonCredential.mapTo(PasswordCredential.class);

        assertNotNull(credential);
        assertEquals("foo", credential.getAuthId());
        assertTrue(credential.isEnabled());
        assertEquals(1, credential.getSecrets().size());

        final PasswordSecret secret = credential.getSecrets().get(0);

        assertEquals("setec astronomy", secret.getComment());
        assertEquals("abc", secret.getSalt());
        assertEquals(RegistryManagementConstants.HASH_FUNCTION_SHA256, secret.getHashFunction());
        assertEquals("2a5d81942494986ce6e23aadfa18cd426a1d7ab90629a0814d244c4cd82cc81f", secret.getPasswordHash());
    }

    /**
     * Test to ensure we can decode what we encoded.
     */
    @Test
    public void testEncodeDecode1() {

        final String authId = "abcd+#:,%\"";
        final Instant notAfter = Instant.parse("1992-09-11T11:38:00.123456Z");

        // create credentials to encode

        final PasswordSecret secret = new PasswordSecret();
        secret.setPasswordHash("setec astronomy");
        secret.setSalt("abc");
        secret.setNotAfter(notAfter);

        final PasswordCredential cred = new PasswordCredential();
        cred.setAuthId(authId);
        cred.setSecrets(Arrays.asList(secret));

        // encode

        final String encode = Json.encode(new CommonCredential[] { cred });

        // Test for the exact format

        assertEquals(
                "[{\"type\":\"hashed-password\",\"secrets\":[{\"not-after\":\"1992-09-11T11:38:00Z\",\"pwd-hash\":\"setec astronomy\",\"salt\":\"abc\"}],\"auth-id\":\"abcd+#:,%\\\"\"}]",
                encode);

        // now decode

        final CommonCredential[] decode = Json.decodeValue(encode, CommonCredential[].class);

        // and assert
        assertThat(decode[0]).isInstanceOf(PasswordCredential.class);

        final PasswordCredential decodeCredential = (PasswordCredential) decode[0];

        assertThat(decodeCredential.getAuthId()).isEqualTo(authId);
        assertThat(decodeCredential.getSecrets()).hasSize(1);
        final PasswordSecret decodeSecret = decodeCredential.getSecrets().get(0);
        assertThat(decodeSecret.getNotAfter()).isEqualTo(Instant.parse("1992-09-11T11:38:00Z"));
    }

    /**
     * Test merging of two password credentials.
     */
    @Test
    public void testMergingOfPasswordCredential() {
        final String authId = "test-auth-1";
        final String secretId = DeviceRegistryUtils.getUniqueIdentifier();
        final Instant date = Instant.parse("2020-09-11T11:38:00.123456Z");

        //Create password credential 1
        final PasswordSecret secret1 = new PasswordSecret();
        secret1.setId(secretId);
        secret1.setPasswordHash("hash-1");
        secret1.setSalt("salt-1");
        secret1.setNotAfter(date);

        final PasswordCredential credential1 = new PasswordCredential();
        credential1.setAuthId(authId);
        credential1.setSecrets(Arrays.asList(secret1));
        credential1.setComment("Comment-1");

        //Create PSK credential 2 with a secret having an id and another without id
        final PasswordSecret secret2 = new PasswordSecret();
        secret2.setId(secretId);

        final PasswordSecret secret3 = new PasswordSecret();
        secret3.setPasswordHash("hash-3");
        secret3.setSalt("salt-3");
        secret3.setNotBefore(date);

        final PasswordCredential credential2 = new PasswordCredential();
        credential2.setAuthId(authId);
        credential2.setSecrets(Arrays.asList(secret2, secret3));
        credential2.setComment("Comment-2");

        //Merge those two credentials
        final CommonCredential mergedCredential = credential2.merge(credential1);

        //verify the result
        assertEquals(2, mergedCredential.getSecrets().size());
        assertEquals("Comment-2", mergedCredential.getComment());
        final PasswordSecret mergedSecret = (PasswordSecret) mergedCredential.getSecrets().get(0);
        assertEquals(secretId, mergedSecret.getId());
        assertEquals("hash-1", mergedSecret.getPasswordHash());
        assertEquals("salt-1", mergedSecret.getSalt());
        assertNull(mergedSecret.getNotAfter());
    }

    /**
     * Test merging of two PSK credentials.
     */
    @Test
    public void testMergingOfPskCredential() {
        final String authId = "test-auth-1";
        final String secretId = DeviceRegistryUtils.getUniqueIdentifier();
        final Instant date = Instant.parse("1992-09-11T11:38:00.123456Z");

        //Create PSK credential 1
        final PskSecret secret1 = new PskSecret();
        secret1.setId(secretId);
        secret1.setKey("key-1".getBytes(StandardCharsets.UTF_8));
        secret1.setNotAfter(date);

        final PskCredential credential1 = new PskCredential();
        credential1.setAuthId(authId);
        credential1.setSecrets(Arrays.asList(secret1));
        credential1.setComment("Comment-1");

        //Create PSK credential 2 with a secret having an id and another without id
        final PskSecret secret2 = new PskSecret();
        secret2.setId(secretId);

        final PskSecret secret3 = new PskSecret();
        secret3.setKey("key-3".getBytes(StandardCharsets.UTF_8));
        secret3.setNotBefore(date);

        final PskCredential credential2 = new PskCredential();
        credential2.setAuthId(authId);
        credential2.setSecrets(Arrays.asList(secret2, secret3));
        credential2.setComment("Comment-2");

        //Merge those two credentials
        final CommonCredential mergedCredential = credential2.merge(credential1);

        //verify the result
        assertEquals(2, mergedCredential.getSecrets().size());
        assertEquals("Comment-2", mergedCredential.getComment());
        final PskSecret mergedSecret = (PskSecret) mergedCredential.getSecrets().get(0);
        assertEquals(secretId, mergedSecret.getId());
        assertEquals("key-1", new String(mergedSecret.getKey()));
        assertNull(mergedSecret.getNotBefore());
    }

    /**
     * Test merging two credentials of different types fails.
     */
    @Test
    public void testMergingCredentialsOfDifferentType() {
        final String authId = "test-auth-1";
        final String secretId = DeviceRegistryUtils.getUniqueIdentifier();
        final Instant date = Instant.parse("2020-09-11T11:38:00.123456Z");

        // Create password credential 1
        final PasswordSecret secret1 = new PasswordSecret();
        secret1.setId(secretId);
        secret1.setPasswordHash("hash-1");
        secret1.setSalt("salt-1");
        secret1.setNotAfter(date);

        final PasswordCredential credential1 = new PasswordCredential();
        credential1.setAuthId(authId);
        credential1.setSecrets(Arrays.asList(secret1));
        credential1.setComment("Comment-1");

        // Create PSK credential 2
        final PskSecret secret2 = new PskSecret();
        secret2.setId(secretId);

        final PskCredential credential2 = new PskCredential();
        credential2.setAuthId(authId);
        credential2.setSecrets(Arrays.asList(secret2));
        credential2.setComment("Comment-2");

        //Merge those two credentials and verify the result
        assertThrows(IllegalArgumentException.class, () -> credential2.merge(credential1));
    }
}
