/*******************************************************************************
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
 *******************************************************************************/

package org.eclipse.hono.deviceregistry.jdbc.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.net.HttpURLConnection;
import java.util.Optional;

import org.eclipse.hono.deviceregistry.util.Assertions;
import org.eclipse.hono.service.management.device.Device;
import org.eclipse.hono.service.management.tenant.RegistrationLimits;
import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.service.registration.AbstractRegistrationServiceTest;
import org.junit.jupiter.api.Test;

import io.opentracing.noop.NoopSpan;
import io.vertx.core.Future;
import io.vertx.junit5.VertxTestContext;

class JdbcBasedRegistrationServiceTest extends AbstractJdbcRegistryTest implements AbstractRegistrationServiceTest {

    /**
     * Verifies that a request to create more devices than the globally configured limit fails with a 403.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testCreateDeviceFailsIfGlobalDeviceLimitHasBeenReached(final VertxTestContext ctx) {

        when(properties.maxDevicesPerTenant()).thenReturn(1);

        getDeviceManagementService().createDevice(TENANT, Optional.empty(), new Device(), NoopSpan.INSTANCE)
            .onFailure(ctx::failNow)
            .compose(ok -> getDeviceManagementService().createDevice(TENANT, Optional.empty(), new Device(), NoopSpan.INSTANCE))
            .onComplete(ctx.failing(t -> {
                ctx.verify(() -> Assertions.assertServiceInvocationException(t, HttpURLConnection.HTTP_FORBIDDEN));
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that a request to create more devices than the limit configured at the tenant level fails with a 403.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testCreateDeviceFailsIfTenantLevelDeviceLimitHasBeenReached(final VertxTestContext ctx) {

        when(properties.maxDevicesPerTenant()).thenReturn(3);

        when(tenantInformationService.getTenant(anyString(), any())).thenReturn(
                Future.succeededFuture(new Tenant().setRegistrationLimits(new RegistrationLimits().setMaxNumberOfDevices(1))));

        getDeviceManagementService().createDevice(TENANT, Optional.empty(), new Device(), NoopSpan.INSTANCE)
            .onFailure(ctx::failNow)
            .compose(ok -> getDeviceManagementService().createDevice(TENANT, Optional.empty(), new Device(), NoopSpan.INSTANCE))
            .onComplete(ctx.failing(t -> {
                ctx.verify(() -> Assertions.assertServiceInvocationException(t, HttpURLConnection.HTTP_FORBIDDEN));
                ctx.completeNow();
            }));
    }
}
