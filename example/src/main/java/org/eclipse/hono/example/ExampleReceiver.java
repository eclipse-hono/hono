/**
 * Copyright (c) 2016, 2017 Bosch Software Innovations GmbH.
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

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.proton.ProtonConnection;

/**
 * Example of a event/telemetry receiver that connects to the Hono Server, waits for incoming messages and logs the message
 * payload if anything is received.
 */
@Component
@Profile("receiver")
public class ExampleReceiver extends AbstractExampleClient {

    private static final String PROFILE_TELEMETRY = "telemetry";
    private static final String PROFILE_EVENT = "event";

    @PostConstruct
    private void start() {

        final Future<MessageConsumer> startupTracker = Future.future();
        startupTracker.setHandler(startup -> {
            if (startup.succeeded()) {
                String consumerType = activeProfiles.contains(PROFILE_EVENT) ? PROFILE_EVENT : PROFILE_TELEMETRY;
                LOG.info("Receiver [tenant: {}, type: {}] created successfully, hit ctrl-c to exit", tenantId, consumerType);
            } else {
                LOG.error("Error occurred during initialization of receiver: {}", startup.cause().getMessage());
                vertx.close();
            }
        });

        final Future<HonoClient> connectionTracker = Future.future();
        client.connect(getClientOptions(), connectionTracker.completer(), this::onDisconnect);
        connectionTracker.compose(honoClient -> {
            onConnectionEstablished(startupTracker.completer());
        }, startupTracker);
    }

    private void onConnectionEstablished(Handler<AsyncResult<MessageConsumer>> handler) {

        if (activeProfiles.contains(PROFILE_EVENT)) {
            client.createEventConsumer(tenantId,
                    (msg) -> handleMessage(PROFILE_EVENT, msg),
                    handler);
        } else {
            // default is telemetry consumer
            client.createTelemetryConsumer(tenantId,
                    msg -> handleMessage(PROFILE_TELEMETRY, msg),
                    handler);
        }
    }

    private void onDisconnect(final ProtonConnection con) {

        // give Vert.x some time to clean up NetClient
        vertx.setTimer(DEFAULT_CONNECT_TIMEOUT_MILLIS, reconnect -> {
            LOG.info("attempting to re-connect to Hono server");
            client.connect(getClientOptions(), connectionAttempt -> onConnectionEstablished(done -> {}), this::onDisconnect);
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
