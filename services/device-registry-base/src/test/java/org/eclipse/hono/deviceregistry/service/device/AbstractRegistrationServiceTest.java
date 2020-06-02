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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.net.HttpURLConnection;
import java.util.Map;
import java.util.Optional;

import org.eclipse.hono.deviceregistry.service.tenant.TenantInformationService;
import org.eclipse.hono.deviceregistry.service.tenant.TenantKey;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.Result;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.RegistrationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

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

    private Span span;
    private AbstractRegistrationService service;
    private TenantInformationService tenantInformationService;


    /**
     * Sets up the fixture.
     */
    @BeforeEach
    public void setUp() {
        tenantInformationService = mock(TenantInformationService.class);
        when(tenantInformationService.tenantExists(anyString(), any(Span.class)))
            .thenAnswer(invocation -> {
                return Future.succeededFuture(OperationResult.ok(
                        HttpURLConnection.HTTP_OK,
                        TenantKey.from(invocation.getArgument(0), "tenant-name"),
                        Optional.empty(),
                        Optional.empty()));
            });
        span = mock(Span.class);
        service = spy(AbstractRegistrationService.class);
        service.setTenantInformationService(tenantInformationService);
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

}
