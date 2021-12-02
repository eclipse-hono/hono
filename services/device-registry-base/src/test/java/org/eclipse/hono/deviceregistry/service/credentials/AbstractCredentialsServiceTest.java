/*******************************************************************************
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.deviceregistry.service.credentials;

import static org.mockito.Mockito.mock;

import static com.google.common.truth.Truth.assertThat;

import java.net.HttpURLConnection;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.eclipse.hono.client.util.MessagingClientProvider;
import org.eclipse.hono.deviceregistry.service.tenant.TenantKey;
import org.eclipse.hono.service.management.credentials.CredentialsManagementService;
import org.eclipse.hono.service.management.device.DeviceAndGatewayAutoProvisioner;
import org.eclipse.hono.service.management.device.DeviceManagementService;
import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.opentracing.Span;
import io.opentracing.noop.NoopSpan;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Tests verifying the behavior of {@link AbstractCredentialsService}.
 *
 */
@ExtendWith(VertxExtension.class)
@Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
public class AbstractCredentialsServiceTest {

    /**
     * Verifies that when the <em>processGet</em> method returns an error result, its status
     * is adopted for the <em>get</em> method result.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetCredentialsPreservesOriginalErrorStatus(final VertxTestContext ctx) {

        final AbstractCredentialsService credentialsService = new AbstractCredentialsService() {
            @Override
            protected Future<CredentialsResult<JsonObject>> processGet(final TenantKey tenant, final CredentialKey key,
                    final JsonObject clientContext, final Span span) {
                return Future.succeededFuture(CredentialsResult.from(HttpURLConnection.HTTP_BAD_GATEWAY));
            }
        };

        final String tenantId = "tenant";
        final String type = CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD;
        final String authId = UUID.randomUUID().toString();
        final NoopSpan span = NoopSpan.INSTANCE;

        credentialsService.get(tenantId, type, authId, span)
                .onComplete(ctx.succeeding(getCredentialsResult -> {
                    ctx.verify(() -> {
                        assertThat(getCredentialsResult.getCacheDirective()).isNotNull();
                        assertThat(getCredentialsResult.getCacheDirective()).isEqualTo(CacheDirective.noCacheDirective());
                        assertThat(getCredentialsResult.getStatus()).isEqualTo(HttpURLConnection.HTTP_BAD_GATEWAY);
                    });

                    // another test with auto-provisioning enabled
                    credentialsService.setDeviceAndGatewayAutoProvisioner(getDeviceAndGatewayAutoProvisionerMock());
                    credentialsService.get(tenantId, type, authId, span)
                            .onComplete(ctx.succeeding(getCredentialsResult2 -> {
                                ctx.verify(() -> {
                                    assertThat(getCredentialsResult2.getCacheDirective()).isNotNull();
                                    assertThat(getCredentialsResult2.getCacheDirective()).isEqualTo(CacheDirective.noCacheDirective());
                                    assertThat(getCredentialsResult2.getStatus()).isEqualTo(HttpURLConnection.HTTP_BAD_GATEWAY);
                                });
                                ctx.completeNow();
                            }));
                }));
    }

    private DeviceAndGatewayAutoProvisioner getDeviceAndGatewayAutoProvisionerMock() {
        return new DeviceAndGatewayAutoProvisioner(
                mock(Vertx.class),
                mock(DeviceManagementService.class),
                mock(CredentialsManagementService.class),
                new MessagingClientProvider<>());
    }
}
