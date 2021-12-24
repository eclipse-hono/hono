/**
 * Copyright (c) 2020, 2021 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */


package org.eclipse.hono.service.management.credentials;

import static org.junit.jupiter.api.Assertions.assertThrows;

import static com.google.common.truth.Truth.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import org.eclipse.hono.client.ClientErrorException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


/**
 * A CredentialsDtoTest.
 *
 */
class CredentialsDtoTest {

    private PskSecret existingSecret;
    private PskCredential existingCred;

    /**
     * Sets up the fixture.
     */
    @BeforeEach
    void setUp() {
        existingSecret = new PskSecret();
        existingSecret.setId("abc");
        existingSecret.setKey("shared-key".getBytes(StandardCharsets.UTF_8));
        existingCred = new PskCredential("psk-id", List.of(existingSecret));
    }

    @Test
    void testConstructorDetectsDuplicateSecretId() {
        final PskSecret dup = new PskSecret();
        dup.setId(existingSecret.getId());

        existingCred.setSecrets(List.of(existingSecret, dup));

        assertThrows(ClientErrorException.class,
                () -> CredentialsDto.forCreation(CredentialsDto::new, List.of(existingCred), "1"));
    }

    @Test
    void testNewSecretWithoutIdDoesNotRequireMerging() {

        final PskCredential updatedCred = Credentials.createPSKCredential("psk-id", "other-key");

        final CredentialsDto updatedDto = CredentialsDto.forUpdate(CredentialsDto::new, List.of(updatedCred), "1");
        assertThat(updatedDto.requiresMerging()).isFalse();
    }

    @Test
    void testMergeRejectsUnknownSecretId() {


        final CredentialsDto existingDto = CredentialsDto.forRead(CredentialsDto::new, List.of(existingCred), Instant.now(), Instant.now(), "1");

        final PskSecret updatedSecret = new PskSecret();
        updatedSecret.setId("def");
        updatedSecret.setKey("irrelevant".getBytes(StandardCharsets.UTF_8));
        final PskCredential updatedCred = new PskCredential("psk-id", List.of(existingSecret, updatedSecret));

        final CredentialsDto updatedDto = CredentialsDto.forUpdate(CredentialsDto::new, List.of(updatedCred), "1");
        assertThat(updatedDto.requiresMerging()).isTrue();
        assertThrows(IllegalArgumentException.class, () -> updatedDto.merge(existingDto));
    }

    /**
     * Verifies that existing credentials with a secret are merged into updated credentials
     * that contain a secret with the same ID as the existing secret and an additional secret
     * without an ID.
     */
    @Test
    void testMergeSucceedsForAdditionalSecretWithNoId() {

        final CredentialsDto existingDto = CredentialsDto.forRead(CredentialsDto::new, List.of(existingCred), Instant.now(), Instant.now(), "1");

        final PskSecret unchangedSecret = new PskSecret();
        unchangedSecret.setId(existingSecret.getId());

        final PskSecret newSecret = new PskSecret();
        newSecret.setKey("irrelevant".getBytes(StandardCharsets.UTF_8));

        final PskCredential updatedCred = new PskCredential("psk-id", List.of(unchangedSecret, newSecret));

        final CredentialsDto updatedDto = CredentialsDto.forUpdate(CredentialsDto::new, List.of(updatedCred), "1");
        assertThat(updatedDto.requiresMerging()).isTrue();
        updatedDto.merge(existingDto);
        final PskSecret secret = updatedDto.getCredentials().get(0).getSecrets()
                .stream()
                .filter(s -> s.getId().equals(existingSecret.getId()))
                .map(PskSecret.class::cast)
                .findAny()
                .orElse(null);
        assertThat(secret).isNotNull();
        assertThat(secret.getKey()).isEqualTo(existingSecret.getKey());
    }
}
