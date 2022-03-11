/**
 * Copyright (c) 2019, 2022 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.hono.tests.registry;

import static com.google.common.truth.Truth.assertThat;

import java.util.Objects;

import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.client.amqp.connection.ConnectionLifecycle;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.util.Lifecycle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxTestContext;

/**
 * A base class for implementing integration tests for
 * Tenant, Device Registration and Credentials service implementations.
 *
 */
abstract class DeviceRegistryTestBase {

    /**
     * A logger to be shared with subclasses.
     */
    protected Logger log = LoggerFactory.getLogger(getClass());

    /**
     * The integration test helper to use for managing registry content.
     */
    private IntegrationTestSupport helper;

    /**
     * Asserts that a given error is a {@link ServiceInvocationException}
     * with a particular error code.
     *
     * @param error The error.
     * @param expectedErrorCode The error code.
     * @throws AssertionError if the assertion fails.
     */
    static void assertErrorCode(final Throwable error, final int expectedErrorCode) {
        assertThat(error).isInstanceOf(ServiceInvocationException.class);
        assertThat(((ServiceInvocationException) error).getErrorCode()).isEqualTo(expectedErrorCode);
    }

    /**
     * Closes the connection of a factory and flags the given checkpoint accordingly.
     * <p>
     * Any senders or consumers opened by this client will be implicitly closed as well. Any subsequent attempts to
     * connect this client again will fail.
     *
     * @param ctx The test context that the tests are executed on.
     * @param checkpoint The checkpoint to flag on successful closing.
     * @param factory The factory to disconnect.
     * @throws NullPointerException if any of the parameters other than factory are {@code null}.
     */
    static void disconnect(
            final VertxTestContext ctx,
            final Checkpoint checkpoint,
            final ConnectionLifecycle<?> factory) {

        Objects.requireNonNull(ctx);
        Objects.requireNonNull(checkpoint);

        final Promise<Void> clientTracker = Promise.promise();
        if (factory != null) {
            factory.disconnect(clientTracker);
        } else {
            clientTracker.complete();
        }
        clientTracker.future().otherwiseEmpty().onComplete(ctx.succeeding(ok -> checkpoint.flag()));
    }

    /**
     * Stops a component and flags the given checkpoint accordingly.
     *
     * @param ctx The test context that the tests are executed on.
     * @param checkpoint The checkpoint to flag on successful stopping.
     * @param component The component to stop.
     * @throws NullPointerException if any of the parameters other than component are {@code null}.
     */
    static void stop(
            final VertxTestContext ctx,
            final Checkpoint checkpoint,
            final Lifecycle component) {

        Objects.requireNonNull(ctx);
        Objects.requireNonNull(checkpoint);

        final Promise<Void> clientTracker = Promise.promise();
        if (component != null) {
            component.stop().onComplete(clientTracker);
        } else {
            clientTracker.complete();
        }
        clientTracker.future().otherwiseEmpty().onComplete(ctx.succeeding(ok -> checkpoint.flag()));
    }

    /**
     * Logs the current test case's display name.
     *
     * @param testInfo The test case meta data.
     */
    @BeforeEach
    public void logTestName(final TestInfo testInfo) {
        log.info("running test {}", testInfo.getDisplayName());
    }

    /**
     * Creates a new integration test helper and connects to the messaging network.
     *
     * @param vertx The vert.x instance to use for the helper.
     * @param ctx The vert.x test context.
     */
    @BeforeEach
    public void setUp(final Vertx vertx, final VertxTestContext ctx) {

        helper = new IntegrationTestSupport(vertx);
        helper.initRegistryClient();
        helper.init().onComplete(ctx.succeedingThenComplete());
    }

    /**
     * Remove the fixture from the device registry if the test had set up any.
     *
     * @param ctx The vert.x test context.
     */
    @AfterEach
    public void cleanupDeviceRegistry(final VertxTestContext ctx) {
        helper.deleteObjects(ctx);
    }

    /**
     * Closes the connection to the Messaging Network.
     *
     * @param ctx The vert.x context.
     */
    @AfterEach
    public void closeConnectionToMessagingNetwork(final VertxTestContext ctx) {
        helper.disconnect().onComplete(r -> ctx.completeNow());
    }

    /**
     * Gets the integration test helper.
     *
     * @return The helper.
     */
    protected final IntegrationTestSupport getHelper() {
        return helper;
    }
}
