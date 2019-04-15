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
package org.eclipse.hono.cli.app;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.PostConstruct;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.util.MessageHelper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;

/**
 * A command line client for receiving messages from via Hono's north bound Telemetry and/or Event API
 * <p>
 * Messages are output to stdout.
 * <p>
 * Note that this example intentionally does not support Command &amp; Control and rather is the most simple version of a
 * receiver for downstream data. Please refer to the documentation of Command &amp; Control for the example that supports
 * it (found in the User Guide section).
 */
@Component
@Profile("receiver")
public class Receiver extends AbstractApplicationClient {

    private static final String TYPE_TELEMETRY = "telemetry";
    private static final String TYPE_EVENT = "event";
    private static final String TYPE_ALL = "all";
    /**
     * The type of messages to create a consumer for.
     */
    @Value(value = "${message.type}")
    protected String messageType;

    /**
     * Starts this component.
     * <p>
     * 
     * @return A future indicating the outcome of the startup process.
     */
    @PostConstruct
    Future<CompositeFuture> start() {
        return clientFactory.connect()
                .compose(con -> {
                    clientFactory.addReconnectListener(this::createConsumer);
                    return createConsumer(con);
                })
                .setHandler(this::handleCreateConsumerStatus);
    }

    private CompositeFuture createConsumer(final HonoConnection connection) {

        final Handler<Void> closeHandler = closeHook -> {
            LOG.info("close handler of consumer is called");
            vertx.setTimer(connectionRetryInterval, reconnect -> {
                LOG.info("attempting to re-open the consumer link ...");
                createConsumer(connection);
            });
        };

        @SuppressWarnings("rawtypes")
        final List<Future> consumerFutures = new ArrayList<>();
        if (messageType.equals(TYPE_EVENT) || messageType.equals(TYPE_ALL)) {
            consumerFutures.add(
                    clientFactory.createEventConsumer(tenantId, msg -> handleMessage(TYPE_EVENT, msg), closeHandler));
        }

        if (messageType.equals(TYPE_TELEMETRY) || messageType.equals(TYPE_ALL)) {
            consumerFutures.add(
                    clientFactory.createTelemetryConsumer(tenantId, msg -> handleMessage(TYPE_TELEMETRY, msg), closeHandler));
        }

        if (consumerFutures.isEmpty()) {
            consumerFutures.add(Future.failedFuture(
                    String.format(
                            "Invalid message type [\"%s\"]. Valid types are \"telemetry\", \"event\" or \"all\"",
                            messageType)));
        }
        return CompositeFuture.all(consumerFutures);
    }

    private void handleMessage(final String endpoint, final Message msg) {
        final String deviceId = MessageHelper.getDeviceId(msg);

        final Buffer payload = MessageHelper.getPayload(msg);

        LOG.info("received {} message [device: {}, content-type: {}]: {}", endpoint, deviceId, msg.getContentType(),
                payload);

        if (msg.getApplicationProperties() != null) {
            LOG.info("... with application properties: {}", msg.getApplicationProperties().getValue());
        }
    }

    private void handleCreateConsumerStatus(final AsyncResult<CompositeFuture> startup) {
        if (startup.succeeded()) {
            LOG.info("Receiver [tenant: {}, mode: {}] created successfully, hit ctrl-c to exit", tenantId,
                    messageType);
        } else {
            LOG.error("Error occurred during initialization of receiver: {}", startup.cause().getMessage());
            vertx.close();
        }
    }
}
