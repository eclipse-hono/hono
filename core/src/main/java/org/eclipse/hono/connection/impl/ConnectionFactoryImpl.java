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

package org.eclipse.hono.connection.impl;

import java.util.Objects;
import java.util.UUID;

import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.connection.ConnectionFactory;
import org.eclipse.hono.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.handler.ssl.OpenSsl;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.net.KeyCertOptions;
import io.vertx.core.net.OpenSSLEngineOptions;
import io.vertx.core.net.TrustOptions;
import io.vertx.proton.ProtonClient;
import io.vertx.proton.ProtonClientOptions;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.sasl.impl.ProtonSaslExternalImpl;
import io.vertx.proton.sasl.impl.ProtonSaslPlainImpl;

/**
 * A <em>vertx-proton</em> based connection factory.
 */
public final class ConnectionFactoryImpl implements ConnectionFactory {

    private static final Logger logger = LoggerFactory.getLogger(ConnectionFactoryImpl.class);
    private final Vertx vertx;
    private final ClientConfigProperties config;
    private ProtonClient protonClient;

    /**
     * Constructor with the Vert.x instance to use and the configuration 
     * parameters for connecting to the AMQP server as parameters.
     * <p>
     * The <em>name</em> property of the configuration is used as the basis
     * for the local container name which is then appended with a UUID.
     * 
     * @param vertx The Vert.x instance.
     * @param config The configuration parameters.
     * @throws NullPointerException if the parameters are {@code null}.
     */
    public ConnectionFactoryImpl(final Vertx vertx, final ClientConfigProperties config) {
        this.vertx = Objects.requireNonNull(vertx);
        this.config = Objects.requireNonNull(config);
    }

    /**
     * Sets the client object to use for creating the AMQP 1.0 connection.
     * <p>
     * If not set, a client instance will be created when any of the <em>connect</em>
     * methods is invoked.
     * 
     * @param protonClient The client.
     * @throws NullPointerException if the client is {@code null}.
     */
    public void setProtonClient(final ProtonClient protonClient) {
        this.protonClient = Objects.requireNonNull(protonClient);
    }

    @Override
    public String getName() {
        return config.getName();
    }

    @Override
    public String getHost() {
        return config.getHost();
    }

    @Override
    public int getPort() {
        return config.getPort();
    }

    @Override
    public String getPathSeparator() {
        return config.getPathSeparator();
    }

    @Override
    public void connect(
            final ProtonClientOptions options,
            final Handler<AsyncResult<ProtonConnection>> closeHandler,
            final Handler<ProtonConnection> disconnectHandler,
            final Handler<AsyncResult<ProtonConnection>> connectionResultHandler) {
        connect(options, null, null, closeHandler, disconnectHandler, connectionResultHandler);
    }

    @Override
    public void connect(
            final ProtonClientOptions options,
            final String username,
            final String password,
            final Handler<AsyncResult<ProtonConnection>> closeHandler,
            final Handler<ProtonConnection> disconnectHandler,
            final Handler<AsyncResult<ProtonConnection>> connectionResultHandler) {

        if (vertx == null) {
            throw new IllegalStateException("Vert.x instance must be set");
        } else if (config == null) {
            throw new IllegalStateException("Client configuration must be set");
        }

        Objects.requireNonNull(connectionResultHandler);
        final ProtonClientOptions clientOptions = options != null ? options : createClientOptions();
        final String effectiveUsername = username == null ? config.getUsername() : username;
        final String effectivePassword = password == null ? config.getPassword() : password;
        addOptions(clientOptions, effectiveUsername, effectivePassword);

        final ProtonClient client = protonClient != null ? protonClient : ProtonClient.create(vertx);
        logger.debug("connecting to AMQP 1.0 container [{}://{}:{}]", clientOptions.isSsl() ? "amqps" : "amqp",
                config.getHost(), config.getPort());
        client.connect(
                clientOptions,
                config.getHost(),
                config.getPort(),
                effectiveUsername,
                effectivePassword,
                conAttempt -> handleConnectionAttemptResult(conAttempt, clientOptions, closeHandler, disconnectHandler, connectionResultHandler));
    }

    private void handleConnectionAttemptResult(
            final AsyncResult<ProtonConnection> conAttempt,
            final ProtonClientOptions clientOptions,
            final Handler<AsyncResult<ProtonConnection>> closeHandler,
            final Handler<ProtonConnection> disconnectHandler,
            final Handler<AsyncResult<ProtonConnection>> connectionResultHandler) {

        if (conAttempt.failed()) {

            logger.debug("can't connect to AMQP 1.0 container [{}://{}:{}]: {}", clientOptions.isSsl() ? "amqps" : "amqp",
                    config.getHost(), config.getPort(), conAttempt.cause().getMessage());
            connectionResultHandler.handle(Future.failedFuture(conAttempt.cause()));

        } else {

            // at this point the SASL exchange has completed successfully
            logger.debug("connected to AMQP 1.0 container [{}://{}:{}], opening connection ...",
                    clientOptions.isSsl() ? "amqps" : "amqp", config.getHost(), config.getPort());
            final ProtonConnection downstreamConnection = conAttempt.result();
            downstreamConnection
                    .setContainer(String.format("%s-%s", config.getName(), UUID.randomUUID()))
                    .setHostname(config.getAmqpHostname())
                    .openHandler(openCon -> {
                        if (openCon.succeeded()) {
                            logger.debug("connection to container [{}] at [{}://{}:{}] open", downstreamConnection.getRemoteContainer(),
                                    clientOptions.isSsl() ? "amqps" : "amqp", config.getHost(), config.getPort());
                            downstreamConnection.disconnectHandler(disconnectHandler);
                            downstreamConnection.closeHandler(closeHandler);
                            connectionResultHandler.handle(Future.succeededFuture(downstreamConnection));
                        } else {
                            final ErrorCondition error = downstreamConnection.getRemoteCondition();
                            if (error == null) {
                                logger.warn("can't open connection to container [{}] at [{}://{}:{}]", downstreamConnection.getRemoteContainer(),
                                        clientOptions.isSsl() ? "amqps" : "amqp", config.getHost(), config.getPort(), openCon.cause());
                            } else {
                                logger.warn("can't open connection to container [{}] at [{}://{}:{}]: {} -{}",
                                        downstreamConnection.getRemoteContainer(), clientOptions.isSsl() ? "amqps" : "amqp",
                                        config.getHost(), config.getPort(), error.getCondition(), error.getDescription());
                            }
                            connectionResultHandler.handle(Future.failedFuture(openCon.cause()));
                        }
                    }).disconnectHandler(disconnectedCon -> {
                        logger.warn("can't open connection to container [{}] at [{}://{}:{}]: {}",
                                downstreamConnection.getRemoteContainer(),
                                clientOptions.isSsl() ? "amqps" : "amqp", config.getHost(), config.getPort(),
                                "underlying connection was disconnected while opening AMQP connection");
                        connectionResultHandler.handle(Future
                                .failedFuture("underlying connection was disconnected while opening AMQP connection"));
                    }).open();
        }
    }

    private void addOptions(final ProtonClientOptions clientOptions, final String username, final String password) {

        addTlsTrustOptions(clientOptions);
        if (!Strings.isNullOrEmpty(username) && !Strings.isNullOrEmpty(password)) {
            clientOptions.addEnabledSaslMechanism(ProtonSaslPlainImpl.MECH_NAME);
        } else {
            addTlsKeyCertOptions(clientOptions);
        }
    }

    private void addTlsTrustOptions(final ProtonClientOptions clientOptions) {

        if (config.isTlsEnabled()) {
            clientOptions.setSsl(true);
        }

        if (clientOptions.getTrustOptions() == null) {
            final TrustOptions trustOptions = config.getTrustOptions();
            if (trustOptions != null) {
                clientOptions.setSsl(true).setTrustOptions(trustOptions);
            }
        }

        if (clientOptions.isSsl()) {

            final boolean isOpenSslAvailable = OpenSsl.isAvailable();
            final boolean supportsKeyManagerFactory =  OpenSsl.supportsKeyManagerFactory();
            final boolean supportsHostnameValidation = OpenSsl.supportsHostnameValidation();
            final boolean useOpenSsl = isOpenSslAvailable && supportsKeyManagerFactory &&
                    (supportsHostnameValidation || !config.isHostnameVerificationRequired());

            logger.debug("OpenSSL [available: {}, supports KeyManagerFactory: {}, supports Hostname validation: {}]",
                    isOpenSslAvailable, supportsKeyManagerFactory, supportsHostnameValidation);

            if (useOpenSsl) {
                logger.debug("using OpenSSL [version: {}] instead of JDK's default SSL engine",
                        OpenSsl.versionString());
                clientOptions.setSslEngineOptions(new OpenSSLEngineOptions());
            } else {
                logger.debug("using JDK's default SSL engine");
            }

            if (config.isHostnameVerificationRequired()) {
                clientOptions.setHostnameVerificationAlgorithm("HTTPS");
            } else {
                clientOptions.setHostnameVerificationAlgorithm("");
            }
            clientOptions.getEnabledSecureTransportProtocols()
                .forEach(protocol -> clientOptions.removeEnabledSecureTransportProtocol(protocol));
            config.getSecureProtocols().forEach(protocol -> {
                logger.debug("enabling secure protocol [{}]", protocol);
                clientOptions.addEnabledSecureTransportProtocol(protocol);
            });
        }
    }

    private void addTlsKeyCertOptions(final ProtonClientOptions clientOptions) {

        if (clientOptions.getKeyCertOptions() == null) {
            final KeyCertOptions keyCertOptions = config.getKeyCertOptions();
            if (keyCertOptions != null) {
                clientOptions.setSsl(true).setKeyCertOptions(keyCertOptions);
                clientOptions.addEnabledSaslMechanism(ProtonSaslExternalImpl.MECH_NAME);
            }
        }
    }

    private ProtonClientOptions createClientOptions() {
        final ProtonClientOptions options = new ProtonClientOptions();
        return options;
    }
}
