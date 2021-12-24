/*******************************************************************************
 * Copyright (c) 2019, 2021 Contributors to the Eclipse Foundation
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


import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.spy;

import static com.google.common.truth.Truth.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.eclipse.hono.util.RegistryManagementConstants;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;

/**
 * Verifies behavior of {@link CommonSecret}.
 */
public class CommonSecretTest {

    /**
     * Adds common properties to a secret.
     * <p>
     * In particular, adds
     * <ul>
     * <li>an {@value RegistryManagementConstants#FIELD_ID} property with value {@code 1234}</li>
     * <li>a {@value RegistryManagementConstants#FIELD_COMMENT} property with value {@code a comment}</li>
     * <li>a {@value RegistryManagementConstants#FIELD_SECRETS_NOT_BEFORE} property with value {@code 2017-05-01T14:00:00+01:00}</li>
     * <li>a {@value RegistryManagementConstants#FIELD_SECRETS_NOT_AFTER} property with value {@code 2018-01-01T00:00:00Z}</li>
     * </ul>
     *
     * @param <T> The type of secret.
     * @param secret The secret to add the properties to.
     * @return The secret.
     */
    protected static <T extends CommonSecret> T addCommonProperties(final T secret) {

        final LocalDateTime before = LocalDateTime.of(2017, 05, 01, 14, 00, 00);
        final LocalDateTime after = LocalDateTime.of(2018, 01, 01, 00, 00, 00);

        secret.setComment("a comment");
        secret.setId("1234");
        secret.setNotBefore(before.toInstant(ZoneOffset.of("+01:00")));
        secret.setNotAfter(after.toInstant(ZoneOffset.UTC));
        return secret;
    }

    /**
     * Asserts that a JSON document contains common secret properties.
     *
     * @param json The document to check.
     * @throws AssertionError if the document does not contain all of the properties added as part
     *                        of {@link #addCommonProperties(CommonSecret)}.
     */
    protected static void assertCommonProperties(final JsonObject json) {
        assertThat(json.getString(RegistryManagementConstants.FIELD_ID)).isEqualTo("1234");
        assertThat(json.getString(RegistryManagementConstants.FIELD_COMMENT)).isEqualTo("a comment");
        assertThat(json.getString(RegistryManagementConstants.FIELD_SECRETS_NOT_BEFORE)).isEqualTo("2017-05-01T13:00:00Z");
        assertThat(json.getString(RegistryManagementConstants.FIELD_SECRETS_NOT_AFTER)).isEqualTo("2018-01-01T00:00:00Z");
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
        assertThat(before.toInstant(ZoneOffset.of("+01:00"))).isEqualTo(secret.getNotBefore());

        final JsonObject decodedSecret = JsonObject.mapFrom(secret);
        assertThat(decodedSecret.getValue("not-before")).isEqualTo("2017-05-01T13:00:00Z");
        assertThat(decodedSecret.getValue("not-after")).isEqualTo("2037-06-01T14:00:00Z");
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

        assertThrows(IllegalArgumentException.class, () -> updatedSecret.merge(otherSecret));
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

        assertThrows(IllegalArgumentException.class, () -> updatedSecret.merge(otherSecret));
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
}
