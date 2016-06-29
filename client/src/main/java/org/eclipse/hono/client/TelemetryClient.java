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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import javax.annotation.PreDestroy;

import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.apache.qpid.proton.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
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

    public static final String SENDER_TARGET_ADDRESS       = "telemetry/%s";
    public static final String REGISTRATION_TARGET_ADDRESS = "registration/%s";
    public static final String RECEIVER_SOURCE_ADDRESS     = "telemetry/%s";
    public static final int    DEFAULT_RECEIVER_CREDITS    = 20;

    private static final String PROPERTY_NAME_ACTION    = "action";
    private static final String PROPERTY_NAME_DEVICE_ID = "device_id";
    private static final Logger LOG = LoggerFactory.getLogger(TelemetryClient.class);

    private final Vertx                               vertx;
    private final CompletableFuture<ProtonConnection> connection;
    private final Future<ProtonSender>                telemetrySender    = Future.future();
    private final Future<ProtonSender>                registrationSender = Future.future();
    private final Map<String, Future<Integer>>        replyMap = new HashMap<>();
    private final AtomicLong                          messageTagCounter  = new AtomicLong();
    private final String                              host;
    private final int                                 port;
    private final String                              tenantId;
    private final String                              registrationReplyAddress;

    /**
     * Instantiates a new TelemetryClient.
     *
     * @param host the Hono host
     * @param port the Hono port
     * @param tenantId the ID of the tenant for which this client instance sends/receives messages
     */
    public TelemetryClient(final String host, final int port, final String tenantId) {
        this(host, port, tenantId, new ProtonClientOptions());
//        this.host = host;
//        this.port = port;
//        this.tenantId = tenantId;
//        vertx = Vertx.vertx();
//        connection = new CompletableFuture<>();
//        connection.whenComplete((o,t) -> {
//            if (t != null) {
//                LOG.info("Connection to Hono ({}:{}) failed.", host, port, t);
//            }
//        });
//        connectToHono(new ProtonClientOptions());
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
        registrationReplyAddress = REGISTRATION_TARGET_ADDRESS + "/" + UUID.randomUUID().toString();
        vertx = Vertx.vertx();
        connection = new CompletableFuture<>();
        connection.whenComplete((o,t) -> {
            if (t != null) {
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

        connection.thenAccept(connection -> {
            LOG.info("Registering handler for registration responses.");
            createReceiver(message -> {
                final Future<Integer> future = replyMap.remove(message.getCorrelationId());
                if (future != null) {
                    final String status = (String) message.getApplicationProperties().getValue().get("status");
                    future.complete(Integer.valueOf(status));
                } else {
                    LOG.info("No handler registered for {}", message.getCorrelationId());
                }
            }, registrationReplyAddress);
        });
    }

    public Future<CompositeFuture> createSender() {
        return createSender(null);
    }

    public Future<CompositeFuture> createSender(final Handler<AsyncResult<?>> closeHandler) {
        connection.thenAccept(connection ->
        {
            final String address = String.format(SENDER_TARGET_ADDRESS, tenantId);
            getProtonSender(closeHandler, telemetrySender, connection, address, ProtonQoS.AT_MOST_ONCE);

            final String registrationAdress = String.format(REGISTRATION_TARGET_ADDRESS, tenantId);
            getProtonSender(closeHandler, registrationSender, connection, registrationAdress, ProtonQoS.AT_LEAST_ONCE);
        });
        return CompositeFuture.all(telemetrySender, registrationSender);
    }

    private ProtonSender getProtonSender(final Handler<AsyncResult<?>> closeHandler, final Future<ProtonSender> future,
            final ProtonConnection connection, final String address, final ProtonQoS qos) {
        final ProtonSender sender = connection.createSender(address);
        sender.setQoS(qos);
        sender.openHandler(senderOpen -> {
            if (senderOpen.succeeded()) {
                LOG.info("sender open to [{}]", senderOpen.result().getRemoteTarget());
                future.complete(senderOpen.result());
            } else {
                future.fail(new IllegalStateException("cannot open sender for telemetry data", senderOpen.cause()));
            }
        }).closeHandler(loggingHandler("sender closed", closeHandler)).open();
        return sender;
    }

    public Future<Void> createReceiver(final Consumer<Message> consumer) {
        return createReceiver(consumer, RECEIVER_SOURCE_ADDRESS);
    }

    public Future<Void> createReceiver(final Consumer<Message> consumer, final String receiverAddress) {
        final Future<Void> future = Future.future();
        connection.thenAccept(connection -> {
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
                        consumer.accept(msg);
                        ProtonHelper.accepted(delivery, true);
                    }).setPrefetch(DEFAULT_RECEIVER_CREDITS).open();
        });
        return future;
    }

    public void send(final String deviceId, final String body) {
        if (telemetrySender.failed() || !telemetrySender.isComplete()) {
            throw new IllegalStateException("Sender is not open, failed to send message.");
        }


        final Message msg = ProtonHelper.message(body);
        final Map<String, String> properties = new HashMap<>();
        properties.put(PROPERTY_NAME_DEVICE_ID, deviceId);
        msg.setApplicationProperties(new ApplicationProperties(properties));
        telemetrySender.result().send(msg);
    }

    public Future<Integer> register(final String deviceId) {
        if (registrationSender.failed() || !registrationSender.isComplete()) {
            throw new IllegalStateException("Sender is not open, failed to send message.");
        }
        final String messageId = "msg-" + messageTagCounter.getAndIncrement();
        final Message msg = ProtonHelper.message();
        final Map<String, String> properties = new HashMap<>();
        properties.put(PROPERTY_NAME_DEVICE_ID, deviceId);
        properties.put(PROPERTY_NAME_ACTION, "register");
        msg.setApplicationProperties(new ApplicationProperties(properties));
        msg.setReplyTo(String.format(registrationReplyAddress, tenantId));
        msg.setMessageId(messageId);
        final Future<Integer> response = Future.future();
        replyMap.put(messageId, response);
        registrationSender.result().send(msg);
        return response;
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

    private <T> Handler<AsyncResult<T>> loggingHandler(final String label) {
        return loggingHandler(label, null);
    }

    private <T> Handler<AsyncResult<T>> loggingHandler(final String label, final Handler<AsyncResult<?>> delegate) {
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
