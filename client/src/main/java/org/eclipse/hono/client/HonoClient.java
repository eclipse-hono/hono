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
import org.eclipse.hono.client.impl.EventConsumerImpl;
import org.eclipse.hono.client.impl.EventSenderImpl;
import org.eclipse.hono.client.impl.RegistrationClientImpl;
import org.eclipse.hono.client.impl.TelemetryConsumerImpl;
import org.eclipse.hono.client.impl.TelemetrySenderImpl;
import org.eclipse.hono.config.HonoClientConfigProperties;
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
    private final String name;
    private final String host;
    private final int port;
    private final String pathSeparator;
    private final Map<String, MessageSender> activeSenders = new ConcurrentHashMap<>();
    private final Map<String, RegistrationClient> activeRegClients = new ConcurrentHashMap<>();
    private final String user;
    private final String password;
    private ProtonClientOptions clientOptions;
    private ProtonConnection connection;
    private final AtomicBoolean connecting = new AtomicBoolean(false);
    private final Vertx vertx;
    private Context context;

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
            this.vertx = builder.vertx;
        } else {
            this.vertx = Vertx.vertx();
        }
        if (builder.name != null) {
            this.name = builder.name;
        } else {
            this.name = String.format("Hono-Client-%s", UUID.randomUUID().toString());
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

            connection = null;
            if (options == null) {
                clientOptions = new ProtonClientOptions();
            } else {
                clientOptions = options;
            }
            LOG.debug("connecting to server [{}:{}] as user [{}]...", host, port, user);

            final ProtonClient protonClient = ProtonClient.create(vertx);
            protonClient.connect(clientOptions, host, port, user, password, conAttempt -> {

                if (conAttempt.succeeded()) {
                    LOG.info("connected to server [{}:{}]", host, port);

                    conAttempt.result()
                        .setHostname("hono")
                        .setContainer(name)
                        .openHandler(opened -> {
                            connecting.compareAndSet(true, false);
                            if (opened.succeeded()) {
                                LOG.info("connection to [{}] open", opened.result().getRemoteContainer());
                                connection = opened.result();
                                context = Vertx.currentContext();
                                if (disconnectHandler != null) {
                                    connection.disconnectHandler(disconnectHandler);
                                } else {
                                    connection.disconnectHandler(this::onRemoteDisconnect);
                                }

                                connectionHandler.handle(Future.succeededFuture(this));
                            } else {
                                LOG.warn("cannot open connection to container [{}:{}]", host, port, opened.cause());
                                connectionHandler.handle(Future.failedFuture(opened.cause()));
                            }
                        }).open();
                } else {
                    LOG.warn("connection to server [{}:{}] failed", host, port, conAttempt.cause());
                    connectionHandler.handle(Future.failedFuture(conAttempt.cause()));
                }
            });

        } else {
            LOG.debug("already trying to connect to Hono server ...");
        }
        return this;
    }

    private void onRemoteDisconnect(final ProtonConnection con) {

        LOG.warn("lost connection to Hono server [{}:{}]", host, port);
        con.disconnectHandler(null);
        con.disconnect();
        activeSenders.clear();
        activeRegClients.clear();
        if (clientOptions.getReconnectAttempts() != 0) {
            // give Vert.x some time to clean up NetClient
            vertx.setTimer(300, reconnect -> {
                LOG.info("attempting to re-connect to Hono server [{}:{}]", host, port);
                connect(clientOptions, done -> {});
            });
        }
    }

    public HonoClient getOrCreateTelemetrySender( final String tenantId, final Handler<AsyncResult<MessageSender>> resultHandler) {
        Objects.requireNonNull(tenantId);
        getOrCreateSender( "telemetry/" + tenantId,  (creationResult) -> createTelemetrySender(tenantId, creationResult), resultHandler);
        return this;
    }

    public HonoClient getOrCreateEventSender( final String tenantId, final Handler<AsyncResult<MessageSender>> resultHandler) {
        Objects.requireNonNull(tenantId);
        getOrCreateSender("event/" + tenantId, (creationResult) -> createEventSender(tenantId, creationResult), resultHandler);
        return this;
    }

    private void getOrCreateSender(final String key, final Consumer<Handler> newSenderSupplier,
            final Handler<AsyncResult<MessageSender>> resultHandler) {

        final MessageSender sender = activeSenders.get(key);
        if (sender != null && sender.isOpen()) {
            resultHandler.handle(Future.succeededFuture(sender));
        } else {
            final Future<MessageSender> internal = Future.future();
            internal.setHandler(result -> {
                if (result.succeeded()) {
                    activeSenders.put(key, result.result());
                }
                resultHandler.handle(result);
            });
            newSenderSupplier.accept(internal.completer());
        }
    }

    private HonoClient createTelemetrySender(
            final String tenantId,
            final Handler<AsyncResult<MessageSender>> creationHandler) {

        checkConnection().compose(
                connected -> TelemetrySenderImpl.create(context, connection, tenantId, creationHandler),
                Future.<MessageSender> future().setHandler(creationHandler));
        return this;
    }

    public HonoClient createTelemetryConsumer(
            final String tenantId,
            final Consumer<Message> telemetryConsumer,
            final Handler<AsyncResult<MessageConsumer>> creationHandler) {

        checkConnection().compose(
                connected -> TelemetryConsumerImpl.create(context, connection, tenantId, pathSeparator, telemetryConsumer, creationHandler),
                Future.<MessageConsumer> future().setHandler(creationHandler));
        return this;
    }

    public HonoClient createEventConsumer(
            final String tenantId,
            final Consumer<Message> eventConsumer,
            final Handler<AsyncResult<MessageConsumer>> creationHandler) {

        checkConnection().compose(
                connected -> EventConsumerImpl.create(context, connection, tenantId, pathSeparator, eventConsumer, creationHandler),
                Future.<MessageConsumer> future().setHandler(creationHandler));
        return this;
    }

    private HonoClient createEventSender(
            final String tenantId,
            final Handler<AsyncResult<MessageSender>> creationHandler) {

        checkConnection().compose(
                connected -> EventSenderImpl.create(context, connection, tenantId, creationHandler),
                Future.<MessageSender> future().setHandler(creationHandler));
        return this;
    }

    private <T> Future<T> checkConnection() {
        if (connection == null || connection.isDisconnected()) {
            return Future.failedFuture("client is not connected to Hono (yet)");
        } else {
            return Future.succeededFuture();
        }
    }

    public HonoClient getOrCreateRegistrationClient(
            final String tenantId,
            final Handler<AsyncResult<RegistrationClient>> resultHandler) {

        final RegistrationClient regClient = activeRegClients.get(Objects.requireNonNull(tenantId));
        if (regClient != null) {
            resultHandler.handle(Future.succeededFuture(regClient));
        } else {
            createRegistrationClient(tenantId, resultHandler);
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
            RegistrationClientImpl.create(context, connection, tenantId, creationAttempt -> {
                if (creationAttempt.succeeded()) {
                    activeRegClients.put(tenantId, creationAttempt.result());
                    creationHandler.handle(Future.succeededFuture(creationAttempt.result()));
                } else {
                    creationHandler.handle(Future.failedFuture(creationAttempt.cause()));
                }
            });
        }
        return this;
    }

    /**
     * Closes this client's connection to the Hono server.
     * <p>
     * This method waits for at most 5 seconds for the connection to be closed properly.
     */
    public void shutdown() {
        final CountDownLatch latch = new CountDownLatch(1);
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
        } catch (final InterruptedException e) {
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

        private String name;
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
            final HonoClientBuilder builder = new HonoClientBuilder();
            builder
                .name(config.getName())
                .host(config.getHost())
                .port(config.getPort())
                .user(config.getUsername())
                .password(config.getPassword())
                .pathSeparator(config.getPathSeparator());
            return builder;
        }

        /**
         * Sets the name the client should use as its container name when connecting to the
         * server.
         * 
         * @param name The client's container name.
         * @return the builder instance
         */
        public HonoClientBuilder name(final String name) {
            this.name = name;
            return this;
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
         * @return the builder instance
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
