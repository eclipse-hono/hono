/**
 * Copyright (c) 2020, 2023 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.hono.example.protocolgateway;

import java.util.Optional;

import org.eclipse.hono.config.ServerConfig;
import org.eclipse.hono.config.ServerOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.smallrye.config.ConfigMapping;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetSocket;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

/**
 * The TCP server that devices connect to.
 *
 */
@Dependent
public class TcpServer {

    private static final Logger LOG = LoggerFactory.getLogger(TcpServer.class);

    private final Vertx vertx;
    private final ServerConfig config;

    private NetServer server;
    private Handler<NetSocket> connectHandler;

    /**
     * @param vertx The vert.x instance to run on.
     * @param serverOptions The server configuration to use.
     */
    @Inject
    public TcpServer(
            final Vertx vertx,
            @ConfigMapping(prefix = "gateway.tcp")
            final ServerOptions serverOptions) {
        this.vertx = vertx;
        this.config = new ServerConfig(serverOptions);
    }

    /**
     * Sets the handler for client connections.
     *
     * @param handler The handler to process connections from clients.
     */
    public void setConnectHandler(final Handler<NetSocket> handler) {
        this.connectHandler = handler;
    }

    /**
     * Starts the server listening on the configured address and port.
     *
     * @return A future indicating the outcome.
     */
    public Future<NetServer> start() {
        if (connectHandler == null) {
            LOG.warn("no connect handler set, server will reject all connections");
            connectHandler = socket -> socket.close();
        }
        final Promise<NetServer> result = Promise.promise();
        vertx.createNetServer()
            .connectHandler(connectHandler)
            .exceptionHandler(this::handleHandshakeException)
            .listen(config.getInsecurePort(6666), config.getInsecurePortBindAddress(), result);
        return result.future()
                .map(s -> {
                    server = s;
                    LOG.info("successfully started TCP server [address: {}, port: {}]",
                            config.getInsecurePortBindAddress(), s.actualPort());
                    return s;
                })
                .recover(t -> {
                    LOG.error("failed to start TCP server [address: {}, port: {}]",
                            config.getInsecurePortBindAddress(), config.getInsecurePort(6666), t);
                    return Future.failedFuture(t);
                });
    }

    private void handleHandshakeException(final Throwable cause) {
        LOG.error("cannot establish connection with client", cause);
    }

    /**
     * Stops the socket server.
     *
     * @return A future indicating the outcome.
     */
    public Future<Void> stop() {
        final Promise<Void> result = Promise.promise();
        Optional.ofNullable(server).ifPresentOrElse(
                s -> s.close(result),
                () -> result.complete());
        return result.future();
    }
}
