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

package org.eclipse.hono.tests.client;

import java.util.function.Consumer;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.util.MessageHelper;
import org.junit.runner.RunWith;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

/**
 * Verifies that a device can send telemetry messages to a Hono server and
 * that the telemetry messages can be consumed from the downstream host.
 */
@RunWith(VertxUnitRunner.class)
public class DeviceTelemetryClientIT extends ClientTestBase {

    @Override
    void createProducer(final String tenantId, final Handler<AsyncResult<MessageSender>> resultHandler) {
        honoClient.getOrCreateTelemetrySender(tenantId, DEVICE_ID, resultHandler);
    }

    @Override
    void createConsumer(final String tenantId, final Consumer<Message> messageConsumer,
            final Handler<AsyncResult<MessageConsumer>> resultHandler) {
        downstreamClient.createTelemetryConsumer(tenantId, messageConsumer, resultHandler);
    }

    @Override
    protected void assertAdditionalMessageProperties(TestContext ctx, Message msg) {
        ctx.assertNotNull(MessageHelper.getTenantIdAnnotation(msg));
        ctx.assertNotNull(MessageHelper.getDeviceIdAnnotation(msg));
    }
}
