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


import static com.google.common.truth.Truth.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import org.eclipse.hono.util.RegistryManagementConstants;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Verifies behavior of {@link GenericSecret}.
 */
public class GenericSecretTest {

    /**
     * Test encoding a generic credential.
     */
    @Test
    public void testEncodeGeneric() {

        final GenericSecret secret = new GenericSecret();
        CommonSecretTest.addCommonProperties(secret);
        secret.setAdditionalProperties(Map.of("foo", "bar"));

        final JsonObject json = JsonObject.mapFrom(secret);
        CommonSecretTest.assertCommonProperties(json);
        assertThat(json.getString("foo")).isEqualTo("bar");
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

        assertThat(credential).isNotNull();
        assertThat(credential).isInstanceOf(GenericCredential.class);

        assertThat(credential.getSecrets()).isNotNull();
        assertThat(credential.getSecrets()).hasSize(1);
        assertThat(credential.getSecrets().get(0)).isInstanceOf(GenericSecret.class);

        final var secret = (GenericSecret) credential.getSecrets().get(0);
        assertThat(secret.getAdditionalProperties()).isNotNull();
        assertThat(secret.getAdditionalProperties()).hasSize(1);
        assertThat(secret.getAdditionalProperties()).containsEntry("quote", "setec astronomy");
        assertThat(secret.getNotBefore().atOffset(ZoneOffset.ofHours(-4))).isEqualTo(notBefore);
        assertThat(secret.getNotAfter().atOffset(ZoneOffset.ofHours(0))).isEqualTo(notAfter);

        assertThat(credential.getAuthId()).isEqualTo("authId1");
        assertThat(((GenericCredential) credential).getType()).isEqualTo("foo");

    }
}
