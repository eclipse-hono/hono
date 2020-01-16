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

package org.eclipse.hono.tests.mqtt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtendWith;

import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.vertx.core.net.SelfSignedCertificate;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.mqtt.MqttConnectionException;

/**
 * Integration tests for checking connection to the MQTT adapter.
 *
 */
@ExtendWith(VertxExtension.class)
@Timeout(timeUnit = TimeUnit.SECONDS, value = 5)
public class MqttConnectionIT extends MqttTestBase {

    private SelfSignedCertificate deviceCert;
    private String tenantId;
    private String deviceId;
    private String password;

    /**
     * Sets up the fixture.
     */
    @BeforeEach
    @Override
    public void setUp(final TestInfo testInfo) {
        LOGGER.info("running {}", testInfo.getDisplayName());
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
    public void testConnectSucceedsForRegisteredDevice(final VertxTestContext ctx) {

        final Tenant tenant = new Tenant();

        helper.registry
                .addDeviceForTenant(tenantId, tenant, deviceId, password)
                .compose(ok -> connectToAdapter(IntegrationTestSupport.getUsername(deviceId, tenantId), password))
                .setHandler(ctx.completing());
    }

    /**
     * Verifies that an attempt to open a connection using a valid X.509 client certificate succeeds.
     *
     * @param ctx The test context
     */
    @Test
    public void testConnectX509SucceedsForRegisteredDevice(final VertxTestContext ctx) {

        helper.getCertificate(deviceCert.certificatePath())
                .compose(cert -> {
                    final var tenant = Tenants.createTenantForTrustAnchor(cert);
                    return helper.registry.addDeviceForTenant(tenantId, tenant, deviceId, cert);
                })
                .compose(ok -> connectToAdapter(deviceCert))
                .setHandler(ctx.completing());
    }

    /**
     * Verifies that the adapter opens a connection if auto-provisioning is enabled for the device certificate.
     *
     * @param ctx The test context
     */
    @Test
    public void testConnectSucceedsWithAutoProvisioning(final VertxTestContext ctx) {
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

        // GIVEN a tenant configured with a trust anchor that does not allow auto-provisioning
        // WHEN an unknown device tries to connect
        helper.getCertificate(deviceCert.certificatePath())
                .compose(cert -> {
                    final var tenant = Tenants.createTenantForTrustAnchor(cert);
                    tenant.getTrustedCertificateAuthorities().get(0).setAutoProvisioningEnabled(false);
                    return helper.registry.addTenant(tenantId, tenant);
                })
                // WHEN a unknown device tries to connect to the adapter
                // using a client certificate with the trust anchor registered for the device's tenant
                .compose(ok -> connectToAdapter(deviceCert))
                .setHandler(ctx.failing(t -> {
                    // THEN the connection is refused
                    ctx.verify(() -> {
                        assertThat(t).isInstanceOf(MqttConnectionException.class);
                        assertThat(((MqttConnectionException) t).code())
                                .isEqualTo(MqttConnectReturnCode.CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD);
                    });
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

        // GIVEN an adapter
        // WHEN an unknown device tries to connect
        connectToAdapter(IntegrationTestSupport.getUsername("non-existing", Constants.DEFAULT_TENANT), "secret")
        .setHandler(ctx.failing(t -> {
            // THEN the connection is refused
            ctx.verify(() -> {
                assertThat(t).isInstanceOf(MqttConnectionException.class);
                assertThat(((MqttConnectionException) t).code()).isEqualTo(MqttConnectReturnCode.CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD);
            });
            ctx.completeNow();
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
    public void testConnectX509FailsForNonExistingDevice(final VertxTestContext ctx) {

        // GIVEN an adapter
        // WHEN an unknown device tries to connect
        connectToAdapter(deviceCert)
        .setHandler(ctx.failing(t -> {
            // THEN the connection is refused
            ctx.verify(() -> {
                assertThat(t).isInstanceOf(MqttConnectionException.class);
                assertThat(((MqttConnectionException) t).code()).isEqualTo(MqttConnectReturnCode.CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD);
            });
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
        final Tenant tenant = new Tenant();

        helper.registry
                .addDeviceForTenant(tenantId, tenant, deviceId, password)
        // WHEN the device tries to connect using a wrong password
        .compose(ok -> connectToAdapter(IntegrationTestSupport.getUsername(deviceId, tenantId), "wrong password"))
        .setHandler(ctx.failing(t -> {
            // THEN the connection is refused
            ctx.verify(() -> {
                assertThat(t).isInstanceOf(MqttConnectionException.class);
                assertThat(((MqttConnectionException) t).code()).isEqualTo(MqttConnectReturnCode.CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD);
            });
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that the adapter rejects connection attempts from devices using a client certificate with an unknown
     * subject DN.
     *
     * @param ctx The test context
     */
    @Test
    public void testConnectX509FailsForUnknownSubjectDN(final VertxTestContext ctx) {

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
                .setHandler(ctx.failing(t -> {
                    // THEN the connection is refused
                    ctx.verify(() -> {
                        assertThat(t).isInstanceOf(MqttConnectionException.class);
                        assertThat(((MqttConnectionException) t).code()).isEqualTo(MqttConnectReturnCode.CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD);
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that the adapter rejects connection attempts from devices belonging to a tenant for which the MQTT
     * adapter has been disabled.
     *
     * @param ctx The test context
     */
    @Test
    public void testConnectFailsForDisabledAdapter(final VertxTestContext ctx) {

        final Tenant tenant = new Tenant();
        tenant.addAdapterConfig(new Adapter(Constants.PROTOCOL_ADAPTER_TYPE_MQTT).setEnabled(false));

        helper.registry
                .addDeviceForTenant(tenantId, tenant, deviceId, password)
                // WHEN a device that belongs to the tenant tries to connect to the adapter
                .compose(ok -> connectToAdapter(IntegrationTestSupport.getUsername(deviceId, tenantId), password))
                .setHandler(ctx.failing(t -> {
                    // THEN the connection is refused with a NOT_AUTHORIZED code
                    ctx.verify(() -> {
                        assertThat(t).isInstanceOf(MqttConnectionException.class);
                        assertThat(((MqttConnectionException) t).code()).isEqualTo(MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED);
                    });
                    ctx.completeNow();
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
    public void testConnectX509FailsForDisabledAdapter(final VertxTestContext ctx) {
        helper.getCertificate(deviceCert.certificatePath())
        .compose(cert -> {
                    final var tenant = Tenants.createTenantForTrustAnchor(cert);
                    tenant.addAdapterConfig(new Adapter(Constants.PROTOCOL_ADAPTER_TYPE_MQTT).setEnabled(false));
                    return helper.registry.addDeviceForTenant(tenantId, tenant, deviceId, cert);
        })
        // WHEN a device that belongs to the tenant tries to connect to the adapter
        .compose(ok -> connectToAdapter(deviceCert))
        .setHandler(ctx.failing(t -> {
            // THEN the connection is refused with a NOT_AUTHORIZED code
            ctx.verify(() -> {
                assertThat(t).isInstanceOf(MqttConnectionException.class);
                assertThat(((MqttConnectionException) t).code()).isEqualTo(MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED);
            });
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that the adapter rejects connection attempts from devices for which credentials exist but are disabled.
     *
     * @param ctx The test context
     */
    @Test
    public void testConnectFailsForDisabledCredentials(final VertxTestContext ctx) {

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
                .setHandler(ctx.failing(t -> {
                    // THEN the connection is refused with a NOT_AUTHORIZED code
                    ctx.verify(() -> {
                        assertThat(t).isInstanceOf(MqttConnectionException.class);
                        assertThat(((MqttConnectionException) t).code()).isEqualTo(MqttConnectReturnCode.CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD);
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that the adapter rejects connection attempts from devices for which credentials exist but the device is
     * disabled.
     *
     * @param ctx The test context
     */
    @Test
    public void testConnectFailsForDisabledDevice(final VertxTestContext ctx) {

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
                .setHandler(ctx.failing(t -> {
                    // THEN the connection is refused with a NOT_AUTHORIZED code
                    ctx.verify(() -> {
                        assertThat(t).isInstanceOf(MqttConnectionException.class);
                        assertThat(((MqttConnectionException) t).code()).isEqualTo(MqttConnectReturnCode.CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD);
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that the adapter rejects connection attempts from devices that belong to a disabled tenant.
     *
     * @param ctx The test context
     */
    @Test
    public void testConnectFailsForDisabledTenant(final VertxTestContext ctx) {

        // Given a disabled tenant for which the MQTT adapter is enabled
        final Tenant tenant = new Tenant();
        tenant.setEnabled(false);

        helper.registry
            .addDeviceForTenant(tenantId, tenant, deviceId, password)
            .compose(ok -> connectToAdapter(IntegrationTestSupport.getUsername(deviceId, tenantId), password))
            .setHandler(ctx.failing(t -> {
                // THEN the connection is refused with a NOT_AUTHORIZED code
                ctx.verify(() -> {
                    assertThat(t).isInstanceOf(MqttConnectionException.class);
                    assertThat(((MqttConnectionException) t).code()).isEqualTo(MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED);
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that the adapter rejects connection attempts from devices
     * using a client certificate that belong to a disabled tenant.
     *
     * @param ctx The test context
     */
    @Test
    public void testConnectX509FailsForDisabledTenant(final VertxTestContext ctx) {

        // Given a disabled tenant for which the MQTT adapter is enabled
        helper.getCertificate(deviceCert.certificatePath())
        .compose(cert -> {
                    final var tenant = Tenants.createTenantForTrustAnchor(cert);
                    tenant.setEnabled(false);
                    return helper.registry.addDeviceForTenant(tenantId, tenant, deviceId, cert);
        })
        .compose(ok -> connectToAdapter(deviceCert))
        .setHandler(ctx.failing(t -> {
            // THEN the connection is refused with a NOT_AUTHORIZED code
            ctx.verify(() -> {
                assertThat(t).isInstanceOf(MqttConnectionException.class);
                assertThat(((MqttConnectionException) t).code()).isEqualTo(MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED);
            });
            ctx.completeNow();
        }));
    }
}
