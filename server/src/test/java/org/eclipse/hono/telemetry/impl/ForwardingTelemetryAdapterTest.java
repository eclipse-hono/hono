/**
 * Copyright (c) 2016 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */
package org.eclipse.hono.telemetry.impl;

import org.apache.qpid.proton.message.Message;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.Vertx;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.proton.ProtonHelper;

/**
 * 
 *
 */
@RunWith(VertxUnitRunner.class)
public class ForwardingTelemetryAdapterTest {

    static Vertx               vertx;
    ForwardingTelemetryAdapter adapter;
    String                     adapterDeploymentId;

    @BeforeClass
    public static void init(final TestContext ctx) {

        // Create the Vert.x instance
        vertx = Vertx.vertx();
    }

    @Before
    public void deploy(final TestContext ctx) {
        adapter = new ForwardingTelemetryAdapter();
        adapter.setDownstreamContainerHost("192.168.11.10");
        adapter.setDownstreamContainerPort(5672);
        vertx.deployVerticle(adapter, ctx.asyncAssertSuccess(id -> adapterDeploymentId = id));
    }

    @Test
    public void test(final TestContext ctx) {
        Message msg = ProtonHelper.message("telemetry/4711", "hello");
        ctx.assertTrue(adapter.processTelemetryData(msg));
    }

    @After
    public void undeploy(final TestContext ctx) {
        vertx.undeploy(adapterDeploymentId, ctx.asyncAssertSuccess());
    }

    @AfterClass
    public static void close(final TestContext ctx) {
        vertx.close(ctx.asyncAssertSuccess());
    }
}
