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

package org.eclipse.hono.client.impl;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.eclipse.hono.client.RegistrationClient;
import org.eclipse.hono.client.RegistrationClientFactory;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.RegistrationConstants;
import org.junit.Before;
import org.junit.Test;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

/**
 * Verifies behavior of {@link GatewayMapperImpl}.
 */
public class GatewayMapperImplTest {

    private GatewayMapperImpl gatewayMapper;
    private RegistrationClient regClient;
    private String tenantId;
    private String deviceId;

    /**
     * Sets up common fixture.
     */
    @Before
    public void setup() {
        tenantId = "testTenant";
        deviceId = "testDevice";
        regClient = mock(RegistrationClient.class);
        final RegistrationClientFactory registrationClientFactory = mock(RegistrationClientFactory.class);
        when(registrationClientFactory.getOrCreateRegistrationClient(anyString()))
                .thenReturn(Future.succeededFuture(regClient));
        gatewayMapper = new GatewayMapperImpl(registrationClientFactory);
    }

    /**
     * Verifies that the <em>getMappedGatewayDevice</em> method returns a Future with the device id as result for a
     * device for which 'via' is not set.
     */
    @Test
    public void testGetMappedGatewayDeviceUsingEmptyDeviceData() {
        // GIVEN deviceData with no 'via'
        final JsonObject deviceData = new JsonObject();
        when(regClient.get(anyString(), any())).thenReturn(Future.succeededFuture(deviceData));

        // WHEN getMappedGatewayDevice() is invoked
        final Future<String> mappedGatewayDeviceFuture = gatewayMapper.getMappedGatewayDevice(tenantId, deviceId, null);

        // THEN the returned Future is complete and contains the deviceId
        assertThat(mappedGatewayDeviceFuture.isComplete(), is(true));
        assertThat(mappedGatewayDeviceFuture.result(), is(deviceId));
    }

    /**
     * Verifies that the <em>getMappedGatewayDevice</em> method returns the correct value for a device for which 'via'
     * and 'last-via' is set.
     */
    @Test
    public void testGetMappedGatewayDeviceUsingDeviceDataWithLastVia() {
        final String deviceViaId = "testDeviceVia";

        // GIVEN deviceData with 'via' and 'last-via'
        final JsonObject deviceData = new JsonObject();
        deviceData.put(RegistrationConstants.FIELD_VIA, deviceViaId);
        final JsonObject lastViaObject = new JsonObject();
        lastViaObject.put(Constants.JSON_FIELD_DEVICE_ID, deviceViaId);
        deviceData.put(RegistrationConstants.FIELD_LAST_VIA, lastViaObject);
        when(regClient.get(anyString(), any())).thenReturn(Future.succeededFuture(deviceData));

        // WHEN getMappedGatewayDevice() is invoked
        final Future<String> mappedGatewayDeviceFuture = gatewayMapper.getMappedGatewayDevice(tenantId, deviceId, null);

        // THEN the returned Future is complete and contains the deviceViaId
        assertThat(mappedGatewayDeviceFuture.isComplete(), is(true));
        assertThat(mappedGatewayDeviceFuture.result(), is(deviceViaId));
    }

    /**
     * Verifies that the <em>getMappedGatewayDevice</em> method returns a Future with {@code null} as result for a
     * device for which 'via' is set but 'last-via' is not set.
     */
    @Test
    public void testGetMappedGatewayDeviceUsingDeviceDataWithoutLastVia() {
        final String deviceViaId = "testDeviceVia";

        // GIVEN deviceData with 'via' but no 'last-via'
        final JsonObject deviceData = new JsonObject();
        deviceData.put(RegistrationConstants.FIELD_VIA, deviceViaId);

        when(regClient.get(anyString(), any())).thenReturn(Future.succeededFuture(deviceData));

        // WHEN getMappedGatewayDevice() is invoked
        final Future<String> mappedGatewayDeviceFuture = gatewayMapper.getMappedGatewayDevice(tenantId, deviceId, null);

        // THEN the returned Future is complete and contains null as result
        assertThat(mappedGatewayDeviceFuture.isComplete(), is(true));
        assertThat(mappedGatewayDeviceFuture.result(), is(nullValue()));
    }

}
