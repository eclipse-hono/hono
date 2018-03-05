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

package org.eclipse.hono.tests.registry;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.TestContext;
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.impl.HonoClientImpl;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.tests.IntegrationTestSupport;

/**
 * Helper class to support integration tests for the device registry.
 *
 */
public final class DeviceRegistryAmqpTestSupport {

    private DeviceRegistryAmqpTestSupport() {}

    /**
     * Build a HonoClient to access the device registry service.
     *
     * @param vertx The Vert.x instance to execute the client on, if {@code null} a new Vert.x instance is used.
     * @return The client that is configured for accessing the device registry.
     */
    protected static HonoClient prepareDeviceRegistryClient(final Vertx vertx) {

        final ClientConfigProperties clientProps = new ClientConfigProperties();
        clientProps.setName("test");
        clientProps.setHost(IntegrationTestSupport.HONO_DEVICEREGISTRY_HOST);
        clientProps.setPort(IntegrationTestSupport.HONO_DEVICEREGISTRY_AMQP_PORT);
        clientProps.setUsername(IntegrationTestSupport.HONO_USER);
        clientProps.setPassword(IntegrationTestSupport.HONO_PWD);
        return new HonoClientImpl(vertx, clientProps);

    }

    /**
     * Closes the connection of the provided client to the device registry service.
     * <p>
     * Any senders or consumers opened by this client will be implicitly closed as well. Any subsequent attempts to
     * connect this client again will fail.
     *
     * @param vertx The Vert.x instance on which the client is executed on.
     * @param ctx The test context that the tests are executed on.
     * @param client The client to shutdown.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    protected static void shutdownDeviceRegistryClient(final TestContext ctx, final Vertx vertx, final HonoClient client) {

        final Future<Void> clientTracker = Future.future();
        if (client != null) {
            client.shutdown(clientTracker.completer());
        } else {
            clientTracker.complete();
        }
        clientTracker.otherwiseEmpty().compose(s -> {
            final Future<Void> vertxTracker = Future.future();
            vertx.close(vertxTracker.completer());
            return vertxTracker;
        }).setHandler(ctx.asyncAssertSuccess());
    }

}
