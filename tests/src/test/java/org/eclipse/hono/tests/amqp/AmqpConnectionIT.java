/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.tests.amqp;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.util.UUID;

import javax.security.sasl.SaslException;

import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.tests.Tenants;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.TenantObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;

import io.vertx.core.json.JsonObject;
import io.vertx.core.net.SelfSignedCertificate;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

/**
 * Integration tests for checking connection to the AMQP adapter.
 *
 */
@RunWith(VertxUnitRunner.class)
public class AmqpConnectionIT extends AmqpAdapterTestBase {

    /**
     * Time out all tests after 5 seconds.
     */
    @Rule
    public Timeout timeout = Timeout.seconds(5);

    /**
     * Logs the currently executing test method name.
     */
    @Before
    public void setup() {

        log.info("running {}", testName.getMethodName());
    }

    /**
     * Closes the connection to the adapter.
     */
    @After
    public void disconnnect() {
        if (connection != null) {
            connection.closeHandler(null);
            connection.close();
            connection = null;
        }
    }

    /**
     * Verifies that the adapter opens a connection to registered devices with credentials.
     *
     * @param ctx The test context
     */
    @Test
    public void testConnectSucceedsForRegisteredDevice(final TestContext ctx) {
        final String tenantId = helper.getRandomTenantId();
        final String deviceId = helper.getRandomDeviceId(tenantId);
        final String password = "secret";
        final Tenant tenant = new Tenant();

        helper.registry
                .addDeviceForTenant(tenantId, tenant, deviceId, password)
        .compose(ok -> connectToAdapter(IntegrationTestSupport.getUsername(deviceId, tenantId), password))
        .setHandler(ctx.asyncAssertSuccess());
    }

    /**
     * Verifies that the adapter rejects connection attempts from unknown devices
     * for which neither registration information nor credentials are on record.
     *
     * @param ctx The test context
     */
    @Test
    public void testConnectFailsForNonExistingDevice(final TestContext ctx) {

        // GIVEN an existing tenant
        final String tenantId = helper.getRandomTenantId();
        final TenantObject tenant = TenantObject.from(tenantId, true);

        helper.registry.addTenant(JsonObject.mapFrom(tenant))
        .compose(ok ->
            // WHEN an unknown device tries to connect
            connectToAdapter(IntegrationTestSupport.getUsername("non-existing", tenantId), "secret"))
        .setHandler(ctx.asyncAssertFailure(t -> {
            // THEN the connection is refused
            ctx.assertTrue(t instanceof SaslException);
        }));
    }

    /**
     * Verifies that the adapter rejects connection attempts from devices
     * using wrong credentials.
     *
     * @param ctx The test context
     */
    @Test
    public void testConnectFailsForWrongCredentials(final TestContext ctx) {

        // GIVEN a registered device
        final String tenantId = helper.getRandomTenantId();
        final String deviceId = helper.getRandomDeviceId(tenantId);
        final String password = "secret";
        final Tenant tenant = new Tenant();

        helper.registry
                .addDeviceForTenant(tenantId, tenant, deviceId, password)
        // WHEN the device tries to connect using a wrong password
        .compose(ok -> connectToAdapter(IntegrationTestSupport.getUsername(deviceId, tenantId), "wrong password"))
        .setHandler(ctx.asyncAssertFailure(t -> {
            // THEN the connection is refused
            ctx.assertTrue(t instanceof SaslException);
        }));
    }

    /**
     * Verifies that the adapter rejects connection attempts from devices belonging
     * to a tenant for which the AMQP adapter has been disabled.
     *
     * @param ctx The test context
     */
    @Test
    public void testConnectFailsForDisabledAdapter(final TestContext ctx) {

        final String tenantId = helper.getRandomTenantId();
        final String deviceId = helper.getRandomDeviceId(tenantId);
        final String password = "secret";

        // GIVEN a tenant for which the AMQP adapter is disabled
        final Tenant tenant = new Tenant();
        Tenants.setAdapterEnabled(tenant, Constants.PROTOCOL_ADAPTER_TYPE_HTTP, true);
        Tenants.setAdapterEnabled(tenant, Constants.PROTOCOL_ADAPTER_TYPE_AMQP, false);
        helper.registry.addDeviceForTenant(tenantId, tenant, deviceId, password)
        // WHEN a device that belongs to the tenant tries to connect to the adapter
        .compose(ok -> connectToAdapter(IntegrationTestSupport.getUsername(deviceId, tenantId), password))
        .setHandler(ctx.asyncAssertFailure(t -> {
                // THEN the connection is refused
            ctx.assertTrue(t instanceof SaslException);
         }));
    }

    /**
     * Verifies that the adapter rejects connection attempts from devices for which
     * credentials exist but for which no registration assertion can be retrieved.
     *
     * @param ctx The test context
     */
    @Test
    public void testConnectFailsForDeletedDevices(final TestContext ctx) {

        final String tenantId = helper.getRandomTenantId();
        final String deviceId = helper.getRandomDeviceId(tenantId);
        final String password = "secret";
        final Tenant tenant = new Tenant();

        helper.registry
                .addDeviceForTenant(tenantId, tenant, deviceId, password)
            .compose(device -> helper.registry.deregisterDevice(tenantId, deviceId))
            .compose(ok -> connectToAdapter(IntegrationTestSupport.getUsername(deviceId, tenantId), password))
            .setHandler(ctx.asyncAssertFailure(t -> {
                // THEN the connection is refused
            }));
    }

    /**
     * Verifies that the AMQP Adapter will fail to authenticate a device whose username does not match the expected pattern
     * {@code [<authId>@<tenantId>]}.
     * 
     * @param context The Vert.x test context.
     */
    @Test
    public void testConnectFailsForInvalidUsernamePattern(final TestContext context) {

        // GIVEN an adapter with a registered device
        final String tenantId = helper.getRandomTenantId();
        final String deviceId = helper.getRandomDeviceId(tenantId);
        final String password = "secret";
        final Tenant tenant = new Tenant();

        helper.registry.addDeviceForTenant(tenantId, tenant, deviceId, password)
        // WHEN the device tries to connect using a malformed username
        .compose(ok -> connectToAdapter(deviceId, "secret"))
        .setHandler(context.asyncAssertFailure(t -> {
            // THEN the SASL handshake fails
            context.assertTrue(t instanceof SaslException);
        }));
    }

    /**
     * Verifies that the adapter fails to authenticate a device if the device's client certificate's signature cannot be
     * validated using the trust anchor that is registered for the tenant that the device belongs to.
     *
     * @param ctx The test context.
     * @throws GeneralSecurityException if the tenant's trust anchor cannot be generated
     */
    @Test
    public void testConnectFailsForNonMatchingTrustAnchor(final TestContext ctx) throws GeneralSecurityException {

        final String tenantId = helper.getRandomTenantId();
        final String deviceId = helper.getRandomDeviceId(tenantId);
        final KeyPair keyPair = helper.newEcKeyPair();

        final SelfSignedCertificate deviceCert = SelfSignedCertificate.create(UUID.randomUUID().toString());

        // GIVEN a tenant configured with a trust anchor
        helper.getCertificate(deviceCert.certificatePath())
                .compose(cert -> {
                    final var tenant = Tenants.createTenantForTrustAnchor(cert, keyPair.getPublic());
                    return helper.registry.addDeviceForTenant(tenantId, tenant, deviceId, cert);
                })
                .compose(ok -> {
                    // WHEN a device tries to connect to the adapter
                    // using a client certificate that cannot be validated
                    // using the trust anchor registered for the device's tenant
                    return connectToAdapter(deviceCert);
                })
                .setHandler(ctx.asyncAssertFailure(t -> {
                    // THEN the connection is not established
                    ctx.assertTrue(t instanceof SaslException);
                }));
    }

}
