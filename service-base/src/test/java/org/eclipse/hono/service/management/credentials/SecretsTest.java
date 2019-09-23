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
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.RegistryManagementConstants;
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

        secret.setNotBefore(Instant.EPOCH.truncatedTo(ChronoUnit.SECONDS));
        secret.setNotAfter(Instant.EPOCH.plusSeconds(1).truncatedTo(ChronoUnit.SECONDS));

        secret.setComment("setec astronomy");

        secret.setPasswordHash("2a5d81942494986ce6e23aadfa18cd426a1d7ab90629a0814d244c4cd82cc81f");
        secret.setSalt("abc");

        secret.setHashFunction(CredentialsConstants.HASH_FUNCTION_SHA256);

        final JsonObject json = JsonObject.mapFrom(secret);
        assertNotNull(json);

        assertEquals("abc", json.getString(CredentialsConstants.FIELD_SECRETS_SALT));
        assertEquals("2a5d81942494986ce6e23aadfa18cd426a1d7ab90629a0814d244c4cd82cc81f",
                json.getString(CredentialsConstants.FIELD_SECRETS_PWD_HASH));
        assertEquals("setec astronomy", json.getString(RegistryManagementConstants.FIELD_SECRETS_COMMENT));
    }

    /**
     * Test encoding a psk secret.
     */
    @Test
    public void testEncodePskSecret() {

        final Instant notBefore = Instant.from(OffsetDateTime.of(2018, 1, 1, 00, 00, 00, 0, ZoneOffset.ofHours(-4)));
        final Instant notAfter = Instant.from(OffsetDateTime.of(2019, 7, 22, 14, 30, 15, 0, ZoneOffset.ofHours(2)));
        final PskSecret secret = new PskSecret();
        secret.setKey(new byte[] { 1, 2, 3 });
        secret.setNotBefore(notBefore);
        secret.setNotAfter(notAfter);

        final JsonObject json = JsonObject.mapFrom(secret);
        assertNotNull(json);

        assertArrayEquals(new byte[] { 1, 2, 3 }, json.getBinary(CredentialsConstants.FIELD_SECRETS_KEY));
        assertThat(
                DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(json.getString(RegistryManagementConstants.FIELD_SECRETS_NOT_BEFORE), OffsetDateTime::from).toInstant(),
                is(notBefore));
        assertThat(
                DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(json.getString(RegistryManagementConstants.FIELD_SECRETS_NOT_AFTER), OffsetDateTime::from).toInstant(),
                is(notAfter));
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
        assertThat(json.getString(RegistryManagementConstants.FIELD_TYPE), is(CredentialsConstants.SECRETS_TYPE_X509_CERT));
        assertThat(json.getJsonObject(RegistryManagementConstants.FIELD_EXT), nullValue());
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
     * Tests that secret dates are properly encoded and decoded.
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

}
