/**
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */
package org.eclipse.hono.auth;

import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.CredentialsConstants;
import org.junit.Test;
import static org.junit.Assert.*;

/**
  * Tests the behaviour of the helper class {@link UsernamePasswordCredentials}.
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

        UsernamePasswordCredentials mqttUsernamePassword = UsernamePasswordCredentials.create(TEST_USER_OTHER_TENANT, TEST_PASSWORD, false);

        assertEquals(mqttUsernamePassword.getType(), CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD);
        assertEquals(mqttUsernamePassword.getTenantId(), TEST_OTHER_TENANT);
        assertEquals(mqttUsernamePassword.getAuthId(), TEST_USER);
        assertEquals(mqttUsernamePassword.getPassword(), TEST_PASSWORD);
    }

    /**
     * Verifies that if no tenantId is present in the username, the created object for multi tenant mode is null.
     */
    @Test
    public void testTenantFromUserMultiTenantWrongUsername() {

        UsernamePasswordCredentials mqttUserNamePassword = UsernamePasswordCredentials.create(TEST_USER, TEST_PASSWORD, false);
        assertNull(mqttUserNamePassword);
    }

    /**
     * Verifies that if username is null, the created object for multi tenant mode is null.
     */
    @Test
    public void testTenantFromUserMultiTenantNullUsername() {

        UsernamePasswordCredentials mqttUserNamePassword = UsernamePasswordCredentials.create(null, TEST_PASSWORD, false);
        assertNull(mqttUserNamePassword);
    }

    /**
     * Verifies that if username does not comply to the structure authId@tenantId, the created object for multi tenant mode is null.
     */
    @Test
    public void testTenantFromUserMultiTenantWrongUsernameStructure() {

        UsernamePasswordCredentials mqttUserNamePassword = UsernamePasswordCredentials.create("user/tenant", TEST_PASSWORD, false);
        assertNull(mqttUserNamePassword);
    }

    /**
     * Verifies that for single tenant mode, the tenant is automatically set to {@link Constants#DEFAULT_TENANT};
     */
    @Test
    public void testTenantFromUserSingleTenant() {

        UsernamePasswordCredentials mqttUsernamePassword = UsernamePasswordCredentials.create(TEST_USER, TEST_PASSWORD, true);

        assertEquals(mqttUsernamePassword.getType(), CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD);
        assertEquals(mqttUsernamePassword.getTenantId(), Constants.DEFAULT_TENANT);
        assertEquals(mqttUsernamePassword.getAuthId(), TEST_USER);
        assertEquals(mqttUsernamePassword.getPassword(), TEST_PASSWORD);
    }
}
