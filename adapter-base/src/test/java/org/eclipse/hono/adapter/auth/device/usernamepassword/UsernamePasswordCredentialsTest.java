/*******************************************************************************
 * Copyright (c) 2016, 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.adapter.auth.device.usernamepassword;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.eclipse.hono.util.CredentialsConstants;
import org.junit.jupiter.api.Test;

/**
  * Tests verifying behavior of {@link UsernamePasswordCredentials}.
 */
public class UsernamePasswordCredentialsTest {

    private static final String TEST_USER                = "billie";
    private static final String TEST_OTHER_TENANT        = "OTHER_TENANT";
    private static final String TEST_USER_OTHER_TENANT   = TEST_USER + "@" + TEST_OTHER_TENANT;
    private static final String TEST_PASSWORD            = "hono";

    /**
     * Verifies that in multi tenant mode, a username containing userId@tenantId leads to a correctly filled instance.
     */
    @Test
    public void testTenantFromUserMultiTenant() {

        final UsernamePasswordCredentials mqttUsernamePassword = UsernamePasswordCredentials.create(TEST_USER_OTHER_TENANT, TEST_PASSWORD);

        assertEquals(CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, mqttUsernamePassword.getType());
        assertEquals(TEST_OTHER_TENANT, mqttUsernamePassword.getTenantId());
        assertEquals(TEST_USER, mqttUsernamePassword.getAuthId());
        assertEquals(TEST_PASSWORD, mqttUsernamePassword.getPassword());
    }

    /**
     * Verifies that if no tenantId is present in the username, the created object for multi tenant mode is null.
     */
    @Test
    public void testTenantFromUserMultiTenantWrongUsername() {

        final UsernamePasswordCredentials mqttUserNamePassword = UsernamePasswordCredentials.create(TEST_USER, TEST_PASSWORD);
        assertNull(mqttUserNamePassword);
    }

    /**
     * Verifies that if username does not comply to the structure authId@tenantId, the created object for multi tenant mode is null.
     */
    @Test
    public void testTenantFromUserMultiTenantWrongUsernameStructure() {

        final UsernamePasswordCredentials mqttUserNamePassword = UsernamePasswordCredentials.create("user/tenant", TEST_PASSWORD);
        assertNull(mqttUserNamePassword);
    }
}
