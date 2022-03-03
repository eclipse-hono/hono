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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;

import java.net.HttpURLConnection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.hono.client.util.StatusCodeMapper;
import org.eclipse.hono.deviceregistry.service.tenant.TenantInformationService;
import org.eclipse.hono.deviceregistry.service.tenant.TenantKey;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.Result;
import org.eclipse.hono.service.management.device.Device;
import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.test.TracingMockSupport;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.RegistrationResult;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;

import io.opentracing.Span;
import io.vertx.core.Future;
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
    private AbstractRegistrationService service;
    private TenantInformationService tenantInformationService;
    private EdgeDeviceAutoProvisioner edgeDeviceAutoProvisioner;

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
                .thenAnswer(invocation -> Future.succeededFuture(new Tenant()));

        span = TracingMockSupport.mockSpan();

        service = spy(AbstractRegistrationService.class);
        service.setTenantInformationService(tenantInformationService);

        edgeDeviceAutoProvisioner = mock(EdgeDeviceAutoProvisioner.class);
        service.setEdgeDeviceAutoProvisioner(edgeDeviceAutoProvisioner);
    }

    /**
     * Verifies that the payload returned for a request to assert the registration
     * of an existing device contains the information registered for the device.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAssertRegistrationContainsDeviceInfo(final VertxTestContext ctx) {

        final JsonObject registeredDevice = new JsonObject()
                .put(RegistrationConstants.FIELD_DOWNSTREAM_MESSAGE_MAPPER, "mapping-service")
                .put(RegistrationConstants.FIELD_PAYLOAD_DEFAULTS, new JsonObject().put("foo", "bar"))
                .put(RegistrationConstants.FIELD_VIA, new JsonArray().add("gw1").add("gw2"))
                .put("ext", new JsonObject().put("key", "value"));

        when(service.getRegistrationInformation(any(DeviceKey.class), any(Span.class)))
            .thenReturn(Future.succeededFuture(RegistrationResult.from(HttpURLConnection.HTTP_OK,
                    new JsonObject().put(RegistrationConstants.FIELD_PAYLOAD_DEVICE_ID, "device")
                        .put(RegistrationConstants.FIELD_DATA, registeredDevice))));

        service.assertRegistration(Constants.DEFAULT_TENANT, "device", span)
            .onComplete(ctx.succeeding(result -> {
                ctx.verify(() -> {
                    assertThat(result.getStatus()).isEqualTo(HttpURLConnection.HTTP_OK);
                    assertThat(result.getPayload().getString(RegistrationConstants.FIELD_DOWNSTREAM_MESSAGE_MAPPER)).isEqualTo("mapping-service");
                    assertThat(result.getPayload().getJsonArray(RegistrationConstants.FIELD_VIA)).containsExactly("gw1", "gw2");
                    assertThat(result.getPayload().getJsonObject(RegistrationConstants.FIELD_PAYLOAD_DEFAULTS)).containsExactly(Map.entry("foo", "bar"));
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
        mockAssertRegistration(DEVICE_ID);

        when(edgeDeviceAutoProvisioner.performAutoProvisioning(any(), any(), any(), any(), any(), any()))
                .thenReturn(Future.succeededFuture(new Device()));

        service.assertRegistration(Constants.DEFAULT_TENANT, DEVICE_ID, GATEWAY_ID, span)
                .onComplete(ctx.succeeding(result -> {
                    ctx.verify(() -> {
                        assertThat(result.getStatus()).isEqualTo(HttpURLConnection.HTTP_OK);

                        final ArgumentCaptor<Device> registeredDeviceArgumentCaptor = ArgumentCaptor.forClass(Device.class);
                        verify(edgeDeviceAutoProvisioner).performAutoProvisioning(eq(Constants.DEFAULT_TENANT), any(),
                                eq(DEVICE_ID), eq(GATEWAY_ID), registeredDeviceArgumentCaptor.capture(), any());

                        final Device registeredDevice = registeredDeviceArgumentCaptor.getValue();
                        assertThat(registeredDevice.getStatus().isAutoProvisioned()).isTrue();
                        assertThat(registeredDevice.getVia()).containsExactly(GATEWAY_ID);
                        assertThat(registeredDevice.getViaGroups()).containsExactly(GATEWAY_GROUP_ID);
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that a device is not auto-provisioned when an unauthorized gateway sends data on behalf of it.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAssertRegistrationDoesNotPerformAutoProvisioningForUnauthorizedGateway(final VertxTestContext ctx) {
        mockAssertRegistration(GATEWAY_ID, Collections.singletonList(GATEWAY_GROUP_ID), Collections.emptyList());
        mockAssertRegistration(DEVICE_ID);

        service.assertRegistration(Constants.DEFAULT_TENANT, AbstractRegistrationServiceTest.DEVICE_ID, AbstractRegistrationServiceTest.GATEWAY_ID, span)
                .onComplete(ctx.succeeding(result -> {
                    ctx.verify(() -> {
                        verifyNoInteractions(edgeDeviceAutoProvisioner);
                        assertThat(result.getStatus()).isEqualTo(HttpURLConnection.HTTP_NOT_FOUND);
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that auto-provisioning errors are propagated correctly.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAssertRegistrationPropagatesAutoProvisioningErrorsCorrectly(final VertxTestContext ctx) {
        mockAssertRegistration(GATEWAY_ID, Collections.singletonList(GATEWAY_GROUP_ID), Collections.singletonList(RegistryManagementConstants.AUTHORITY_AUTO_PROVISIONING_ENABLED));
        mockAssertRegistration(DEVICE_ID);

        when(edgeDeviceAutoProvisioner.performAutoProvisioning(any(), any(), any(), any(), any(), any()))
                .thenReturn(Future.failedFuture(StatusCodeMapper.from(HttpURLConnection.HTTP_FORBIDDEN, "foobar")));

        service.assertRegistration(Constants.DEFAULT_TENANT, AbstractRegistrationServiceTest.DEVICE_ID, AbstractRegistrationServiceTest.GATEWAY_ID, span)
                .onComplete(ctx.succeeding(result -> {
                    ctx.verify(() -> {
                        assertThat(result.getStatus()).isEqualTo(HttpURLConnection.HTTP_FORBIDDEN);
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies the event to the northbound application is re-sent, if it failed at the point of time the device
     * was auto-provisioned initially.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAssertRegistrationResendsDeviceNotification(final VertxTestContext ctx) {
        mockAssertRegistration(GATEWAY_ID, Collections.singletonList(GATEWAY_GROUP_ID), Collections.singletonList(RegistryManagementConstants.AUTHORITY_AUTO_PROVISIONING_ENABLED));

        when(service.getRegistrationInformation(eq(DeviceKey.from(TenantKey.from(Constants.DEFAULT_TENANT), DEVICE_ID)), any(Span.class)))
                .thenReturn(Future.succeededFuture(newRegistrationResult()));

        when(edgeDeviceAutoProvisioner
                .sendDelayedAutoProvisioningNotificationIfNeeded(any(), any(), any(), any(), any(), any()))
                .thenReturn(Future.succeededFuture());

        service.assertRegistration(Constants.DEFAULT_TENANT, DEVICE_ID, GATEWAY_ID, span)
                .onComplete(ctx.succeeding(result -> {
                    ctx.verify(() -> {
                        assertThat(result.getStatus()).isEqualTo(HttpURLConnection.HTTP_OK);
                        verify(edgeDeviceAutoProvisioner, never()).performAutoProvisioning(any(), any(), any(), any(), any(),
                                any());
                        verify(edgeDeviceAutoProvisioner).sendDelayedAutoProvisioningNotificationIfNeeded(
                                eq(Constants.DEFAULT_TENANT), any(), eq(DEVICE_ID), eq(GATEWAY_ID), any(), any());
                    });
                    ctx.completeNow();
                }));
    }

    private void mockAssertRegistration(final String deviceId, final List<String> memberOf, final List<String> authorities) {
        final JsonObject registeredGateway = new JsonObject()
                .put(RegistryManagementConstants.FIELD_MEMBER_OF, new JsonArray(memberOf))
                .put(RegistryManagementConstants.FIELD_AUTHORITIES, new JsonArray(authorities));

        when(service.getRegistrationInformation(eq(DeviceKey.from(TenantKey.from(Constants.DEFAULT_TENANT), deviceId)), any(Span.class)))
                .thenReturn(Future.succeededFuture(RegistrationResult.from(HttpURLConnection.HTTP_OK,
                        new JsonObject().put(RegistrationConstants.FIELD_PAYLOAD_DEVICE_ID, deviceId)
                                .put(RegistrationConstants.FIELD_DATA, registeredGateway))));

    }

    private void mockAssertRegistration(final String deviceId) {
        when(service.getRegistrationInformation(eq(DeviceKey.from(TenantKey.from(Constants.DEFAULT_TENANT), deviceId)), any(Span.class)))
                .thenReturn(Future.succeededFuture(RegistrationResult.from(HttpURLConnection.HTTP_NOT_FOUND)));

    }

    private RegistrationResult newRegistrationResult() {
        return RegistrationResult.from(HttpURLConnection.HTTP_OK,
                new JsonObject().put(RegistrationConstants.FIELD_DATA, new JsonObject()
                                .put(RegistrationConstants.FIELD_VIA, AbstractRegistrationServiceTest.GATEWAY_ID)));
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

        when(service.getRegistrationInformation(any(DeviceKey.class), any(Span.class)))
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

        when(service.getRegistrationInformation(any(DeviceKey.class), any(Span.class)))
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

        when(service.getRegistrationInformation(any(DeviceKey.class), any(Span.class)))
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

        when(service.getRegistrationInformation(any(DeviceKey.class), any(Span.class)))
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
}
