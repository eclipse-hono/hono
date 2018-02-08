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
 */

package org.eclipse.hono.tests.client;

import java.util.function.Consumer;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.client.MessageSender;
import org.junit.runner.RunWith;

import io.vertx.core.Future;
import io.vertx.ext.unit.junit.VertxUnitRunner;

/**
 * Verifies that a device can send event messages to a Hono server and
 * that the events can be consumed from the downstream host.
 */
@RunWith(VertxUnitRunner.class)
public class DeviceEventClientIT extends ClientTestBase {

    @Override
    Future<MessageSender> createProducer(final String tenantId) {
        return honoClient.getOrCreateEventSender(tenantId, DEVICE_ID);
    }

    @Override
    Future<MessageConsumer> createConsumer(final String tenantId, final Consumer<Message> messageConsumer) {
        return downstreamClient.createEventConsumer(tenantId, messageConsumer, close -> {});
    }
}
