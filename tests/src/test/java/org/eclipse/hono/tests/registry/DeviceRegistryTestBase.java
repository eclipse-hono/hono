/**
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.hono.client.ConnectionLifecycle;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Promise;
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
     * Gets the helper to use for running tests.
     * 
     * @return The helper.
     */
    protected abstract IntegrationTestSupport getHelper();

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
     * Closes the connection of the provided factory.
     * <p>
     * Any senders or consumers opened by this client will be implicitly closed as well. Any subsequent attempts to
     * connect this client again will fail.
     *
     * @param ctx The test context that the tests are executed on.
     * @param checkpoint The checkpoint to flag on successful closing.
     * @param factory The factory to disconnect.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    static void disconnect(
            final VertxTestContext ctx,
            final Checkpoint checkpoint,
            final ConnectionLifecycle<?> factory) {

        final Promise<Void> clientTracker = Promise.promise();
        if (factory != null) {
            factory.disconnect(clientTracker);
        } else {
            clientTracker.complete();
        }
        clientTracker.future().otherwiseEmpty().setHandler(ctx.succeeding(ok -> checkpoint.flag()));
    }

}
