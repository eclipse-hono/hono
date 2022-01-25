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

import org.eclipse.hono.service.tenant.AbstractTenantServiceTest;

import io.vertx.junit5.VertxTestContext;

class JdbcBasedTenantServiceTest extends AbstractJdbcRegistryTest implements AbstractTenantServiceTest {

    @Override
    public void testAddTenantWithTrustAnchorGroupAndDuplicateTrustAnchorFails(
            final VertxTestContext ctx) {
        //This feature is not implemented
    }

    @Override
    public void testAddTenantWithTrustAnchorGroupAndDuplicateTrustAnchorSucceeds(
            final VertxTestContext ctx) {
        //This feature is not implemented
    }

    @Override
    public void testUpdateTenantWithTrustAnchorGroupAndDuplicateTrustAnchorFails(final VertxTestContext ctx) {
        //This feature is not implemented
    }

    @Override
    public void testUpdateTenantWithTrustAnchorGroupAndDuplicateTrustAnchorSucceeds(
            final VertxTestContext ctx) {
        //This feature is not implemented
    }
}
