/**
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
 */


package org.eclipse.hono.deviceregistry.service.tenant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;

import java.net.HttpURLConnection;
import java.util.Optional;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.service.management.tenant.TenantManagementService;
import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.opentracing.noop.NoopSpan;
import io.vertx.core.Future;


/**
 * Verifies behavior of {@link DefaultTenantInformationService}.
 *
 */
public class DefaultTenantInformationServiceTest {

    private TenantManagementService tenantMgmtService;
    private DefaultTenantInformationService service;

    /**
     * Sets up the fixture.
     */
    @BeforeEach
    void setUp() {
        tenantMgmtService = mock(TenantManagementService.class);
        service = new DefaultTenantInformationService(tenantMgmtService);
    }

    /**
     * Verifies that the service returns a succeeded future if the queried tenant exists.
     */
    @Test
    public void testTenantExistsSucceedsForExistingTenant() {
        when(tenantMgmtService.readTenant(anyString(), any()))
        .thenAnswer(invocation -> Future.succeededFuture(OperationResult.ok(
                HttpURLConnection.HTTP_OK,
                new Tenant().setEnabled(true),
                Optional.of(CacheDirective.noCacheDirective()),
                Optional.empty())));
    final var result = service.tenantExists(Constants.DEFAULT_TENANT, NoopSpan.INSTANCE);
    assertThat(result.succeeded()).isTrue();
    assertThat(result.result().getStatus()).isEqualTo(HttpURLConnection.HTTP_OK);
    assertThat(result.result().getPayload().getTenantId()).isEqualTo(Constants.DEFAULT_TENANT);
    }

    /**
     * Verifies that the service returns a succeeded future with status 404 if the queried tenant does not exist.
     */
    @Test
    public void testTenantExistsSucceedsForNonExistingTenant() {
        when(tenantMgmtService.readTenant(anyString(), any()))
            .thenReturn(Future.succeededFuture(OperationResult.empty(HttpURLConnection.HTTP_NOT_FOUND)));
        final var result = service.tenantExists(Constants.DEFAULT_TENANT, NoopSpan.INSTANCE);
        assertThat(result.succeeded()).isTrue();
        assertThat(result.result().getStatus()).isEqualTo(HttpURLConnection.HTTP_NOT_FOUND);
        assertThat(result.result().getPayload()).isNull();
    }

    /**
     * Verifies that the service returns a succeeded future with status 503 if the underlying tenant
     * management service is not available.
     */
    @Test
    public void testTenantExistsFailsIfTenantManagementServiceIsUnavailable() {
        when(tenantMgmtService.readTenant(anyString(), any()))
            .thenReturn(Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE)));
        final var result = service.tenantExists(Constants.DEFAULT_TENANT, NoopSpan.INSTANCE);
        assertThat(result.failed()).isTrue();
        assertThat(result.cause()).isInstanceOf(ServerErrorException.class);
        assertThat(((ServerErrorException) result.cause()).getErrorCode()).isEqualTo(HttpURLConnection.HTTP_UNAVAILABLE);
    }

    /**
     * Verifies that the service returns a succeeded future containing the queried tenant.
     */
    @Test
    public void testGetTenantSucceedsForExistingTenant() {
        when(tenantMgmtService.readTenant(anyString(), any()))
        .thenAnswer(invocation -> Future.succeededFuture(OperationResult.ok(
                HttpURLConnection.HTTP_OK,
                new Tenant().setEnabled(true),
                Optional.of(CacheDirective.noCacheDirective()),
                Optional.empty())));
        final var result = service.getTenant(Constants.DEFAULT_TENANT, NoopSpan.INSTANCE);
        assertThat(result.succeeded()).isTrue();
        assertThat(result.result().isEnabled()).isTrue();
    }

    /**
     * Verifies that the service returns a failed future with status 404 if the queried tenant does not exist.
     */
    @Test
    public void testGetTenantFailsForNonExistingTenant() {
        when(tenantMgmtService.readTenant(anyString(), any()))
            .thenReturn(Future.succeededFuture(OperationResult.empty(HttpURLConnection.HTTP_NOT_FOUND)));
        final var result = service.getTenant(Constants.DEFAULT_TENANT, NoopSpan.INSTANCE);
        assertThat(result.failed()).isTrue();
        assertThat(result.cause()).isInstanceOf(ClientErrorException.class);
        assertThat(((ClientErrorException) result.cause()).getErrorCode()).isEqualTo(HttpURLConnection.HTTP_NOT_FOUND);
    }


}
