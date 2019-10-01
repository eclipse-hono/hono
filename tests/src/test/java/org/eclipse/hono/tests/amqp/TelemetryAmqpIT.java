/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.tests.amqp;

import java.util.function.Consumer;
import java.util.stream.Stream;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.MessageConsumer;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Future;
import io.vertx.junit5.VertxExtension;
import io.vertx.proton.ProtonQoS;

/**
 * A Telemetry based integration test for the AMQP adapter.
 */
@ExtendWith(VertxExtension.class)
public class TelemetryAmqpIT extends AmqpUploadTestBase {

    private static final String TELEMETRY_ENDPOINT = "telemetry";

    static Stream<ProtonQoS> senderQoSTypes() {
        return Stream.of(ProtonQoS.AT_LEAST_ONCE, ProtonQoS.AT_MOST_ONCE);
    }

    @Override
    protected Future<MessageConsumer> createConsumer(final String tenantId, final Consumer<Message> messageConsumer) {
        return helper.applicationClientFactory.createTelemetryConsumer(tenantId, messageConsumer, close -> {});
    }

    @Override
    protected String getEndpointName() {
        return TELEMETRY_ENDPOINT;
    }

}
