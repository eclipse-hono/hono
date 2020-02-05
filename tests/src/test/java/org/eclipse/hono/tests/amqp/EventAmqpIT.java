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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Consumer;
import java.util.stream.Stream;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.MessageConsumer;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Future;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.proton.ProtonQoS;

/**
 * An Event based integration test for the AMQP adapter.
 */
@ExtendWith(VertxExtension.class)
public class EventAmqpIT extends AmqpUploadTestBase {

    private static final String EVENT_ENDPOINT = "event";

    static Stream<ProtonQoS> senderQoSTypes() {
        // events may only be published using AT LEAST ONCE delivery semantics
        return Stream.of(ProtonQoS.AT_LEAST_ONCE);
    }

    @Override
    protected Future<MessageConsumer> createConsumer(final String tenantId, final Consumer<Message> messageConsumer) {
        return helper.applicationClientFactory.createEventConsumer(tenantId, messageConsumer, close -> {});
    }

    @Override
    protected String getEndpointName() {
        return EVENT_ENDPOINT;
    }

    @Override
    protected void assertAdditionalMessageProperties(final VertxTestContext ctx, final Message msg) {
        // assert that events are marked as "durable"
        ctx.verify(() -> {
            assertThat(msg.isDurable()).isTrue();
        });
    }
}
