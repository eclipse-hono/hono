/*******************************************************************************
 * Copyright (c) 2016, 2023 Contributors to the Eclipse Foundation
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

import static com.google.common.truth.Truth.assertThat;

import java.net.HttpURLConnection;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import javax.net.ssl.SSLHandshakeException;
import javax.security.auth.x500.X500Principal;
import javax.security.sasl.AuthenticationException;
import javax.security.sasl.SaslException;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.tests.EnabledIfDnsRebindingIsSupported;
import org.eclipse.hono.tests.EnabledIfProtocolAdaptersAreRunning;
import org.eclipse.hono.tests.EnabledIfRegistrySupportsFeatures;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.tests.Tenants;
import org.eclipse.hono.util.Adapter;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.IdentityTemplate;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.SelfSignedCertificate;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Integration tests for checking connection to the AMQP adapter.
 *
 */
@ExtendWith(VertxExtension.class)
@Timeout(timeUnit = TimeUnit.SECONDS, value = 7)
@EnabledIfProtocolAdaptersAreRunning(amqpAdapter = true)
public class AmqpConnectionIT extends AmqpAdapterTestBase {

    /**
     * Verifies that the adapter opens a connection to registered devices with credentials.
     *
     * @param tlsVersion The TLS protocol version to use for connecting to the adapter.
     * @param ctx The test context
     */
    @ParameterizedTest(name = IntegrationTestSupport.PARAMETERIZED_TEST_NAME_PATTERN)
    @ValueSource(strings = { IntegrationTestSupport.TLS_VERSION_1_2, IntegrationTestSupport.TLS_VERSION_1_3 })
    public void testConnectSucceedsForRegisteredDevice(final String tlsVersion, final VertxTestContext ctx) {

        final String tenantId = helper.getRandomTenantId();
        final String deviceId = helper.getRandomDeviceId(tenantId);
        final String password = "secret";
        final Tenant tenant = new Tenant();

        helper.registry.addDeviceForTenant(tenantId, tenant, deviceId, password)
            .compose(ok -> connectToAdapter(
                    tlsVersion,
                    null,
                    IntegrationTestSupport.getUsername(deviceId, tenantId),
                    password))
            .onComplete(ctx.succeeding(con -> {
                ctx.verify(() -> assertThat(con.isDisconnected()).isFalse());
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that the adapter opens a connection if auto-provisioning is enabled for the device certificate, and it
     * is not configured auto-provisioning-device-id template and auth-id template.
     *
     * @param ctx The test context
     */
    @Test
    public void testConnectSucceedsWithAutoProvisioningWithoutTemplate(final VertxTestContext ctx) {

        final String tenantId = helper.getRandomTenantId();
        final SelfSignedCertificate deviceCert = SelfSignedCertificate.create(UUID.randomUUID().toString());
        final Promise<String> autoProvisionedDeviceId = Promise.promise();

        final IdentityTemplate defaultTemplate = new IdentityTemplate(
                RegistryManagementConstants.PLACEHOLDER_SUBJECT_DN);

        final Future<X509Certificate> certTracker = helper.getCertificate(deviceCert.certificatePath());
        final Future<String> subjectDNTracker = certTracker
                .map(cert -> cert.getSubjectX500Principal().getName(X500Principal.RFC2253));

        helper.createAutoProvisioningNotificationConsumer(ctx, autoProvisionedDeviceId, tenantId)
                .compose(ok -> certTracker)
                .compose(cert -> {
                    final var tenant = Tenants.createTenantForTrustAnchor(cert);
                    tenant.getTrustedCertificateAuthorities().get(0).setAutoProvisioningEnabled(true);
                    return helper.registry.addTenant(tenantId, tenant);
                })
                .compose(ok -> connectToAdapter(deviceCert))
                .compose(ok -> autoProvisionedDeviceId.future())
                .compose(deviceId -> {
                    // verify the device ID is not generated by subject-dn template
                    ctx.verify(
                            () -> assertThat(deviceId).isNotEqualTo(defaultTemplate.apply(subjectDNTracker.result())));
                    return helper.registry.getRegistrationInfo(tenantId, deviceId);
                })
                .compose(registrationResult -> {
                    ctx.verify(() -> {
                        final var infoRegistration = registrationResult.bodyAsJsonObject();
                        IntegrationTestSupport.assertDeviceStatusProperties(
                                infoRegistration.getJsonObject(RegistryManagementConstants.FIELD_STATUS),
                                true);
                    });
                    return helper.registry.getCredentials(tenantId, autoProvisionedDeviceId.future().result());
                })
                .onComplete(ctx.succeeding(credentialsResult -> {
                    ctx.verify(() -> {
                        final var infoCredentials = credentialsResult.bodyAsJsonArray();
                        // verify the auth ID is generated by subject-dn template
                        assertThat(infoCredentials.getJsonObject(0)
                                .getString(RegistryManagementConstants.FIELD_AUTH_ID))
                                .isEqualTo(defaultTemplate.apply(subjectDNTracker.result()));
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that the adapter opens a connection if auto-provisioning is enabled for the device certificate, and it
     * is configured auto-provisioning-device-id template and auth-id template.
     *
     * @param ctx The test context
     */
    @Test
    public void testConnectSucceedsWithAutoProvisioningWithTemplate(final VertxTestContext ctx) {

        final String tenantId = helper.getRandomTenantId();
        final SelfSignedCertificate deviceCert = SelfSignedCertificate.create(UUID.randomUUID().toString());
        final Promise<String> autoProvisionedDeviceId = Promise.promise();

        final IdentityTemplate deviceIdTemplate = new IdentityTemplate("{{subject-dn}}");
        final IdentityTemplate authIdTemplate = new IdentityTemplate("{{subject-cn}}");

        final Future<X509Certificate> certTracker = helper.getCertificate(deviceCert.certificatePath());
        final Future<String> subjectDNTracker = certTracker
                .map(cert -> cert.getSubjectX500Principal().getName(X500Principal.RFC2253));

        helper.createAutoProvisioningNotificationConsumer(ctx, autoProvisionedDeviceId, tenantId)
                .compose(ok -> certTracker)
                .compose(cert -> {
                    final var tenant = Tenants.createTenantForTrustAnchor(cert);
                    tenant.getTrustedCertificateAuthorities().get(0)
                            .setAutoProvisioningEnabled(true)
                            .setAutoProvisioningDeviceIdTemplate(deviceIdTemplate.toString())
                            .setAuthIdTemplate(authIdTemplate.toString());
                    return helper.registry.addTenant(tenantId, tenant);
                })
                .compose(ok -> connectToAdapter(deviceCert))
                .compose(ok -> autoProvisionedDeviceId.future())
                .compose(deviceId -> {
                    // verify the device ID is generated by auto-provisioning-device-id template
                    ctx.verify(() -> assertThat(deviceId).isEqualTo(deviceIdTemplate.apply(subjectDNTracker.result())));
                    return helper.registry.getRegistrationInfo(tenantId, deviceId);
                })
                .compose(registrationResult -> {
                    ctx.verify(() -> {
                        final var infoRegistration = registrationResult.bodyAsJsonObject();
                        IntegrationTestSupport.assertDeviceStatusProperties(
                                infoRegistration.getJsonObject(RegistryManagementConstants.FIELD_STATUS),
                                true);
                    });
                    final var deviceId = deviceIdTemplate.apply(subjectDNTracker.result());
                    return helper.registry.getCredentials(tenantId, deviceId);
                })
                .onComplete(ctx.succeeding(credentialsResult -> {
                    ctx.verify(() -> {
                        final var infoCredentials = credentialsResult.bodyAsJsonArray();
                        // verify the auth ID is generated by auth-id template
                        assertThat(infoCredentials.getJsonObject(0)
                                .getString(RegistryManagementConstants.FIELD_AUTH_ID))
                                .isEqualTo(authIdTemplate.apply(subjectDNTracker.result()));
                    });
                    ctx.completeNow();

                }));
    }

    /**
     * Verifies that an attempt to open a connection using a valid X.509 client certificate succeeds.
     *
     * @param tlsVersion The TLS protocol version to use for connecting to the adapter.
     * @param ctx The test context
     */
    @ParameterizedTest(name = IntegrationTestSupport.PARAMETERIZED_TEST_NAME_PATTERN)
    @ValueSource(strings = { IntegrationTestSupport.TLS_VERSION_1_2, IntegrationTestSupport.TLS_VERSION_1_3 })
    public void testConnectX509SucceedsForRegisteredDevice(final String tlsVersion, final VertxTestContext ctx) {

        final String tenantId = helper.getRandomTenantId();
        final String deviceId = helper.getRandomDeviceId(tenantId);
        final SelfSignedCertificate deviceCert = SelfSignedCertificate.create(deviceId + ".iot.eclipse.org");

        helper.getCertificate(deviceCert.certificatePath())
            .compose(cert -> helper.registry.addDeviceForTenant(
                    tenantId,
                    Tenants.createTenantForTrustAnchor(cert),
                    deviceId,
                    cert))
            .compose(ok -> connectToAdapter(IntegrationTestSupport.AMQP_HOST, deviceCert, tlsVersion))
            .onComplete(ctx.succeeding(con -> {
                ctx.verify(() -> assertThat(con.isDisconnected()).isFalse());
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that an attempt to open a connection using a valid X.509 client certificate succeeds
     * for a device belonging to a tenant that uses the same trust anchor as another tenant.
     *
     * @param tlsVersion The TLS protocol version to use for connecting to the adapter.
     * @param ctx The test context
     */
    @ParameterizedTest(name = IntegrationTestSupport.PARAMETERIZED_TEST_NAME_PATTERN)
    @ValueSource(strings = { IntegrationTestSupport.TLS_VERSION_1_2, IntegrationTestSupport.TLS_VERSION_1_3 })
    @EnabledIfDnsRebindingIsSupported
    @EnabledIfRegistrySupportsFeatures(trustAnchorGroups = true)
    public void testConnectX509SucceedsUsingSni(final String tlsVersion, final VertxTestContext ctx) {

        final String tenantId = helper.getRandomTenantId();
        final String deviceId = helper.getRandomDeviceId(tenantId);
        final SelfSignedCertificate deviceCert = SelfSignedCertificate.create(deviceId + ".iot.eclipse.org");

        helper.getCertificate(deviceCert.certificatePath())
            .compose(cert -> helper.registry.addTenant(
                    helper.getRandomTenantId(),
                    Tenants.createTenantForTrustAnchor(cert).setTrustAnchorGroup("test-group"))
                .map(cert))
            .compose(cert -> helper.registry.addDeviceForTenant(
                    tenantId,
                    Tenants.createTenantForTrustAnchor(cert).setTrustAnchorGroup("test-group"),
                    deviceId,
                    cert))
            .compose(ok -> connectToAdapter(
                    IntegrationTestSupport.getSniHostname(IntegrationTestSupport.AMQP_HOST, tenantId),
                    deviceCert,
                    tlsVersion))
            .onComplete(ctx.succeeding(con -> {
                ctx.verify(() -> assertThat(con.isDisconnected()).isFalse());
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that an attempt to open a connection using a valid X.509 client certificate succeeds
     * for a device belonging to a tenant with a tenant alias.
     *
     * @param tlsVersion The TLS protocol version to use for connecting to the adapter.
     * @param ctx The test context
     */
    @ParameterizedTest(name = IntegrationTestSupport.PARAMETERIZED_TEST_NAME_PATTERN)
    @ValueSource(strings = { IntegrationTestSupport.TLS_VERSION_1_2, IntegrationTestSupport.TLS_VERSION_1_3 })
    @EnabledIfDnsRebindingIsSupported
    @EnabledIfRegistrySupportsFeatures(trustAnchorGroups = true, tenantAlias = true)
    public void testConnectX509SucceedsUsingSniWithTenantAlias(final String tlsVersion, final VertxTestContext ctx) {

        final String tenantId = helper.getRandomTenantId();
        final String deviceId = helper.getRandomDeviceId(tenantId);
        final SelfSignedCertificate deviceCert = SelfSignedCertificate.create(deviceId + ".iot.eclipse.org");

        helper.getCertificate(deviceCert.certificatePath())
            .compose(cert -> helper.registry.addTenant(
                    helper.getRandomTenantId(),
                    Tenants.createTenantForTrustAnchor(cert).setTrustAnchorGroup("test-group"))
                .map(cert))
            .compose(cert -> helper.registry.addDeviceForTenant(
                    tenantId,
                    Tenants.createTenantForTrustAnchor(cert)
                        .setTrustAnchorGroup("test-group")
                        .setAlias("test-alias"),
                    deviceId,
                    cert))
            .compose(ok -> connectToAdapter(
                    IntegrationTestSupport.getSniHostname(IntegrationTestSupport.AMQP_HOST, "test-alias"),
                    deviceCert,
                    tlsVersion))
            .onComplete(ctx.succeeding(con -> {
                ctx.verify(() -> assertThat(con.isDisconnected()).isFalse());
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that the adapter rejects a connection attempt from a registered device if the device uses an unsupported
     * set of TLS security parameters.
     *
     * @param tlsVersion The TLS protocol version to use for connecting to the adapter.
     * @param cipherSuite The TLS cipher suite to use for connecting to the adapter.
     * @param ctx The test context
     */
    @ParameterizedTest(name = IntegrationTestSupport.PARAMETERIZED_TEST_NAME_PATTERN)
    @CsvSource(value = {
          IntegrationTestSupport.TLS_VERSION_1_2 + ",TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA",
          IntegrationTestSupport.TLS_VERSION_1_3 + ",TLS_AES_256_GCM_SHA384"
          })
    public void testConnectFailsForUnsupportedTlsSecurityParameters(
            final String tlsVersion,
            final String cipherSuite,
            final VertxTestContext ctx) {

        // GIVEN a client that is configured to use a combination of TLS version and cipher suite
        // that is not supported by the AMQP adapter
        final String tenantId = helper.getRandomTenantId();
        final String deviceId = helper.getRandomDeviceId(tenantId);
        final String password = "secret";
        final Tenant tenant = new Tenant();

        helper.registry.addDeviceForTenant(tenantId, tenant, deviceId, password)
            // WHEN the client tries to establish an AMQP connection to the adapter
            .compose(ok -> connectToAdapter(tlsVersion, cipherSuite, IntegrationTestSupport.getUsername(deviceId, tenantId), password))
            .onComplete(ctx.failing(t -> {
                // THEN the TLS handshake fails
                ctx.verify(() -> assertThat(t).isInstanceOf(SSLHandshakeException.class));
                ctx.completeNow();
            }));
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
                .onComplete(ctx.failing(t -> {
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
        .onComplete(ctx.failing(t -> {
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
        .onComplete(ctx.failing(t -> {
            // THEN the connection is refused
            ctx.verify(() -> assertThat(t).isInstanceOf(AuthenticationException.class));
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that the adapter rejects connection attempts from devices
     * using credentials that contain a non-existing tenant.
     *
     * @param ctx The test context
     */
    @Test
    public void testConnectFailsForNonExistingTenant(final VertxTestContext ctx) {

        // GIVEN a registered device
        final String tenantId = helper.getRandomTenantId();
        final String deviceId = helper.getRandomDeviceId(tenantId);
        final String password = "secret";
        final Tenant tenant = new Tenant();

        helper.registry
                .addDeviceForTenant(tenantId, tenant, deviceId, password)
                // WHEN a device of a non-existing tenant tries to connect
                .compose(ok -> connectToAdapter(IntegrationTestSupport.getUsername(deviceId, "nonExistingTenant"), password))
                .onComplete(ctx.failing(t -> {
                    // THEN the connection is refused
                    ctx.verify(() -> assertThat(t).isInstanceOf(AuthenticationException.class));
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
        .onComplete(ctx.failing(t -> {
            // THEN the connection is refused
            ctx.verify(() -> assertThat(((ClientErrorException) t).getErrorCode()).isEqualTo(HttpURLConnection.HTTP_FORBIDDEN));
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
            .onComplete(ctx.failing(t -> {
                ctx.verify(() -> assertThat(t).isInstanceOf(AuthenticationException.class));
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that after a device has already connected successfully to the adapter, the deletion of device
     * registration data causes the adapter to refuse any following connection attempts.
     * <p>
     * This test relies upon the registration client cache data in the adapter getting deleted when the device is
     * deleted (triggered via a corresponding notification from the device registry).
     *
     * @param ctx The test context.
     */
    @Test
    public void testConnectFailsAfterDeviceDeleted(final VertxTestContext ctx) {

        final String tenantId = helper.getRandomTenantId();
        final String deviceId = helper.getRandomDeviceId(tenantId);
        final String password = "secret";
        final Tenant tenant = new Tenant();

        helper.registry
                .addDeviceForTenant(tenantId, tenant, deviceId, password)
                .compose(ok -> connectToAdapter(IntegrationTestSupport.getUsername(deviceId, tenantId), password))
                .compose(con -> {
                    // first connection attempt successful
                    con.close();
                    // now remove device
                    return helper.registry.deregisterDevice(tenantId, deviceId);
                })
                .compose(ok -> {
                    final Promise<Void> resultPromise = Promise.promise();
                    // device deleted, now wait a bit for the device registry notifications to trigger registration cache invalidation
                    vertx.setTimer(500, tid -> resultPromise.complete());
                    return resultPromise.future();
                })
                .compose(ok -> connectToAdapter(IntegrationTestSupport.getUsername(deviceId, tenantId), password))
                .onComplete(ctx.failing(t -> {
                    ctx.verify(() -> assertThat(t).isInstanceOf(AuthenticationException.class));
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that the adapter closes the connection to a device when the registration data of the device
     * is deleted.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testDeviceConnectionIsClosedOnDeviceDeleted(final VertxTestContext ctx) {
        final String tenantId = helper.getRandomTenantId();
        final String deviceId = helper.getRandomDeviceId(tenantId);

        testDeviceConnectionIsClosedOnDeviceOrTenantDisabledOrDeleted(ctx, tenantId, deviceId,
                () -> helper.registry.deregisterDevice(tenantId, deviceId));
    }

    /**
     * Verifies that the adapter closes the connection to a device when the registration data of the device
     * is disabled.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testDeviceConnectionIsClosedOnDeviceDisabled(final VertxTestContext ctx) {
        final String tenantId = helper.getRandomTenantId();
        final String deviceId = helper.getRandomDeviceId(tenantId);
        final JsonObject updatedDeviceData = new JsonObject()
                .put(RegistrationConstants.FIELD_ENABLED, Boolean.FALSE);

        testDeviceConnectionIsClosedOnDeviceOrTenantDisabledOrDeleted(ctx, tenantId, deviceId,
                () -> helper.registry.updateDevice(tenantId, deviceId, updatedDeviceData));
    }

    /**
     * Verifies that the adapter closes the connection to a device when the tenant of the device
     * is deleted.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testDeviceConnectionIsClosedOnTenantDeleted(final VertxTestContext ctx) {
        final String tenantId = helper.getRandomTenantId();
        final String deviceId = helper.getRandomDeviceId(tenantId);

        testDeviceConnectionIsClosedOnDeviceOrTenantDisabledOrDeleted(ctx, tenantId, deviceId,
                () -> helper.registry.removeTenant(tenantId));
    }

    /**
     * Verifies that the adapter closes the connection to a device when the tenant of the device
     * is disabled.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testDeviceConnectionIsClosedOnTenantDisabled(final VertxTestContext ctx) {
        final String tenantId = helper.getRandomTenantId();
        final String deviceId = helper.getRandomDeviceId(tenantId);
        final Tenant updatedTenant = new Tenant().setEnabled(false);

        testDeviceConnectionIsClosedOnDeviceOrTenantDisabledOrDeleted(ctx, tenantId, deviceId,
                () -> helper.registry.updateTenant(tenantId, updatedTenant, HttpURLConnection.HTTP_NO_CONTENT));
    }

    /**
     * Verifies that the adapter closes the connection to a device when registration data for all devices of the device
     * tenant is deleted.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testDeviceConnectionIsClosedOnAllDevicesOfTenantDeleted(final VertxTestContext ctx) {
        final String tenantId = helper.getRandomTenantId();
        final String deviceId = helper.getRandomDeviceId(tenantId);

        testDeviceConnectionIsClosedOnDeviceOrTenantDisabledOrDeleted(ctx, tenantId, deviceId,
                () -> helper.registry.deregisterDevicesOfTenant(tenantId));
    }

    private void testDeviceConnectionIsClosedOnDeviceOrTenantDisabledOrDeleted(
            final VertxTestContext ctx,
            final String tenantId,
            final String deviceId,
            final Supplier<Future<?>> deviceRegistryChangeOperation) {

        final String password = "secret";
        final Promise<Void> disconnectedPromise = Promise.promise();
        // GIVEN a connected device
        helper.registry
                .addDeviceForTenant(tenantId, new Tenant(), deviceId, password)
                .compose(ok -> connectToAdapter(IntegrationTestSupport.getUsername(deviceId, tenantId), password))
                .compose(con -> {
                    con.disconnectHandler(ignore -> disconnectedPromise.complete());
                    con.closeHandler(remoteClose -> {
                        con.close();
                        con.disconnect();
                    });
                    // WHEN corresponding device/tenant is removed/disabled
                    return deviceRegistryChangeOperation.get();
                })
                // THEN the device connection is closed
                .compose(ok -> disconnectedPromise.future())
                .onComplete(ctx.succeedingThenComplete());
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
        .onComplete(ctx.failing(t -> {
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
                .onComplete(ctx.failing(t -> {
                    // THEN the connection is not established
                    ctx.verify(() -> assertThat(t).isInstanceOf(SaslException.class));
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that an attempt to open a connection using a valid X.509 client certificate fails
     * for a device belonging to a tenant using a non-existing tenant alias.
     *
     * @param ctx The test context
     */
    @Test
    @EnabledIfDnsRebindingIsSupported
    @EnabledIfRegistrySupportsFeatures(trustAnchorGroups = true, tenantAlias = true)
    public void testConnectX509FailsUsingSniWithNonExistingTenantAlias(final VertxTestContext ctx) {

        final String tenantId = helper.getRandomTenantId();
        final String deviceId = helper.getRandomDeviceId(tenantId);
        final SelfSignedCertificate deviceCert = SelfSignedCertificate.create(deviceId + ".iot.eclipse.org");

        helper.getCertificate(deviceCert.certificatePath())
            .compose(cert -> helper.registry.addTenant(
                    helper.getRandomTenantId(),
                    Tenants.createTenantForTrustAnchor(cert).setTrustAnchorGroup("test-group"))
                .map(cert))
            .compose(cert -> helper.registry.addDeviceForTenant(
                    tenantId,
                    Tenants.createTenantForTrustAnchor(cert)
                        .setTrustAnchorGroup("test-group")
                        .setAlias("test-alias"),
                    deviceId,
                    cert))
            .compose(ok -> connectToAdapter(
                    IntegrationTestSupport.getSniHostname(IntegrationTestSupport.AMQP_HOST, "wrong-alias"),
                    deviceCert,
                    IntegrationTestSupport.TLS_VERSION_1_2))
            .onComplete(ctx.failing(t -> {
                // THEN the connection is not established
                ctx.verify(() -> assertThat(t).isInstanceOf(SaslException.class));
                ctx.completeNow();
            }));
    }
}
