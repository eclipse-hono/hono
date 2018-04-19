/**
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 1.0 which is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * SPDX-License-Identifier: EPL-1.0
 */
package org.eclipse.hono.messaging;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.service.registration.RegistrationAssertionHelper;
import org.eclipse.hono.service.registration.RegistrationAssertionHelperImpl;
import org.eclipse.hono.util.Constants;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;

/**
 * Base class for implementing stand alone integration tests for Hono Messaging's API endpoints.
 *
 */
public abstract class AbstractStandaloneApiTest {

    /**
     * The prefix to use for device identifiers.
     */
    protected static final String                             DEVICE_PREFIX = "device";
    /**
     * The identifier of the test device.
     */
    protected static final String                             DEVICE_1 = DEVICE_PREFIX + "1";
    /**
     * The user name for connecting to Hono Messaging.
     */
    protected static final String                             USER = "hono-client";
    /**
     * The password for connecting to Hono Messaging.
     */
    protected static final String                             PWD = "secret";
    /**
     * The helper to use for signing and verifying assertions.
     */
    protected static final RegistrationAssertionHelper        assertionHelper;

    /**
     * The Hono client to use for connecting to Hono Messaging.
     */
    protected static HonoClient                         client;
    /**
     * The vert.x instance to run the tests on.
     */
    protected static Vertx                              vertx;
    /**
     * The adapter to forward messages to.
     */
    protected static MessageDiscardingDownstreamAdapter downstreamAdapter;

    /**
     * A logger to be shared with subclasses.
     */
    protected final Logger LOG = LoggerFactory.getLogger(getClass());

    private static final String SECRET = "dajAIOFDHUIFHFSDAJKGFKSDF,SBDFAZUSDJBFFNCLDNC";

    static {
        assertionHelper = RegistrationAssertionHelperImpl.forSharedSecret(SECRET, 10);
    }

    /**
     * Global timeout for all test cases.
     */
    @Rule
    public Timeout globalTimeout = new Timeout(5, TimeUnit.SECONDS);

    /**
     * Resets the downstream message consumer.
     */
    @Before
    public void resetDownstreamMessageConsumer() {

        downstreamAdapter.setMessageConsumer(msg -> {});
    }

    /**
     * Closes the client after all tests have been run.
     * 
     * @param ctx The vert.x test context.
     */
    @AfterClass
    public static void shutdown(final TestContext ctx) {

        final Future<Void> clientTracker = Future.future();
        final Future<Void> vertxTracker = Future.future();
        if (client != null) {
            client.shutdown(clientTracker.completer());
        }
        clientTracker.compose(s -> {
            vertx.close(vertxTracker.completer());
            return vertxTracker;
        }).recover(t -> {
            vertx.close(vertxTracker.completer());
            return vertxTracker;
        }).setHandler(ctx.asyncAssertSuccess());
    }

    /**
     * Gets a message sender for a tenant.
     * 
     * @param tenantId The tenant.
     * @return The sender.
     */
    protected abstract Future<MessageSender> getSender(String tenantId);

    /**
     * Gets a message sender for a device.
     * 
     * @param tenantId The tenant.
     * @param deviceId The identifier of the device.
     * @return The sender.
     */
    protected abstract Future<MessageSender> getSender(String tenantId, String deviceId);

    /**
     * Gets an assertion for a device.
     * 
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The device's identifier.
     * @return The assertion.
     */
    protected String getAssertion(final String tenantId, final String deviceId) {
        return assertionHelper.getAssertion(tenantId, deviceId);
    }

    /**
     * Verifies that proper messages are forwarded downstream.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testMessageUploadSucceedsForRegisteredDevice(final TestContext ctx) {

        int count = 30;
        final Async messagesReceived = ctx.async(count);
        downstreamAdapter.setMessageConsumer(msg -> {
            messagesReceived.countDown();
            LOG.debug("received message [id: {}]", msg.getMessageId());
        });

        String registrationAssertion = getAssertion(Constants.DEFAULT_TENANT, DEVICE_1);
        LOG.debug("got registration assertion for device [{}]: {}", DEVICE_1, registrationAssertion);

        final AtomicReference<MessageSender> senderRef = new AtomicReference<>();
        final Async senderCreation = ctx.async();

        getSender(Constants.DEFAULT_TENANT).setHandler(ctx.asyncAssertSuccess(sender -> {
            senderRef.set(sender);
            senderCreation.complete();
        }));
        senderCreation.await();

        IntStream.range(0, count).forEach(i -> {
            Async waitForCredit = ctx.async();
            LOG.trace("sending message {}", i);
            senderRef.get().send(DEVICE_1, "payload" + i, "text/plain; charset=utf-8", registrationAssertion, done -> waitForCredit.complete());
            waitForCredit.await();
        });
    }
}
