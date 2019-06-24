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

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.HttpURLConnection;
import java.util.Collections;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.DeviceConnectionClient;
import org.eclipse.hono.client.DeviceConnectionClientFactory;
import org.eclipse.hono.client.RegistrationClient;
import org.eclipse.hono.client.RegistrationClientFactory;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.util.DeviceConnectionConstants;
import org.eclipse.hono.util.RegistrationConstants;
import org.junit.Before;
import org.junit.Test;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Verifies behavior of {@link GatewayMapperImpl}.
 */
public class GatewayMapperImplTest {

    private GatewayMapperImpl gatewayMapper;
    private RegistrationClient regClient;
    private DeviceConnectionClient devConClient;
    private String tenantId;
    private String deviceId;
    private Span span;

    /**
     * Sets up common fixture.
     */
    @Before
    public void setup() {
        final SpanContext spanContext = mock(SpanContext.class);
        span = mock(Span.class);
        when(span.context()).thenReturn(spanContext);
        final Tracer.SpanBuilder spanBuilder = HonoClientUnitTestHelper.mockSpanBuilder(span);
        final Tracer tracer = mock(Tracer.class);
        when(tracer.buildSpan(anyString())).thenReturn(spanBuilder);

        tenantId = "testTenant";
        deviceId = "testDevice";
        regClient = mock(RegistrationClient.class);
        final RegistrationClientFactory registrationClientFactory = mock(RegistrationClientFactory.class);
        when(registrationClientFactory.getOrCreateRegistrationClient(anyString()))
                .thenReturn(Future.succeededFuture(regClient));

        devConClient = mock(DeviceConnectionClient.class);
        final DeviceConnectionClientFactory deviceConnectionClientFactory = mock(DeviceConnectionClientFactory.class);
        when(deviceConnectionClientFactory.getOrCreateDeviceConnectionClient(anyString()))
                .thenReturn(Future.succeededFuture(devConClient));
        gatewayMapper = new GatewayMapperImpl(registrationClientFactory, deviceConnectionClientFactory, tracer);
    }

    /**
     * Verifies that the <em>getMappedGatewayDevice</em> method returns a Future with the device id as result for a
     * device for which no 'via' entry is set.
     */
    @Test
    public void testGetMappedGatewayDeviceUsingDeviceWithNoVia() {
        // GIVEN assertRegistration result with no 'via'
        final JsonObject assertRegistrationResult = new JsonObject();
        when(regClient.assertRegistration(anyString(), any(), any())).thenReturn(Future.succeededFuture(assertRegistrationResult));

        // WHEN getMappedGatewayDevice() is invoked
        final Future<String> mappedGatewayDeviceFuture = gatewayMapper.getMappedGatewayDevice(tenantId, deviceId, null);

        // THEN the returned Future is complete and contains the deviceId
        assertThat(mappedGatewayDeviceFuture.isComplete(), is(true));
        assertThat(mappedGatewayDeviceFuture.result(), is(deviceId));
        verify(span).finish();
    }

    /**
     * Verifies that the <em>getMappedGatewayDevice</em> method returns the gateway set in both the 'via' entry of
     * the given device and the <em>getLastKnownGatewayForDevice</em> operation return value.
     */
    @Test
    public void testGetMappedGatewayDeviceUsingDeviceWithMappedGateway() {
        final String gatewayId = "testDeviceVia";

        // GIVEN assertRegistration result with non-empty 'via'
        final JsonObject assertRegistrationResult = new JsonObject();
        final JsonArray viaArray = new JsonArray(Collections.singletonList(gatewayId));
        assertRegistrationResult.put(RegistrationConstants.FIELD_VIA, viaArray);
        when(regClient.assertRegistration(anyString(), any(), any())).thenReturn(Future.succeededFuture(assertRegistrationResult));
        // and a non-empty getLastKnownGatewayForDevice result
        final JsonObject lastKnownGatewayResult = new JsonObject();
        lastKnownGatewayResult.put(DeviceConnectionConstants.FIELD_GATEWAY_ID, gatewayId);
        when(devConClient.getLastKnownGatewayForDevice(anyString(), any())).thenReturn(Future.succeededFuture(lastKnownGatewayResult));

        // WHEN getMappedGatewayDevice() is invoked
        final Future<String> mappedGatewayDeviceFuture = gatewayMapper.getMappedGatewayDevice(tenantId, deviceId, null);

        // THEN the returned Future is complete and contains the gatewayId
        assertThat(mappedGatewayDeviceFuture.isComplete(), is(true));
        assertThat(mappedGatewayDeviceFuture.result(), is(gatewayId));
        verify(span).finish();
    }

    /**
     * Verifies that the <em>getMappedGatewayDevice</em> method returns the single gateway entry of a device's 'via'
     * entry if no gateway is returned by the <em>getLastKnownGatewayForDevice</em> operation.
     */
    @Test
    public void testGetMappedGatewayDeviceReturningSingleDefinedViaGateway() {
        final String gatewayId = "testDeviceVia";

        // GIVEN assertRegistrationResult with a single 'via' array entry
        final JsonObject assertRegistrationResult = new JsonObject();
        final JsonArray viaArray = new JsonArray(Collections.singletonList(gatewayId));
        assertRegistrationResult.put(RegistrationConstants.FIELD_VIA, viaArray);
        when(regClient.assertRegistration(anyString(), any(), any())).thenReturn(Future.succeededFuture(assertRegistrationResult));
        // and a 404 getLastKnownGatewayForDevice response
        when(devConClient.getLastKnownGatewayForDevice(anyString(), any()))
                .thenReturn(Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_NOT_FOUND)));

        // WHEN getMappedGatewayDevice() is invoked
        final Future<String> mappedGatewayDeviceFuture = gatewayMapper.getMappedGatewayDevice(tenantId, deviceId, null);

        // THEN the returned Future is complete and contains the gatewayId
        assertThat(mappedGatewayDeviceFuture.isComplete(), is(true));
        assertThat(mappedGatewayDeviceFuture.result(), is(gatewayId));
        verify(span).finish();
    }

    /**
     * Verifies that the <em>getMappedGatewayDevice</em> method returns a failed Future if no gateway is returned by
     * the <em>getLastKnownGatewayForDevice</em> operation and the device has multiple gateways defined in its 'via'
     * entry.
     */
    @Test
    public void testGetMappedGatewayDeviceFailureUsingMultiGatewayDevice() {
        final String gatewayId = "testDeviceVia";

        // GIVEN assertRegistrationResult with 'via'
        final JsonObject assertRegistrationResult = new JsonObject();
        final JsonArray viaArray = new JsonArray();
        viaArray.add(gatewayId);
        viaArray.add("otherGatewayId");
        assertRegistrationResult.put(RegistrationConstants.FIELD_VIA, viaArray);
        when(regClient.assertRegistration(anyString(), any(), any())).thenReturn(Future.succeededFuture(assertRegistrationResult));
        // and a 404 getLastKnownGatewayForDevice response
        when(devConClient.getLastKnownGatewayForDevice(anyString(), any()))
                .thenReturn(Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_NOT_FOUND)));

        // WHEN getMappedGatewayDevice() is invoked
        final Future<String> mappedGatewayDeviceFuture = gatewayMapper.getMappedGatewayDevice(tenantId, deviceId, null);

        // THEN the returned Future is failed
        assertThat(mappedGatewayDeviceFuture.isComplete(), is(true));
        assertThat(mappedGatewayDeviceFuture.result(), is(nullValue()));
        assertThat(mappedGatewayDeviceFuture.cause(), instanceOf(ClientErrorException.class));
        assertThat(((ClientErrorException) mappedGatewayDeviceFuture.cause()).getErrorCode(), is(HttpURLConnection.HTTP_NOT_FOUND));
        verify(span).finish();
    }

    /**
     * Verifies that the <em>getMappedGatewayDevice</em> method returns a failed Future if the gateway returned by
     * the <em>getLastKnownGatewayForDevice</em> operation is not in the list of gateways of the device's 'via' entry.
     */
    @Test
    public void testGetMappedGatewayDeviceFailureIfGatewayNotInVia() {
        final String gatewayId = "testDeviceVia";

        // GIVEN assertRegistrationResult with 'via'
        final JsonObject assertRegistrationResult = new JsonObject();
        final JsonArray viaArray = new JsonArray(Collections.singletonList(gatewayId));
        assertRegistrationResult.put(RegistrationConstants.FIELD_VIA, viaArray);
        when(regClient.assertRegistration(anyString(), any(), any())).thenReturn(Future.succeededFuture(assertRegistrationResult));
        // and a getLastKnownGatewayForDevice response with a different gateway
        final JsonObject lastKnownGatewayResult = new JsonObject();
        lastKnownGatewayResult.put(DeviceConnectionConstants.FIELD_GATEWAY_ID, "otherGatewayId");
        when(devConClient.getLastKnownGatewayForDevice(anyString(), any())).thenReturn(Future.succeededFuture(lastKnownGatewayResult));

        // WHEN getMappedGatewayDevice() is invoked
        final Future<String> mappedGatewayDeviceFuture = gatewayMapper.getMappedGatewayDevice(tenantId, deviceId, null);

        // THEN the returned Future is failed
        assertThat(mappedGatewayDeviceFuture.isComplete(), is(true));
        assertThat(mappedGatewayDeviceFuture.result(), is(nullValue()));
        assertThat(mappedGatewayDeviceFuture.cause(), instanceOf(ClientErrorException.class));
        assertThat(((ClientErrorException) mappedGatewayDeviceFuture.cause()).getErrorCode(), is(HttpURLConnection.HTTP_NOT_FOUND));
        verify(span).finish();
    }

    /**
     * Verifies that the <em>getMappedGatewayDevice</em> method returns a failed Future if the
     * <em>getLastKnownGatewayForDevice</em> operation failed.
     */
    @Test
    public void testGetMappedGatewayDeviceFailureIfGetLastGatewayFailed() {
        final String gatewayId = "testDeviceVia";

        // GIVEN assertRegistrationResult with 'via'
        final JsonObject assertRegistrationResult = new JsonObject();
        final JsonArray viaArray = new JsonArray(Collections.singletonList(gatewayId));
        assertRegistrationResult.put(RegistrationConstants.FIELD_VIA, viaArray);
        when(regClient.assertRegistration(anyString(), any(), any())).thenReturn(Future.succeededFuture(assertRegistrationResult));
        // and a 500 getLastKnownGatewayForDevice response
        when(devConClient.getLastKnownGatewayForDevice(anyString(), any()))
                .thenReturn(Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_INTERNAL_ERROR)));

        // WHEN getMappedGatewayDevice() is invoked
        final Future<String> mappedGatewayDeviceFuture = gatewayMapper.getMappedGatewayDevice(tenantId, deviceId, null);

        // THEN the returned Future is failed
        assertThat(mappedGatewayDeviceFuture.isComplete(), is(true));
        assertThat(mappedGatewayDeviceFuture.result(), is(nullValue()));
        assertThat(mappedGatewayDeviceFuture.cause(), instanceOf(ServerErrorException.class));
        assertThat(((ServerErrorException) mappedGatewayDeviceFuture.cause()).getErrorCode(), is(HttpURLConnection.HTTP_INTERNAL_ERROR));
        verify(span).finish();
    }
}
