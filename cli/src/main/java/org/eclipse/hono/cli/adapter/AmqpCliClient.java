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
import java.util.Optional;
import java.util.concurrent.CountDownLatch;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import org.eclipse.hono.cli.AbstractCliClient;
import org.eclipse.hono.cli.client.ClientConfig;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.util.Strings;

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
public class AmqpCliClient extends AbstractCliClient {

    /**
     * The connection to the AMQP org.eclipse.hono.cli.app.adapter.
     */
    public ProtonConnection adapterConnection;
    /**
     * A writer to stdout.
     */
    protected PrintWriter writer = new PrintWriter(System.out);
    /**
     * The configuration properties to use for connecting
     * to the AMQP org.eclipse.hono.cli.app.adapter.
     */
    protected ClientConfig clientConfig;
    /**
     * To signal the CLI main class of the ended execution.
     */
    protected CountDownLatch latch;

    /**
     * Constructor to create the config environment for the execution of the command.
     *
     * @param vertx The instance of vert.x connection.
     * @param ctx The context of vert.x connection.
     * @param clientConfig The class with all config parameters .
     */
    public AmqpCliClient(final Vertx vertx, final Context ctx, final ClientConfig clientConfig) {
        this.vertx = vertx;
        this.clientConfig = clientConfig;
        this.ctx = ctx;
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
    public Future<ProtonSender> createSender() {
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
    public Future<ProtonConnection> connectToAdapter() {
        final ClientConfigProperties properties = this.clientConfig.honoClientConfig;
        final Promise<ProtonConnection> connectAttempt = Promise.promise();
        final ProtonClientOptions options = new ProtonClientOptions();
        final ProtonClient client = ProtonClient.create(vertx);

        options.setConnectTimeout(properties.getConnectTimeout());
        options.setHeartbeat(properties.getHeartbeatInterval());
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
