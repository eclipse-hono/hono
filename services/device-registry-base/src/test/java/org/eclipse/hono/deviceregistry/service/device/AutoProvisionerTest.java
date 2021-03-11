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
package org.eclipse.hono.deviceregistry.service.device;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.HttpURLConnection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.hono.adapter.MessagingClientSet;
import org.eclipse.hono.adapter.MessagingClients;
import org.eclipse.hono.adapter.client.command.CommandResponseSender;
import org.eclipse.hono.adapter.client.telemetry.EventSender;
import org.eclipse.hono.adapter.client.telemetry.TelemetrySender;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.deviceregistry.service.tenant.TenantInformationService;
import org.eclipse.hono.service.management.Id;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.device.Device;
import org.eclipse.hono.service.management.device.DeviceManagementService;
import org.eclipse.hono.service.management.device.DeviceStatus;
import org.eclipse.hono.test.TracingMockSupport;
import org.eclipse.hono.test.VertxMockSupport;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.MessagingType;
import org.eclipse.hono.util.RegistrationAssertion;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.eclipse.hono.util.TenantObject;
import org.eclipse.hono.util.TenantResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;

import io.opentracing.Span;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class AutoProvisionerTest {

    private static final String GATEWAY_ID = "barfoo4711";
    private static final String GATEWAY_GROUP_ID = "barfoospam4711";
    private static final String DEVICE_ID = "foobar42";
    private static final Device NEW_EDGE_DEVICE = new Device();

    private Span span;
    private Vertx vertx;
    private DeviceManagementService deviceManagementService;
    private TenantInformationService tenantInformationService;
    private EventSender sender;

    private AutoProvisioner autoProvisioner;

    /**
     * Sets up the fixture.
     */
    @SuppressWarnings("unchecked")
    @BeforeEach
    public void setUp() {
        tenantInformationService = mock(TenantInformationService.class);
        when(tenantInformationService.getTenant(anyString(), any(Span.class)))
                .thenAnswer(invocation -> Future.succeededFuture(TenantResult.from(HttpURLConnection.HTTP_OK,
                        TenantObject.from(invocation.getArgument(0), true))));

        span = TracingMockSupport.mockSpan();
        vertx = mock(Vertx.class);
        VertxMockSupport.runTimersImmediately(vertx);

        deviceManagementService = mock(DeviceManagementService.class);

        autoProvisioner = new AutoProvisioner();
        autoProvisioner.setTenantInformationService(tenantInformationService);
        autoProvisioner.setDeviceManagementService(deviceManagementService);
        autoProvisioner.setVertx(vertx);
        autoProvisioner.setConfig(new AutoProvisionerConfigProperties());

        sender = mock(EventSender.class);
        when(sender.sendEvent(
                any(TenantObject.class),
                any(RegistrationAssertion.class),
                anyString(),
                any(),
                any(Map.class),
                any()))
            .thenReturn(Future.succeededFuture());

        final MessagingClientSet messagingClientSet = new MessagingClientSet(MessagingType.amqp,
                sender,
                mock(TelemetrySender.class),
                mock(CommandResponseSender.class));

        final MessagingClients messagingClients = new MessagingClients();
        messagingClients.addClientSet(messagingClientSet);

        autoProvisioner.setMessagingClients(messagingClients);

        when(deviceManagementService
                .updateDevice(eq(Constants.DEFAULT_TENANT), eq(DEVICE_ID), any(Device.class), any(), any()))
                .thenReturn(Future.succeededFuture(OperationResult.empty(HttpURLConnection.HTTP_OK)));
    }


    /**
     * Verifies a successful auto-provisioning call.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testSuccessfulAutoProvisioning(final VertxTestContext ctx) {
        mockAssertRegistration(GATEWAY_ID, Collections.singletonList(GATEWAY_GROUP_ID), Collections.singletonList(
                RegistryManagementConstants.AUTHORITY_AUTO_PROVISIONING_ENABLED));
        mockAssertRegistration(DEVICE_ID, true);
        mockAddEdgeDevice(HttpURLConnection.HTTP_CREATED);

        autoProvisioner.performAutoProvisioning(Constants.DEFAULT_TENANT, DEVICE_ID, GATEWAY_ID, NEW_EDGE_DEVICE, span.context())
                .onComplete(ctx.succeeding(device -> {
                    ctx.verify(() -> verifySuccessfulAutoProvisioning());
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that auto-provisioning still succeeds if the device to be auto-provisioned has already been created
     * (e.g. by a concurrently running request) and the notification has already been sent.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAutoProvisioningIsSuccessfulForAlreadyPresentEdgeDevice(final VertxTestContext ctx) {
        mockAssertRegistration(GATEWAY_ID, Collections.singletonList(GATEWAY_GROUP_ID), Collections.singletonList(RegistryManagementConstants.AUTHORITY_AUTO_PROVISIONING_ENABLED));
        mockAssertRegistration(DEVICE_ID, true);
        mockAddEdgeDevice(HttpURLConnection.HTTP_CONFLICT);

        autoProvisioner.performAutoProvisioning(Constants.DEFAULT_TENANT, DEVICE_ID, GATEWAY_ID, NEW_EDGE_DEVICE, span.context())
                .onComplete(ctx.succeeding(result -> {
                    ctx.verify(() -> {
                        verify(deviceManagementService).createDevice(eq(Constants.DEFAULT_TENANT), eq(Optional.of(DEVICE_ID)), any(Device.class), any(Span.class));

                        verify(sender, never()).sendEvent(any(), any(), any(), any(), any(), any());
                        verify(deviceManagementService, never()).updateDevice(eq(Constants.DEFAULT_TENANT), eq(DEVICE_ID), any(), any(), any());
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that auto-provisioning fails if two different gateways try to provision the same device concurrently.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAutoProvisioningFailsWhenTriggeredConcurrentlyByDifferentGatewaysForTheSameDevice(final VertxTestContext ctx) {
        mockAssertRegistration(GATEWAY_ID, Collections.singletonList(GATEWAY_GROUP_ID), Collections.singletonList(RegistryManagementConstants.AUTHORITY_AUTO_PROVISIONING_ENABLED));
        mockAssertRegistration(DEVICE_ID, false);
        mockAddEdgeDevice(HttpURLConnection.HTTP_CONFLICT);

        autoProvisioner.performAutoProvisioning(Constants.DEFAULT_TENANT, DEVICE_ID, "another-" + GATEWAY_ID, NEW_EDGE_DEVICE, span.context())
                .onComplete(ctx.failing(throwable -> {
                    ctx.verify(() -> {
                        assertThat(throwable).isInstanceOf(ServiceInvocationException.class);
                        assertThat(((ServiceInvocationException) throwable).getErrorCode()).isEqualTo(HttpURLConnection.HTTP_FORBIDDEN);

                        verify(deviceManagementService).createDevice(eq(Constants.DEFAULT_TENANT), eq(Optional.of(DEVICE_ID)), any(Device.class), any(Span.class));
                        verify(sender, never()).sendEvent(any(), any(), any(), any(), any(), any());
                        verify(deviceManagementService, never()).updateDevice(eq(Constants.DEFAULT_TENANT), eq(DEVICE_ID), any(), any(), any());

                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that auto-provisioning still succeeds if the flag device in the device registration cannot be updated
     * after the device notification has been sent. In that case another device notification will be sent when the next
     * telemetry message is received, i.e. the application will receive a duplicate event.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAutoProvisioningIsSuccessfulWhenUpdateOfNotificationFlagFails(final VertxTestContext ctx) {
        mockAssertRegistration(GATEWAY_ID, Collections.singletonList(GATEWAY_GROUP_ID), Collections.singletonList(RegistryManagementConstants.AUTHORITY_AUTO_PROVISIONING_ENABLED));
        mockAssertRegistration(DEVICE_ID, true);
        mockAddEdgeDevice(HttpURLConnection.HTTP_CREATED);
        when(deviceManagementService
                .updateDevice(eq(Constants.DEFAULT_TENANT), eq(DEVICE_ID), any(Device.class), any(), any()))
                .thenReturn(Future.succeededFuture(OperationResult.empty(HttpURLConnection.HTTP_INTERNAL_ERROR)));

        autoProvisioner.performAutoProvisioning(Constants.DEFAULT_TENANT, DEVICE_ID, GATEWAY_ID, NEW_EDGE_DEVICE, span.context())
                .onComplete(ctx.succeeding(result -> {
                    ctx.verify(() -> verifySuccessfulAutoProvisioning());
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies the event to the northbound application is re-sent, if it is not sent yet when auto-provisioning
     * is performed for an already present edge device.
     *
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testAutoProvisioningResendsDeviceNotificationForAlreadyPresentEdgeDevice(final VertxTestContext ctx) {
        mockAssertRegistration(GATEWAY_ID, Collections.singletonList(GATEWAY_GROUP_ID), Collections.singletonList(RegistryManagementConstants.AUTHORITY_AUTO_PROVISIONING_ENABLED));
        mockAssertRegistration(DEVICE_ID, false);
        mockAddEdgeDevice(HttpURLConnection.HTTP_CONFLICT);

        autoProvisioner.performAutoProvisioning(Constants.DEFAULT_TENANT, DEVICE_ID, GATEWAY_ID, new Device(), span.context())
                .onComplete(ctx.succeeding(result -> {
                    ctx.verify(() -> {
                        verify(deviceManagementService).createDevice(
                                eq(Constants.DEFAULT_TENANT),
                                eq(Optional.of(DEVICE_ID)),
                                any(Device.class),
                                any(Span.class));

                        final ArgumentCaptor<Map<String, Object>> messageArgumentCaptor = ArgumentCaptor.forClass(Map.class);
                        verify(sender).sendEvent(
                                argThat(tenant -> tenant.getTenantId().equals(Constants.DEFAULT_TENANT)),
                                argThat(assertion -> assertion.getDeviceId().equals(DEVICE_ID)),
                                eq(EventConstants.CONTENT_TYPE_DEVICE_PROVISIONING_NOTIFICATION),
                                any(),
                                messageArgumentCaptor.capture(),
                                any());
                        // verify sending the event was done as part of running the timer task
                        verify(vertx).setTimer(anyLong(), VertxMockSupport.anyHandler());

                        verify(deviceManagementService).updateDevice(
                                eq(Constants.DEFAULT_TENANT),
                                eq(AutoProvisionerTest.DEVICE_ID),
                                argThat(device -> device.getStatus().isAutoProvisioningNotificationSent()),
                                any(Optional.class),
                                any(Span.class));

                        final Map<String, Object> applicationProperties = messageArgumentCaptor.getValue();
                        verifyApplicationProperties(AutoProvisionerTest.GATEWAY_ID, AutoProvisionerTest.DEVICE_ID, applicationProperties);

                    });
                    ctx.completeNow();
                }));
    }

    private void mockAssertRegistration(final String deviceId, final List<String> memberOf, final List<String> authorities) {
        final Device registeredGateway = new Device()
                .setMemberOf(memberOf)
                .setAuthorities(new HashSet<>(authorities));

        when(deviceManagementService.readDevice(eq(Constants.DEFAULT_TENANT), eq(deviceId), any(Span.class)))
                .thenReturn(Future.succeededFuture(
                        OperationResult.ok(HttpURLConnection.HTTP_OK, registeredGateway, Optional.empty(), Optional.empty())));

    }

    private void mockAssertRegistration(final String deviceId, final boolean autoProvisioningNotificationSent) {
        when(deviceManagementService.readDevice(eq(Constants.DEFAULT_TENANT), eq(deviceId), any(Span.class)))
                .thenReturn(Future.succeededFuture(newRegistrationResult(autoProvisioningNotificationSent)));

    }

    private OperationResult<Device> newRegistrationResult(final boolean autoProvisioningNotificationSent) {
        final Device edgeDevice = new Device()
                .setVia(Collections.singletonList(AutoProvisionerTest.GATEWAY_ID))
                .setStatus(new DeviceStatus()
                        .setAutoProvisioned(true)
                        .setAutoProvisioningNotificationSent(autoProvisioningNotificationSent));

        return OperationResult.ok(HttpURLConnection.HTTP_OK, edgeDevice, Optional.empty(), Optional.empty());
    }

    @SuppressWarnings("unchecked")
    private void verifySuccessfulAutoProvisioning() {

        final ArgumentCaptor<Device> registeredDeviceArgumentCaptor = ArgumentCaptor.forClass(Device.class);
        verify(deviceManagementService).createDevice(
                eq(Constants.DEFAULT_TENANT),
                eq(Optional.of(DEVICE_ID)),
                registeredDeviceArgumentCaptor.capture(),
                any());

        final Device registeredDevice = registeredDeviceArgumentCaptor.getValue();
        assertThat(registeredDevice).isEqualTo(NEW_EDGE_DEVICE);

        final ArgumentCaptor<Map<String, Object>> messageArgumentCaptor = ArgumentCaptor.forClass(Map.class);
        verify(sender).sendEvent(
                argThat(tenant -> tenant.getTenantId().equals(Constants.DEFAULT_TENANT)),
                argThat(assertion -> assertion.getDeviceId().equals(DEVICE_ID)),
                eq(EventConstants.CONTENT_TYPE_DEVICE_PROVISIONING_NOTIFICATION),
                any(),
                messageArgumentCaptor.capture(),
                any());

        verify(deviceManagementService).updateDevice(
                eq(Constants.DEFAULT_TENANT),
                eq(DEVICE_ID),
                argThat(device -> device.getStatus().isAutoProvisioningNotificationSent()),
                any(Optional.class),
                any(Span.class));

        final Map<String, Object> applicationProperties = messageArgumentCaptor.getValue();
        verifyApplicationProperties(GATEWAY_ID, DEVICE_ID, applicationProperties);
    }

    private void verifyApplicationProperties(final String gatewayId, final String deviceId, final Map<String, Object> applicationProperties) {
        assertThat(applicationProperties.get(MessageHelper.APP_PROPERTY_REGISTRATION_STATUS))
                .isEqualTo(EventConstants.RegistrationStatus.NEW.name());
        assertThat(applicationProperties.get(MessageHelper.APP_PROPERTY_TENANT_ID))
                .isEqualTo(Constants.DEFAULT_TENANT);
        assertThat(applicationProperties.get(MessageHelper.APP_PROPERTY_GATEWAY_ID))
                .isEqualTo(gatewayId);
    }

    private void mockAddEdgeDevice(final int httpOk) {
        when(deviceManagementService.createDevice(any(), any(), any(), any()))
                .thenAnswer((Answer<Future<OperationResult<Id>>>) invocation -> {
                    final Optional<String> deviceId = invocation.getArgument(1);
                    if (!deviceId.isPresent()) {
                        return Future.failedFuture("missing device id");
                    }
                    return Future.succeededFuture(OperationResult.ok(httpOk, Id.of(deviceId.get()),
                            Optional.empty(), Optional.empty()));
                });
    }
}
