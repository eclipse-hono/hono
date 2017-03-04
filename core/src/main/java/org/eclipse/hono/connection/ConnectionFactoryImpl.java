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

package org.eclipse.hono.connection;

import java.util.Objects;
import java.util.UUID;

import org.eclipse.hono.config.HonoClientConfigProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

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
public class ConnectionFactoryImpl implements ConnectionFactory {

    private static final Logger logger = LoggerFactory.getLogger(ConnectionFactoryImpl.class);
    private Vertx                      vertx;
    private HonoClientConfigProperties config;

    /**
     * Sets the Vert.x instance to use.
     * 
     * @param vertx The Vert.x instance.
     * @throws NullPointerException if the instance is {@code null}.
     */
    @Autowired
    public final void setVertx(final Vertx vertx) {
        this.vertx = Objects.requireNonNull(vertx);
    }

    /**
     * Sets the configuration parameters for connecting to the AMQP server.
     * <p>
     * The <em>name</em> property of the configuration is used as the basis
     * for the local container name which is then appended with a UUID.
     *  
     * @param config The configuration parameters.
     * @throws NullPointerException if the parameters are {@code null}.
     */
    @Autowired
    public final void setClientConfig(final HonoClientConfigProperties config) {
        this.config = Objects.requireNonNull(config);
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

        if (vertx == null) {
            throw new IllegalStateException("Vert.x instance must be set");
        } else if (config == null) {
            throw new IllegalStateException("Client configuration must be set");
        }

        Objects.requireNonNull(connectionResultHandler);
        final ProtonClientOptions clientOptions = options != null ? options : new ProtonClientOptions();
        addOptions(clientOptions);

        ProtonClient client = ProtonClient.create(vertx);
        logger.info("connecting to AMQP 1.0 container [{}://{}:{}]", clientOptions.isSsl() ? "amqps" : "amqp",
                config.getHost(), config.getPort());
        client.connect(
                clientOptions,
                config.getHost(),
                config.getPort(),
                config.getUsername(),
                config.getPassword(),
                conAttempt -> handleConnectionAttemptResult(conAttempt, clientOptions, closeHandler, disconnectHandler, connectionResultHandler));
    }

    private void handleConnectionAttemptResult(
            final AsyncResult<ProtonConnection> conAttempt,
            final ProtonClientOptions clientOptions,
            final Handler<AsyncResult<ProtonConnection>> closeHandler,
            final Handler<ProtonConnection> disconnectHandler,
            final Handler<AsyncResult<ProtonConnection>> connectionResultHandler) {

        if (conAttempt.failed()) {
            logger.warn("can't connect to AMQP 1.0 container [{}://{}:{}]", clientOptions.isSsl() ? "amqps" : "amqp",
                    config.getHost(), config.getPort(), conAttempt.cause());
            connectionResultHandler.handle(Future.failedFuture(conAttempt.cause()));
        } else {
            logger.info("connected to AMQP 1.0 container [{}://{}:{}], opening connection ...",
                    clientOptions.isSsl() ? "amqps" : "amqp", config.getHost(), config.getPort());
            ProtonConnection downstreamConnection = conAttempt.result();
            downstreamConnection
                    .setContainer(String.format("%s-%s", config.getName(), UUID.randomUUID()))
                    .setHostname(config.getAmqpHostname())
                    .openHandler(openCon -> {
                        if (openCon.succeeded()) {
                            logger.info("connection to container [{}] at [{}://{}:{}] open", downstreamConnection.getRemoteContainer(),
                                    clientOptions.isSsl() ? "amqps" : "amqp", config.getHost(), config.getPort());
                            downstreamConnection.disconnectHandler(disconnectHandler);
                            downstreamConnection.closeHandler(closeHandler);
                            connectionResultHandler.handle(Future.succeededFuture(downstreamConnection));
                        } else {
                            logger.warn("can't open connection to container [{}] at [{}://{}:{}]", downstreamConnection.getRemoteContainer(),
                                    clientOptions.isSsl() ? "amqps" : "amqp", config.getHost(), config.getPort(), openCon.cause());
                            connectionResultHandler.handle(Future.failedFuture(openCon.cause()));
                        }
                    }).open();
        }
    }

    private void addOptions(final ProtonClientOptions clientOptions) {

        if (config.getUsername() != null && config.getPassword() != null) {
            clientOptions.addEnabledSaslMechanism(ProtonSaslPlainImpl.MECH_NAME);
        }
        addTlsTrustOptions(clientOptions);
        addTlsKeyCertOptions(clientOptions);
    }

    private void addTlsTrustOptions(final ProtonClientOptions clientOptions) {

        if (clientOptions.getTrustOptions() == null) {
            TrustOptions trustOptions = config.getTrustOptions();
            if (trustOptions != null) {
                clientOptions.setSsl(true).setTrustOptions(trustOptions);
                if (config.isHostnameVerificationRequired()) {
                    clientOptions.setHostnameVerificationAlgorithm("HTTPS");
                } else {
                    clientOptions.setHostnameVerificationAlgorithm("");
                }
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

    /**
     * Builder for ConnectionFactory instances.
     */
    public static final class ConnectionFactoryBuilder {

        private Vertx  vertx;
        private final HonoClientConfigProperties properties;

        private ConnectionFactoryBuilder(final HonoClientConfigProperties properties) {
            this.properties = properties;
        }

        /**
         * Creates a new builder using default configuration values.
         * 
         * @return The builder.
         */
        public static ConnectionFactoryBuilder newBuilder() {
            return new ConnectionFactoryBuilder(new HonoClientConfigProperties());
        }

        /**
         * Creates a new builder using given configuration values.
         * 
         * @param config The configuration to use.
         * @return The builder.
         */
        public static ConnectionFactoryBuilder newBuilder(final HonoClientConfigProperties config) {
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
         * Creates a new client based on this builder's configuration properties.
         * 
         * @return The client instance.
         * @throws IllegalStateException if any of the mandatory properties are not set.
         */
        public ConnectionFactory build() {
            if (vertx == null) {
                vertx = Vertx.vertx();
            }
            ConnectionFactoryImpl impl = new ConnectionFactoryImpl();
            impl.setVertx(vertx);
            impl.setClientConfig(properties);
            return impl;
        }
    }
}
