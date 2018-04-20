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
import org.eclipse.hono.util.TelemetryConstants;
import org.junit.runner.RunWith;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.unit.junit.VertxUnitRunner;


/**
 * Integration tests for uploading telemetry data to the HTTP adapter.
 *
 */
@RunWith(VertxUnitRunner.class)
public class TelemetryHttpIT extends HttpTestBase {

    @Override
    protected Future<MultiMap> send(
            final String origin,
            final String tenantId,
            final String deviceId,
            final String password,
            final Buffer payload) {

        return httpClient.create(
                String.format("/%s", TelemetryConstants.TELEMETRY_ENDPOINT),
                payload,
                MultiMap.caseInsensitiveMultiMap()
                    .add(HttpHeaders.CONTENT_TYPE, "binary/octet-stream")
                    .add(HttpHeaders.AUTHORIZATION, getBasicAuth(tenantId, deviceId, password))
                    .add(HttpHeaders.ORIGIN, origin),
                statusCode -> statusCode == HttpURLConnection.HTTP_ACCEPTED);
    }

    @Override
    protected Future<MessageConsumer> createConsumer(final String tenantId, final Consumer<Message> messageConsumer) {

        return helper.downstreamClient.createTelemetryConsumer(tenantId, messageConsumer, remoteClose -> {});
    }
}
