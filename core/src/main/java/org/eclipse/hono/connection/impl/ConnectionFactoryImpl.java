/*******************************************************************************
 * Copyright (c) 2016, 2020 Contributors to the Eclipse Foundation
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

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.connection.ConnectTimeoutException;
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

    /**
     * {@inheritDoc}
     */
    @Override
    public String getServerRole() {
        return config.getServerRole();
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
        connect(options, username, password, null, closeHandler, disconnectHandler, connectionResultHandler);
    }

    @Override
    public void connect(
            final ProtonClientOptions options,
            final String username,
            final String password,
            final String containerId,
            final Handler<AsyncResult<ProtonConnection>> closeHandler,
            final Handler<ProtonConnection> disconnectHandler,
            final Handler<AsyncResult<ProtonConnection>> connectionResultHandler) {

        Objects.requireNonNull(connectionResultHandler);
        final ProtonClientOptions clientOptions = options != null ? options : createClientOptions();
        final String effectiveUsername = username == null ? config.getUsername() : username;
        final String effectivePassword = password == null ? config.getPassword() : password;
        addOptions(clientOptions, effectiveUsername, effectivePassword);
        final String effectiveContainerId = containerId == null ? getContainerIdDefault() : containerId;

        final ProtonClient client = protonClient != null ? protonClient : ProtonClient.create(vertx);
        logger.debug("connecting to AMQP 1.0 container [{}://{}:{}, role: {}]",
                clientOptions.isSsl() ? "amqps" : "amqp",
                config.getHost(),
                config.getPort(),
                config.getServerRole());

        final AtomicBoolean connectionTimeoutReached = new AtomicBoolean(false);
        final Long connectionTimeoutTimerId = config.getConnectTimeout() > 0
                ? vertx.setTimer(config.getConnectTimeout(), id -> {
                    if (connectionTimeoutReached.compareAndSet(false, true)) {
                        failConnectionAttempt(clientOptions, connectionResultHandler, new ConnectTimeoutException(
                                "connection attempt timed out after " + config.getConnectTimeout() + "ms"));
                    }
                })
                : null;

        client.connect(
                clientOptions,
                config.getHost(),
                config.getPort(),
                effectiveUsername,
                effectivePassword,
                conAttempt -> handleConnectionAttemptResult(conAttempt, effectiveContainerId, connectionTimeoutTimerId,
                        connectionTimeoutReached, clientOptions, closeHandler, disconnectHandler, connectionResultHandler));
    }

    private String getContainerIdDefault() {
        return ConnectionFactory.createContainerId(config.getName(), config.getServerRole(), UUID.randomUUID());
    }

    private void failConnectionAttempt(final ProtonClientOptions clientOptions,
                                       final Handler<AsyncResult<ProtonConnection>> connectionResultHandler, final Throwable cause) {
        logger.debug("can't connect to AMQP 1.0 container [{}://{}:{}, role: {}]: {}",
                clientOptions.isSsl() ? "amqps" : "amqp",
                config.getHost(),
                config.getPort(),
                config.getServerRole(),
                cause.getMessage());
        connectionResultHandler.handle(Future.failedFuture(cause));
    }

    private void handleConnectionAttemptResult(
            final AsyncResult<ProtonConnection> conAttempt,
            final String containerId,
            final Long connectionTimeoutTimerId,
            final AtomicBoolean connectionTimeoutReached,
            final ProtonClientOptions clientOptions,
            final Handler<AsyncResult<ProtonConnection>> closeHandler,
            final Handler<ProtonConnection> disconnectHandler,
            final Handler<AsyncResult<ProtonConnection>> connectionResultHandler) {

        if (connectionTimeoutReached.get()) {
            handleTimedOutConnectionAttemptResult(conAttempt, clientOptions);
            return;
        }

        if (conAttempt.failed()) {
            if (connectionTimeoutTimerId != null) {
                vertx.cancelTimer(connectionTimeoutTimerId);
            }
            failConnectionAttempt(clientOptions, connectionResultHandler, conAttempt.cause());
        } else {

            // at this point the SASL exchange has completed successfully
            logger.debug("connected to AMQP 1.0 container [{}://{}:{}, role: {}], opening connection ...",
                    clientOptions.isSsl() ? "amqps" : "amqp",
                    config.getHost(),
                    config.getPort(),
                    config.getServerRole());
            final ProtonConnection downstreamConnection = conAttempt.result();
            downstreamConnection
                    .setContainer(containerId)
                    .setHostname(config.getAmqpHostname())
                    .openHandler(openCon -> {
                        if (connectionTimeoutTimerId != null) {
                            vertx.cancelTimer(connectionTimeoutTimerId);
                        }
                        downstreamConnection.disconnectHandler(null);

                        if (connectionTimeoutReached.get()) {
                            logTimedOutOpenHandlerResult(openCon, downstreamConnection, clientOptions);
                            // close the connection again
                            closeAndDisconnect(downstreamConnection);
                        } else {
                            if (openCon.succeeded()) {
                                logger.debug("connection to container [{}] at [{}://{}:{}, role: {}] open",
                                        downstreamConnection.getRemoteContainer(),
                                        clientOptions.isSsl() ? "amqps" : "amqp",
                                        config.getHost(),
                                        config.getPort(),
                                        config.getServerRole());
                                downstreamConnection.disconnectHandler(disconnectHandler);
                                downstreamConnection.closeHandler(closeHandler);
                                connectionResultHandler.handle(Future.succeededFuture(downstreamConnection));
                            } else {
                                logFailedOpenHandlerResult(openCon, downstreamConnection, clientOptions);
                                // close the connection again
                                closeAndDisconnect(downstreamConnection);

                                connectionResultHandler.handle(Future.failedFuture(openCon.cause()));
                            }
                        }
                    }).disconnectHandler(disconnectedCon -> {
                        if (connectionTimeoutTimerId != null) {
                            vertx.cancelTimer(connectionTimeoutTimerId);
                        }
                        if (connectionTimeoutReached.get()) {
                            logger.warn("ignoring error - connection attempt already timed out: can't open connection to container [{}] at [{}://{}:{}, role: {}]: {}",
                                    downstreamConnection.getRemoteContainer(),
                                    clientOptions.isSsl() ? "amqps" : "amqp",
                                    config.getHost(),
                                    config.getPort(),
                                    config.getServerRole(),
                                    "underlying connection was disconnected while opening AMQP connection");
                        } else {
                            logger.warn("can't open connection to container [{}] at [{}://{}:{}, role: {}]: {}",
                                    downstreamConnection.getRemoteContainer(),
                                    clientOptions.isSsl() ? "amqps" : "amqp",
                                    config.getHost(),
                                    config.getPort(),
                                    config.getServerRole(),
                                    "underlying connection was disconnected while opening AMQP connection");
                            connectionResultHandler.handle(Future
                                    .failedFuture("underlying connection was disconnected while opening AMQP connection"));
                        }
                    }).open();
        }
    }

    private void closeAndDisconnect(final ProtonConnection downstreamConnection) {
        downstreamConnection.closeHandler(null);
        downstreamConnection.disconnectHandler(null);
        downstreamConnection.close();
        downstreamConnection.disconnect();
    }

    private void handleTimedOutConnectionAttemptResult(final AsyncResult<ProtonConnection> conAttempt, final ProtonClientOptions clientOptions) {
        if (conAttempt.succeeded()) {
            logger.debug("ignoring successful connection attempt to AMQP 1.0 container [{}://{}:{}, role: {}]: attempt already timed out",
                    clientOptions.isSsl() ? "amqps" : "amqp",
                    config.getHost(),
                    config.getPort(),
                    config.getServerRole());
            final ProtonConnection downstreamConnection = conAttempt.result();
            closeAndDisconnect(downstreamConnection);
        } else {
            logger.debug("ignoring failed connection attempt to AMQP 1.0 container [{}://{}:{}, role: {}]: attempt already timed out",
                    clientOptions.isSsl() ? "amqps" : "amqp",
                    config.getHost(),
                    config.getPort(),
                    config.getServerRole(),
                    conAttempt.cause());
        }
    }

    private void logTimedOutOpenHandlerResult(final AsyncResult<ProtonConnection> openConnectionResult,
            final ProtonConnection downstreamConnection, final ProtonClientOptions clientOptions) {
        if (openConnectionResult.succeeded()) {
            logger.debug("ignoring received open frame from container [{}] at [{}://{}:{}, role: {}]: connection attempt already timed out",
                    downstreamConnection.getRemoteContainer(),
                    clientOptions.isSsl() ? "amqps" : "amqp",
                    config.getHost(),
                    config.getPort(),
                    config.getServerRole());
        } else {
            final ErrorCondition error = downstreamConnection.getRemoteCondition();
            if (error == null) {
                logger.warn("ignoring failure to open connection to container [{}] at [{}://{}:{}, role: {}]: attempt already timed out",
                        downstreamConnection.getRemoteContainer(),
                        clientOptions.isSsl() ? "amqps" : "amqp",
                        config.getHost(),
                        config.getPort(),
                        config.getServerRole(),
                        openConnectionResult.cause());
            } else {
                logger.warn("ignoring failure to open connection to container [{}] at [{}://{}:{}, role: {}]: attempt already timed out; error: {} -{}",
                        downstreamConnection.getRemoteContainer(),
                        clientOptions.isSsl() ? "amqps" : "amqp",
                        config.getHost(),
                        config.getPort(),
                        config.getServerRole(),
                        error.getCondition(),
                        error.getDescription());
            }
        }
    }

    private void logFailedOpenHandlerResult(final AsyncResult<ProtonConnection> openCon,
            final ProtonConnection downstreamConnection, final ProtonClientOptions clientOptions) {
        final ErrorCondition error = downstreamConnection.getRemoteCondition();
        if (error == null) {
            logger.warn("can't open connection to container [{}] at [{}://{}:{}, role: {}]",
                    downstreamConnection.getRemoteContainer(),
                    clientOptions.isSsl() ? "amqps" : "amqp",
                    config.getHost(),
                    config.getPort(),
                    config.getServerRole(),
                    openCon.cause());
        } else {
            logger.warn("can't open connection to container [{}] at [{}://{}:{}, role: {}]: {} -{}",
                    downstreamConnection.getRemoteContainer(),
                    clientOptions.isSsl() ? "amqps" : "amqp",
                    config.getHost(),
                    config.getPort(),
                    config.getServerRole(),
                    error.getCondition(),
                    error.getDescription());
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
            final boolean useOpenSsl = isOpenSslAvailable && supportsKeyManagerFactory;

            logger.debug("OpenSSL [available: {}, supports KeyManagerFactory: {}]",
                    isOpenSslAvailable, supportsKeyManagerFactory);

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
            // it is important to use a sorted set here to maintain
            // the list's order
            final Set<String> protocols = new LinkedHashSet<>(config.getSecureProtocols().size());
            config.getSecureProtocols().forEach(p -> {
                logger.debug("enabling secure protocol [{}]", p);
                protocols.add(p);
            });
            clientOptions.setEnabledSecureTransportProtocols(protocols);

            config.getSupportedCipherSuites()
                .forEach(suiteName -> {
                    logger.debug("adding supported cipher suite [{}]", suiteName);
                    clientOptions.addEnabledCipherSuite(suiteName);
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
        options.setConnectTimeout(config.getConnectTimeout());
        options.setHeartbeat(config.getHeartbeatInterval());
        options.setMaxFrameSize(config.getMaxFrameSize());
        options.setReconnectAttempts(0);
        return options;
    }
}
