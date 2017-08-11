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
package org.eclipse.hono.adapter.mqtt;

import io.vertx.mqtt.MqttAuth;
import io.vertx.mqtt.MqttEndpoint;
import org.eclipse.hono.adapter.mqtt.credentials.MqttUsernamePassword;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.CredentialsConstants;
import org.junit.Test;
import static org.junit.Assert.*;

import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
  * Tests the behaviour of the helper class {@link MqttUsernamePassword}.
 */
@RunWith(MockitoJUnitRunner.class)
public class MqttUsernamePasswordTest {

    public static final String TEST_USER                = "billie";
    public static final String TEST_USER_DEFAULT_TENANT = TEST_USER + "@" + Constants.DEFAULT_TENANT;
    public static final String TEST_OTHER_TENANT        = "OTHER_TENANT";
    public static final String TEST_USER_OTHER_TENANT   = TEST_USER + "@" + TEST_OTHER_TENANT;
    public static final String TEST_PASSWORD            = "hono";

    /**
     * Verifies that in multi tenant mode, a username containing userId@tenantId leads to a correctly filled instance.
     */
    @Test
    public void testTenantFromUserMultiTenant() throws Exception {
        MqttAuth auth = mock(MqttAuth.class);
        when(auth.userName()).thenReturn(TEST_USER_OTHER_TENANT);
        when(auth.password()).thenReturn(TEST_PASSWORD);
        MqttEndpoint mqttEndpoint = mockMqttEndpoint(auth);

        MqttUsernamePassword mqttUsernamePassword = MqttUsernamePassword.create(mqttEndpoint, false);

        assertEquals(mqttUsernamePassword.getType(), CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD);
        assertEquals(mqttUsernamePassword.getTenantId(), TEST_OTHER_TENANT);
        assertEquals(mqttUsernamePassword.getAuthId(), TEST_USER);
        assertEquals(mqttUsernamePassword.getPassword(), TEST_PASSWORD);
    }

    /**
     * Verifies that if no tenantId is present in the username, an IllegalArgumentException is thrown for multi tenant mode.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testTenantFromUserMultiTenantWrongUsername() throws Exception {
        MqttAuth auth = mockMqttAuthWithoutTenantIdInUsername();

        MqttEndpoint mqttEndpoint = mockMqttEndpoint(auth);
        MqttUsernamePassword.create(mqttEndpoint, false);
    }

    /**
     * Verifies that if username is null, an IllegalArgumentException is thrown for multi tenant mode.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testTenantFromUserMultiTenantNullUsername() throws Exception {
        MqttAuth auth = mock(MqttAuth.class);
        when(auth.userName()).thenReturn(null); // null user

        MqttEndpoint mqttEndpoint = mockMqttEndpoint(auth);
        MqttUsernamePassword.create(mqttEndpoint, false);
    }

    /**
     * Verifies that if username does not comply to the structure authId@tenantId, an IllegalArgumentException is thrown for multi tenant mode.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testTenantFromUserMultiTenantWrongUsernameStructure() throws Exception {
        MqttAuth auth = mockMqttAuthWithoutTenantIdInUsername();

        MqttEndpoint mqttEndpoint = mockMqttEndpoint(auth);
        MqttUsernamePassword.create(mqttEndpoint, false);
    }

    /**
     * Verifies that for single tenant mode, the tenant is automatically set to {@link Constants#DEFAULT_TENANT};
     */
    @Test
    public void testTenantFromUserSingleTenant() throws Exception {
        MqttAuth auth = mockMqttAuthWithoutTenantIdInUsername();
        MqttEndpoint mqttEndpoint = mockMqttEndpoint(auth);

        MqttUsernamePassword mqttUsernamePassword = MqttUsernamePassword.create(mqttEndpoint, true);

        assertEquals(mqttUsernamePassword.getType(), CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD);
        assertEquals(mqttUsernamePassword.getTenantId(), Constants.DEFAULT_TENANT);
        assertEquals(mqttUsernamePassword.getAuthId(), TEST_USER);
        assertEquals(mqttUsernamePassword.getPassword(), TEST_PASSWORD);
    }

    private MqttAuth mockMqttAuthWithoutTenantIdInUsername() {
        MqttAuth auth = mock(MqttAuth.class);
        when(auth.userName()).thenReturn(TEST_USER); // no tenant in user
        when(auth.password()).thenReturn(TEST_PASSWORD);
        return auth;
    }

    private MqttEndpoint mockMqttEndpoint(MqttAuth auth) {
        MqttEndpoint mqttEndpoint = mock(MqttEndpoint.class);
        when(mqttEndpoint.auth()).thenReturn(auth);
        return mqttEndpoint;
    }

}
