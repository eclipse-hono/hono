/*******************************************************************************
 * Copyright (c) 2019, 2023 Contributors to the Eclipse Foundation
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import static com.google.common.truth.Truth.assertThat;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.security.auth.x500.X500Principal;

import org.eclipse.hono.util.RegistryManagementConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.SelfSignedCertificate;

/**
 * Verifies {@link CommonCredential} and others.
 */
public class CredentialsTest {

    private static final String NOT_BEFORE_STRING = "2020-08-11T11:38:00Z";
    private static final Instant NOT_BEFORE = Instant.parse(NOT_BEFORE_STRING);
    private static final String NOT_AFTER_STRING = "2031-09-11T11:38:00Z";
    private static final Instant NOT_AFTER = Instant.parse(NOT_AFTER_STRING);
    private static final String SECRET_COMMENT = "secret comment";

    @BeforeEach
    void setDefaultUsernamePattern() {
        PasswordCredential.setAuthIdPattern(RegistryManagementConstants.DEFAULT_PATTERN_USERNAME);
    }

    static Stream<String> illegalPasswordCredentialAuthIds() {
        return Stream.of("$", "#", "%", "!", "(", ")", "?", ",", ";", ":", "@", " ", "");
    }

    private static <T extends CommonSecret> T addCommonProperties(final T secret) {
        secret.setComment(SECRET_COMMENT);
        secret.setNotBefore(NOT_BEFORE);
        secret.setNotAfter(NOT_AFTER);
        return secret;
    }

    private static X509Certificate getCertificate() {
        try {
            final var selfSignedCert = SelfSignedCertificate.create("hono.eclipse.org");
            final var factory = CertificateFactory.getInstance("X.509");
            return (X509Certificate) factory.generateCertificate(new FileInputStream(selfSignedCert.certificatePath()));
        } catch (final CertificateException | FileNotFoundException e) {
            throw new RuntimeException("cannot create certificate", e);
        }
    }

    private void assertCommonSecretProperties(final JsonObject secret) {
        assertThat(secret.getString(RegistryManagementConstants.FIELD_SECRETS_NOT_BEFORE)).isEqualTo(NOT_BEFORE_STRING);
        assertThat(secret.getString(RegistryManagementConstants.FIELD_SECRETS_NOT_AFTER)).isEqualTo(NOT_AFTER_STRING);
    }

    private void assertCommonSecretProperties(final CommonSecret secret) {
        assertThat(secret.getComment()).isEqualTo(SECRET_COMMENT);
        assertThat(secret.getNotBefore()).isEqualTo(NOT_BEFORE);
        assertThat(secret.getNotAfter()).isEqualTo(NOT_AFTER);
    }

    /**
     * Test encoding of a simple password credential.
     */
    @Test
    public void testEncodePasswordCredential() {

        final PasswordSecret secret = new PasswordSecret();
        addCommonProperties(secret);
        secret.setPasswordHash("2a5d81942494986ce6e23aadfa18cd426a1d7ab90629a0814d244c4cd82cc81f");
        secret.setSalt("abc");
        secret.setHashFunction(RegistryManagementConstants.HASH_FUNCTION_SHA256);

        final PasswordCredential credential = new PasswordCredential("foo", List.of(secret));
        credential.setComment("setec astronomy");

        final JsonObject jsonCredential = JsonObject.mapFrom(credential);
        assertNotNull(jsonCredential);
        assertEquals("hashed-password", jsonCredential.getString(RegistryManagementConstants.FIELD_TYPE));
        assertEquals("foo", jsonCredential.getString(RegistryManagementConstants.FIELD_AUTH_ID));
        assertEquals("setec astronomy", jsonCredential.getString(RegistryManagementConstants.FIELD_SECRETS_COMMENT));
        assertEquals(1, jsonCredential.getJsonArray(RegistryManagementConstants.FIELD_SECRETS).size());

        final JsonObject jsonSecret = jsonCredential.getJsonArray(RegistryManagementConstants.FIELD_SECRETS).getJsonObject(0);
        assertCommonSecretProperties(jsonSecret);

        assertThat(jsonSecret.getString(RegistryManagementConstants.FIELD_SECRETS_HASH_FUNCTION))
        .isEqualTo(RegistryManagementConstants.HASH_FUNCTION_SHA256);
        assertEquals("2a5d81942494986ce6e23aadfa18cd426a1d7ab90629a0814d244c4cd82cc81f",
                jsonSecret.getString(RegistryManagementConstants.FIELD_SECRETS_PWD_HASH));
        assertEquals("abc", jsonSecret.getString(RegistryManagementConstants.FIELD_SECRETS_SALT));
    }

    /**
     * Test the decoding of a Json Object to a simple password credential.
     */
    @Test
    public void testDecodePasswordCredentialSucceeds() {

        final JsonObject jsonCredential = new JsonObject()
                .put(RegistryManagementConstants.FIELD_TYPE, RegistryManagementConstants.SECRETS_TYPE_HASHED_PASSWORD)
                .put(RegistryManagementConstants.FIELD_AUTH_ID, "foo_ID-ext.4563=F")
                .put(RegistryManagementConstants.FIELD_ENABLED, true)
                .put(RegistryManagementConstants.FIELD_SECRETS, new JsonArray()
                        .add(new JsonObject()
                                .put(RegistryManagementConstants.FIELD_ENABLED, true)
                                .put(RegistryManagementConstants.FIELD_SECRETS_NOT_BEFORE, NOT_BEFORE_STRING)
                                .put(RegistryManagementConstants.FIELD_SECRETS_NOT_AFTER, NOT_AFTER_STRING)
                                .put(RegistryManagementConstants.FIELD_SECRETS_HASH_FUNCTION, RegistryManagementConstants.HASH_FUNCTION_SHA256)
                                .put(RegistryManagementConstants.FIELD_SECRETS_PWD_HASH,
                                        "2a5d81942494986ce6e23aadfa18cd426a1d7ab90629a0814d244c4cd82cc81f")
                                .put(RegistryManagementConstants.FIELD_SECRETS_SALT, "abc")
                                .put(RegistryManagementConstants.FIELD_SECRETS_COMMENT, SECRET_COMMENT)));

        final PasswordCredential credential = jsonCredential.mapTo(PasswordCredential.class);

        assertNotNull(credential);
        assertEquals("foo_ID-ext.4563=F", credential.getAuthId());
        assertTrue(credential.isEnabled());
        assertEquals(1, credential.getSecrets().size());

        final PasswordSecret secret = credential.getSecrets().get(0);
        assertCommonSecretProperties(secret);
        assertEquals("abc", secret.getSalt());
        assertEquals(RegistryManagementConstants.HASH_FUNCTION_SHA256, secret.getHashFunction());
        assertEquals("2a5d81942494986ce6e23aadfa18cd426a1d7ab90629a0814d244c4cd82cc81f", secret.getPasswordHash());
    }

    /**
     * Test the decoding of a JSON Object to a simple password credential succeeds for authentication identifiers
     * that would be rejected as illegal by default, if an <em>accept all</em> username verification pattern has
     * been set.
     */
    @Test
    public void testDecodePasswordCredentialSucceedsWithLaxAuthIdPattern() {

        PasswordCredential.setAuthIdPattern(Pattern.compile(".*"));
        final JsonObject pwdSecret = new JsonObject()
                .put(RegistryManagementConstants.FIELD_SECRETS_PWD_PLAIN, "the-pwd");

        illegalPasswordCredentialAuthIds().forEach(illegalAuthId -> {
            final JsonObject jsonCredential = new JsonObject()
                    .put(RegistryManagementConstants.FIELD_TYPE, RegistryManagementConstants.SECRETS_TYPE_HASHED_PASSWORD)
                    .put(RegistryManagementConstants.FIELD_AUTH_ID, illegalAuthId)
                    .put(RegistryManagementConstants.FIELD_SECRETS, new JsonArray().add(pwdSecret));
            final PasswordCredential credential = jsonCredential.mapTo(PasswordCredential.class);
            assertThat(credential.getAuthId()).isEqualTo(illegalAuthId);
        });
    }

    /**
     * Test encoding a psk secret.
     */
    @Test
    public void testEncodePskCredential() {

        final byte[] key = new byte[] { 0x00, 0x01 };

        final PskSecret pskSecret = new PskSecret();
        addCommonProperties(pskSecret);
        pskSecret.setKey(key);

        final PskCredential credential = new PskCredential("foo", List.of(pskSecret));

        final JsonObject json = JsonObject.mapFrom(credential);
        assertNotNull(json);
        assertEquals("psk", json.getString(RegistryManagementConstants.FIELD_TYPE));
        assertThat(json.getJsonArray(RegistryManagementConstants.FIELD_SECRETS)).hasSize(1);
        final JsonObject secret = json.getJsonArray(RegistryManagementConstants.FIELD_SECRETS).getJsonObject(0);
        assertCommonSecretProperties(secret);
        assertThat(secret.getBinary(RegistryManagementConstants.FIELD_SECRETS_KEY)).isEqualTo(key);
    }

    /**
     * Verifies that a JSON object can be decoded into a PSK credential.
     */
    @Test
    public void testDecodePskCredential() {

        final byte[] key = new byte[] { 0x00, 0x01 };
        final JsonObject jsonCredential = new JsonObject()
                .put(RegistryManagementConstants.FIELD_TYPE, RegistryManagementConstants.SECRETS_TYPE_PRESHARED_KEY)
                .put(RegistryManagementConstants.FIELD_AUTH_ID, "foo_ID-ext.4563=F")
                .put(RegistryManagementConstants.FIELD_ENABLED, true)
                .put(RegistryManagementConstants.FIELD_SECRETS, new JsonArray()
                        .add(new JsonObject()
                                .put(RegistryManagementConstants.FIELD_ENABLED, true)
                                .put(RegistryManagementConstants.FIELD_SECRETS_NOT_BEFORE, NOT_BEFORE_STRING)
                                .put(RegistryManagementConstants.FIELD_SECRETS_NOT_AFTER, NOT_AFTER_STRING)
                                .put(RegistryManagementConstants.FIELD_SECRETS_KEY, Base64.getEncoder().encodeToString(key))
                                .put(RegistryManagementConstants.FIELD_SECRETS_COMMENT, SECRET_COMMENT)));


        final PskCredential credential = jsonCredential.mapTo(PskCredential.class);

        assertEquals("foo_ID-ext.4563=F", credential.getAuthId());
        assertTrue(credential.isEnabled());
        assertEquals(1, credential.getSecrets().size());

        final PskSecret secret = credential.getSecrets().get(0);
        assertCommonSecretProperties(secret);
        assertThat(secret.getKey()).isEqualTo(key);
    }

    /**
     * Test encoding an X.509 secret.
     */
    @Test
    public void testEncodeX509Credential() {

        final X509CertificateSecret x509Secret = new X509CertificateSecret();
        addCommonProperties(x509Secret);
        var credential = X509CertificateCredential.fromAuthId("CN=foo, O=bar", List.of(x509Secret));

        JsonObject json = JsonObject.mapFrom(credential);
        assertEquals("x509-cert", json.getString(RegistryManagementConstants.FIELD_TYPE));
        assertEquals("CN=foo,O=bar", json.getString(RegistryManagementConstants.FIELD_AUTH_ID));
        JsonObject secret = json.getJsonArray(RegistryManagementConstants.FIELD_SECRETS).getJsonObject(0);
        assertCommonSecretProperties(secret);

        credential = X509CertificateCredential.fromAuthId("emailAddress=hono@eclipse.org,O=Eclipse", List.of(x509Secret));
        json = JsonObject.mapFrom(credential);
        assertEquals("x509-cert", json.getString(RegistryManagementConstants.FIELD_TYPE));
        assertEquals(
                // OID(emailAddress)=(ASN.1 ia5str encoding of the email address)
                "1.2.840.113549.1.9.1=#1610686f6e6f4065636c697073652e6f7267,O=Eclipse",
                json.getString(RegistryManagementConstants.FIELD_AUTH_ID));
        secret = json.getJsonArray(RegistryManagementConstants.FIELD_SECRETS).getJsonObject(0);
        assertCommonSecretProperties(secret);
    }

    /**
     * Verifies that a JSON object containing an auth-id and a secret can be decoded into an X.509 credential.
     */
    @Test
    public void testDecodeX509CredentialFromSubjectDn() {

        final JsonObject jsonCredential = new JsonObject()
                .put(RegistryManagementConstants.FIELD_TYPE, RegistryManagementConstants.SECRETS_TYPE_X509_CERT)
                .put(RegistryManagementConstants.FIELD_AUTH_ID, "cn=thing, C=Germany, o=ACME Inc")
                .put(RegistryManagementConstants.FIELD_ENABLED, true)
                .put(RegistryManagementConstants.FIELD_SECRETS, new JsonArray()
                        .add(new JsonObject()
                                .put(RegistryManagementConstants.FIELD_ENABLED, true)
                                .put(RegistryManagementConstants.FIELD_SECRETS_NOT_BEFORE, NOT_BEFORE_STRING)
                                .put(RegistryManagementConstants.FIELD_SECRETS_NOT_AFTER, NOT_AFTER_STRING)
                                .put(RegistryManagementConstants.FIELD_SECRETS_COMMENT, SECRET_COMMENT)));

        final X509CertificateCredential credential = jsonCredential.mapTo(X509CertificateCredential.class);

        assertEquals("CN=thing,C=Germany,O=ACME Inc", credential.getAuthId());
        assertTrue(credential.isEnabled());
        assertEquals(1, credential.getSecrets().size());

        final X509CertificateSecret secret = credential.getSecrets().get(0);
        assertCommonSecretProperties(secret);
    }

    /**
     * Verifies that a JSON object containing a client certificate can be decoded into an X.509 credential.
     *
     * @throws CertificateEncodingException if the client certificate's DER encoding cannot be created.
     */
    @Test
    public void testDecodeX509CredentialFromClientCertificate() throws CertificateEncodingException {

        final var cert = getCertificate();
        final JsonObject jsonCredential = new JsonObject()
                .put(RegistryManagementConstants.FIELD_TYPE, RegistryManagementConstants.SECRETS_TYPE_X509_CERT)
                .put(RegistryManagementConstants.FIELD_PAYLOAD_CERT, cert.getEncoded())
                .put(RegistryManagementConstants.FIELD_ENABLED, true)
                .put(RegistryManagementConstants.FIELD_SECRETS, new JsonArray()
                        .add(new JsonObject()
                                .put(RegistryManagementConstants.FIELD_ENABLED, true)
                                .put(RegistryManagementConstants.FIELD_SECRETS_NOT_BEFORE, NOT_BEFORE_STRING)
                                .put(RegistryManagementConstants.FIELD_SECRETS_NOT_AFTER, NOT_AFTER_STRING)
                                .put(RegistryManagementConstants.FIELD_SECRETS_COMMENT, SECRET_COMMENT)));

        final var credential = jsonCredential.mapTo(X509CertificateCredential.class);

        assertThat(credential.getAuthId()).isEqualTo(cert.getSubjectX500Principal().getName(X500Principal.RFC2253));
        assertThat(credential.isEnabled()).isTrue();
        assertThat(credential.getSecrets()).hasSize(1);

        final X509CertificateSecret secret = credential.getSecrets().get(0);
        assertThat(secret.getNotBefore()).isEqualTo(cert.getNotBefore().toInstant());
        assertThat(secret.getNotAfter()).isEqualTo(cert.getNotAfter().toInstant());
    }

    /**
     * Test encoding a generic secret.
     */
    @Test
    public void testEncodeGenericCredential() {

        final GenericSecret genericSecret = new GenericSecret();
        addCommonProperties(genericSecret);
        final GenericCredential credential = new GenericCredential("custom-type", "foo", List.of(genericSecret));

        final JsonObject json = JsonObject.mapFrom(credential);
        assertThat(json.getString(RegistryManagementConstants.FIELD_TYPE)).isEqualTo("custom-type");
        final JsonObject secret = json.getJsonArray(RegistryManagementConstants.FIELD_SECRETS).getJsonObject(0);
        assertCommonSecretProperties(secret);
        final CommonCredential decodedCredential = json.mapTo(CommonCredential.class);
        assertThat(decodedCredential).isInstanceOf(GenericCredential.class);

    }

    /**
     * Verifies that a JSON object can be decoded into a Generic credential.
     */
    @Test
    public void testDecodeGenericCredential() {

        final JsonObject jsonCredential = new JsonObject()
                .put(RegistryManagementConstants.FIELD_TYPE, "custom-type")
                .put(RegistryManagementConstants.FIELD_AUTH_ID, "foo_ID-ext.4563=F")
                .put(RegistryManagementConstants.FIELD_ENABLED, true)
                .put(RegistryManagementConstants.FIELD_SECRETS, new JsonArray()
                        .add(new JsonObject()
                                .put(RegistryManagementConstants.FIELD_ENABLED, true)
                                .put(RegistryManagementConstants.FIELD_SECRETS_NOT_BEFORE, NOT_BEFORE_STRING)
                                .put(RegistryManagementConstants.FIELD_SECRETS_NOT_AFTER, NOT_AFTER_STRING)
                                .put(RegistryManagementConstants.FIELD_SECRETS_COMMENT, SECRET_COMMENT)));

        final GenericCredential credential = jsonCredential.mapTo(GenericCredential.class);

        assertThat(credential.getType()).isEqualTo("custom-type");
        assertEquals("foo_ID-ext.4563=F", credential.getAuthId());
        assertTrue(credential.isEnabled());
        assertEquals(1, credential.getSecrets().size());

        final GenericSecret secret = credential.getSecrets().get(0);
        assertCommonSecretProperties(secret);
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

        final PskSecret secret = new PskSecret();
        secret.setKey("foo".getBytes(StandardCharsets.UTF_8));
        final PskCredential credential = new PskCredential("device", List.of(secret));
        credentials.add(credential);
        final GenericCredential genCred = new GenericCredential("custom", "device", List.of(new GenericSecret()));
        credentials.add(genCred);

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
     * Decode credentials with unknown property fails.
     */
    @Test
    public void testDecodeFailsForUnknownProperties() {
        assertThrows(DecodeException.class, () -> Json.decodeValue(
                "{\"type\": \"psk\", \"auth-id\": \"device1\", \"unexpected\": \"property\"}",
                CommonCredential.class));
        assertThrows(DecodeException.class, () -> Json.decodeValue(
                "{\"type\": \"hashed-password\", \"auth-id\": \"device1\", \"unexpected\": \"property\"}",
                CommonCredential.class));
        assertThrows(DecodeException.class, () -> Json.decodeValue(
                "{\"type\": \"x509-cert\", \"auth-id\": \"CN=foo\", \"unexpected\": \"property\"}",
                CommonCredential.class));
    }

    /**
     * Verifies that the decoding of a JSON object representing credentials fails
     * if it does not contain a secrets property or if the property contains an empty array.
     */
    @Test
    public void testDecodeCredentialsFailsForMissingSecrets() {

        final JsonObject pwdCreds = new JsonObject()
                .put(RegistryManagementConstants.FIELD_TYPE, RegistryManagementConstants.SECRETS_TYPE_HASHED_PASSWORD)
                .put(RegistryManagementConstants.FIELD_AUTH_ID, "foo_ID-ext.4563=F")
                .put(RegistryManagementConstants.FIELD_ENABLED, true);

        assertThrows(IllegalArgumentException.class, () -> pwdCreds.mapTo(PasswordCredential.class));

        pwdCreds.put(RegistryManagementConstants.FIELD_SECRETS, new JsonArray());

        assertThrows(IllegalArgumentException.class, () -> pwdCreds.mapTo(PasswordCredential.class));

        final JsonObject pskCreds = new JsonObject()
                .put(RegistryManagementConstants.FIELD_TYPE, RegistryManagementConstants.SECRETS_TYPE_PRESHARED_KEY)
                .put(RegistryManagementConstants.FIELD_AUTH_ID, "foo_ID-ext.4563=F")
                .put(RegistryManagementConstants.FIELD_ENABLED, true);

        assertThrows(IllegalArgumentException.class, () -> pskCreds.mapTo(PasswordCredential.class));

        pskCreds.put(RegistryManagementConstants.FIELD_SECRETS, new JsonArray());

        assertThrows(IllegalArgumentException.class, () -> pskCreds.mapTo(PasswordCredential.class));

        final JsonObject x509Creds = new JsonObject()
                .put(RegistryManagementConstants.FIELD_TYPE, RegistryManagementConstants.SECRETS_TYPE_X509_CERT)
                .put(RegistryManagementConstants.FIELD_AUTH_ID, "CN=Acme")
                .put(RegistryManagementConstants.FIELD_ENABLED, true);

        assertThrows(IllegalArgumentException.class, () -> x509Creds.mapTo(PasswordCredential.class));

        x509Creds.put(RegistryManagementConstants.FIELD_SECRETS, new JsonArray());

        assertThrows(IllegalArgumentException.class, () -> x509Creds.mapTo(PasswordCredential.class));

        final JsonObject genericCreds = new JsonObject()
                .put(RegistryManagementConstants.FIELD_TYPE, "custom")
                .put(RegistryManagementConstants.FIELD_AUTH_ID, "foo_ID-ext.4563=F")
                .put(RegistryManagementConstants.FIELD_ENABLED, true);

        assertThrows(IllegalArgumentException.class, () -> genericCreds.mapTo(GenericCredential.class));

        genericCreds.put(RegistryManagementConstants.FIELD_SECRETS, new JsonArray());

        assertThrows(IllegalArgumentException.class, () -> genericCreds.mapTo(GenericCredential.class));

    }

    /**
     * Test to ensure we can decode what we encoded.
     */
    @Test
    public void testEncodeDecode1() {

        final String authId = "abcd-=.";

        // create credentials to encode

        final PasswordSecret secret = new PasswordSecret();
        secret.setPasswordHash("setec astronomy");
        secret.setSalt("abc");
        secret.setNotAfter(NOT_AFTER);

        final PasswordCredential cred = new PasswordCredential(authId, List.of(secret));

        // encode

        final String encode = Json.encode(new CommonCredential[] { cred });

        // now decode

        final CommonCredential[] decode = Json.decodeValue(encode, CommonCredential[].class);

        // and assert
        assertThat(decode[0]).isInstanceOf(PasswordCredential.class);

        final PasswordCredential decodeCredential = (PasswordCredential) decode[0];

        assertThat(decodeCredential.getAuthId()).isEqualTo(authId);
        assertThat(decodeCredential.getSecrets()).hasSize(1);
        final PasswordSecret decodeSecret = decodeCredential.getSecrets().get(0);
        assertThat(decodeSecret.getNotAfter()).isEqualTo(NOT_AFTER);
    }

    /**
     * Verifies that merging other credentials fails if they are of a different
     * type.
     */
    @Test
    public void testMergeFailsForDifferentType() {

        final PasswordCredential pwdCredentials = Credentials.createPasswordCredential("foo", "bar");
        assertThrows(IllegalArgumentException.class,
                () -> pwdCredentials.merge(Credentials.createPSKCredential("acme", "key")));
    }

    /**
     * Verifies that merging other credentials fails if they do not contain
     * secrets of matching IDs.
     */
    @Test
    public void testMergeFailsForNonMatchingSecretId() {

        final PasswordSecret existingSecret = spy(PasswordSecret.class);
        existingSecret.setId("two");
        final PasswordCredential existingCredentials = new PasswordCredential("foo", List.of(existingSecret));

        final PasswordSecret newSecret = spy(PasswordSecret.class);
        newSecret.setId("one");
        final PasswordCredential newCredentials = new PasswordCredential("foo", List.of(newSecret));

        assertThrows(IllegalArgumentException.class, () -> newCredentials.merge(existingCredentials));
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
        existingSecret.setKey("secret".getBytes(StandardCharsets.UTF_8));
        final PskCredential existingCredentials = new PskCredential("foo", List.of(existingSecret));

        final PskSecret updatedSecret = spy(PskSecret.class);
        updatedSecret.setId("one");
        updatedSecret.setKey("updated-secret".getBytes(StandardCharsets.UTF_8));
        final PskSecret newSecret = spy(PskSecret.class);
        newSecret.setKey("new-secret".getBytes(StandardCharsets.UTF_8));
        final PskCredential updatedCredentials = new PskCredential("foo", List.of(updatedSecret, newSecret));

        updatedCredentials.merge(existingCredentials);
        verify(updatedSecret).merge(existingSecret);
        verify(existingSecret, never()).merge(any(CommonSecret.class));
        verify(newSecret, never()).merge(any(CommonSecret.class));
        assertThat(updatedCredentials.getSecrets()).hasSize(2);
    }

    /**
     * Verifies that PasswordCredentials and X.509CertificateCredentials require the authentication
     * identifier to match type specific patterns.
     */
    @Test
    public void testInstantiationFailsForIllegalAuthId() {

        illegalPasswordCredentialAuthIds().forEach(authId -> {
            assertThrows(IllegalArgumentException.class,
                    () -> new PasswordCredential(authId, List.of(new PasswordSecret())));
        });

        assertThrows(IllegalArgumentException.class,
                () -> X509CertificateCredential.fromAuthId("", List.of(new X509CertificateSecret())));
    }

    /**
     * Verifies that decoding of a JSON object to a credential object
     * fails if the authentication identifier does not match the auth ID regex.
     */
    @Test
    public void testDecodeFailsForIllegalAuthId() {

        final JsonObject pwdSecret = new JsonObject()
                .put(RegistryManagementConstants.FIELD_SECRETS_PWD_PLAIN, "the-pwd");
        illegalPasswordCredentialAuthIds().forEach(illegalAuthId -> {
            final JsonObject jsonCredential = new JsonObject()
                    .put(RegistryManagementConstants.FIELD_TYPE, RegistryManagementConstants.SECRETS_TYPE_HASHED_PASSWORD)
                    .put(RegistryManagementConstants.FIELD_AUTH_ID, illegalAuthId)
                    .put(RegistryManagementConstants.FIELD_SECRETS, new JsonArray().add(pwdSecret));
            assertThrows(IllegalArgumentException.class, () -> jsonCredential.mapTo(PasswordCredential.class));
        });
        final JsonObject jsonCredential = new JsonObject()
                .put(RegistryManagementConstants.FIELD_TYPE, RegistryManagementConstants.SECRETS_TYPE_X509_CERT)
                .put(RegistryManagementConstants.FIELD_AUTH_ID, "")
                .put(RegistryManagementConstants.FIELD_SECRETS, new JsonArray().add(new JsonObject()));
        assertThrows(IllegalArgumentException.class, () -> jsonCredential.mapTo(X509CertificateCredential.class));
    }

    /**
     * Verifies that decoding of a JSON object to a credentials object
     * fails if the JSON does not contain a type property.
     */
    @Test
    public void testDecodeCredentialFailsForMissingType() {
        final JsonObject jsonCredential = new JsonObject()
                .put(RegistryManagementConstants.FIELD_AUTH_ID, "device1")
                .put(RegistryManagementConstants.FIELD_SECRETS, new JsonArray().add(new JsonObject()));
        assertThrows(IllegalArgumentException.class, () -> jsonCredential.mapTo(CommonCredential.class));
    }
}
