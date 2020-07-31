/**
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.HttpURLConnection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.DownstreamSender;
import org.eclipse.hono.client.DownstreamSenderFactory;
import org.eclipse.hono.deviceregistry.service.tenant.TenantInformationService;
import org.eclipse.hono.deviceregistry.service.tenant.TenantKey;
import org.eclipse.hono.service.management.Id;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.Result;
import org.eclipse.hono.service.management.device.Device;
import org.eclipse.hono.service.management.device.DeviceManagementService;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.RegistrationResult;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.eclipse.hono.util.TenantObject;
import org.eclipse.hono.util.TenantResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;


/**
 * Tests verifying behavior of {@link AbstractRegistrationService}.
 *
 */
@ExtendWith(VertxExtension.class)
public class AbstractRegistrationServiceTest {

    private static final JsonObject PAYLOAD_ENABLED = new JsonObject().put(RegistrationConstants.FIELD_DATA,
            new JsonObject().put(RegistrationConstants.FIELD_ENABLED, Boolean.TRUE));
    private static final JsonObject PAYLOAD_DISABLED = new JsonObject().put(RegistrationConstants.FIELD_DATA,
            new JsonObject().put(RegistrationConstants.FIELD_ENABLED, Boolean.FALSE));

    private static final String GATEWAY_ID = "barfoo4711";
    private static final String GATEWAY_GROUP_ID = "barfoospam4711";
    private static final String DEVICE_ID = "foobar42";

    private Span span;
    private Vertx vertx;
    private AbstractRegistrationServiceTestClass service;
    private TenantInformationService tenantInformationService;
    private DownstreamSender sender;

    /**
     * Sets up the fixture.
     */
    @BeforeEach
    public void setUp() {
        tenantInformationService = mock(TenantInformationService.class);
        when(tenantInformationService.tenantExists(anyString(), any(Span.class)))
            .thenAnswer(invocation -> Future.succeededFuture(OperationResult.ok(
                HttpURLConnection.HTTP_OK,
                TenantKey.from(invocation.getArgument(0), "tenant-name"),
                Optional.empty(),
                Optional.empty())));
        when(tenantInformationService.getTenant(anyString(), any(Span.class)))
            .thenAnswer(invocation -> Future.succeededFuture(TenantResult.from(HttpURLConnection.HTTP_OK,
                TenantObject.from(invocation.getArgument(0), true))));

        span = mock(Span.class);
        when(span.context()).thenReturn(mock(SpanContext.class));
        vertx = mock(Vertx.class);
        // run timers immediately
        when(vertx.setTimer(anyLong(), any(Handler.class))).thenAnswer(invocation -> {
            final Handler<Void> task = invocation.getArgument(1);
            task.handle(null);
            return 1L;
        });

        service = spy(AbstractRegistrationServiceTestClass.class);
        service.setTenantInformationService(tenantInformationService);

        final AutoProvisioner autoProvisioner = new AutoProvisioner(service, service);
        autoProvisioner.setVertx(vertx);
        autoProvisioner.setConfig(new AutoProvisionerConfigProperties());
        service.setAutoProvisioner(autoProvisioner);

        final DownstreamSenderFactory downstreamSenderFactoryMock = mock(DownstreamSenderFactory.class);
        sender = mock(DownstreamSender.class);
        when(sender.sendAndWaitForOutcome(any(Message.class), any())).thenReturn(Future.succeededFuture());
        when(downstreamSenderFactoryMock.getOrCreateEventSender(anyString())).thenReturn(Future.succeededFuture(sender));

        autoProvisioner.setDownstreamSenderFactory(downstreamSenderFactoryMock);

        when(service.updateDevice(eq(Constants.DEFAULT_TENANT), eq(DEVICE_ID), any(Device.class), any(), any()))
                .thenReturn(Future.succeededFuture(OperationResult.empty(HttpURLConnection.HTTP_OK)));
    }

    /**
     * Verifies that the payload returned for a request to assert the registration
     * of an existing device contains the information registered for the device.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAssertRegistrationContainsDeviceInfo(final VertxTestContext ctx) {

        final JsonObject registreredDevice = new JsonObject()
                .put(RegistrationConstants.FIELD_MAPPER, "mapping-service")
                .put(RegistrationConstants.FIELD_PAYLOAD_DEFAULTS, new JsonObject().put("foo", "bar"))
                .put(RegistrationConstants.FIELD_VIA, new JsonArray().add("gw1").add("gw2"))
                .put("ext", new JsonObject().put("key", "value"));

        when(service.processAssertRegistration(any(DeviceKey.class), any(Span.class)))
            .thenReturn(Future.succeededFuture(RegistrationResult.from(HttpURLConnection.HTTP_OK,
                    new JsonObject().put(RegistrationConstants.FIELD_PAYLOAD_DEVICE_ID, "device")
                        .put(RegistrationConstants.FIELD_DATA, registreredDevice))));

        service.assertRegistration(Constants.DEFAULT_TENANT, "device", span)
            .onComplete(ctx.succeeding(result -> {
                ctx.verify(() -> {
                    assertThat(result.getStatus()).isEqualTo(HttpURLConnection.HTTP_OK);
                    assertThat(result.getPayload().getString(RegistrationConstants.FIELD_MAPPER)).isEqualTo("mapping-service");
                    assertThat(result.getPayload().getJsonArray(RegistrationConstants.FIELD_VIA)).containsOnly("gw1", "gw2");
                    assertThat(result.getPayload().getJsonObject(RegistrationConstants.FIELD_PAYLOAD_DEFAULTS)).containsOnly(Map.entry("foo", "bar"));
                    assertThat(result.getPayload().containsKey("ext")).isFalse();
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that a device is auto-provisioned when an authorized gateway sends data on behalf of it.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAssertRegistrationPerformsAutoProvisioningForAuthorizedGateway(final VertxTestContext ctx) {
        mockAssertRegistration(GATEWAY_ID, Collections.singletonList(GATEWAY_GROUP_ID), Collections.singletonList(RegistryManagementConstants.AUTHORITY_AUTO_PROVISIONING_ENABLED));
        mockAssertRegistration(DEVICE_ID, true);
        mockAddEdgeDevice(HttpURLConnection.HTTP_CREATED);

        service.assertRegistration(Constants.DEFAULT_TENANT, DEVICE_ID, GATEWAY_ID, span)
                .onComplete(ctx.succeeding(result -> {
                    ctx.verify(() -> {
                        verifySuccessfulAutoProvisioning(result);
                    });
                    ctx.completeNow();
                }));
    }

    private void mockAssertRegistration(final String deviceId, final List<String> memberOf, final List<String> authorities) {
        final JsonObject registeredGateway = new JsonObject()
                .put(RegistryManagementConstants.FIELD_MEMBER_OF, new JsonArray(memberOf))
                .put(RegistryManagementConstants.FIELD_AUTHORITIES, new JsonArray(authorities));

        when(service.processAssertRegistration(eq(DeviceKey.from(TenantKey.from(Constants.DEFAULT_TENANT), deviceId)), any(Span.class)))
                .thenReturn(Future.succeededFuture(RegistrationResult.from(HttpURLConnection.HTTP_OK,
                        new JsonObject().put(RegistrationConstants.FIELD_PAYLOAD_DEVICE_ID, deviceId)
                                .put(RegistrationConstants.FIELD_DATA, registeredGateway))));

    }

    private void mockAssertRegistration(final String deviceId, final boolean autoProvisioningNotificationSent) {
        when(service.processAssertRegistration(eq(DeviceKey.from(TenantKey.from(Constants.DEFAULT_TENANT), deviceId)), any(Span.class)))
                .thenReturn(Future.succeededFuture(RegistrationResult.from(HttpURLConnection.HTTP_NOT_FOUND)))
                .thenReturn(Future.succeededFuture(newRegistrationResult(deviceId, autoProvisioningNotificationSent)));

    }

    private RegistrationResult newRegistrationResult(final String deviceId, final boolean autoProvisioningNotificationSent) {
        return RegistrationResult.from(HttpURLConnection.HTTP_OK,
                new JsonObject().put(RegistrationConstants.FIELD_PAYLOAD_DEVICE_ID, deviceId)
                        .put(RegistrationConstants.FIELD_DATA, new JsonObject()
                                .put(RegistrationConstants.FIELD_VIA, AbstractRegistrationServiceTest.GATEWAY_ID)
                                .put(RegistryManagementConstants.FIELD_STATUS, new JsonObject()
                                        .put(RegistrationConstants.FIELD_AUTO_PROVISIONED, true)
                                        .put(RegistrationConstants.FIELD_AUTO_PROVISIONING_NOTIFICATION_SENT, autoProvisioningNotificationSent))));
    }

    /**
     * Verifies that auto-provisioning still succeeds if the device to be auto-provisioned has already been created
     * (e.g. by a concurrently running request) and the notification has already been sent.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAssertRegistrationCanAutoProvisionForAlreadyPresentEdgeDevice(final VertxTestContext ctx) {
        mockAssertRegistration(GATEWAY_ID, Collections.singletonList(GATEWAY_GROUP_ID), Collections.singletonList(RegistryManagementConstants.AUTHORITY_AUTO_PROVISIONING_ENABLED));
        mockAssertRegistration(DEVICE_ID, true);
        mockAddEdgeDevice(HttpURLConnection.HTTP_CONFLICT);

        service.assertRegistration(Constants.DEFAULT_TENANT, DEVICE_ID, GATEWAY_ID, span)
                .onComplete(ctx.succeeding(result -> {
                    ctx.verify(() -> {
                        assertThat(result.getStatus()).isEqualTo(HttpURLConnection.HTTP_OK);

                        verify(service).createDevice(any(), any(), any(), any());

                        verify(sender, never()).sendAndWaitForOutcome(any(), any());
                        verify(service, never()).updateDevice(eq(Constants.DEFAULT_TENANT), eq(DEVICE_ID), any(), any(), any());
                    });
                    ctx.completeNow();
                }));
    }

    private void mockAddEdgeDevice(final int httpOk) {
        when(service.createDevice(any(), any(), any(), any()))
                .thenAnswer((Answer<Future<OperationResult<Id>>>) invocation -> {
                    final Optional<String> deviceId = invocation.getArgument(1);
                    if (!deviceId.isPresent()) {
                        return Future.failedFuture("missing device id");
                    }
                    return Future.succeededFuture(OperationResult.ok(httpOk, Id.of(deviceId.get()),
                            Optional.empty(), Optional.empty()));
                });
    }

    /**
     * Verifies that auto-provisioning still succeeds if the flag device in the device registration cannot be updated
     * after the device notification has been sent. In that case another device notification will be sent when the next
     * telemetry message is received, i.e. the application will receive a duplicate event.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAssertRegistrationPerformsSuccessfulAutoProvisioningWhenUpdateOfNotificationFlagFails(final VertxTestContext ctx) {
        mockAssertRegistration(GATEWAY_ID, Collections.singletonList(GATEWAY_GROUP_ID), Collections.singletonList(RegistryManagementConstants.AUTHORITY_AUTO_PROVISIONING_ENABLED));
        mockAssertRegistration(DEVICE_ID, true);
        mockAddEdgeDevice(HttpURLConnection.HTTP_CREATED);
        when(service.updateDevice(eq(Constants.DEFAULT_TENANT), eq(DEVICE_ID), any(Device.class), any(), any()))
                .thenReturn(Future.succeededFuture(OperationResult.empty(HttpURLConnection.HTTP_INTERNAL_ERROR)));

        service.assertRegistration(Constants.DEFAULT_TENANT, DEVICE_ID, GATEWAY_ID, span)
                .onComplete(ctx.succeeding(result -> {
                    ctx.verify(() -> {
                        verifySuccessfulAutoProvisioning(result);
                    });
                    ctx.completeNow();
                }));
    }

    private void verifySuccessfulAutoProvisioning(final RegistrationResult result) {
        assertThat(result.getStatus()).isEqualTo(HttpURLConnection.HTTP_OK);

        final ArgumentCaptor<Device> registeredDeviceArgumentCaptor = ArgumentCaptor.forClass(Device.class);
        verify(service).createDevice(any(), any(), registeredDeviceArgumentCaptor.capture(), any());

        final Device registeredDevice = registeredDeviceArgumentCaptor.getValue();
        assertThat(registeredDevice.getStatus().isAutoProvisioned()).isTrue();
        assertThat(registeredDevice.getVia()).containsOnly(GATEWAY_ID);
        assertThat(registeredDevice.getViaGroups()).containsOnly(GATEWAY_GROUP_ID);

        final ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
        verify(sender).sendAndWaitForOutcome(messageArgumentCaptor.capture(), any());

        final ArgumentCaptor<Device> updatedDeviceArgumentCaptor = ArgumentCaptor.forClass(Device.class);
        verify(service).updateDevice(eq(Constants.DEFAULT_TENANT), eq(DEVICE_ID), updatedDeviceArgumentCaptor.capture(), any(), any());

        final Device updatedDevice = updatedDeviceArgumentCaptor.getValue();
        assertThat(updatedDevice.getStatus().isAutoProvisioningNotificationSent()).isTrue();

        final Map<String, Object> applicationProperties = messageArgumentCaptor.getValue().getApplicationProperties().getValue();
        verifyApplicationProperties(GATEWAY_ID, DEVICE_ID, applicationProperties);
    }


    /**
     * Verifies the event to the northbound application is re-sent, if it failed at the point of time the device
     * was auto-provisioned initially..
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAssertRegistrationResendsDeviceNotification(final VertxTestContext ctx) {
        mockAssertRegistration(GATEWAY_ID, Collections.singletonList(GATEWAY_GROUP_ID), Collections.singletonList(RegistryManagementConstants.AUTHORITY_AUTO_PROVISIONING_ENABLED));

        when(service.processAssertRegistration(eq(DeviceKey.from(TenantKey.from(Constants.DEFAULT_TENANT), DEVICE_ID)), any(Span.class)))
                .thenReturn(Future.succeededFuture(newRegistrationResult(DEVICE_ID, false)));

        service.assertRegistration(Constants.DEFAULT_TENANT, DEVICE_ID, GATEWAY_ID, span)
                .onComplete(ctx.succeeding(result -> {
                    ctx.verify(() -> {
                        assertThat(result.getStatus()).isEqualTo(HttpURLConnection.HTTP_OK);

                        verify(service, never()).createDevice(any(), any(), any(), any());

                        final ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
                        verify(sender).sendAndWaitForOutcome(messageArgumentCaptor.capture(), any());
                        // verify sending the event was done as part of running the timer task
                        verify(vertx).setTimer(anyLong(), notNull());

                        final ArgumentCaptor<Device> updatedDeviceArgumentCaptor = ArgumentCaptor.forClass(Device.class);
                        verify(service).updateDevice(eq(Constants.DEFAULT_TENANT), eq(AbstractRegistrationServiceTest.DEVICE_ID), updatedDeviceArgumentCaptor.capture(), any(), any());

                        final Device updatedDevice = updatedDeviceArgumentCaptor.getValue();
                        assertThat(updatedDevice.getStatus().isAutoProvisioningNotificationSent()).isTrue();

                        final Map<String, Object> applicationProperties = messageArgumentCaptor.getValue().getApplicationProperties().getValue();
                        verifyApplicationProperties(AbstractRegistrationServiceTest.GATEWAY_ID, AbstractRegistrationServiceTest.DEVICE_ID, applicationProperties);

                    });
                    ctx.completeNow();
                }));

    }

    /**
     * Verifies the event to the northbound application is re-sent, if it is not sent yet when auto-provisioning
     * is performed for an already present edge device.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAssertRegistrationResendsDeviceNotificationForAlreadyPresentEdgeDevice(final VertxTestContext ctx) {
        mockAssertRegistration(GATEWAY_ID, Collections.singletonList(GATEWAY_GROUP_ID), Collections.singletonList(RegistryManagementConstants.AUTHORITY_AUTO_PROVISIONING_ENABLED));
        mockAssertRegistration(DEVICE_ID, false);
        mockAddEdgeDevice(HttpURLConnection.HTTP_CONFLICT);

        service.assertRegistration(Constants.DEFAULT_TENANT, DEVICE_ID, GATEWAY_ID, span)
                .onComplete(ctx.succeeding(result -> {
                    ctx.verify(() -> {
                        assertThat(result.getStatus()).isEqualTo(HttpURLConnection.HTTP_OK);

                        verify(service).createDevice(any(), any(), any(), any());

                        final ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
                        verify(sender).sendAndWaitForOutcome(messageArgumentCaptor.capture(), any());
                        // verify sending the event was done as part of running the timer task
                        verify(vertx).setTimer(anyLong(), notNull());

                        final ArgumentCaptor<Device> updatedDeviceArgumentCaptor = ArgumentCaptor.forClass(Device.class);
                        verify(service).updateDevice(eq(Constants.DEFAULT_TENANT), eq(AbstractRegistrationServiceTest.DEVICE_ID), updatedDeviceArgumentCaptor.capture(), any(), any());

                        final Device updatedDevice = updatedDeviceArgumentCaptor.getValue();
                        assertThat(updatedDevice.getStatus().isAutoProvisioningNotificationSent()).isTrue();

                        final Map<String, Object> applicationProperties = messageArgumentCaptor.getValue().getApplicationProperties().getValue();
                        verifyApplicationProperties(AbstractRegistrationServiceTest.GATEWAY_ID, AbstractRegistrationServiceTest.DEVICE_ID, applicationProperties);

                    });
                    ctx.completeNow();
                }));
    }

    private void verifyApplicationProperties(final String gatewayId, final String deviceId, final Map<String, Object> applicationProperties) {
        assertThat(applicationProperties.get(MessageHelper.APP_PROPERTY_REGISTRATION_STATUS))
                .isEqualTo(EventConstants.RegistrationStatus.NEW.name());
        assertThat(applicationProperties.get(MessageHelper.APP_PROPERTY_TENANT_ID))
                .isEqualTo(Constants.DEFAULT_TENANT);
        assertThat(applicationProperties.get(MessageHelper.APP_PROPERTY_DEVICE_ID))
                .isEqualTo(deviceId);
        assertThat(applicationProperties.get(MessageHelper.APP_PROPERTY_GATEWAY_ID))
                .isEqualTo(gatewayId);
    }

    /**
     * Verifies that a device is not auto-provisioned when an unauthorized gateway sends data on behalf of it.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAssertRegistrationDoesNotPerformAutoProvisioningForUnauthorizedGateway(final VertxTestContext ctx) {
        mockAssertRegistration(GATEWAY_ID, Collections.singletonList(GATEWAY_GROUP_ID), Collections.emptyList());
        mockAddEdgeDevice(HttpURLConnection.HTTP_OK);

        when(service.processAssertRegistration(eq(DeviceKey.from(TenantKey.from(Constants.DEFAULT_TENANT), AbstractRegistrationServiceTest.GATEWAY_ID)), any(Span.class)))
                .thenReturn(Future.succeededFuture(RegistrationResult.from(HttpURLConnection.HTTP_OK,
                        new JsonObject().put(RegistrationConstants.FIELD_PAYLOAD_DEVICE_ID, AbstractRegistrationServiceTest.GATEWAY_ID)
                                .put(RegistrationConstants.FIELD_DATA, new JsonObject()))));

        when(service.processAssertRegistration(eq(DeviceKey.from(TenantKey.from(Constants.DEFAULT_TENANT), AbstractRegistrationServiceTest.DEVICE_ID)), any(Span.class)))
                .thenReturn(Future.succeededFuture(RegistrationResult.from(HttpURLConnection.HTTP_NOT_FOUND)));

        service.assertRegistration(Constants.DEFAULT_TENANT, AbstractRegistrationServiceTest.DEVICE_ID, AbstractRegistrationServiceTest.GATEWAY_ID, span)
                .onComplete(ctx.succeeding(result -> {
                    ctx.verify(() -> {
                        assertThat(result.getStatus()).isEqualTo(HttpURLConnection.HTTP_NOT_FOUND);
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that the service returns a 404 status code for a request for asserting the registration
     * of a device that belongs to a non-existing tenant.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAssertRegistrationFailsForNonExistingTenant(final VertxTestContext ctx) {

        when(tenantInformationService.tenantExists(anyString(), any(Span.class)))
            .thenReturn(Future.succeededFuture(Result.from(HttpURLConnection.HTTP_NOT_FOUND)));

        service.assertRegistration(Constants.DEFAULT_TENANT, "device", span)
            .onComplete(ctx.succeeding(result -> {
                ctx.verify(() -> {
                    assertThat(result.getStatus()).isEqualTo(HttpURLConnection.HTTP_NOT_FOUND);
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that the service returns a 404 status code for a request for asserting the registration
     * of a non-existing device.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAssertRegistrationFailsForNonExistingDevice(final VertxTestContext ctx) {

        when(service.processAssertRegistration(any(DeviceKey.class), any(Span.class)))
            .thenReturn(Future.succeededFuture(RegistrationResult.from(HttpURLConnection.HTTP_NOT_FOUND)));

        service.assertRegistration(Constants.DEFAULT_TENANT, "device", span)
            .onComplete(ctx.succeeding(result -> {
                ctx.verify(() -> {
                    assertThat(result.getStatus()).isEqualTo(HttpURLConnection.HTTP_NOT_FOUND);
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that the service returns a 404 status code for a request for asserting the registration
     * of a disabled device.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAssertRegistrationFailsForDisabledDevice(final VertxTestContext ctx) {

        when(service.processAssertRegistration(any(DeviceKey.class), any(Span.class)))
            .thenReturn(Future.succeededFuture(RegistrationResult.from(HttpURLConnection.HTTP_OK, PAYLOAD_DISABLED)));

        service.assertRegistration(Constants.DEFAULT_TENANT, "device", span)
            .onComplete(ctx.succeeding(result -> {
                ctx.verify(() -> {
                    assertThat(result.getStatus()).isEqualTo(HttpURLConnection.HTTP_NOT_FOUND);
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that the service returns a 403 status code for a request for asserting the registration
     * of a device via a non-existing gateway.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAssertRegistrationFailsForNonExistingGateway(final VertxTestContext ctx) {

        when(service.processAssertRegistration(any(DeviceKey.class), any(Span.class)))
            .thenAnswer(invocation -> {
                final DeviceKey key = invocation.getArgument(0);
                if (key.getDeviceId().equals("gw")) {
                    return Future.succeededFuture(RegistrationResult.from(HttpURLConnection.HTTP_NOT_FOUND));
                } else {
                    return Future.succeededFuture(RegistrationResult.from(HttpURLConnection.HTTP_OK, PAYLOAD_ENABLED));
                }
            });

        service.assertRegistration(Constants.DEFAULT_TENANT, "device", "gw", span)
            .onComplete(ctx.succeeding(result -> {
                ctx.verify(() -> {
                    assertThat(result.getStatus()).isEqualTo(HttpURLConnection.HTTP_FORBIDDEN);
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that the service returns a 403 status code for a request for asserting the registration
     * of a device via a disabled gateway.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAssertRegistrationFailsForDisabledGateway(final VertxTestContext ctx) {

        when(service.processAssertRegistration(any(DeviceKey.class), any(Span.class)))
            .thenAnswer(invocation -> {
                final DeviceKey key = invocation.getArgument(0);
                if (key.getDeviceId().equals("gw")) {
                    return Future.succeededFuture(RegistrationResult.from(HttpURLConnection.HTTP_OK, PAYLOAD_DISABLED));
                } else {
                    return Future.succeededFuture(RegistrationResult.from(HttpURLConnection.HTTP_OK, PAYLOAD_ENABLED));
                }
            });

        service.assertRegistration(Constants.DEFAULT_TENANT, "device", "gw", span)
            .onComplete(ctx.succeeding(result -> {
                ctx.verify(() -> {
                    assertThat(result.getStatus()).isEqualTo(HttpURLConnection.HTTP_FORBIDDEN);
                });
                ctx.completeNow();
            }));
    }

    /**
     * Test class implementing the interface on which auto-provisioning is based upon.
     */
    public abstract static class AbstractRegistrationServiceTestClass extends AbstractRegistrationService
            implements DeviceManagementService {

    }

}
