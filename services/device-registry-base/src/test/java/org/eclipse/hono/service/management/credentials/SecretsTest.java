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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.spy;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Verifies {@link CommonSecret} and others.
 */
public class SecretsTest {

    private <T extends CommonSecret> T addCommonProperties(final T secret) {

        final LocalDateTime before = LocalDateTime.of(2017, 05, 01, 14, 00, 00);
        final LocalDateTime after = LocalDateTime.of(2018, 01, 01, 00, 00, 00);

        secret.setComment("a comment");
        secret.setId("1234");
        secret.setNotBefore(before.toInstant(ZoneOffset.of("+01:00")));
        secret.setNotAfter(after.toInstant(ZoneOffset.UTC));
        return secret;
    }

    private void assertCommonProperties(final JsonObject json) {
        assertThat(json.getString(RegistryManagementConstants.FIELD_ID), is("1234"));
        assertThat(json.getString(RegistryManagementConstants.FIELD_COMMENT), is("a comment"));
        assertThat(json.getString(RegistryManagementConstants.FIELD_SECRETS_NOT_BEFORE), is("2017-05-01T13:00:00Z"));
        assertThat(json.getString(RegistryManagementConstants.FIELD_SECRETS_NOT_AFTER), is("2018-01-01T00:00:00Z"));
    }

    /**
     * Test encoding of a simple password secret.
     */
    @Test
    public void testEncodePasswordSecret1() {

        final PasswordSecret secret = new PasswordSecret();
        addCommonProperties(secret);

        secret.setHashFunction(CredentialsConstants.HASH_FUNCTION_SHA256);
        secret.setPasswordHash("2a5d81942494986ce6e23aadfa18cd426a1d7ab90629a0814d244c4cd82cc81f");
        secret.setSalt("abc");

        final JsonObject json = JsonObject.mapFrom(secret);
        assertCommonProperties(json);
        assertEquals(CredentialsConstants.HASH_FUNCTION_SHA256, json.getString(RegistryManagementConstants.FIELD_SECRETS_HASH_FUNCTION));
        assertEquals("abc", json.getString(CredentialsConstants.FIELD_SECRETS_SALT));
        assertEquals("2a5d81942494986ce6e23aadfa18cd426a1d7ab90629a0814d244c4cd82cc81f",
                json.getString(CredentialsConstants.FIELD_SECRETS_PWD_HASH));
    }

    /**
     * Test encoding a psk secret.
     */
    @Test
    public void testEncodePskSecret() {

        final PskSecret secret = new PskSecret();
        addCommonProperties(secret);
        secret.setKey(new byte[] { 1, 2, 3 });

        final JsonObject json = JsonObject.mapFrom(secret);
        assertCommonProperties(json);
        assertArrayEquals(new byte[] { 1, 2, 3 }, json.getBinary(CredentialsConstants.FIELD_SECRETS_KEY));
    }

    /**
     * Test encoding a x509 credential.
     */
    @Test
    public void testEncodeX509Secret() {

        final X509CertificateSecret secret = new X509CertificateSecret();
        addCommonProperties(secret);

        final JsonObject json = JsonObject.mapFrom(secret);
        assertCommonProperties(json);
    }

    /**
     * Test encoding a generic credential.
     */
    @Test
    public void testEncodeGeneric() {

        final GenericSecret secret = new GenericSecret();
        addCommonProperties(secret);
        secret.setAdditionalProperties(Map.of("foo", "bar"));

        final JsonObject json = JsonObject.mapFrom(secret);
        assertCommonProperties(json);
        assertThat(json.getString("foo"), is("bar"));
    }

    /**
     * Test decode an unknown type.
     */
    @Test
    public void testDecodeGeneric() {

        final OffsetDateTime notBefore = OffsetDateTime.of(2019, 4, 5, 13, 45, 07, 0, ZoneOffset.ofHours(-4));
        final OffsetDateTime notAfter = OffsetDateTime.of(2020, 1, 1, 00, 00, 00, 0, ZoneOffset.ofHours(0));

        final CommonCredential credential = new JsonObject()
                .put(RegistryManagementConstants.FIELD_TYPE, "foo")
                .put(RegistryManagementConstants.FIELD_AUTH_ID, "authId1")
                .put(RegistryManagementConstants.FIELD_SECRETS, new JsonArray()
                        .add(new JsonObject()
                                .put(RegistryManagementConstants.FIELD_SECRETS_NOT_BEFORE, "2019-04-05T13:45:07-04:00")
                                .put(RegistryManagementConstants.FIELD_SECRETS_NOT_AFTER, "2020-01-01T00:00:00Z")
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
        assertThat(secret.getNotBefore().atOffset(ZoneOffset.ofHours(-4)), is(notBefore));
        assertThat(secret.getNotAfter().atOffset(ZoneOffset.ofHours(0)), is(notAfter));

        assertThat(credential.getAuthId(), is("authId1"));
        assertThat(((GenericCredential) credential).getType(), is("foo"));

    }

    /**
     * Tests that a secret's notBefore and notAfter instants are properly encoded and decoded.
     * Both formats with timezone offset and without the offset are supported for encoding.
     * For decoding the format without the offset is used.
     */
    @Test
    public void testDateFormats() {

        final JsonObject json = new JsonObject()
                .put(RegistryManagementConstants.FIELD_SECRETS_COMMENT, "test")
                .put(RegistryManagementConstants.FIELD_SECRETS_NOT_BEFORE, "2017-05-01T14:00:00+01:00")
                .put(RegistryManagementConstants.FIELD_SECRETS_NOT_AFTER, "2037-06-01T14:00:00Z");


        final PasswordSecret secret = json.mapTo(PasswordSecret.class);
        final LocalDateTime before = LocalDateTime.of(2017, 05, 01, 14, 00, 00);
        assertEquals(before.toInstant(ZoneOffset.of("+01:00")), secret.getNotBefore());

        final JsonObject decodedSecret = JsonObject.mapFrom(secret);
        assertEquals("2017-05-01T13:00:00Z", decodedSecret.getValue("not-before"));
        assertEquals("2037-06-01T14:00:00Z", decodedSecret.getValue("not-after"));

    }

    /**
     * Verifies that merging a secret of a different type but with same ID fails.
     */
    @Test
    public void testMergeFailsForNonMatchingSecretTypes() {

        final CommonSecret updatedSecret = spy(CommonSecret.class);
        updatedSecret.setId("one");

        final PasswordSecret otherSecret = new PasswordSecret();
        otherSecret.setId("one");

        assertThatThrownBy(() -> updatedSecret.merge(otherSecret)).isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Verifies that merging a secret of same type but with different IDs fails.
     */
    @Test
    public void testMergeFailsForNonMatchingSecretIds() {

        final CommonSecret updatedSecret = spy(CommonSecret.class);
        updatedSecret.setId("one");

        final CommonSecret otherSecret = spy(CommonSecret.class);
        otherSecret.setId("two");

        assertThatThrownBy(() -> updatedSecret.merge(otherSecret)).isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Verifies that the common properties of an updated secret have higher precendence
     * than the corresponding properties of an existing secret that is being merged
     * into the updated secret.
     */
    @Test
    public void testMergeUsesNewCommonProperties() {

        final Instant existingNotBefore = Instant.now().minus(Duration.ofDays(100));
        final Instant existingNotAfter = Instant.now().plus(Duration.ofDays(100));
        final CommonSecret existingSecret = spy(CommonSecret.class);
        existingSecret.setId("one");
        existingSecret.setComment("existing comment");
        existingSecret.setNotBefore(existingNotBefore);
        existingSecret.setNotAfter(existingNotAfter);

        final Instant updatedNotAfter = Instant.now();
        final CommonSecret updatedSecret = spy(CommonSecret.class);
        updatedSecret.setId("one");
        updatedSecret.setNotBefore(existingNotBefore);
        updatedSecret.setNotAfter(updatedNotAfter);

        updatedSecret.merge(existingSecret);
        assertThat(updatedSecret.getComment()).isNull();
        assertThat(updatedSecret.getNotBefore()).isEqualTo(existingNotBefore);
        assertThat(updatedSecret.getNotAfter()).isEqualTo(updatedNotAfter);
    }

    /**
     * Verifies that the password, hash function and salt of an existing secret
     * are not merged into an updated secret if it contains a new (plaintext) password.
     */
    @Test
    public void testMergePropertiesUsesNewPlaintextPassword() {

        final PasswordSecret updatedSecret = new PasswordSecret();
        updatedSecret.setId("one");
        updatedSecret.setPasswordPlain("new-pwd");

        final PasswordSecret existingSecret = new PasswordSecret();
        existingSecret.setId("one");
        existingSecret.setPasswordHash("hash-1");
        existingSecret.setSalt("salt-1");
        existingSecret.setHashFunction(CredentialsConstants.HASH_FUNCTION_SHA512);

        updatedSecret.merge(existingSecret);

        assertThat(updatedSecret.getPasswordPlain()).isEqualTo("new-pwd");
        assertThat(updatedSecret.getPasswordHash()).isNull();
        assertThat(updatedSecret.getHashFunction()).isNull();
        assertThat(updatedSecret.getSalt()).isNull();
    }

    /**
     * Verifies that the password, hash function and salt of an existing secret
     * are not merged into an updated secret if it contains a new (hashed) password.
     */
    @Test
    public void testMergePropertiesUsesNewHashedPassword() {

        final PasswordSecret updatedSecret = new PasswordSecret();
        updatedSecret.setId("one");
        updatedSecret.setPasswordHash("new-hash");
        updatedSecret.setHashFunction(CredentialsConstants.HASH_FUNCTION_BCRYPT);

        final PasswordSecret existingSecret = new PasswordSecret();
        existingSecret.setId("one");
        existingSecret.setPasswordHash("hash-1");
        existingSecret.setSalt("salt-1");
        existingSecret.setHashFunction(CredentialsConstants.HASH_FUNCTION_SHA512);

        updatedSecret.merge(existingSecret);

        assertThat(updatedSecret.getPasswordHash()).isEqualTo("new-hash");
        assertThat(updatedSecret.getPasswordPlain()).isNull();
        assertThat(updatedSecret.getHashFunction()).isEqualTo(CredentialsConstants.HASH_FUNCTION_BCRYPT);
        assertThat(updatedSecret.getSalt()).isNull();
    }

    /**
     * Verifies that the password, hash function and salt of an existing secret
     * are merged into an updated secret if it contains an ID only.
     */
    @Test
    public void testMergePropertiesUsesExistingHashedPassword() {

        final PasswordSecret updatedSecret = new PasswordSecret();
        updatedSecret.setId("one");

        final PasswordSecret existingSecret = new PasswordSecret();
        existingSecret.setId("one");
        existingSecret.setPasswordHash("hash-1");
        existingSecret.setSalt("salt-1");
        existingSecret.setHashFunction(CredentialsConstants.HASH_FUNCTION_SHA512);

        updatedSecret.merge(existingSecret);

        assertThat(updatedSecret.getPasswordHash()).isEqualTo("hash-1");
        assertThat(updatedSecret.getPasswordPlain()).isNull();
        assertThat(updatedSecret.getHashFunction()).isEqualTo(CredentialsConstants.HASH_FUNCTION_SHA512);
        assertThat(updatedSecret.getSalt()).isEqualTo("salt-1");
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
