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
 */
package org.eclipse.hono.client;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.impl.RegistrationClientImpl;
import org.eclipse.hono.client.impl.TelemetryConsumerImpl;
import org.eclipse.hono.client.impl.TelemetrySenderImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.proton.ProtonClient;
import io.vertx.proton.ProtonClientOptions;
import io.vertx.proton.ProtonConnection;

/**
 * A helper class for creating Vert.x based clients for Hono's arbitrary APIs.
 */
public class HonoClient {

    private static final Logger LOG = LoggerFactory.getLogger(HonoClient.class);
    private final String host;
    private final int port;
    private final String pathSeparator;
    private final Map<String, TelemetrySender> activeSenders = new ConcurrentHashMap<>();
    private final String user;
    private final String password;
    private ProtonClientOptions clientOptions;
    private ProtonConnection connection;
    private AtomicBoolean connecting = new AtomicBoolean(false);
    private Context connectionContext;
    private ProtonClient protonClient;

    /**
     * Creates a new client for a set of configuration properties.
     * 
     * @param vertx The Vert.x instance to execute the client on, if {@code null} a new Vert.x instance is used.
     * @param config The configuration properties.
     */
    public HonoClient(final Vertx vertx, final HonoClientConfigProperties config) {
        this(HonoClientBuilder.newClient(config).vertx(vertx));
    }

    private HonoClient(final HonoClientBuilder builder) {

        if (builder.vertx != null) {
            this.protonClient = ProtonClient.create(builder.vertx);
        } else {
            this.protonClient = ProtonClient.create(Vertx.vertx());
        }
        this.host = Objects.requireNonNull(builder.host);
        this.port = builder.port;
        this.user = builder.user;
        this.password = builder.password;
        this.pathSeparator = builder.pathSeparator == null ? "/" : builder.pathSeparator;
        
    }

    /**
     * Checks whether this client is connected to the Hono server.
     * 
     * @return {@code true} if this client is connected.
     */
    public boolean isConnected() {
        return connection != null && !connection.isDisconnected();
    }

    public HonoClient connect(final ProtonClientOptions options, final Handler<AsyncResult<HonoClient>> connectionHandler) {
        return connect(options, connectionHandler, null);
    }

    public HonoClient connect(
            final ProtonClientOptions options,
            final Handler<AsyncResult<HonoClient>> connectionHandler,
            final Handler<ProtonConnection> disconnectHandler) {

        Objects.requireNonNull(connectionHandler);

        if (isConnected()) {
            LOG.debug("already connected to server [{}:{}]", host, port);
            connectionHandler.handle(Future.succeededFuture(this));
        } else if (connecting.compareAndSet(false, true)) {
            if (options == null) {
                clientOptions = new ProtonClientOptions();
            } else {
                clientOptions = options;
            }
            LOG.debug("connecting to server [{}:{}] with user [{}]...", host, port, user);

            protonClient.connect(clientOptions, host, port, user, password, conAttempt -> {

                // capture current context for using it when reconnecting
                connectionContext = Vertx.currentContext();

                if (conAttempt.succeeded()) {
                    LOG.info("connected to server [{}:{}]", host, port);

                    if (disconnectHandler != null) {
                        conAttempt.result().disconnectHandler(disconnectHandler);
                    } else {
                        conAttempt.result().disconnectHandler(this::handleRemoteDisconnect);
                    }

                    conAttempt.result()
                        .setHostname("hono")
                        .setContainer("Hono-Client-" + UUID.randomUUID().toString())
                        .openHandler(opened -> {
                            connecting.compareAndSet(true, false);
                            if (opened.succeeded()) {
                                LOG.info("connection to [{}] open", opened.result().getRemoteContainer());
                                connection = opened.result();
                                connectionHandler.handle(Future.succeededFuture(this));
                            } else {
                                LOG.info("cannot open connection to container [{}:{}]", host, port, opened.cause());
                                connectionHandler.handle(Future.failedFuture(opened.cause()));
                            }
                        }).open();
                } else {
                    LOG.info("connection to server [{}:{}] failed", host, port, conAttempt.cause());
                    connectionHandler.handle(Future.failedFuture(conAttempt.cause()));
                }
            });

        } else {
            LOG.debug("already trying to connect to Hono server ...");
        }
        return this;
    }

    private void handleRemoteDisconnect(final ProtonConnection con) {
        LOG.info("connection to Hono has been interrupted");
        activeSenders.clear();
        if (clientOptions.getReconnectInterval() > 0) {
            // assume that we want to reconnect
            clientOptions = new ProtonClientOptions().setReconnectAttempts(clientOptions.getReconnectAttempts()).setReconnectInterval(clientOptions.getReconnectInterval());
            connectionContext.runOnContext(reconnect -> {
                con.disconnect();
                LOG.debug("attempting to re-connect {} time(s) every {} ms", clientOptions.getReconnectAttempts(), clientOptions.getReconnectInterval());
                connect(clientOptions, done -> {});
            });
        }
    }

    public HonoClient getOrCreateTelemetrySender(
            final String tenantId,
            final Handler<AsyncResult<TelemetrySender>> resultHandler) {

        TelemetrySender sender = activeSenders.get(Objects.requireNonNull(tenantId));
        if (sender != null) {
            resultHandler.handle(Future.succeededFuture(sender));
        } else {
            createTelemetrySender(tenantId, resultHandler);
        }
        return this;
    }

    public HonoClient createTelemetrySender(
            final String tenantId,
            final Handler<AsyncResult<TelemetrySender>> creationHandler) {

        Objects.requireNonNull(tenantId);
        if (connection == null || connection.isDisconnected()) {
            creationHandler.handle(Future.failedFuture("client is not connected to Hono (yet)"));
        } else {
            TelemetrySenderImpl.create(connection, tenantId, creationResult -> {
                if (creationResult.succeeded()) {
                    activeSenders.put(tenantId, creationResult.result());
                    creationHandler.handle(Future.succeededFuture(creationResult.result()));
                } else {
                    creationHandler.handle(Future.failedFuture(creationResult.cause()));
                }
            });
        }
        return this;
    }

    public HonoClient createTelemetryConsumer(
            final String tenantId,
            final Consumer<Message> telemetryConsumer,
            final Handler<AsyncResult<TelemetryConsumer>> creationHandler) {

        Objects.requireNonNull(tenantId);
        if (connection == null || connection.isDisconnected()) {
            creationHandler.handle(Future.failedFuture("client is not connected to Hono (yet)"));
        } else {
            TelemetryConsumerImpl.create(connection, tenantId, pathSeparator, telemetryConsumer, creationHandler);
        }
        return this;
    }

    public HonoClient createRegistrationClient(
            final String tenantId,
            final Handler<AsyncResult<RegistrationClient>> creationHandler) {

        Objects.requireNonNull(tenantId);
        if (connection == null || connection.isDisconnected()) {
            creationHandler.handle(Future.failedFuture("client is not connected to Hono (yet)"));
        } else {
            RegistrationClientImpl.create(connection, tenantId, creationHandler);
        }
        return this;
    }

    /**
     * Closes this client's connection to the Hono server.
     * <p>
     * This method waits for at most 5 seconds for the connection to be closed properly.
     */
    public void shutdown() {
        CountDownLatch latch = new CountDownLatch(1);
        shutdown(done -> {
            if (done.succeeded()) {
                latch.countDown();
            } else {
                LOG.error("could not close connection to server", done.cause());
            }
        });
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                LOG.error("shutdown of client timed out");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void shutdown(final Handler<AsyncResult<Void>> completionHandler) {
        if (connection == null || connection.isDisconnected()) {
            LOG.info("connection to server [{}:{}] already closed", host, port);
            completionHandler.handle(Future.succeededFuture());
        } else {
            LOG.info("closing connection to server [{}:{}]...", host, port);
            connection.disconnectHandler(null); // make sure we are not trying to re-connect
            connection.closeHandler(closedCon -> {
                if (closedCon.succeeded()) {
                    LOG.info("closed connection to server [{}:{}]", host, port);
                } else {
                    LOG.info("could not close connection to server [{}:{}]", host, port, closedCon.cause());
                }
                connection.disconnect();
                if (completionHandler != null) {
                    completionHandler.handle(Future.succeededFuture());
                }
            }).close();
        }
    }

    /**
     * Builder for HonoClient instances.
     */
    public static class HonoClientBuilder {

        private Vertx  vertx;
        private String host;
        private int    port;
        private String user;
        private String password;
        private String pathSeparator;

        public static HonoClientBuilder newClient() {
            return new HonoClientBuilder();
        }

        public static HonoClientBuilder newClient(final HonoClientConfigProperties config) {
            HonoClientBuilder builder = new HonoClientBuilder();
            builder
                .host(config.getHost())
                .port(config.getPort())
                .user(config.getUsername())
                .password(config.getPassword())
                .pathSeparator(config.getPathSeparator());
            return builder;
        }

        /**
         * @param vertx the Vertx instance to use for the client (may be {@code null})
         * @return the builder instance
         */
        public HonoClientBuilder vertx(final Vertx vertx) {
            this.vertx = vertx;
            return this;
        }

        /**
         * @param host the Hono host
         * @return the builder instance
         */
        public HonoClientBuilder host(final String host) {
            this.host = host;
            return this;
        }

        /**
         * @param port the Hono port
         * @return
         */
        public HonoClientBuilder port(final int port) {
            this.port = port;
            return this;
        }

        /**
         * @param user username used to authenticate
         * @return the builder instance
         */
        public HonoClientBuilder user(final String user) {
            this.user = user;
            return this;
        }

        /**
         * @param password the secret used to authenticate
         * @return the builder instance
         */
        public HonoClientBuilder password(final String password) {
            this.password = password;
            return this;
        }

        /**
         * @param pathSeparator the character to use to separate the segments of message addresses.
         * @return the builder instance
         */
        public HonoClientBuilder pathSeparator(final String pathSeparator) {
            this.pathSeparator = pathSeparator;
            return this;
        }

        /**
         * @return a new HonoClient instance
         */
        public HonoClient build() {
            return new HonoClient(this);
        }
    }
}
