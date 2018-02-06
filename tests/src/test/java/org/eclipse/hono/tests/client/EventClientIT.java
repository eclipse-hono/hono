/**
 * Copyright (c) 2016, 2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 *
 */

package org.eclipse.hono.tests.client;

import java.util.function.Consumer;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.client.MessageSender;
import org.junit.runner.RunWith;

import io.vertx.core.Future;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

/**
 * A simple test that uses the {@code HonoClient} to send some event messages to Hono server and verifies they are forwarded to the downstream host
 * via the configured Artemis broker.
 */
@RunWith(VertxUnitRunner.class)
public class EventClientIT extends ClientTestBase {

    @Override
    Future<MessageConsumer> createConsumer(final String tenantId, final Consumer<Message> messageConsumer) {
        return downstreamClient.createEventConsumer(tenantId, messageConsumer, close -> {});
    }

    @Override
    Future<MessageSender> createProducer(final String tenantId) {
        return honoClient.getOrCreateEventSender(tenantId);
    }

    @Override
    protected void assertAdditionalMessageProperties(final TestContext ctx, final Message msg) {
        // assert that events are marked as "durable"
        ctx.assertTrue(msg.isDurable());
    }
}
