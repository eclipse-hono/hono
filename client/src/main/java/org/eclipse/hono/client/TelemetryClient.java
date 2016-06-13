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

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import javax.annotation.PreDestroy;

import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.amqp.messaging.Data;
import org.apache.qpid.proton.amqp.messaging.Section;
import org.apache.qpid.proton.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.proton.ProtonClient;
import io.vertx.proton.ProtonClientOptions;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonSender;

/**
 * A sample client for uploading and retrieving telemetry data to/from Hono.
 */
public class TelemetryClient {

    private static final Logger LOG = LoggerFactory.getLogger(TelemetryClient.class);
    public static final String SENDER_TARGET_ADDRESS = "telemetry/%s";
    public static final String RECEIVER_SOURCE_ADDRESS = "telemetry/%s";
    public static final String SENDER_TO_PROPERTY = "telemetry/%s/%s";
    public static final int DEFAULT_RECEIVER_CREDITS = 20;

    private final Vertx                               vertx;
    private final CompletableFuture<ProtonConnection> connection;
    private volatile ProtonSender                        honoSender;
    private final AtomicLong messageTagCounter = new AtomicLong();
    private final String host;
    private final int    port;
    private final String tenantId;

    /**
     * Instantiates a new TelemetryClient.
     *
     * @param host the Hono host
     * @param port the Hono port
     * @param tenantId the ID of the tenant for which this client instance sends/receives messages
     */
    public TelemetryClient(final String host, final int port, final String tenantId) {
        this.host = host;
        this.port = port;
        this.tenantId = tenantId;
        vertx = Vertx.vertx();
        connection = new CompletableFuture<>();
        connection.whenComplete((o,t) -> {
            if (t != null)
            {
                LOG.info("Connection to Hono ({}:{}) failed.", host, port, t);
            }
        });
        connectToHono(new ProtonClientOptions());
    }

    /**
     * Instantiates a new TelemetryClient.
     *
     * @param host the Hono host
     * @param port the Hono port
     * @param tenantId the ID of the tenant for which this client instance sends/receives messages
     * @param clientOptions options for configuring the created TelemetryClient
     */
    public TelemetryClient(final String host, final int port, final String tenantId, final ProtonClientOptions clientOptions) {
        this.host = host;
        this.port = port;
        this.tenantId = tenantId;
        vertx = Vertx.vertx();
        connection = new CompletableFuture<>();
        connection.whenComplete((o,t) -> {
            if (t != null)
            {
                LOG.info("Connection to Hono ({}:{}) failed.", host, port, t);
            }
        });
        connectToHono(clientOptions);
    }

    private void connectToHono(final ProtonClientOptions options) {
        final ProtonClient client = ProtonClient.create(vertx);

        client.connect(options, host, port, conAttempt -> {
            if (conAttempt.succeeded()) {
                LOG.debug("connected to Hono server [{}:{}]", host, port);
                final ProtonConnection protonConnection = conAttempt.result();
                protonConnection
                   .openHandler(loggingHandler("connection opened"))
                   .closeHandler(loggingHandler("connection closed"))
                   .open();
                this.connection.complete(protonConnection);
            } else {
                connection.completeExceptionally(conAttempt.cause());
            }
        });
    }

    public Future<Void> createSender() throws Exception {
        return createSender(null);
    }

    public Future<Void> createSender(final Handler<AsyncResult<?>> closeHandler) throws Exception {
        final Future<Void> future = Future.future();
        connection.thenAccept(connection ->
        {
            final String address = String.format(SENDER_TARGET_ADDRESS, tenantId);
            final ProtonSender sender = connection.createSender(address);
            sender.setQoS(ProtonQoS.AT_MOST_ONCE);
            sender.openHandler(senderOpen -> {
                if (senderOpen.succeeded()) {
                    honoSender = senderOpen.result();
                    LOG.info("sender open to [{}]", honoSender.getRemoteTarget());
                    future.complete();
                } else {
                    future.fail(new IllegalStateException("cannot open sender for telemetry data", senderOpen.cause()));
                }
            }).closeHandler(loggingHandler("sender closed", closeHandler)).open();
        });
        return future;
    }

    public Future<Void> createReceiver(final Consumer<String> consumer) throws Exception {
        return createReceiver(consumer, RECEIVER_SOURCE_ADDRESS);
    }

    public Future<Void> createReceiver(final Consumer<String> consumer, final String receiverAddress) throws Exception {
        final Future<Void> future = Future.future();
        connection.thenAccept(connection ->
        {
            final String address = String.format(receiverAddress, tenantId);
            LOG.info("creating receiver at [{}]", address);
            connection.createReceiver(address)
                    .openHandler(recOpen -> {
                        if (recOpen.succeeded()) {
                            LOG.info("reading telemetry data for tenant [{}]", tenantId);
                            future.complete();
                        } else {
                            LOG.info("reading telemetry data for tenant [{}] failed", tenantId, recOpen.cause());
                            future.fail(recOpen.cause());
                        }
                    })
                    .closeHandler(loggingHandler("receiver closed"))
                    .handler((delivery, msg) -> {
                        final Section section = msg.getBody();
                        String content = null;
                        if (section == null) {
                            content = "empty";
                        } else if (section instanceof Data) {
                            content = ((Data) section).toString();
                        } else if (section instanceof AmqpValue) {
                            final AmqpValue amqpValue = (AmqpValue) section;
                            content = String.valueOf (amqpValue.getValue());
                        }
                        consumer.accept(content);
                        ProtonHelper.accepted(delivery, true);
                    }).setPrefetch(DEFAULT_RECEIVER_CREDITS).open();
        });
        return future;
    }

    public void send(final String deviceId, final String body)
    {
        if (honoSender != null) {
            if (!honoSender.isOpen())
            {
                throw new IllegalStateException("Sender is not open, failed to send message.");
            }
            final ByteBuffer b = ByteBuffer.allocate(8);
            b.putLong(messageTagCounter.getAndIncrement());
            b.flip();
            final String address = String.format(SENDER_TO_PROPERTY, tenantId, deviceId);
            final Message msg = ProtonHelper.message(address, body);
            honoSender.send(b.array(), msg);
            b.clear();
        }
        else {
            LOG.info("Create sender first..");
        }
    }

    @PreDestroy
    public void shutdown() {
        shutdown(null);
    }

    @PreDestroy
    public void shutdown(final Handler<AsyncResult<Void>> completionHandler) {
        connection.thenAccept(ProtonConnection::close);
        vertx.close(completionHandler);
    }

    private <T> Handler<AsyncResult<T>> loggingHandler(final String label)
    {
        return loggingHandler(label, null);
    }

    private <T> Handler<AsyncResult<T>> loggingHandler(final String label, final Handler<AsyncResult<?>> delegate)
    {
        return result -> {
            if (result.succeeded()) {
                LOG.info("{} [{}]", label, tenantId);
            } else {
                LOG.info("{} [{}]", label, tenantId, result.cause());
            }
            if (delegate!= null) {
                delegate.handle(result);
            }
        };
    }
}
