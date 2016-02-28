/**
 * Copyright (c) 2016 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial API and implementation and initial documentation
 */
package org.eclipse.hono.mom.rabbitmq;

import java.io.IOException;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.ExceptionHandler;
import com.rabbitmq.client.TopologyRecoveryException;

/**
 * Utility to create new Connection to AMQP broker with the given configuration.
 */
public final class RabbitMqConnection {
    public static final int     RETRY_TIMEOUT = 10000;
    public static final int     MAX_RETRIES   = 10;
    private static final Logger LOGGER        = LoggerFactory.getLogger(RabbitMqConnection.class);

    /**
     * Creates a new connection.
     *
     * @param amqpUri amqp connection uri
     * @return the new connection
     */
    public static Connection connect(final String amqpUri) {
        ConnectionFactory factory = createConnectionFactory(amqpUri);
        for (int i = 0; i < MAX_RETRIES; i++) {
            try {
                return factory.newConnection();
            } catch (IOException | TimeoutException e) {
                if (i < MAX_RETRIES - 1) {
                    LOGGER.warn("Error initializing AQMP connection to {}. Retry in {}s.", amqpUri,
                            RETRY_TIMEOUT / 1000, e);
                    try {
                        Thread.sleep(RETRY_TIMEOUT);
                    } catch (final InterruptedException interrupted) {
                        LOGGER.info("Waiting for retry was interrupted.");
                    }
                }
            }

        }
        throw new IllegalStateException("Error initializing AQMP connection to " + amqpUri);
    }

    private static ConnectionFactory createConnectionFactory(final String amqpUri) {
        try {
            final ConnectionFactory factory = new ConnectionFactory();

            factory.setUri(amqpUri);
            factory.setRequestedHeartbeat(60);
            factory.setAutomaticRecoveryEnabled(true);
            factory.setTopologyRecoveryEnabled(true);
            factory.setExceptionHandler(new ExceptionHandler() {
                @Override
                public void handleUnexpectedConnectionDriverException(final Connection conn,
                        final Throwable exception) {
                    RabbitMqConnection.LOGGER.warn("UnexpectedConnectionDriverException", exception);
                }

                @Override
                public void handleReturnListenerException(final Channel channel, final Throwable exception) {
                    RabbitMqConnection.LOGGER.warn("ReturnListenerException", exception);
                }

                @Override
                public void handleFlowListenerException(final Channel channel, final Throwable exception) {
                    RabbitMqConnection.LOGGER.warn("FlowListenerException", exception);
                }

                @Override
                public void handleConfirmListenerException(final Channel channel, final Throwable exception) {
                    RabbitMqConnection.LOGGER.warn("ConfirmListenerException", exception);
                }

                @Override
                public void handleBlockedListenerException(final Connection connection, final Throwable exception) {
                    RabbitMqConnection.LOGGER.warn("BlockedListenerException", exception);
                }

                @Override
                public void handleConsumerException(final Channel channel, final Throwable exception,
                        final Consumer consumer,
                        final String consumerTag, final String methodName) {
                    RabbitMqConnection.LOGGER.warn("ConsumerException", exception);
                }

                @Override
                public void handleConnectionRecoveryException(final Connection conn, final Throwable exception) {
                    RabbitMqConnection.LOGGER.warn("ConnectionRecoveryException", exception);
                }

                @Override
                public void handleChannelRecoveryException(final Channel ch, final Throwable exception) {
                    RabbitMqConnection.LOGGER.warn("ChannelRecoveryException", exception);
                }

                @Override
                public void handleTopologyRecoveryException(final Connection conn, final Channel ch,
                        final TopologyRecoveryException exception) {
                    RabbitMqConnection.LOGGER.warn("TopologyRecoveryException", exception);
                }
            });

            return factory;
        } catch (GeneralSecurityException | URISyntaxException e) {
            throw new IllegalArgumentException("Cannot create ConnectionFactory", e);
        }
    }

    private RabbitMqConnection() {
        // no instantiation
    }
}
