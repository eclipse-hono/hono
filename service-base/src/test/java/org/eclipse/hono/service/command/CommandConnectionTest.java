/**
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 1.0 which is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * SPDX-License-Identifier: EPL-1.0
 */

package org.eclipse.hono.service.command;

import org.eclipse.hono.service.credentials.BaseCredentialsService;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;

import io.vertx.core.Vertx;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

/**
 * Tests verifying behavior of {@link BaseCredentialsService}.
 */
@RunWith(VertxUnitRunner.class)
public class CommandConnectionTest {

    private static Vertx vertx;
    private CommandConnection commandConnection;

    /**
     * Time out each test after 5 seconds.
     */
    public Timeout timeout = Timeout.seconds(5);

    /**
     * Sets up the fixture.
     */
    @BeforeClass
    public static void setUpVertx() {
        vertx = Vertx.vertx();
    }

    /**
     * Cleans up after test execution.
     *
     * @param ctx The helper to use for running async tests.
     */
    @AfterClass
    public static void shutdown(final TestContext ctx) {
        if (vertx != null) {
            vertx.close(ctx.asyncAssertSuccess());
        }
    }

    /**
     * Sets up the fixture.
     */
    @Before
    public void setUp() {
        commandConnection = new CommandConnectionImpl(vertx, new CommandConfigProperties());
    }

    /**
     * Tests the command responder.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testCreateCommandConsumer(final TestContext ctx) {
    }
}
