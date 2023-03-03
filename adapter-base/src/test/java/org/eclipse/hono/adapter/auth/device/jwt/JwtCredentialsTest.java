/**
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.hono.adapter.auth.device.jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.eclipse.hono.util.CredentialsConstants;
import org.junit.jupiter.api.Test;

/**
 * Tests verifying behavior of {@link JwtCredentials}.
 *
 */
class JwtCredentialsTest {

    private final String authId = "authId";
    private final String jwt = "jwt";

    /**
     * Verifies that create returns a valid JwtCredentials object, when valid input parameter are provided.
     */
    @Test
    void TestCreateValidParameter() {
        final String tenantId = "tenantId";

        final JwtCredentials jwtCredentials = JwtCredentials.create(tenantId, authId, jwt);
        assertNotNull(jwtCredentials);
        assertEquals(tenantId, jwtCredentials.getTenantId());
        assertEquals(authId, jwtCredentials.getAuthId());
        assertEquals(jwt, jwtCredentials.getJws());
        assertEquals(CredentialsConstants.SECRETS_TYPE_RAW_PUBLIC_KEY, jwtCredentials.getType());
    }

    /**
     * Verifies that create throws a {@link NullPointerException}, when one or more of the provided input parameter are
     * null.
     */
    @Test
    void TestCreateParameterNull() {
        assertThrows(NullPointerException.class, () -> JwtCredentials.create(null, authId, jwt));
    }
}
