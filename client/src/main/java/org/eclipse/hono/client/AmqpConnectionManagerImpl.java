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
package org.eclipse.hono.client;

import java.io.IOException;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeoutException;

import org.eclipse.hono.client.api.ConnectionConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.impl.DefaultExceptionHandler;

public class AmqpConnectionManagerImpl implements AmqpConnectionManager {
    private static Connection   connection;

    private static final Object SYNC   = new Object();
    private static final Logger LOGGER = LoggerFactory.getLogger(AmqpConnectionManagerImpl.class);

    @Override
    public void connect(final ConnectionConfig connectionConfig)
            throws URISyntaxException, IOException, TimeoutException, NoSuchAlgorithmException, KeyManagementException {
        synchronized (AmqpConnectionManagerImpl.SYNC) {
            if (AmqpConnectionManagerImpl.connection == null) {
                AmqpConnectionManagerImpl.connection = createConnection(connectionConfig);
            }
        }
    }

    @Override
    public void disconnect() throws IOException {
        synchronized (AmqpConnectionManagerImpl.SYNC) {
            AmqpConnectionManagerImpl.connection.close();
        }
    }

    @Override
    public Channel getChannel() throws IOException {
        if (AmqpConnectionManagerImpl.connection == null) {
            AmqpConnectionManagerImpl.LOGGER.error("Connection doesn't established jet");
            return null;
        } else {
            return AmqpConnectionManagerImpl.connection.createChannel();
        }
    }

    private Connection createConnection(final ConnectionConfig connectionConfig)
            throws NoSuchAlgorithmException, KeyManagementException, URISyntaxException, IOException, TimeoutException {
        final ConnectionFactory factory = new ConnectionFactory();
        final String amqpUri = connectionConfig.getConnectionUri();
        AmqpConnectionManagerImpl.LOGGER.info("Connecting to " + amqpUri);
        factory.setUri(amqpUri);
        factory.setAutomaticRecoveryEnabled(true);
        factory.setExceptionHandler(new DefaultExceptionHandler());
        return factory.newConnection();
    }
}
