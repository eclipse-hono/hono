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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.eclipse.hono.deviceregistry.util.Assertions;
import org.eclipse.hono.service.credentials.CredentialsServiceTestBase;
import org.eclipse.hono.service.management.credentials.Credentials;
import org.eclipse.hono.service.management.device.Device;
import org.eclipse.hono.service.management.tenant.RegistrationLimits;
import org.eclipse.hono.service.management.tenant.Tenant;
import org.junit.jupiter.api.Test;

import io.opentracing.noop.NoopSpan;
import io.vertx.core.Future;
import io.vertx.junit5.VertxTestContext;

class JdbcBasedCredentialsServiceTest extends AbstractJdbcRegistryTest implements CredentialsServiceTestBase {

    /**
     * Verifies that a request to update credentials of a device fails with a 403 status code
     * if the number of credentials exceeds the tenant's configured limit.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testUpdateCredentialsFailsForExceededCredentialsPerDeviceLimit(final VertxTestContext ctx) {

        final var tenantId = UUID.randomUUID().toString();
        final var deviceId = UUID.randomUUID().toString();

        when(tenantInformationService.getTenant(anyString(), any()))
            .thenReturn(Future.succeededFuture(new Tenant().setRegistrationLimits(
                    new RegistrationLimits().setMaxCredentialsPerDevice(1))));

        getDeviceManagementService().createDevice(tenantId, Optional.of(deviceId), new Device(), NoopSpan.INSTANCE)
            .onFailure(ctx::failNow)
            .compose(ok -> getCredentialsManagementService().updateCredentials(
                    tenantId,
                    deviceId,
                    List.of(
                            Credentials.createPasswordCredential("device1", "secret"),
                            Credentials.createPasswordCredential("device2", "secret")),
                    Optional.empty(),
                    NoopSpan.INSTANCE))
            .onComplete(ctx.failing(t -> {
                ctx.verify(() -> {
                    Assertions.assertServiceInvocationException(t, HttpURLConnection.HTTP_FORBIDDEN);
                });
                ctx.completeNow();
            }));
    }
}
