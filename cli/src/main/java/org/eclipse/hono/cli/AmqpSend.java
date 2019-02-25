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
package org.eclipse.hono.cli;

import java.io.PrintWriter;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import javax.annotation.PostConstruct;

import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.amqp.transport.DeliveryState;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import io.vertx.core.Future;
import io.vertx.proton.ProtonClient;
import io.vertx.proton.ProtonClientOptions;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonSender;
import io.vertx.proton.sasl.impl.ProtonSaslPlainImpl;

/**
 * A simple command-line client for interacting with the AMQP adapter.
 */
@Component
@Profile("amqp-adapter-cli")
public class AmqpSend extends AbstractCliClient {

    private ClientConfigProperties properties = new ClientConfigProperties();

    /**
     * The address to set on the message being sent.
     */
    @Value(value = "${message.address}")
    private String messageAddress;

    /**
     * The payload to send.
     */
    @Value(value = "${message.payload}")
    private String payload;

    private ProtonConnection adapterConnection;

    /**
     * Sets the configuration properties to use for connecting
     * to the AMQP adapter.
     * 
     * @param props The properties.
     * @throws NullPointerException if properties are {@code null}.
     */
    @Autowired(required = false)
    public void setClientConfig(final ClientConfigProperties props) {
        this.properties = Objects.requireNonNull(props);
    }

    @PostConstruct
    void start() {

        final CompletableFuture<ProtonDelivery> messageSent = new CompletableFuture<>();
        final Message message = ProtonHelper.message(messageAddress, payload);

        ctx.runOnContext(go -> {
            connectToAdapter()
            .compose(con -> {
                adapterConnection = con;
                final Future<ProtonSender> senderTracker = Future.future();
                final ProtonSender sender = adapterConnection.createSender(null);
                sender.openHandler(senderTracker);
                sender.open();
                return senderTracker;
            })
            .map(sender -> {
                sender.send(message, delivery -> {
                    adapterConnection.close();
                    messageSent.complete(delivery);
                });
                return sender;
            })
            .otherwise(t -> {
                messageSent.completeExceptionally(t);
                return null;
            });
        });

        final PrintWriter pw = new PrintWriter(System.out);

        try {
            final ProtonDelivery delivery = messageSent.join();
            // Logs the delivery state to the console
            pw.println();
            printDelivery(delivery, pw);
        } catch (CompletionException e) {
            pw.println(e.getCause());
            pw.flush();
        } catch (CancellationException e) {
            // do-nothing
        }
        System.exit(0);
    }

    private void printDelivery(final ProtonDelivery delivery, final PrintWriter pw) {

        final DeliveryState state = delivery.getRemoteState();
        pw.println(state.getType());
        switch(state.getType()) {
        case Rejected:
            final Rejected rejected = (Rejected) state;
            if (rejected.getError() != null) {
                pw.println(rejected.getError().getCondition() + ": " + rejected.getError().getDescription());
            }
            break;
        default:
            break;
        }
        pw.flush();
    }

    // ----------------------------------< Vertx-proton >---

    private Future<ProtonConnection> connectToAdapter() {

        final Future<ProtonConnection> connectAttempt = Future.future();
        final ProtonClientOptions options = new ProtonClientOptions();
        final ProtonClient client = ProtonClient.create(vertx);

        options.setConnectTimeout(properties.getConnectTimeout());
        options.setHeartbeat(properties.getHeartbeatInterval());
        Optional.ofNullable(properties.getAmqpHostname()).ifPresent(s -> options.setVirtualHost(s));

        if (!Strings.isNullOrEmpty(properties.getUsername()) && !Strings.isNullOrEmpty(properties.getPassword())) {
            // SASL PLAIN auth
            options.addEnabledSaslMechanism(ProtonSaslPlainImpl.MECH_NAME);

            LOG.info("connecting to AMQP adapter using SASL PLAIN [host: {}, port: {}, username: {}]",
                    properties.getHost(), properties.getPort(), properties.getUsername());

            client.connect(
                    options,
                    properties.getHost(),
                    properties.getPort(),
                    properties.getUsername(),
                    properties.getPassword(),
                    connectAttempt);
        } else {
            // SASL ANONYMOUS auth
            LOG.info("connecting to AMQP adapter [host: {}, port: {}]", properties.getHost(), properties.getPort());
            client.connect(options, properties.getHost(), properties.getPort(), connectAttempt);
        }

        return connectAttempt
                .compose(unopenedConnection -> {
                    final Future<ProtonConnection> con = Future.future();
                    unopenedConnection.openHandler(con);
                    unopenedConnection.open();
                    return con;
                });
    }
}
