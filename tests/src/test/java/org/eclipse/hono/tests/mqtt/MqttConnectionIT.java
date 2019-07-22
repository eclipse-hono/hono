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

import java.util.Collections;
import java.util.UUID;

import javax.security.auth.x500.X500Principal;

import org.eclipse.hono.service.management.credentials.PasswordCredential;
import org.eclipse.hono.service.management.credentials.X509CertificateCredential;
import org.eclipse.hono.service.management.credentials.X509CertificateSecret;
import org.eclipse.hono.service.management.device.Device;
import org.eclipse.hono.service.management.tenant.Adapter;
import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.tests.Tenants;
import org.eclipse.hono.util.Constants;
import org.hamcrest.core.IsInstanceOf;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;

import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
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
     * Time out tests after 5 seconds.
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

        final Tenant tenant = new Tenant();

        helper.registry
                .addDeviceForTenant(tenantId, tenant, deviceId, password)
                .compose(ok -> connectToAdapter(IntegrationTestSupport.getUsername(deviceId, tenantId), password))
                .setHandler(ctx.asyncAssertSuccess());
    }

    /**
     * Verifies that an attempt to open a connection using a valid X.509 client certificate succeeds.
     *
     * @param ctx The test context
     */
    @Test
    public void testConnectX509SucceedsForRegisteredDevice(final TestContext ctx) {

        helper.getCertificate(deviceCert.certificatePath())
                .compose(cert -> {
                    final var tenant = Tenants.createTenantForTrustAnchor(cert);
                    return helper.registry.addDeviceForTenant(tenantId, tenant, deviceId, cert);
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
            ctx.verify(v -> {
                Assert.assertThat(t,  IsInstanceOf.instanceOf(MqttConnectionException.class));
            });
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
            ctx.verify(v -> {
                Assert.assertThat(t,  IsInstanceOf.instanceOf(MqttConnectionException.class));
            });
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
        final Tenant tenant = new Tenant();

        helper.registry
                .addDeviceForTenant(tenantId, tenant, deviceId, password)
        // WHEN the device tries to connect using a wrong password
        .compose(ok -> connectToAdapter(IntegrationTestSupport.getUsername(deviceId, tenantId), "wrong password"))
        .setHandler(ctx.asyncAssertFailure(t -> {
            // THEN the connection is refused
            ctx.verify(v -> {
                Assert.assertThat(t,  IsInstanceOf.instanceOf(MqttConnectionException.class));
            });
            ctx.assertEquals(MqttConnectReturnCode.CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD,
                    ((MqttConnectionException) t).code());
        }));
    }

    /**
     * Verifies that the adapter rejects connection attempts from devices using a client certificate with an unknown
     * subject DN.
     *
     * @param ctx The test context
     */
    @Test
    public void testConnectX509FailsForUnknownSubjectDN(final TestContext ctx) {

        // GIVEN a registered device

        helper.getCertificate(deviceCert.certificatePath())
                .compose(cert -> {
                    final var tenant = Tenants.createTenantForTrustAnchor(cert);
                    return helper.registry.addTenant(tenantId, tenant);
                }).compose(ok -> helper.registry.registerDevice(tenantId, deviceId))
                .compose(ok -> {
                    final X509CertificateCredential credential = new X509CertificateCredential();
                    credential.setAuthId(new X500Principal("CN=4711").getName(X500Principal.RFC2253));
                    credential.getSecrets().add(new X509CertificateSecret());
                    return helper.registry.addCredentials(tenantId, deviceId, Collections.singleton(credential));
                })
                // WHEN the device tries to connect using a client certificate with an unknown subject DN
                .compose(ok -> connectToAdapter(deviceCert))
                .setHandler(ctx.asyncAssertFailure(t -> {
                    // THEN the connection is refused
                    ctx.verify(v -> {
                        Assert.assertThat(t,  IsInstanceOf.instanceOf(MqttConnectionException.class));
                    });
                    ctx.assertEquals(MqttConnectReturnCode.CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD,
                            ((MqttConnectionException) t).code());
                }));
    }

    /**
     * Verifies that the adapter rejects connection attempts from devices belonging to a tenant for which the MQTT
     * adapter has been disabled.
     *
     * @param ctx The test context
     */
    @Test
    public void testConnectFailsForDisabledAdapter(final TestContext ctx) {

        final Tenant tenant = new Tenant();
        tenant.addAdapterConfig(new Adapter(Constants.PROTOCOL_ADAPTER_TYPE_MQTT).setEnabled(false));

        helper.registry
                .addDeviceForTenant(tenantId, tenant, deviceId, password)
                // WHEN a device that belongs to the tenant tries to connect to the adapter
                .compose(ok -> connectToAdapter(IntegrationTestSupport.getUsername(deviceId, tenantId), password))
                .setHandler(ctx.asyncAssertFailure(t -> {
                    // THEN the connection is refused with a NOT_AUTHORIZED code
                    ctx.verify(v -> {
                        Assert.assertThat(t,  IsInstanceOf.instanceOf(MqttConnectionException.class));
                    });
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
        helper.getCertificate(deviceCert.certificatePath())
        .compose(cert -> {
                    final var tenant = Tenants.createTenantForTrustAnchor(cert);
                    tenant.addAdapterConfig(new Adapter(Constants.PROTOCOL_ADAPTER_TYPE_MQTT).setEnabled(false));
                    return helper.registry.addDeviceForTenant(tenantId, tenant, deviceId, cert);
        })
        // WHEN a device that belongs to the tenant tries to connect to the adapter
        .compose(ok -> connectToAdapter(deviceCert))
        .setHandler(ctx.asyncAssertFailure(t -> {
            // THEN the connection is refused with a NOT_AUTHORIZED code
            ctx.verify(v -> {
                Assert.assertThat(t,  IsInstanceOf.instanceOf(MqttConnectionException.class));
            });
            ctx.assertEquals(MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED,
                    ((MqttConnectionException) t).code());
        }));
    }

    /**
     * Verifies that the adapter rejects connection attempts from devices for which credentials exist but are disabled.
     *
     * @param ctx The test context
     */
    @Test
    public void testConnectFailsForDisabledCredentials(final TestContext ctx) {

        final Tenant tenant = new Tenant();

        helper.registry
                .addTenant(tenantId, tenant)
                .compose(ok -> {
                    return helper.registry.registerDevice(tenantId, deviceId);
                })
                .compose(ok -> {
                    final PasswordCredential secret = IntegrationTestSupport.createPasswordCredential(deviceId,
                            password);
                    secret.setEnabled(false);
                    return helper.registry.addCredentials(tenantId, deviceId, Collections.singleton(secret));
                })
                // WHEN a device connects using the correct credentials
                .compose(ok -> connectToAdapter(IntegrationTestSupport.getUsername(deviceId, tenantId), password))
                .setHandler(ctx.asyncAssertFailure(t -> {
                    // THEN the connection is refused with a NOT_AUTHORIZED code
                    ctx.verify(v -> {
                        Assert.assertThat(t, IsInstanceOf.instanceOf(MqttConnectionException.class));
                    });
                    ctx.assertEquals(MqttConnectReturnCode.CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD,
                            ((MqttConnectionException) t).code());
                }));
    }

    /**
     * Verifies that the adapter rejects connection attempts from devices for which credentials exist but the device is
     * disabled.
     *
     * @param ctx The test context
     */
    @Test
    public void testConnectFailsForDisabledDevice(final TestContext ctx) {

        final Tenant tenant = new Tenant();

        helper.registry
                .addTenant(tenantId, tenant)
                .compose(ok -> {
                    final var device = new Device();
                    device.setEnabled(false);
                    return helper.registry.registerDevice(tenantId, deviceId, device);
                })
                .compose(ok -> {
                    final PasswordCredential secret = IntegrationTestSupport.createPasswordCredential(deviceId,
                            password);
                    return helper.registry.addCredentials(tenantId, deviceId, Collections.singleton(secret));
                })
                // WHEN a device connects using the correct credentials
                .compose(ok -> connectToAdapter(IntegrationTestSupport.getUsername(deviceId, tenantId), password))
                .setHandler(ctx.asyncAssertFailure(t -> {
                    // THEN the connection is refused with a NOT_AUTHORIZED code
                    ctx.verify(v -> {
                        Assert.assertThat(t, IsInstanceOf.instanceOf(MqttConnectionException.class));
                    });
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
        final Tenant tenant = new Tenant();
        tenant.setEnabled(false);

        helper.registry
            .addDeviceForTenant(tenantId, tenant, deviceId, password)
            .compose(ok -> connectToAdapter(IntegrationTestSupport.getUsername(deviceId, tenantId), password))
            .setHandler(ctx.asyncAssertFailure(t -> {
                // THEN the connection is refused with a NOT_AUTHORIZED code
                ctx.verify(v -> {
                    Assert.assertThat(t,  IsInstanceOf.instanceOf(MqttConnectionException.class));
                });
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
                    final var tenant = Tenants.createTenantForTrustAnchor(cert);
                    tenant.setEnabled(false);
                    return helper.registry.addDeviceForTenant(tenantId, tenant, deviceId, cert);
        })
        .compose(ok -> connectToAdapter(deviceCert))
        .setHandler(ctx.asyncAssertFailure(t -> {
            // THEN the connection is refused with a NOT_AUTHORIZED code
            ctx.verify(v -> {
                Assert.assertThat(t,  IsInstanceOf.instanceOf(MqttConnectionException.class));
            });
            ctx.assertEquals(((MqttConnectionException) t).code(),
                    MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED);
        }));
    }
}
