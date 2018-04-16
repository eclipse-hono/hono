/**
 * Copyright (c) 2016, 2018 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 *    Red Hat Inc
 */

package org.eclipse.hono.connection;

import java.util.Objects;
import java.util.UUID;

import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.net.KeyCertOptions;
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
            ProtonConnection downstreamConnection = conAttempt.result();
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
            TrustOptions trustOptions = config.getTrustOptions();
            if (trustOptions != null) {
                clientOptions.setSsl(true).setTrustOptions(trustOptions);
            }
        }

        if (clientOptions.isSsl()) {
            if (config.isHostnameVerificationRequired()) {
                clientOptions.setHostnameVerificationAlgorithm("HTTPS");
            } else {
                clientOptions.setHostnameVerificationAlgorithm("");
            }
        }
    }

    private void addTlsKeyCertOptions(final ProtonClientOptions clientOptions) {

        if (clientOptions.getKeyCertOptions() == null) {
            KeyCertOptions keyCertOptions = config.getKeyCertOptions();
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

    /**
     * Builder for ConnectionFactory instances.
     */
    public static final class ConnectionFactoryBuilder {

        private Vertx  vertx;
        private final ClientConfigProperties properties;

        private ConnectionFactoryBuilder(final ClientConfigProperties properties) {
            this.properties = properties;
        }

        /**
         * Creates a new builder using default configuration values.
         * 
         * @return The builder.
         */
        public static ConnectionFactoryBuilder newBuilder() {
            return new ConnectionFactoryBuilder(new ClientConfigProperties());
        }

        /**
         * Creates a new builder using given configuration values.
         * 
         * @param config The configuration to use.
         * @return The builder.
         */
        public static ConnectionFactoryBuilder newBuilder(final ClientConfigProperties config) {
            return new ConnectionFactoryBuilder(config);
        }

        /**
         * Sets the name the client should use as its container name when connecting to the
         * server.
         * 
         * @param name The client's container name.
         * @return This builder for command chaining.
         */
        public ConnectionFactoryBuilder name(final String name) {
            this.properties.setName(name);
            return this;
        }

        /**
         * Sets the vert.x instance to use for running the AMQP protocol.
         * 
         * @param vertx The vert.x instance to use. A new instance will be created if not set or {@code null}.
         * @return This builder for command chaining.
         */
        public ConnectionFactoryBuilder vertx(final Vertx vertx) {
            this.vertx = vertx;
            return this;
        }

        /**
         * @param host the Hono host
         * @return This builder for command chaining.
         */
        public ConnectionFactoryBuilder host(final String host) {
            this.properties.setHost(host);
            return this;
        }

        /**
         * @param port the Hono port
         * @return This builder for command chaining.
         */
        public ConnectionFactoryBuilder port(final int port) {
            this.properties.setPort(port);
            return this;
        }

        /**
         * @param user username used to authenticate
         * @return This builder for command chaining.
         */
        public ConnectionFactoryBuilder user(final String user) {
            this.properties.setUsername(user);
            return this;
        }

        /**
         * @param password the secret used to authenticate
         * @return This builder for command chaining.
         */
        public ConnectionFactoryBuilder password(final String password) {
            this.properties.setPassword(password);
            return this;
        }

        /**
         * @param pathSeparator the character to use to separate the segments of message addresses.
         * @return This builder for command chaining.
         */
        public ConnectionFactoryBuilder pathSeparator(final String pathSeparator) {
            this.properties.setPathSeparator(pathSeparator);
            return this;
        }

        /**
         * Sets the path to the PKCS12 key store to load certificates of trusted CAs from.
         * 
         * @param path The absolute path to the key store.
         * @return This builder for command chaining.
         */
        public ConnectionFactoryBuilder trustStorePath(final String path) {
            this.properties.setTrustStorePath(path);
            return this;
        }

        /**
         * Sets the password for accessing the PKCS12 key store containing the certificates
         * of trusted CAs.
         * 
         * @param password The password to set.
         * @return This builder for command chaining.
         */
        public ConnectionFactoryBuilder trustStorePassword(final String password) {
            this.properties.setTrustStorePassword(password);
            return this;
        }

        /**
         * Disables matching of the <em>host</em> property against the distinguished name
         * asserted by the server's certificate when connecting using TLS.
         * <p>
         * Verification is enabled by default.
         * 
         * @return This builder for command chaining.
         */
        public ConnectionFactoryBuilder disableHostnameVerification() {
            this.properties.setHostnameVerificationRequired(false);
            return this;
        }

        /**
         * Explicitly enables encryption and verification of peer identity using TLS.
         * <p>
         * TLS is disabled by default but is implicitly enabled by setting a trust store.
         * <p>
         * When no trust store is set, peer identity is verified using the JVM's configured
         * standard trust store.
         * 
         * @return This builder for command chaining.
         */
        public ConnectionFactoryBuilder enableTls() {
            this.properties.setTlsEnabled(true);
            return this;
        }

        /**
         * Creates a new client based on this builder's configuration properties.
         * 
         * @return The client instance.
         * @throws IllegalStateException if any of the mandatory properties are not set.
         */
        public ConnectionFactory build() {
            if (vertx == null) {
                vertx = Vertx.vertx();
            }
            return new ConnectionFactoryImpl(vertx, properties);
        }
    }
}
