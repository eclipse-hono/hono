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
package org.eclipse.hono.example;

import javax.annotation.PostConstruct;

import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.amqp.messaging.Data;
import org.apache.qpid.proton.amqp.messaging.Section;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.util.MessageHelper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import io.vertx.core.Future;
import io.vertx.proton.ProtonClientOptions;

/**
 * Example of a event/telemetry receiver that connects to the Hono Server, waits for incoming messages and logs the message
 * payload if anything is received.
 */
@Component
@Profile("receiver")
public class ExampleReceiver extends AbstractExampleClient {

    @PostConstruct
    private void start() {

        final Future<MessageConsumer> startupTracker = Future.future();
        startupTracker.setHandler(done -> {
            if (done.succeeded()) {
                LOG.info("Receiver created successfully, hit ctrl-c to exit");
            } else {
                LOG.error("Error occurred during initialization of message receiver: {}", done.cause().getMessage());
                vertx.close();
            }
        });

        ctx.runOnContext((Void go) -> {
            /* step 1: connect hono client */
            final Future<HonoClient> connectionTracker = Future.future();
            client.connect(new ProtonClientOptions(), connectionTracker.completer());
            connectionTracker.compose(honoClient -> {
                /* step 2: wait for consumers */

                if (activeProfiles.contains("event")) {
                    client.createEventConsumer(tenantId,
                            (msg) -> handleMessage("event", msg),
                            startupTracker.completer());
                } else {
                    // default is telemetry consumer
                    client.createTelemetryConsumer(tenantId,
                            msg -> handleMessage("telemetry", msg),
                            startupTracker.completer());
                }

            }, startupTracker);
        });
    }

    private void handleMessage(final String endpoint, final Message msg) {
        final String deviceId = MessageHelper.getDeviceId(msg);
        final Section body = msg.getBody();
        String content = null;
        if (body instanceof Data) {
            content = ((Data) msg.getBody()).getValue().toString();
        } else if (body instanceof AmqpValue) {
            content = ((AmqpValue) msg.getBody()).getValue().toString();
        }

        LOG.info("received {} message [device: {}, content-type: {}]: {}", endpoint, deviceId, msg.getContentType(), content);

        if (msg.getApplicationProperties() != null) {
            LOG.info("... with application properties: {}", msg.getApplicationProperties().getValue());
        }
    }
}
