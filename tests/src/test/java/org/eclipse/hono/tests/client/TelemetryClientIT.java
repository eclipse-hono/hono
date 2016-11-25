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
import org.junit.runner.RunWith;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.ext.unit.junit.VertxUnitRunner;

/**
 * A simple test that uses the {@code TelemetryClient} to send some messages to
 * Hono server and verifies they are forwarded to the downstream host.
 */
@RunWith(VertxUnitRunner.class)
public class TelemetryClientIT extends ClientTestBase {

    @Override
    void createConsumer(final String tenantId, final Consumer<Message> messageConsumer, final Handler<AsyncResult<MessageConsumer>> setupTracker) {
        downstreamClient.createTelemetryConsumer(tenantId, messageConsumer, setupTracker);
    }

    @Override
    void createProducer(final String tenantId, final Handler<AsyncResult<MessageSender>> setupTracker) {
        honoClient.getOrCreateTelemetrySender(tenantId, setupTracker);
    }

}
