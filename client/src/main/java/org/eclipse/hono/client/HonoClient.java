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

import java.util.Objects;
import java.util.function.Consumer;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.impl.RegistrationClientImpl;
import org.eclipse.hono.client.impl.TelemetryConsumerImpl;
import org.eclipse.hono.client.impl.TelemetrySenderImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AsyncResult;
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

    private final Vertx vertx;
    private final String host;
    private final int port;
    private final String pathSeparator;
    private ProtonConnection connection;
    private final String user;
    private final String password;

    private HonoClient(final HonoClientBuilder builder) {
        if (builder.vertx != null) {
            this.vertx = builder.vertx;
        } else {
            this.vertx = Vertx.vertx();
        }
        this.host = Objects.requireNonNull(builder.host);
        this.port = builder.port;
        this.user = builder.user;
        this.password = builder.password;
        this.pathSeparator = builder.pathSeparator == null ? "/" : builder.pathSeparator;
    }

    public HonoClient connect(final ProtonClientOptions options, final Handler<AsyncResult<HonoClient>> connectionHandler) {

        if (connection != null && !connection.isDisconnected()) {
            LOG.debug("already connected to server [{}:{}]", host, port);
            connectionHandler.handle(Future.succeededFuture(this));
        } else {

            LOG.debug("connecting to server [{}:{}] with user [{}]...", host, port, user);
            final ProtonClient client = ProtonClient.create(vertx);

            client.connect(options, host, port, user, password, conAttempt -> {
                if (conAttempt.succeeded()) {
                    LOG.info("connected to server [{}:{}]", host, port);
                    conAttempt.result()
                        .setHostname("hono")
                        .setContainer("SUBJECT")
                        .openHandler(opened -> {
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
            TelemetrySenderImpl.create(connection, tenantId, creationHandler);
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

    public void shutdown() {
        shutdown(null);
    }

    public void shutdown(final Handler<AsyncResult<Void>> completionHandler) {
        if (connection == null || connection.isDisconnected()) {
            LOG.info("connection to server [{}:{}] already closed", host, port);
            completionHandler.handle(Future.succeededFuture());
        } else {
            LOG.info("closing connection to server [{}:{}]...", host, port);
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
