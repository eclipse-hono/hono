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
import io.vertx.proton.ProtonClient;
import io.vertx.proton.ProtonClientOptions;
import io.vertx.proton.ProtonConnection;

/**
 *
 */
public class ConnectionFactoryImpl implements ConnectionFactory {

    private static final Logger logger = LoggerFactory.getLogger(ConnectionFactoryImpl.class);
    private Vertx                      vertx;
    private String                     hostname;
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
    public void setHostname(final String hostname) {
        this.hostname = hostname;
    }

    @Override
    public void connect(
            final ProtonClientOptions options,
            final Handler<AsyncResult<ProtonConnection>> closeHandler,
            final Handler<ProtonConnection> disconnectHandler,
            final Handler<AsyncResult<ProtonConnection>> connectionResultHandler) {

        Objects.requireNonNull(connectionResultHandler);
        ProtonClientOptions clientOptions = options;
        if (clientOptions == null) {
            clientOptions = new ProtonClientOptions();
        }

        ProtonClient client = ProtonClient.create(vertx);
        client.connect(
                options,
                config.getHost(),
                config.getPort(),
                config.getUsername(),
                config.getPassword(),
                conAttempt -> handleConnectionAttemptResult(conAttempt, closeHandler, disconnectHandler, connectionResultHandler));
    }

    private void handleConnectionAttemptResult(
            final AsyncResult<ProtonConnection> conAttempt,
            final Handler<AsyncResult<ProtonConnection>> closeHandler,
            final Handler<ProtonConnection> disconnectHandler,
            final Handler<AsyncResult<ProtonConnection>> connectionResultHandler) {

        if (conAttempt.failed()) {
            logger.warn("can't connect to AMQP 1.0 container [{}:{}]", config.getHost(), config.getPort(), conAttempt.cause());
        } else {
            logger.info("connected to AMQP 1.0 container [{}:{}], opening connection ...",
                    config.getHost(), config.getPort());
            ProtonConnection downstreamConnection = conAttempt.result();
            downstreamConnection
                    .setContainer(config.getName() + UUID.randomUUID())
                    .setHostname(hostname)
                    .openHandler(openCon -> {
                        if (openCon.succeeded()) {
                            logger.info("connection to container [{}] open", downstreamConnection.getRemoteContainer());
                            downstreamConnection.disconnectHandler(disconnectHandler);
                            downstreamConnection.closeHandler(closeHandler);
                            connectionResultHandler.handle(Future.succeededFuture(downstreamConnection));
                        } else {
                            logger.warn("can't open connection to container [{}]", downstreamConnection.getRemoteContainer(), openCon.cause());
                            connectionResultHandler.handle(Future.failedFuture(openCon.cause()));
                        }
                    })
                    .open();
        }
    }
}
