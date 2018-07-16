/**
 * Copyright (c) 2018 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 */
package org.eclipse.hono.tests.amqp;

import java.util.function.Consumer;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.client.MessageSender;
import org.junit.runner.RunWith;

import io.vertx.core.Future;
import io.vertx.ext.unit.junit.VertxUnitRunner;

/**
 * An Event based integration test for the AMQP adapter.
 */
@RunWith(VertxUnitRunner.class)
public class EventAmqpIT extends AmqpAdapterBase {

    @Override
    protected Future<MessageConsumer> createConsumer(final String tenantId, final Consumer<Message> messageConsumer) {
        return helper.downstreamClient.createEventConsumer(tenantId, messageConsumer, remoteClose -> {
        });
    }

    @Override
    protected Future<MessageSender> createProducer(final String tenantId) {
        return adapterClient.getOrCreateEventSender(tenantId);
    }

}
