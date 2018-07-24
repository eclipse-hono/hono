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
package org.eclipse.hono.tests.client;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

import org.eclipse.hono.tests.IntegrationTestSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;

/**
 * Base class for integration tests for Hono's AMQP 1.0 services.
 */
public abstract class ClientTestBase {

    /**
     * A logger to be used by subclasses.
     */
    protected final Logger log = LoggerFactory.getLogger(getClass());

    static final long   DEFAULT_TEST_TIMEOUT = 15000; // ms

    /**
     * Upload a number of messages to Hono's Telemetry/Event APIs.
     * 
     * @param context The Vert.x test context.
     * @param receiverFactory The factory to use for creating the receiver for consuming
     *                        messages from the messaging network.
     * @param sender The sender for sending messaging to the Hono server.
     * @throws InterruptedException if test execution is interrupted.
     */
    protected void doUploadMessages(
            final TestContext context,
            final Function<Handler<Void>, Future<Void>> receiverFactory,
            final Consumer<String> sender) throws InterruptedException {

        final Async remainingMessages = context.async(IntegrationTestSupport.MSG_COUNT);
        final AtomicInteger messagesSent = new AtomicInteger(0);
        final Async receiverCreation = context.async();

        receiverFactory.apply(msgReceived -> {
            remainingMessages.countDown();
            if (remainingMessages.count() % 200 == 0) {
                log.info("messages received: {}", IntegrationTestSupport.MSG_COUNT - remainingMessages.count());
            }
        }).map(ok -> {
            receiverCreation.complete();
            return null;
        }).otherwise(t -> {
            context.fail(t);
            return null;
        });
        receiverCreation.await();

        while (messagesSent.get() < IntegrationTestSupport.MSG_COUNT) {
            final String payload = "temp: " + messagesSent.getAndIncrement();
            sender.accept(payload);
            if (messagesSent.get() % 200 == 0) {
                log.info("messages sent: {}", messagesSent.get());
            }
        }

        final long timeToWait = Math.max(DEFAULT_TEST_TIMEOUT, Math.round(IntegrationTestSupport.MSG_COUNT * 1.2));
        remainingMessages.await(timeToWait);
    }
}
