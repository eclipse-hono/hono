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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;

import org.eclipse.hono.client.DeviceConnectionClient;
import org.eclipse.hono.client.DeviceConnectionClientFactory;
import org.eclipse.hono.client.RegistrationClient;
import org.eclipse.hono.client.RegistrationClientFactory;
import org.eclipse.hono.util.DeviceConnectionConstants;
import org.eclipse.hono.util.RegistrationConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Verifies behavior of {@link CommandTargetMapperImpl}.
 */
public class CommandTargetMapperImplTest {

    private CommandTargetMapperImpl commandTargetMapper;
    private RegistrationClient regClient;
    private DeviceConnectionClient devConClient;
    private String tenantId;
    private String deviceId;
    private Span span;

    /**
     * Sets up common fixture.
     */
    @BeforeEach
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
        commandTargetMapper = new CommandTargetMapperImpl(tracer);
        commandTargetMapper.initialize(registrationClientFactory, deviceConnectionClientFactory);
    }

    /**
     * Verifies that the <em>getTargetGatewayAndAdapterInstance</em> method returns a Future with the expected result
     * for a device for which no 'via' entry is set.
     */
    @Test
    public void testGetTargetGatewayAndAdapterInstanceUsingDeviceWithNoVia() {
        // GIVEN assertRegistration result with no 'via'
        final JsonObject assertRegistrationResult = new JsonObject();
        when(regClient.assertRegistration(anyString(), any(), any())).thenReturn(Future.succeededFuture(assertRegistrationResult));

        // and a getCommandHandlingAdapterInstances result with one object for the device
        final String adapterInstanceId = "adapter1";
        final JsonObject adapterInstancesResult = new JsonObject();
        final JsonObject adapterInstanceEntry = new JsonObject();
        adapterInstanceEntry.put(DeviceConnectionConstants.FIELD_PAYLOAD_DEVICE_ID, deviceId);
        adapterInstanceEntry.put(DeviceConnectionConstants.FIELD_ADAPTER_INSTANCE_ID, adapterInstanceId);
        adapterInstancesResult.put(DeviceConnectionConstants.FIELD_ADAPTER_INSTANCES, new JsonArray(Collections.singletonList(adapterInstanceEntry)));
        when(devConClient.getCommandHandlingAdapterInstances(eq(deviceId), any(), any())).thenReturn(Future.succeededFuture(adapterInstancesResult));

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
        final JsonObject assertRegistrationResult = new JsonObject();
        final JsonArray viaArray = new JsonArray(Collections.singletonList(gatewayId));
        assertRegistrationResult.put(RegistrationConstants.FIELD_VIA, viaArray);
        when(regClient.assertRegistration(anyString(), any(), any())).thenReturn(Future.succeededFuture(assertRegistrationResult));

        // and a getCommandHandlingAdapterInstances result with one object for the gateway
        final String adapterInstanceId = "adapter1";
        final JsonObject adapterInstancesResult = new JsonObject();
        final JsonObject adapterInstanceEntry = new JsonObject();
        adapterInstanceEntry.put(DeviceConnectionConstants.FIELD_PAYLOAD_DEVICE_ID, gatewayId);
        adapterInstanceEntry.put(DeviceConnectionConstants.FIELD_ADAPTER_INSTANCE_ID, adapterInstanceId);
        adapterInstancesResult.put(DeviceConnectionConstants.FIELD_ADAPTER_INSTANCES, new JsonArray(Collections.singletonList(adapterInstanceEntry)));
        when(devConClient.getCommandHandlingAdapterInstances(eq(deviceId), any(), any())).thenReturn(Future.succeededFuture(adapterInstancesResult));

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

        // GIVEN assertRegistration result with non-empty 'via'
        final JsonObject assertRegistrationResult = new JsonObject();
        final JsonArray viaArray = new JsonArray();
        viaArray.add(gatewayId);
        viaArray.add(otherGatewayId);
        assertRegistrationResult.put(RegistrationConstants.FIELD_VIA, viaArray);
        when(regClient.assertRegistration(anyString(), any(), any())).thenReturn(Future.succeededFuture(assertRegistrationResult));

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
        when(devConClient.getCommandHandlingAdapterInstances(eq(deviceId), any(), any())).thenReturn(Future.succeededFuture(adapterInstancesResult));

        // WHEN getMappedGatewayDevice() is invoked
        final Future<JsonObject> mappedGatewayDeviceFuture = commandTargetMapper.getTargetGatewayAndAdapterInstance(tenantId, deviceId, null);

        // THEN the returned Future is complete and contains the first adapter instance entry
        assertThat(mappedGatewayDeviceFuture.isComplete()).isTrue();
        assertThat(mappedGatewayDeviceFuture.result()).isEqualTo(adapterInstanceEntry);
        verify(span).finish();
    }

}
