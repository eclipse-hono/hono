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

    private final Vertx         vertx;
    private final String        host;
    private final int           port;
    private final String        pathSeparator;
    private ProtonConnection    connection;

    /**
     * Creates a new client for a Hono server.
     *
     * @param vertx the Vertx instance to use for the client (may be {@code null})
     * @param host the Hono host
     * @param port the Hono port
     * @throws NullPointerException if the given host is {@code null}
     */
    private HonoClient(final Vertx vertx, final String host, final int port, final String pathSeparator) {
        if (vertx != null) {
            this.vertx = vertx;
        } else {
            this.vertx = Vertx.vertx();
        }
        this.pathSeparator = pathSeparator == null ? "/" : pathSeparator;
        this.host = Objects.requireNonNull(host);
        this.port = port;
    }

    /**
     * Creates a new client for a host name and port.
     * 
     * @param host The host name or IP address of the Hono server to connect to.
     * @param port The port to connect to.
     * @return The client for connecting to the Hono server.
     */
    public static HonoClient newInstance(final String host, final int port) {
        return new HonoClient(null, host, port, null);
    }

    /**
     * Creates a new client for a host name, port and path separator.
     * 
     * @param host The host name or IP address of the Hono server to connect to.
     * @param port The port to connect to.
     * @param pathSeparator The separator character to use for resource addresses on the Hono server.
     * @return The client for connecting to the Hono server.
     */
    public static HonoClient newInstance(final String host, final int port, String pathSeparator) {
        return new HonoClient(null, host, port, pathSeparator);
    }

    public static HonoClient newInstance(final Vertx vertx, final String host, final int port) {
        return new HonoClient(vertx, host, port, null);
    }

    public static HonoClient newInstance(final Vertx vertx, final String host, final int port, String pathSeparator) {
        return new HonoClient(vertx, host, port, pathSeparator);
    }

    public HonoClient connect(final ProtonClientOptions options, Handler<AsyncResult<HonoClient>> connectionHandler) {

        if (connection != null && !connection.isDisconnected()) {
            LOG.debug("already connected to server [{}:{}]", host, port);
            connectionHandler.handle(Future.succeededFuture(this));
        } else {

            LOG.debug("connecting to server [{}:{}]...", host, port);
            final ProtonClient client = ProtonClient.create(vertx);

            client.connect(options, host, port, conAttempt -> {
                if (conAttempt.succeeded()) {
                    LOG.info("connected to server [{}:{}]", host, port);
                    conAttempt.result()
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
}
