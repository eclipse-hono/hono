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

package org.eclipse.hono.tests.mqtt;

import java.util.UUID;

import javax.security.auth.x500.X500Principal;

import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.CredentialsObject;
import org.eclipse.hono.util.TenantConstants;
import org.eclipse.hono.util.TenantObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;

import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.SelfSignedCertificate;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.mqtt.MqttConnectionException;

/**
 * Integration tests for checking connection to the MQTT adapter.
 *
 */
@RunWith(VertxUnitRunner.class)
public class MqttConnectionIT extends MqttTestBase {

    /**
     * Time out tests after two seconds.
     */
    @Rule
    public Timeout timeout = Timeout.seconds(5);

    private SelfSignedCertificate deviceCert;
    private String tenantId;
    private String deviceId;
    private String password;

    /**
     * Sets up the fixture.
     */
    @Before
    @Override
    public void setUp() {
        LOGGER.info("running {}", testName.getMethodName());
        tenantId = helper.getRandomTenantId();
        deviceId = helper.getRandomDeviceId(tenantId);
        password = "secret";
        deviceCert = SelfSignedCertificate.create(UUID.randomUUID().toString());
    }

    /**
     * Verifies that the adapter opens a connection to registered devices with credentials.
     *
     * @param ctx The test context
     */
    @Test
    public void testConnectSucceedsForRegisteredDevice(final TestContext ctx) {

        final TenantObject tenant = TenantObject.from(tenantId, true);

        helper.registry
        .addDeviceForTenant(tenant, deviceId, password)
        .compose(ok -> connectToAdapter(IntegrationTestSupport.getUsername(deviceId, tenantId), password))
        .setHandler(ctx.asyncAssertSuccess());
    }

    /**
     * Verifies that an attempt to open a connection using a valid X.509 client certificate
     * succeeds.
     *
     * @param ctx The test context
     */
    @Test
    public void testConnectX509SucceedsForRegisteredDevice(final TestContext ctx) {

        helper.getCertificate(deviceCert.certificatePath())
        .compose(cert -> {
            final TenantObject tenant = TenantObject.from(tenantId, true);
            tenant.setTrustAnchor(cert.getPublicKey(), cert.getIssuerX500Principal());
            return helper.registry.addDeviceForTenant(tenant, deviceId, cert);
        }).compose(ok -> {
            return connectToAdapter(deviceCert);
        }).setHandler(ctx.asyncAssertSuccess());
    }

    /**
     * Verifies that the adapter rejects connection attempts from unknown devices
     * for which neither registration information nor credentials are on record.
     *
     * @param ctx The test context
     */
    @Test
    public void testConnectFailsForNonExistingDevice(final TestContext ctx) {

        // GIVEN an adapter
        // WHEN an unknown device tries to connect
        connectToAdapter(IntegrationTestSupport.getUsername("non-existing", Constants.DEFAULT_TENANT), "secret")
        .setHandler(ctx.asyncAssertFailure(t -> {
            // THEN the connection is refused
            ctx.assertEquals(MqttConnectReturnCode.CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD,
                    ((MqttConnectionException) t).code());
        }));
    }

    /**
     * Verifies that the adapter rejects connection attempts from unknown devices
     * trying to authenticate using a client certificate but for which neither
     * registration information nor credentials are on record.
     *
     * @param ctx The test context
     */
    @Test
    public void testConnectX509FailsForNonExistingDevice(final TestContext ctx) {

        // GIVEN an adapter
        // WHEN an unknown device tries to connect
        connectToAdapter(deviceCert)
        .setHandler(ctx.asyncAssertFailure(t -> {
            // THEN the connection is refused
            ctx.assertEquals(MqttConnectReturnCode.CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD,
                    ((MqttConnectionException) t).code());
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
        final TenantObject tenant = TenantObject.from(tenantId, true);

        helper.registry
        .addDeviceForTenant(tenant, deviceId, password)
        // WHEN the device tries to connect using a wrong password
        .compose(ok -> connectToAdapter(IntegrationTestSupport.getUsername(deviceId, tenantId), "wrong password"))
        .setHandler(ctx.asyncAssertFailure(t -> {
            // THEN the connection is refused
            ctx.assertEquals(MqttConnectReturnCode.CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD,
                    ((MqttConnectionException) t).code());
        }));
    }

    /**
     * Verifies that the adapter rejects connection attempts from devices
     * using a client certificate with an unknown subject DN.
     *
     * @param ctx The test context
     */
    @Test
    public void testConnectX509FailsForUnknownSubjectDN(final TestContext ctx) {

        // GIVEN a registered device
        final TenantObject tenant = TenantObject.from(tenantId, true);

        helper.getCertificate(deviceCert.certificatePath())
        .compose(cert -> {
            tenant.setTrustAnchor(cert.getPublicKey(), cert.getIssuerX500Principal());
            return helper.registry.addTenant(JsonObject.mapFrom(tenant));
        }).compose(ok -> helper.registry.registerDevice(tenant.getTenantId(), deviceId))
        .compose(ok -> {
            final CredentialsObject credentialsSpec =
                    CredentialsObject.fromSubjectDn(deviceId, new X500Principal("CN=4711"), null, null);
            return helper.registry.addCredentials(tenant.getTenantId(), JsonObject.mapFrom(credentialsSpec));
        })
        // WHEN the device tries to connect using a client certificate with an unknown subject DN
        .compose(ok -> connectToAdapter(deviceCert))
        .setHandler(ctx.asyncAssertFailure(t -> {
            // THEN the connection is refused
            ctx.assertEquals(MqttConnectReturnCode.CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD,
                    ((MqttConnectionException) t).code());
        }));
    }

    /**
     * Verifies that the adapter rejects connection attempts from devices belonging
     * to a tenant for which the MQTT adapter has been disabled.
     *
     * @param ctx The test context
     */
    @Test
    public void testConnectFailsForDisabledAdapter(final TestContext ctx) {

        final TenantObject tenant = TenantObject.from(tenantId, true);
        final JsonObject adapterDetailsMqtt = new JsonObject()
                .put(TenantConstants.FIELD_ADAPTERS_TYPE, Constants.PROTOCOL_ADAPTER_TYPE_MQTT)
                .put(TenantConstants.FIELD_ENABLED, Boolean.FALSE);
        tenant.addAdapterConfiguration(adapterDetailsMqtt);

        helper.registry
        .addDeviceForTenant(tenant, deviceId, password)
        // WHEN a device that belongs to the tenant tries to connect to the adapter
        .compose(ok -> connectToAdapter(IntegrationTestSupport.getUsername(deviceId, tenantId), password))
        .setHandler(ctx.asyncAssertFailure(t -> {
            // THEN the connection is refused with a NOT_AUTHORIZED code
            ctx.assertEquals(MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED,
                    ((MqttConnectionException) t).code());
        }));
    }

    /**
     * Verifies that the adapter rejects connection attempts from devices
     * using a client certificate which belong to a tenant for which the
     * MQTT adapter has been disabled.
     *
     * @param ctx The test context
     */
    @Test
    public void testConnectX509FailsForDisabledAdapter(final TestContext ctx) {

        final TenantObject tenant = TenantObject.from(tenantId, true);
        final JsonObject adapterDetailsMqtt = new JsonObject()
                .put(TenantConstants.FIELD_ADAPTERS_TYPE, Constants.PROTOCOL_ADAPTER_TYPE_MQTT)
                .put(TenantConstants.FIELD_ENABLED, Boolean.FALSE);
        tenant.addAdapterConfiguration(adapterDetailsMqtt);

        helper.getCertificate(deviceCert.certificatePath())
        .compose(cert -> {
            tenant.setTrustAnchor(cert.getPublicKey(), cert.getIssuerX500Principal());
            return helper.registry.addDeviceForTenant(tenant, deviceId, cert);
        })
        // WHEN a device that belongs to the tenant tries to connect to the adapter
        .compose(ok -> connectToAdapter(deviceCert))
        .setHandler(ctx.asyncAssertFailure(t -> {
            // THEN the connection is refused with a NOT_AUTHORIZED code
            ctx.assertEquals(MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED,
                    ((MqttConnectionException) t).code());
        }));
    }

    /**
     * Verifies that the adapter rejects connection attempts from devices for which
     * credentials exist but for which no registration assertion can be retrieved.
     *
     * @param ctx The test context
     */
    @Test
    public void testConnectFailsForMissingRegistrationInfo(final TestContext ctx) {

        final TenantObject tenant = TenantObject.from(tenantId, true);

        helper.registry
            .addTenant(JsonObject.mapFrom(tenant))
            .compose(ok -> {
                final CredentialsObject spec = CredentialsObject.fromClearTextPassword(deviceId, deviceId, password, null, null);
                return helper.registry.addCredentials(tenantId, JsonObject.mapFrom(spec));
            })
            // WHEN a device connects using the correct credentials
            .compose(ok -> connectToAdapter(IntegrationTestSupport.getUsername(deviceId, tenantId), password))
            .setHandler(ctx.asyncAssertFailure(t -> {
                // THEN the connection is refused with a NOT_AUTHORIZED code
                ctx.assertEquals(MqttConnectReturnCode.CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD,
                        ((MqttConnectionException) t).code());
            }));
    }

    /**
     * Verifies that the adapter rejects connection attempts from devices
     * using a client certificate for which credentials exist but for which
     * no registration assertion can be retrieved.
     *
     * @param ctx The test context
     */
    @Test
    public void testConnectX509FailsForMissingRegistrationInfo(final TestContext ctx) {

        final TenantObject tenant = TenantObject.from(tenantId, true);

        helper.registry
            .addTenant(JsonObject.mapFrom(tenant))
            .compose(ok -> {
                final CredentialsObject spec = CredentialsObject.fromClearTextPassword(deviceId, deviceId, password, null, null);
                return helper.registry.addCredentials(tenantId, JsonObject.mapFrom(spec));
            })
            // WHEN a device connects using the correct credentials
            .compose(ok -> connectToAdapter(IntegrationTestSupport.getUsername(deviceId, tenantId), password))
            .setHandler(ctx.asyncAssertFailure(t -> {
                // THEN the connection is refused with a NOT_AUTHORIZED code
                ctx.assertEquals(MqttConnectReturnCode.CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD,
                        ((MqttConnectionException) t).code());
            }));
    }

    /**
     * Verifies that the adapter rejects connection attempts from devices that belong to a disabled tenant.
     *
     * @param ctx The test context
     */
    @Test
    public void testConnectFailsForDisabledTenant(final TestContext ctx) {

        // Given a disabled tenant for which the MQTT adapter is enabled
        final TenantObject tenant = TenantObject.from(tenantId, false);

        helper.registry
            .addDeviceForTenant(tenant, deviceId, password)
            .compose(ok -> connectToAdapter(IntegrationTestSupport.getUsername(deviceId, tenantId), password))
            .setHandler(ctx.asyncAssertFailure(t -> {
                ctx.assertTrue(t instanceof MqttConnectionException);
                // THEN the connection is refused with a NOT_AUTHORIZED code
                ctx.assertEquals(((MqttConnectionException) t).code(),
                        MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED);
            }));
    }

    /**
     * Verifies that the adapter rejects connection attempts from devices
     * using a client certificate that belong to a disabled tenant.
     *
     * @param ctx The test context
     */
    @Test
    public void testConnectX509FailsForDisabledTenant(final TestContext ctx) {

        // Given a disabled tenant for which the MQTT adapter is enabled
        helper.getCertificate(deviceCert.certificatePath())
        .compose(cert -> {
            final TenantObject tenant = TenantObject.from(tenantId, false);
            tenant.setTrustAnchor(cert.getPublicKey(), cert.getIssuerX500Principal());
            return helper.registry.addDeviceForTenant(tenant, deviceId, cert);
        })
        .compose(ok -> connectToAdapter(deviceCert))
        .setHandler(ctx.asyncAssertFailure(t -> {
            ctx.assertTrue(t instanceof MqttConnectionException);
            // THEN the connection is refused with a NOT_AUTHORIZED code
            ctx.assertEquals(((MqttConnectionException) t).code(),
                    MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED);
        }));
    }
}
