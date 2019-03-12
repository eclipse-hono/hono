/*******************************************************************************
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.registry.infinispan;

import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.eclipse.hono.registry.CacheTenantService;
import org.eclipse.hono.service.tenant.AbstractCompleteTenantServiceTest;
import org.eclipse.hono.service.tenant.CompleteTenantService;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.manager.EmbeddedCacheManager;
import org.junit.Before;
import org.junit.runner.RunWith;

/**
 * Tests verifying behavior of {@link CacheTenantService}.
 *
 */
@RunWith(VertxUnitRunner.class)
public class CacheTenantServiceTest extends AbstractCompleteTenantServiceTest {

    CacheTenantService service;

    /**
     * Spin up the service using Infinispan EmbeddedCache.
     */
    @Before
    public void setUp() {
        final EmbeddedCacheManager manager = new DefaultCacheManager();
        service = new CacheTenantService(manager);
    }

    @Override
    public CompleteTenantService getCompleteTenantService() {
        return service;
    }
}
