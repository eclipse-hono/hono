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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

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
                credential.setAuthId("psk-device");
                credential.setSecrets(List.of(new PskSecret().setKey(new byte[] { 0x00, 0x01 })));

        final JsonObject json = JsonObject.mapFrom(credential);
        System.out.println(json);
        assertNotNull(json);
        assertEquals("psk", json.getString(RegistryManagementConstants.FIELD_TYPE));
        assertThat(json.getJsonArray(RegistryManagementConstants.FIELD_SECRETS)).hasSize(1);

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
     * Verifies that merging other credentials fails if they are of a different
     * type.
     */
    @Test
    public void testMergeFailsForDifferentType() {

        final PasswordCredential pwdCredentials = new PasswordCredential();
        assertThatThrownBy(() -> pwdCredentials.merge(new PskCredential()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Verifies that merging other credentials fails if they do not contain
     * secrets of matching IDs.
     */
    @Test
    public void testMergeFailsForNonMatchingSecretId() {

        final PasswordSecret existingSecret = spy(PasswordSecret.class);
        existingSecret.setId("two");
        final PasswordCredential existingCredentials = new PasswordCredential();
        existingCredentials.setSecrets(List.of(existingSecret));

        final PasswordSecret newSecret = spy(PasswordSecret.class);
        newSecret.setId("one");
        final PasswordCredential newCredentials = new PasswordCredential();
        newCredentials.setSecrets(List.of(newSecret));

        assertThatThrownBy(() -> newCredentials.merge(existingCredentials))
            .isInstanceOf(IllegalArgumentException.class);
        verify(existingSecret, never()).merge(any(PasswordSecret.class));
        verify(newSecret, never()).merge(any(PasswordSecret.class));
    }

    /**
     * Verifies that merging other credentials succeeds if they contain
     * secrets of matching IDs.
     */
    @Test
    public void testMergeSucceedsForMatchingSecretIds() {

        final PskSecret existingSecret = spy(PskSecret.class);
        existingSecret.setId("one");
        final PskCredential existingCredentials = new PskCredential();
        existingCredentials.setSecrets(List.of(existingSecret));

        final PskSecret updatedSecret = spy(PskSecret.class);
        updatedSecret.setId("one");
        final PskSecret newSecret = spy(PskSecret.class);
        final PskCredential updatedCredentials = new PskCredential();
        updatedCredentials.setSecrets(List.of(updatedSecret, newSecret));

        updatedCredentials.merge(existingCredentials);
        verify(updatedSecret).merge(existingSecret);
        verify(existingSecret, never()).merge(any(CommonSecret.class));
        verify(newSecret, never()).merge(any(CommonSecret.class));
        assertThat(updatedCredentials.getSecrets()).hasSize(2);
    }
}
