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

import io.vertx.junit5.VertxExtension;
import org.eclipse.hono.service.registration.AbstractCompleteRegistrationServiceTest;
import org.eclipse.hono.service.registration.CompleteRegistrationService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;

/**
 * Tests verifying behavior of {@link CacheRegistrationService}.
 *
 */
@Disabled
@ExtendWith(VertxExtension.class)
public class CacheRegistrationServiceTest extends AbstractCompleteRegistrationServiceTest {

    private static CacheRegistrationService service;
    private static EmbeddedHotRodServer server;

    /**
     * Spin up the service using Infinispan EmbeddedCache.
     * @throws IOException if the Protobuf spec file cannot be found.
     */
    @BeforeEach
    public void setUp() throws IOException {

        server = new EmbeddedHotRodServer();
        service = new CacheRegistrationService(server.getCache());
    }

    /**
     * Stop the Embedded Infinispan Server.
     */
    @AfterEach
    public void cleanUp() {
        server.stop();
    }

    @Override
    public CompleteRegistrationService getCompleteRegistrationService() {
        return service;
    }


}
