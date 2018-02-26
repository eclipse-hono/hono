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



import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.vertx.core.Vertx;
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.TenantClient;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.TenantConstants;
import org.eclipse.hono.util.TenantObject;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;

import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.proton.ProtonClientOptions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests verifying the behavior of the Device Registry component's Tenant AMQP endpoint.
 */
@RunWith(VertxUnitRunner.class)
public class TenantAmqpIT extends AbstractDeviceRegistryAmqpSupportIT {

    private static final String TEST_HTTP_TENANT = "HTTP_TENANT";

    private static final Vertx vertx = Vertx.vertx();

    private static HonoClient client;
    private static TenantClient tenantClient;

    /**
     * Global timeout for all test cases.
     */
    @Rule
    public Timeout globalTimeout = new Timeout(5, TimeUnit.SECONDS);

    /**
     * Starts the device registry, connects a client and provides a tenant API client.
     * 
     * @param ctx The vert.x test context.
     */
    @BeforeClass
    public static void prepareDeviceRegistry(final TestContext ctx) {

        client = prepareDeviceRegistryClient(vertx);

        client.connect(new ProtonClientOptions())
            .compose(c -> c.getOrCreateTenantClient(TEST_HTTP_TENANT))
            .setHandler(ctx.asyncAssertSuccess(r -> {
                tenantClient = r;
            }));
    }

    /**
     * Shuts down the device registry and closes the client.
     * 
     * @param ctx The vert.x test context.
     */
    @AfterClass
    public static void shutdown(final TestContext ctx) {

        shutdownDeviceRegistryClient(ctx, vertx, client);

    }

    /**
     * Verify that the tenant used for the client can be retrieved with all details.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetTenant(final TestContext ctx) {

        tenantClient
            .get()
            .setHandler(ctx.asyncAssertSuccess(tenantObject -> {
                checkTenantPayloadIsDefaultTenant(tenantObject);
            }));
    }

    private void checkTenantPayloadIsDefaultTenant(final TenantObject tenantObject) {
        assertNotNull(tenantObject);
        assertEquals(tenantObject.getTenantId(), TEST_HTTP_TENANT);
        assertTrue(tenantObject.getEnabled());
        checkTenantAdapterConfigurations(tenantObject);
    }

    private void checkTenantAdapterConfigurations(final TenantObject tenantObject) {
        assertNotNull(tenantObject.getAdapterConfigurations());
        final List<Map<String, String>> adapterConfigurations = tenantObject.getAdapterConfigurations();
        assertEquals(adapterConfigurations.size(), 2);
        for (final Map<String, String> adapterConfiguration: adapterConfigurations) {
            assertNotNull(adapterConfiguration);
            assertTrue(adapterConfiguration.containsKey(TenantConstants.FIELD_ADAPTERS_DEVICE_AUTHENTICATION_REQUIRED));
            assertTrue(adapterConfiguration.containsKey(TenantConstants.FIELD_ENABLED));
            assertTrue(adapterConfiguration.containsKey(TenantConstants.FIELD_ADAPTERS_TYPE));

            final String type = adapterConfiguration.get(TenantConstants.FIELD_ADAPTERS_TYPE);
            switch (type) {
                case Constants.PROTOCOL_ADAPTER_TYPE_HTTP:
                    assertFalse(Boolean.valueOf(adapterConfiguration.get(TenantConstants.FIELD_ADAPTERS_DEVICE_AUTHENTICATION_REQUIRED)));
                    break;
                case Constants.PROTOCOL_ADAPTER_TYPE_MQTT:
                    assertTrue(Boolean.valueOf(adapterConfiguration.get(TenantConstants.FIELD_ADAPTERS_DEVICE_AUTHENTICATION_REQUIRED)));
                    break;
            }
        }
    }

}
