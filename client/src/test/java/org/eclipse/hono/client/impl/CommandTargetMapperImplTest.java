/*******************************************************************************
 * Copyright (c) 2019, 2020 Contributors to the Eclipse Foundation
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.hono.client.CommandTargetMapper.CommandTargetMapperContext;
import org.eclipse.hono.test.TracingMockSupport;
import org.eclipse.hono.util.DeviceConnectionConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.opentracing.Span;
import io.opentracing.Tracer;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Verifies behavior of {@link CommandTargetMapperImpl}.
 */
public class CommandTargetMapperImplTest {

    private CommandTargetMapperImpl commandTargetMapper;
    private CommandTargetMapperContext mapperContext;
    private String tenantId;
    private String deviceId;
    private Span span;

    /**
     * Sets up common fixture.
     */
    @BeforeEach
    public void setup() {
        span = TracingMockSupport.mockSpan();
        final Tracer tracer = TracingMockSupport.mockTracer(span);

        tenantId = "testTenant";
        deviceId = "testDevice";
        mapperContext = mock(CommandTargetMapperContext.class);
        commandTargetMapper = new CommandTargetMapperImpl(tracer);
        commandTargetMapper.initialize(mapperContext);
    }

    /**
     * Verifies that the <em>getTargetGatewayAndAdapterInstance</em> method returns a Future with the expected result
     * for a device for which no 'via' entry is set.
     */
    @Test
    public void testGetTargetGatewayAndAdapterInstanceUsingDeviceWithNoVia() {
        // GIVEN assertRegistration result with no 'via'
        when(mapperContext.getViaGateways(anyString(), anyString(), any()))
            .thenReturn(Future.succeededFuture(Collections.emptyList()));

        // and a getCommandHandlingAdapterInstances result with one object for the device
        final String adapterInstanceId = "adapter1";
        final JsonObject adapterInstancesResult = new JsonObject();
        final JsonObject adapterInstanceEntry = new JsonObject();
        adapterInstanceEntry.put(DeviceConnectionConstants.FIELD_PAYLOAD_DEVICE_ID, deviceId);
        adapterInstanceEntry.put(DeviceConnectionConstants.FIELD_ADAPTER_INSTANCE_ID, adapterInstanceId);
        adapterInstancesResult.put(DeviceConnectionConstants.FIELD_ADAPTER_INSTANCES, new JsonArray(Collections.singletonList(adapterInstanceEntry)));
        when(mapperContext.getCommandHandlingAdapterInstances(eq(tenantId), eq(deviceId), any(), any())).thenReturn(Future.succeededFuture(adapterInstancesResult));

        // WHEN getTargetGatewayAndAdapterInstance() is invoked
        final Future<JsonObject> mappedGatewayDeviceFuture = commandTargetMapper.getTargetGatewayAndAdapterInstance(tenantId, deviceId, null);

        // THEN the returned Future is complete and contains the adapter instance entry
        assertThat(mappedGatewayDeviceFuture.isComplete()).isTrue();
        assertThat(mappedGatewayDeviceFuture.result()).isEqualTo(adapterInstanceEntry);
        verify(span).finish();
    }

    /**
     * Verifies that the <em>getTargetGatewayAndAdapterInstance</em> method returns a result with a gateway
     * if that gateway is one of the device's 'via' gateways and is associated with a command handling adapter
     * instance.
     */
    @Test
    public void testGetTargetGatewayAndAdapterInstanceUsingDeviceWithMappedGateway() {
        final String gatewayId = "testDeviceVia";

        // GIVEN assertRegistration result with non-empty 'via'
        when(mapperContext.getViaGateways(anyString(), anyString(), any()))
            .thenReturn(Future.succeededFuture(Collections.singletonList(gatewayId)));

        // and a getCommandHandlingAdapterInstances result with one object for the gateway
        final String adapterInstanceId = "adapter1";
        final JsonObject adapterInstancesResult = new JsonObject();
        final JsonObject adapterInstanceEntry = new JsonObject();
        adapterInstanceEntry.put(DeviceConnectionConstants.FIELD_PAYLOAD_DEVICE_ID, gatewayId);
        adapterInstanceEntry.put(DeviceConnectionConstants.FIELD_ADAPTER_INSTANCE_ID, adapterInstanceId);
        adapterInstancesResult.put(DeviceConnectionConstants.FIELD_ADAPTER_INSTANCES, new JsonArray(Collections.singletonList(adapterInstanceEntry)));
        when(mapperContext.getCommandHandlingAdapterInstances(eq(tenantId), eq(deviceId), any(), any())).thenReturn(Future.succeededFuture(adapterInstancesResult));

        // WHEN getMappedGatewayDevice() is invoked
        final Future<JsonObject> mappedGatewayDeviceFuture = commandTargetMapper.getTargetGatewayAndAdapterInstance(tenantId, deviceId, null);

        // THEN the returned Future is complete and contains the adapter instance entry
        assertThat(mappedGatewayDeviceFuture.isComplete()).isTrue();
        assertThat(mappedGatewayDeviceFuture.result()).isEqualTo(adapterInstanceEntry);
        verify(span).finish();
    }

    /**
     * Verifies that <em>getTargetGatewayAndAdapterInstance</em> method result for multiple command handling
     * adapter instances associated with gateways in the via list of the device.
     */
    @Test
    public void tesGetTargetGatewayAndAdapterInstanceUsingDeviceWithMappedGateway() {
        final String gatewayId = "testDeviceVia";
        final String otherGatewayId = "otherGatewayId";
        final List<String> viaList = new ArrayList<>();
        viaList.add(gatewayId);
        viaList.add(otherGatewayId);
        // GIVEN assertRegistration result with non-empty 'via'
        when(mapperContext.getViaGateways(anyString(), anyString(), any())).thenReturn(Future.succeededFuture(viaList));

        // and a getCommandHandlingAdapterInstances result with 2 objects with the one with 'gatewayId' being first
        final String adapterInstanceId = "adapter1";
        final JsonObject adapterInstancesResult = new JsonObject();
        final JsonObject adapterInstanceEntry = new JsonObject();
        adapterInstanceEntry.put(DeviceConnectionConstants.FIELD_PAYLOAD_DEVICE_ID, gatewayId);
        adapterInstanceEntry.put(DeviceConnectionConstants.FIELD_ADAPTER_INSTANCE_ID, adapterInstanceId);

        final JsonObject adapterInstanceOtherEntry = new JsonObject();
        adapterInstanceOtherEntry.put(DeviceConnectionConstants.FIELD_PAYLOAD_DEVICE_ID, otherGatewayId);
        adapterInstanceOtherEntry.put(DeviceConnectionConstants.FIELD_ADAPTER_INSTANCE_ID, adapterInstanceId);

        final JsonArray adapterInstances = new JsonArray();
        adapterInstances.add(adapterInstanceEntry);
        adapterInstances.add(adapterInstanceOtherEntry);
        adapterInstancesResult.put(DeviceConnectionConstants.FIELD_ADAPTER_INSTANCES, adapterInstances);
        when(mapperContext.getCommandHandlingAdapterInstances(eq(tenantId), eq(deviceId), any(), any())).thenReturn(Future.succeededFuture(adapterInstancesResult));

        // WHEN getMappedGatewayDevice() is invoked
        final Future<JsonObject> mappedGatewayDeviceFuture = commandTargetMapper.getTargetGatewayAndAdapterInstance(tenantId, deviceId, null);

        // THEN the returned Future is complete and contains the first adapter instance entry
        assertThat(mappedGatewayDeviceFuture.isComplete()).isTrue();
        assertThat(mappedGatewayDeviceFuture.result()).isEqualTo(adapterInstanceEntry);
        verify(span).finish();
    }

}
