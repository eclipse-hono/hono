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
import org.eclipse.hono.auth.SpringBasedHonoPasswordEncoder;
import org.eclipse.hono.service.credentials.AbstractCompleteCredentialsServiceTest;
import org.eclipse.hono.service.credentials.CompleteCredentialsService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;

/**
 * Tests verifying behavior of {@link CacheCredentialService}.
 *
 */
@Disabled
@ExtendWith(VertxExtension.class)
public class CacheCredentialsServiceTest extends AbstractCompleteCredentialsServiceTest {

    private static CacheCredentialService service;
    private static EmbeddedHotRodServer server;

    /**
     * Spin up the service using Infinispan EmbeddedCache.
     * @throws IOException : if the embedded cache cannot start
     */
    @BeforeEach
    public void setUp() throws IOException {

        server = new EmbeddedHotRodServer();
        service = new CacheCredentialService(server.getCache(), new SpringBasedHonoPasswordEncoder());
    }

    /**
     * Stop the Embedded Infinispan Server.
     */
    @AfterEach
    public void cleanUp() {
        server.stop();
    }

    @Override
    public CompleteCredentialsService getCompleteCredentialsService() {
        return service;
    }
}

