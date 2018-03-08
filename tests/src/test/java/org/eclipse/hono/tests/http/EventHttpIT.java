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

package org.eclipse.hono.tests.http;

import java.net.HttpURLConnection;
import java.util.function.Consumer;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.util.EventConstants;
import org.junit.runner.RunWith;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.unit.junit.VertxUnitRunner;


/**
 * Integration tests for uploading events to the HTTP adapter.
 *
 */
@RunWith(VertxUnitRunner.class)
public class EventHttpIT extends HttpTestBase {

    @Override
    protected Future<Void> send(String tenantId, String deviceId, Buffer payload) {

        return httpClient.update(
                String.format("/%s/%s/%s", EventConstants.EVENT_ENDPOINT, tenantId, deviceId),
                payload,
                "binary/octet-stream",
                status -> status == HttpURLConnection.HTTP_ACCEPTED);
    }

    @Override
    protected Future<MessageConsumer> createConsumer(String tenantId, Consumer<Message> messageConsumer) {

        return helper.downstreamClient.createEventConsumer(tenantId, messageConsumer, remoteClose -> {});
    }
}
