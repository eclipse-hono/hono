/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
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
import java.util.concurrent.CountDownLatch;

import javax.annotation.PostConstruct;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.util.Strings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import io.vertx.core.Future;
import io.vertx.proton.ProtonClient;
import io.vertx.proton.ProtonClientOptions;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonSender;
import io.vertx.proton.sasl.impl.ProtonSaslPlainImpl;

/**
 * A simple command-line client for interacting with the AMQP adapter.
 */
@Component
@Profile("amqp-adapter-cli")
public class AmqpSend extends AbstractCliClient {

    private ProtonConnection adapterConnection;

    @Value(value = "${message.address}")
    protected String messageAddress;

    @Value(value = "${amqp.host}")
    protected String amqpHost;

    @Value(value = "${amqp.port}")
    protected int amqpPort;

    @Value(value = "${username}")
    protected String username;

    @Value(value = "${password}")
    protected String password;

    @Value(value = "${payload}")
    protected String payload;

    @PostConstruct
    void start() {

        final Message message = ProtonHelper.message(payload);
        message.setAddress(messageAddress);

        final CountDownLatch sent = new CountDownLatch(1);
        connectToAdapter().setHandler(result -> {
            final PrintWriter pw = new PrintWriter(System.out);
            if (result.succeeded()) {
                final ProtonSender sender = adapterConnection.createSender(null);
                sender.openHandler(remoteAttach -> {
                    if (remoteAttach.succeeded()) {
                        sender.send(message, delivery -> {
                            // Logs the delivery state to the console
                            pw.println("\n" + delivery.getRemoteState() + "\n");
                            pw.flush();

                            sender.close();
                            if (adapterConnection != null) {
                                adapterConnection.close();
                            }
                            sent.countDown();
                        });
                    }
                }).open();

            } else {
                pw.println(result.cause());
                pw.flush();
            }
        });

        try {
            sent.await();
            System.exit(0);
        } catch (InterruptedException e) {
            // do-nothing
        }
    }

    // ----------------------------------< Vertx-proton >---

    private Future<Void> connectToAdapter() {
        final Future<Void> result = Future.future();
        final ProtonClientOptions options = new ProtonClientOptions();
        final ProtonClient client = ProtonClient.create(vertx);
        if (!Strings.isNullOrEmpty(username) && !Strings.isNullOrEmpty(password)) {
            // SASL PLAIN authc.
            options.addEnabledSaslMechanism(ProtonSaslPlainImpl.MECH_NAME);
            client.connect(options, amqpHost, amqpPort, username, password, conAttempt -> {
                if (conAttempt.failed()) {
                    result.fail(conAttempt.cause());
                } else {
                    adapterConnection = conAttempt.result();
                    adapterConnection.openHandler(remoteOpen -> {
                        if (remoteOpen.succeeded()) {
                            result.complete();
                        }
                    }).open();
                }
            });
        } else {
            // SASL ANONYMOUS authc.
            client.connect(amqpHost, amqpPort, conAttempt -> {
                if (conAttempt.failed()) {
                    result.fail(conAttempt.cause());
                } else {
                    adapterConnection = conAttempt.result();
                    adapterConnection.openHandler(remoteOpen -> {
                        if (remoteOpen.succeeded()) {
                            result.complete();
                        }
                    }).open();
                }
            });
        }

        return result;
    }
}
