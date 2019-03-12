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
import org.eclipse.hono.auth.SpringBasedHonoPasswordEncoder;
import org.eclipse.hono.registry.CacheCredentialService;
import org.eclipse.hono.service.credentials.AbstractCompleteCredentialsServiceTest;
import org.eclipse.hono.service.credentials.CompleteCredentialsService;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.manager.EmbeddedCacheManager;
import org.junit.Before;
import org.junit.runner.RunWith;

/**
 * Tests verifying behavior of {@link CacheCredentialService}.
 *
 */
@RunWith(VertxUnitRunner.class)
public class CacheCredentialsServiceTest extends AbstractCompleteCredentialsServiceTest {

    CacheCredentialService service;

    /**
     * Spin up the service using Infinispan EmbeddedCache.
     */
    @Before
    public void setUp() {
        final EmbeddedCacheManager manager = new DefaultCacheManager();
        service = new CacheCredentialService(manager, new SpringBasedHonoPasswordEncoder());
    }


    @Override
    public CompleteCredentialsService getCompleteCredentialsService() {
        return service;
    }

}
