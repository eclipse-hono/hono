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

import java.util.function.Consumer;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.util.TelemetryConstants;
import org.junit.runner.RunWith;

import io.vertx.core.Future;
import io.vertx.ext.unit.junit.VertxUnitRunner;


/**
 * Integration tests for uploading telemetry data to the HTTP adapter.
 *
 */
@RunWith(VertxUnitRunner.class)
public class TelemetryHttpIT extends HttpTestBase {

    private static final String URI = String.format("/%s", TelemetryConstants.TELEMETRY_ENDPOINT);

    @Override
    protected String getEndpointUri() {
        return URI;
    }

    @Override
    protected Future<MessageConsumer> createConsumer(final String tenantId, final Consumer<Message> messageConsumer) {

        return helper.downstreamClient.createTelemetryConsumer(tenantId, messageConsumer, remoteClose -> {});
    }
}
