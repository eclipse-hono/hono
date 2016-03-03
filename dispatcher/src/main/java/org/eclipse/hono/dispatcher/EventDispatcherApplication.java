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
package org.eclipse.hono.dispatcher;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

import org.eclipse.hono.dispatcher.amqp.AmqpConnection;
import org.eclipse.hono.dispatcher.amqp.AmqpHelper;
import org.eclipse.hono.dispatcher.amqp.DefaultAmqpHelper;
import org.eclipse.hono.dispatcher.amqp.configuration.SystemEnvironment;
import org.eclipse.hono.dispatcher.amqp.configuration.QueueConfigurationLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rabbitmq.client.Connection;

import reactor.Environment;
import reactor.bus.EventBus;

/**
 * Launcher for outgoing event dispatcher.
 */
public final class EventDispatcherApplication {
    private static final String            DEFAULT_AMQP_URI = "amqp://rabbitmq:5672";
    private static final String ENV_VAR_AMQP_URI = "AMQP_URI";
    private static final Logger LOGGER = LoggerFactory.getLogger(EventDispatcherApplication.class);
    private static final CountDownLatch    LATCH       = new CountDownLatch(1);
    public static final SystemEnvironment ENVIRONMENT = new SystemEnvironment();

    /**
     * Java main method. Launches the EventDispatcherApplication.
     *
     * @param args command line arguments
     */
    public static void main(final String[] args) throws IOException {
        Thread.setDefaultUncaughtExceptionHandler(
                (thread, throwable) -> EventDispatcherApplication.LOGGER.error("Uncaught exception: ", throwable));
        new EventDispatcherApplication().run();
    }

    private void run() throws IOException {
        // register shutdown hook
        Runtime.getRuntime().addShutdownHook(shutdownThread());

        // load and create exchange/queue configuration
        final QueueConfigurationLoader queueConfig = QueueConfigurationLoader
                .fromEnv(EventDispatcherApplication.ENVIRONMENT);

        final String amqpUri = determineAmqpUri();
        final Connection connection = AmqpConnection.connect(amqpUri);
        try {
            final Environment environment = Environment.initializeIfEmpty();

            final EventBus eventBus = EventBus.config() //
                    .dispatchErrorHandler(t -> EventDispatcherApplication.LOGGER.warn("Failed to dispatch message.", t)) //
                    .uncaughtErrorHandler(
                            t -> EventDispatcherApplication.LOGGER.warn("Uncaught exception occurred.", t)) //
                    .dispatcher(Environment.newDispatcher(2048, 8))
                    .env(environment).get();

            initDispatcher(eventBus, connection, queueConfig);

            // wait for application shutdown
            waitForShutdown();
        } finally {
            closeConnectionQuietly(connection);
        }
    }

    private String determineAmqpUri() {
        return EventDispatcherApplication.ENVIRONMENT.getOrDefault(ENV_VAR_AMQP_URI, DEFAULT_AMQP_URI);
    }

    protected void initDispatcher(final EventBus reactor, final Connection connection,
            final QueueConfigurationLoader queueConfig) throws IOException {
        final AmqpHelper out = DefaultAmqpHelper.getInstance(connection, queueConfig.getConfig("out"));
        final AmqpHelper in = DefaultAmqpHelper.getInstance(connection, queueConfig.getConfig("in"));
        final AuthorizationService authorizationService = new AuthorizationService();
        new EventDispatcher(reactor, out, in, authorizationService);
    }

    /**
     * Wait until app is shutdown.
     */
    public void waitForShutdown() {
        try {
            EventDispatcherApplication.LATCH.await();
        } catch (final InterruptedException e) {
            EventDispatcherApplication.LOGGER.warn("Latch interrupted, shutdown...");
        }
    }

    private Thread shutdownThread() {
        return new Thread() {
            @Override
            public void run() {
                EventDispatcherApplication.LOGGER.info("Received shutdown signal...");
                if (Environment.alive()) {
                    Environment.terminate();
                }
                EventDispatcherApplication.LATCH.countDown();
            }
        };
    }

    private void closeConnectionQuietly(final Connection connection) {
        if (connection != null) {
            try {
                connection.close();
            } catch (final IOException e) {
                EventDispatcherApplication.LOGGER.warn("Closing AMQP connection failed.", e);
            }
        }
    }
}
