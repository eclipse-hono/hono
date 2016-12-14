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
 *
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
 * A simple test that uses the {@code HonoClient} to send some event messages to Hono server and verifies they are forwarded to the downstream host
 * via the configured Artemis broker.
 */
@RunWith(VertxUnitRunner.class)
public class EventClientIT extends ClientTestBase {

    @Override
    void createConsumer(final String tenantId, final Consumer<Message> messageConsumer, final Handler<AsyncResult<MessageConsumer>> setupTracker) {
        downstreamClient.createEventConsumer(tenantId, messageConsumer, setupTracker);
    }

    @Override
    void createProducer(final String tenantId, final Handler<AsyncResult<MessageSender>> setupTracker) {
        honoClient.getOrCreateEventSender(tenantId, setupTracker);
    }

    @Override
    protected void assertAdditionalMessageProperties(final TestContext ctx, final Message msg) {
        if (Boolean.getBoolean("check.artemis.header")) {
            // assert that the message was routed via Artemis by checking for the _AMQ_VALIDATED_USER field which is populated by Artemis
            ctx.assertNotNull(MessageHelper.getApplicationProperty(msg.getApplicationProperties(), "_AMQ_VALIDATED_USER", String.class));
        }
    }
}
