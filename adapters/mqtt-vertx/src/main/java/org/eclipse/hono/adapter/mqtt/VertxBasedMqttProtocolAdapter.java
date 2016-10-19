/**
 * Copyright (c) 2016 Red Hat
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Red Hat - initial creation
 */

package org.eclipse.hono.adapter.mqtt;

import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.MqttServer;
import io.vertx.mqtt.MqttServerOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * A Vert.x based Hono protocol adapter for accessing Hono's Telemetry &amp; Registration API using MQTT.
 */
@Component
public class VertxBasedMqttProtocolAdapter extends AbstractVerticle {

    private static final Logger LOG = LoggerFactory.getLogger(VertxBasedMqttProtocolAdapter.class);

    @Value("${hono.mqtt.bindaddress:0.0.0.0}")
    private String bindAddress;

    @Value("${hono.mqtt.listenport:1883}")
    private int listenPort;

    private MqttServer server;

    private void bindMqttServer(Future<Void> startFuture) {

        MqttServerOptions options = new MqttServerOptions();
        options.setHost(this.bindAddress).setPort(this.listenPort);

        this.server = MqttServer.create(this.vertx, options);

        this.server
                .endpointHandler(this::handleEndpointConnection)
                .listen(done -> {

                    if (done.succeeded()) {
                        LOG.info("Hono MQTT adapter running on {}:{}", this.bindAddress, this.server.actualPort());
                        startFuture.complete();
                    } else {
                        LOG.error("error while starting up Hono MQTT adapter", done.cause());
                        startFuture.fail(done.cause());
                    }

                });

    }

    @Override
    public void start(Future<Void> startFuture) throws Exception {

        this.bindMqttServer(startFuture);
    }

    @Override
    public void stop(Future<Void> stopFuture) throws Exception {

        // TODO
    }

    private void handleEndpointConnection(MqttEndpoint endpoint) {

        LOG.info("Connection request from client {}", endpoint.clientIdentifier());

        endpoint.writeConnack(MqttConnectReturnCode.CONNECTION_ACCEPTED, false);
    }
}
