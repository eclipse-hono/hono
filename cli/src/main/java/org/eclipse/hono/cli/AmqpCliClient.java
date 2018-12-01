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


import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;

import io.vertx.core.Future;
import io.vertx.proton.ProtonClient;
import io.vertx.proton.ProtonClientOptions;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonSender;
import io.vertx.proton.sasl.impl.ProtonSaslPlainImpl;

/**
 * Abstract base CLI client for interacting with the AMQP adapter.
 */
public abstract class AmqpCliClient extends AbstractCliClient {

    protected PrintWriter writer = new PrintWriter(System.out);

    protected ProtonConnection adapterConnection;

    private ClientConfigProperties properties = new ClientConfigProperties();

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

    protected Future<ProtonSender> createSender() {
        final Future<ProtonSender> senderTracker = Future.future();
        final ProtonSender sender = adapterConnection.createSender(null);
        sender.openHandler(senderTracker);
        sender.open();
        return senderTracker;
    }

    // ----------------------------------< Vertx-proton >---

    protected Future<ProtonConnection> connectToAdapter() {

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
            if (properties.getKeyCertOptions() != null && properties.getTrustOptions() != null) {
                // SASL EXTERNAL auth
                options.setSsl(true);
                options.setKeyCertOptions(properties.getKeyCertOptions());
                options.setTrustOptions(properties.getTrustOptions());
            } else {
                // SASL ANONYMOUS auth
            }

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
