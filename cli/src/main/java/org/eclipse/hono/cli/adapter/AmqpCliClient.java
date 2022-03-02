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
package org.eclipse.hono.cli.adapter;

import java.io.PrintWriter;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.cli.AbstractCliClient;
import org.eclipse.hono.client.amqp.config.ClientConfigProperties;
import org.eclipse.hono.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.net.TrustOptions;
import io.vertx.proton.ProtonClient;
import io.vertx.proton.ProtonClientOptions;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonSender;
import io.vertx.proton.sasl.impl.ProtonSaslPlainImpl;

/**
 * Abstract base CLI connection for interacting with Hono's AMQP adapter.
 */
public abstract class AmqpCliClient extends AbstractCliClient {

    /**
     * A writer to stdout.
     */
    protected PrintWriter writer = new PrintWriter(System.out);
    /**
     * The connection to the AMQP org.eclipse.hono.cli.app.adapter.
     */
    protected ProtonConnection adapterConnection;

    private ClientConfigProperties properties = new ClientConfigProperties();

    /**
     * Sets the configuration properties to use for connecting
     * to the AMQP org.eclipse.hono.cli.app.adapter.
     *
     * @param props The properties.
     * @throws NullPointerException if properties are {@code null}.
     */
    @Autowired(required = false)
    public void setClientConfig(final ClientConfigProperties props) {
        this.properties = Objects.requireNonNull(props);
    }

    /**
     * Creates anonymous sender link on the established connection
     * to the AMQP org.eclipse.hono.cli.app.adapter.
     *
     * @return A future containing the sender. The future will be succeeded
     *         once the link is open.
     * @throws IllegalStateException if the connection to the org.eclipse.hono.cli.app.adapter is
     *         not established.
     */
    protected Future<ProtonSender> createSender() {
        if (adapterConnection == null || adapterConnection.isDisconnected()) {
            throw new IllegalStateException("connection to AMQP org.eclipse.hono.cli.app.adapter not established");
        }

        final Promise<ProtonSender> result = Promise.promise();
        final ProtonSender sender = adapterConnection.createSender(null);
        sender.openHandler(result);
        sender.open();
        return result.future();
    }

    /**
     * Connects to the AMQP org.eclipse.hono.cli.app.adapter.
     *
     * @return A future containing the established connection. The future will
     *         be succeeded once the connection is open.
     */
    protected Future<ProtonConnection> connectToAdapter() {

        final Promise<ProtonConnection> connectAttempt = Promise.promise();
        final ProtonClientOptions options = new ProtonClientOptions();
        final ProtonClient client = ProtonClient.create(vertx);

        options.setConnectTimeout(properties.getConnectTimeout());
        options.setHeartbeat(properties.getHeartbeatInterval());
        options.setMaxFrameSize(properties.getMaxFrameSize());
        Optional.ofNullable(properties.getAmqpHostname()).ifPresent(s -> options.setVirtualHost(s));

        addTlsTrustOptions(options, properties);

        if (!Strings.isNullOrEmpty(properties.getUsername()) && !Strings.isNullOrEmpty(properties.getPassword())) {
            // SASL PLAIN auth
            options.addEnabledSaslMechanism(ProtonSaslPlainImpl.MECH_NAME);


            log.info("connecting to AMQP org.eclipse.hono.cli.app.adapter using SASL PLAIN [host: {}, port: {}, username: {}]",
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
                options.setKeyCertOptions(properties.getKeyCertOptions());
            } else {
                // SASL ANONYMOUS auth
            }
            log.info("connecting to AMQP org.eclipse.hono.cli.app.adapter [host: {}, port: {}]", properties.getHost(), properties.getPort());
            client.connect(options, properties.getHost(), properties.getPort(), connectAttempt);
        }

        return connectAttempt.future()
                .compose(unopenedConnection -> {
                    final Promise<ProtonConnection> con = Promise.promise();
                    unopenedConnection.openHandler(con);
                    unopenedConnection.open();
                    return con.future();
                });
    }

    private void addTlsTrustOptions(final ProtonClientOptions clientOptions, final ClientConfigProperties config) {

        if (config.isTlsEnabled()) {
            clientOptions.setSsl(true);
        }

        final TrustOptions trustOptions = config.getTrustOptions();
        if (trustOptions != null) {
            clientOptions.setSsl(true).setTrustOptions(trustOptions);
        }

        if (clientOptions.isSsl()) {
            if (config.isHostnameVerificationRequired()) {
                clientOptions.setHostnameVerificationAlgorithm("HTTPS");
            } else {
                clientOptions.setHostnameVerificationAlgorithm("");
            }
        }
    }
}
