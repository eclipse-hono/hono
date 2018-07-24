/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.tests.client;

import java.util.function.Consumer;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.util.MessageHelper;
import org.junit.runner.RunWith;

import io.vertx.core.Future;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

/**
 * Verifies that a device can send telemetry messages to a Hono server and
 * that the telemetry messages can be consumed from the downstream host.
 */
@RunWith(VertxUnitRunner.class)
public class DeviceTelemetryClientIT extends MessagingClientTestBase {

    @Override
    protected Future<MessageSender> createProducer(final String tenantId) {
        return honoClient.getOrCreateTelemetrySender(tenantId, DEVICE_ID);
    }

    @Override
    protected Future<MessageConsumer> createConsumer(final String tenantId, final Consumer<Message> messageConsumer) {
        return downstreamClient.createTelemetryConsumer(tenantId, messageConsumer, close -> {});
    }

    @Override
    protected void assertAdditionalMessageProperties(final TestContext ctx, final Message msg) {
        ctx.assertNotNull(MessageHelper.getTenantIdAnnotation(msg));
        ctx.assertNotNull(MessageHelper.getDeviceIdAnnotation(msg));
    }
}
