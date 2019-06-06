/*******************************************************************************
 * Copyright (c) 2018, 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.tests.registry;

import org.eclipse.hono.client.ConnectionLifecycle;
import org.eclipse.hono.client.CredentialsClientFactory;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.RegistrationClientFactory;
import org.eclipse.hono.client.TenantClientFactory;
import org.eclipse.hono.tests.IntegrationTestSupport;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.TestContext;

/**
 * Helper class to support integration tests for the device registry.
 *
 */
public final class DeviceRegistryAmqpTestSupport {

    private DeviceRegistryAmqpTestSupport() {
        // prevent instantiation
    }

    /**
     * Gets a factory for creating a client for accessing the Tenant service.
     *
     * @param vertx The Vert.x instance to run on, if {@code null} a new Vert.x instance is used.
     * @param username The username to use for authenticating to the service.
     * @param password The password to use for authenticating to the service.
     * @return The factory.
     */
    protected static TenantClientFactory prepareTenantClientFactory(final Vertx vertx, final String username, final String password) {

        return TenantClientFactory.create(HonoConnection.newConnection(vertx, IntegrationTestSupport.getDeviceRegistryProperties(username, password)));
    }

    /**
     * Gets a factory for creating a client for accessing the Credentials service.
     *
     * @param vertx The Vert.x instance to run on, if {@code null} a new Vert.x instance is used.
     * @param username The username to use for authenticating to the service.
     * @param password The password to use for authenticating to the service.
     * @return The factory.
     */
    protected static CredentialsClientFactory prepareCredentialsClientFactory(final Vertx vertx, final String username, final String password) {

        return CredentialsClientFactory.create(HonoConnection.newConnection(vertx, IntegrationTestSupport.getDeviceRegistryProperties(username, password)));
    }

    /**
     * Gets a factory for creating a client for accessing the Device Registration service.
     *
     * @param vertx The Vert.x instance to run on, if {@code null} a new Vert.x instance is used.
     * @param username The username to use for authenticating to the service.
     * @param password The password to use for authenticating to the service.
     * @return The factory.
     */
    protected static RegistrationClientFactory prepareRegistrationClientFactory(final Vertx vertx, final String username, final String password) {

        return RegistrationClientFactory.create(HonoConnection.newConnection(vertx, IntegrationTestSupport.getDeviceRegistryProperties(username, password)));
    }

    /**
     * Closes the connection of the provided factory.
     * <p>
     * Any senders or consumers opened by this client will be implicitly closed as well. Any subsequent attempts to
     * connect this client again will fail.
     *
     * @param ctx The test context that the tests are executed on.
     * @param factory The factory to disconnect.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    protected static void disconnect(final TestContext ctx, final ConnectionLifecycle<?> factory) {

        final Future<Void> clientTracker = Future.future();
        if (factory != null) {
            factory.disconnect(clientTracker);
        } else {
            clientTracker.complete();
        }
        clientTracker.otherwiseEmpty().setHandler(ctx.asyncAssertSuccess());
    }
}
