/*******************************************************************************
 * Copyright (c) 2016, 2020 Contributors to the Eclipse Foundation
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

import static org.assertj.core.api.Assertions.assertThat;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.security.sasl.SaslException;

import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.tests.Tenants;
import org.eclipse.hono.util.Adapter;
import org.eclipse.hono.util.Constants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.net.SelfSignedCertificate;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Integration tests for checking connection to the AMQP adapter.
 *
 */
@ExtendWith(VertxExtension.class)
@Timeout(timeUnit = TimeUnit.SECONDS, value = 5)
public class AmqpConnectionIT extends AmqpAdapterTestBase {

    /**
     * Logs the currently executing test method name.
     * 
     * @param testInfo Meta info about the test being run.
     */
    @BeforeEach
    public void setup(final TestInfo testInfo) {

        log.info("running {}", testInfo.getDisplayName());
    }

    /**
     * Closes the connection to the adapter.
     */
    @AfterEach
    public void disconnect() {
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
    public void testConnectSucceedsForRegisteredDevice(final VertxTestContext ctx) {
        final String tenantId = helper.getRandomTenantId();
        final String deviceId = helper.getRandomDeviceId(tenantId);
        final String password = "secret";
        final Tenant tenant = new Tenant();

        helper.registry
                .addDeviceForTenant(tenantId, tenant, deviceId, password)
        .compose(ok -> connectToAdapter(IntegrationTestSupport.getUsername(deviceId, tenantId), password))
        .setHandler(ctx.completing());
    }

    /**
     * Verifies that the adapter opens a connection if auto-provisioning is enabled for the device certificate.
     *
     * @param ctx The test context.
     */
    @Test
    public void testConnectSucceedsWithAutoProvisioning(final VertxTestContext ctx) {
        final String tenantId = helper.getRandomTenantId();
        final SelfSignedCertificate deviceCert = SelfSignedCertificate.create(UUID.randomUUID().toString());

        helper.getCertificate(deviceCert.certificatePath())
                .compose(cert -> {
                    final var tenant = Tenants.createTenantForTrustAnchor(cert);
                    tenant.getTrustedCertificateAuthorities().get(0).setAutoProvisioningEnabled(true);
                    return helper.registry.addTenant(tenantId, tenant);
                })
                .compose(ok -> connectToAdapter(deviceCert))
                .setHandler(ctx.completing());
    }

    /**
     * Verifies that the adapter rejects connection attempts from an unknown device for which auto-provisioning is
     * disabled.
     *
     * @param ctx The test context
     */
    @Test
    public void testConnectFailsIfAutoProvisioningIsDisabled(final VertxTestContext ctx) {
        final String tenantId = helper.getRandomTenantId();
        final SelfSignedCertificate deviceCert = SelfSignedCertificate.create(UUID.randomUUID().toString());

        // GIVEN a tenant configured with a trust anchor that does not allow auto-provisioning
        helper.getCertificate(deviceCert.certificatePath())
                .compose(cert -> {
                    final var tenant = Tenants.createTenantForTrustAnchor(cert);
                    tenant.getTrustedCertificateAuthorities().get(0).setAutoProvisioningEnabled(false);
                    return helper.registry.addTenant(tenantId, tenant);
                })
                // WHEN a unknown device tries to connect to the adapter
                // using a client certificate with the trust anchor 
                // registered for the device's tenant
                .compose(ok -> connectToAdapter(deviceCert))
                .setHandler(ctx.failing(t -> {
                    // THEN the connection is refused
                    ctx.verify(() -> assertThat(t).isInstanceOf(SaslException.class));
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that the adapter rejects connection attempts from unknown devices
     * for which neither registration information nor credentials are on record.
     *
     * @param ctx The test context
     */
    @Test
    public void testConnectFailsForNonExistingDevice(final VertxTestContext ctx) {

        // GIVEN an existing tenant
        final String tenantId = helper.getRandomTenantId();
        final Tenant tenant = new Tenant();
        tenant.setEnabled(true);

        helper.registry.addTenant(tenantId, tenant)
        .compose(ok ->
            // WHEN an unknown device tries to connect
            connectToAdapter(IntegrationTestSupport.getUsername("non-existing", tenantId), "secret"))
        .setHandler(ctx.failing(t -> {
            // THEN the connection is refused
            ctx.verify(() -> assertThat(t).isInstanceOf(SaslException.class));
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that the adapter rejects connection attempts from devices
     * using wrong credentials.
     *
     * @param ctx The test context
     */
    @Test
    public void testConnectFailsForWrongCredentials(final VertxTestContext ctx) {

        // GIVEN a registered device
        final String tenantId = helper.getRandomTenantId();
        final String deviceId = helper.getRandomDeviceId(tenantId);
        final String password = "secret";
        final Tenant tenant = new Tenant();

        helper.registry
                .addDeviceForTenant(tenantId, tenant, deviceId, password)
        // WHEN the device tries to connect using a wrong password
        .compose(ok -> connectToAdapter(IntegrationTestSupport.getUsername(deviceId, tenantId), "wrong password"))
        .setHandler(ctx.failing(t -> {
            // THEN the connection is refused
            ctx.verify(() -> assertThat(t).isInstanceOf(SaslException.class));
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that the adapter rejects connection attempts from devices belonging
     * to a tenant for which the AMQP adapter has been disabled.
     *
     * @param ctx The test context
     */
    @Test
    public void testConnectFailsForDisabledAdapter(final VertxTestContext ctx) {

        final String tenantId = helper.getRandomTenantId();
        final String deviceId = helper.getRandomDeviceId(tenantId);
        final String password = "secret";

        // GIVEN a tenant for which the AMQP adapter is disabled
        final Tenant tenant = new Tenant();
        tenant.addAdapterConfig(new Adapter(Constants.PROTOCOL_ADAPTER_TYPE_HTTP).setEnabled(true));
        tenant.addAdapterConfig(new Adapter(Constants.PROTOCOL_ADAPTER_TYPE_AMQP).setEnabled(false));
        helper.registry.addDeviceForTenant(tenantId, tenant, deviceId, password)
        // WHEN a device that belongs to the tenant tries to connect to the adapter
        .compose(ok -> connectToAdapter(IntegrationTestSupport.getUsername(deviceId, tenantId), password))
        .setHandler(ctx.failing(t -> {
            // THEN the connection is refused
            ctx.completeNow();
         }));
    }

    /**
     * Verifies that the adapter rejects connection attempts from devices for which
     * credentials exist but for which no registration assertion can be retrieved.
     *
     * @param ctx The test context
     */
    @Test
    public void testConnectFailsForDeletedDevices(final VertxTestContext ctx) {

        final String tenantId = helper.getRandomTenantId();
        final String deviceId = helper.getRandomDeviceId(tenantId);
        final String password = "secret";
        final Tenant tenant = new Tenant();

        helper.registry
                .addDeviceForTenant(tenantId, tenant, deviceId, password)
            .compose(device -> helper.registry.deregisterDevice(tenantId, deviceId))
            .compose(ok -> connectToAdapter(IntegrationTestSupport.getUsername(deviceId, tenantId), password))
            .setHandler(ctx.failing(t -> {
                // THEN the connection is refused
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that the AMQP Adapter will fail to authenticate a device whose username does not match the expected pattern
     * {@code [<authId>@<tenantId>]}.
     * 
     * @param ctx The Vert.x test context.
     */
    @Test
    public void testConnectFailsForInvalidUsernamePattern(final VertxTestContext ctx) {

        // GIVEN an adapter with a registered device
        final String tenantId = helper.getRandomTenantId();
        final String deviceId = helper.getRandomDeviceId(tenantId);
        final String password = "secret";
        final Tenant tenant = new Tenant();

        helper.registry.addDeviceForTenant(tenantId, tenant, deviceId, password)
        // WHEN the device tries to connect using a malformed username
        .compose(ok -> connectToAdapter(deviceId, password))
        .setHandler(ctx.failing(t -> {
            // THEN the SASL handshake fails
            ctx.verify(() -> assertThat(t).isInstanceOf(SaslException.class));
            ctx.completeNow();
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
    public void testConnectFailsForNonMatchingTrustAnchor(final VertxTestContext ctx) throws GeneralSecurityException {

        final String tenantId = helper.getRandomTenantId();
        final String deviceId = helper.getRandomDeviceId(tenantId);
        final KeyPair keyPair = helper.newEcKeyPair();

        final SelfSignedCertificate deviceCert = SelfSignedCertificate.create(UUID.randomUUID().toString());

        // GIVEN a tenant configured with a trust anchor
        helper.getCertificate(deviceCert.certificatePath())
                .compose(cert -> {
                    final Tenant tenant = Tenants.createTenantForTrustAnchor(cert.getSubjectX500Principal(), keyPair.getPublic());
                    return helper.registry.addDeviceForTenant(tenantId, tenant, deviceId, cert);
                })
                .compose(ok -> {
                    // WHEN a device tries to connect to the adapter
                    // using a client certificate that cannot be validated
                    // using the trust anchor registered for the device's tenant
                    return connectToAdapter(deviceCert);
                })
                .setHandler(ctx.failing(t -> {
                    // THEN the connection is not established
                    ctx.verify(() -> assertThat(t).isInstanceOf(SaslException.class));
                    ctx.completeNow();
                }));
    }

}
