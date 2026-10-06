/*******************************************************************************
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
 *******************************************************************************/

package org.eclipse.hono.deviceregistry.jdbc.impl;

import java.util.concurrent.TimeUnit;

import org.eclipse.hono.service.tenant.AbstractTenantServiceTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
class JdbcBasedTenantServiceTest extends AbstractJdbcRegistryTest implements AbstractTenantServiceTest {

    @Override
    public long getConfiguredTenantCacheMaxAge() {
        return tenantServiceOptions.tenantTtl().toSeconds();
    }

    @Disabled("This feature is not implemented")
    @Test
    @Override
    public void testAddTenantWithTrustAnchorGroupAndDuplicateTrustAnchorFails(
            final VertxTestContext ctx) {
    }

    @Disabled("This feature is not implemented")
    @Test
    @Override
    public void testAddTenantWithTrustAnchorGroupAndDuplicateTrustAnchorSucceeds(
            final VertxTestContext ctx) {
        // This feature is not implemented
    }

    @Disabled("This feature is not implemented")
    @Test
    @Override
    public void testUpdateTenantWithTrustAnchorGroupAndDuplicateTrustAnchorFails(final VertxTestContext ctx) {
        // This feature is not implemented
    }

    @Disabled("This feature is not implemented")
    @Test
    @Override
    public void testUpdateTenantWithTrustAnchorGroupAndDuplicateTrustAnchorSucceeds(
            final VertxTestContext ctx) {
    }
}
