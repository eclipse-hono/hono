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

package org.eclipse.hono.tests.mqtt;

import static com.google.common.truth.Truth.assertThat;

import java.net.HttpURLConnection;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import javax.security.auth.x500.X500Principal;

import org.eclipse.hono.service.management.credentials.Credentials;
import org.eclipse.hono.service.management.credentials.PasswordCredential;
import org.eclipse.hono.service.management.credentials.X509CertificateCredential;
import org.eclipse.hono.service.management.credentials.X509CertificateSecret;
import org.eclipse.hono.service.management.device.Device;
import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.tests.EnabledIfDnsRebindingIsSupported;
import org.eclipse.hono.tests.EnabledIfProtocolAdaptersAreRunning;
import org.eclipse.hono.tests.EnabledIfRegistrySupportsFeatures;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.tests.Tenants;
import org.eclipse.hono.util.Adapter;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.IdentityTemplate;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.jsonwebtoken.Jwts;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.SelfSignedCertificate;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.mqtt.MqttClientOptions;
import io.vertx.mqtt.MqttConnectionException;

/**
 * Integration tests for checking connection to the MQTT adapter.
 *
 */
@ExtendWith(VertxExtension.class)
@Timeout(timeUnit = TimeUnit.SECONDS, value = 15)
@EnabledIfProtocolAdaptersAreRunning(mqttAdapter = true)
public class MqttConnectionIT extends MqttTestBase {

    private SelfSignedCertificate deviceCert;
    private String tenantId;
    private String deviceId;
    private String password;

    /**
     * Sets up the fixture.
     */
    @BeforeEach
    public void setUp() {
        tenantId = helper.getRandomTenantId();
        deviceId = helper.getRandomDeviceId(tenantId);
        password = "secret";
        deviceCert = SelfSignedCertificate.create(UUID.randomUUID().toString());
    }

    /**
     * Verifies that the adapter opens a connection to registered devices with credentials.
     *
     * @param tlsVersion The TLS protocol version to use for connecting to the adapter.
     * @param ctx The test context
     */
    @ParameterizedTest(name = IntegrationTestSupport.PARAMETERIZED_TEST_NAME_PATTERN)
    @ValueSource(strings = { IntegrationTestSupport.TLS_VERSION_1_2, IntegrationTestSupport.TLS_VERSION_1_3 })
    public void testConnectSucceedsForRegisteredDevice(final String tlsVersion, final VertxTestContext ctx) {

        final Tenant tenant = new Tenant();

        helper.registry
                .addDeviceForTenant(tenantId, tenant, deviceId, password)
                .compose(ok -> connectToAdapter(
                        tlsVersion,
                        IntegrationTestSupport.getUsername(deviceId, tenantId), password))
                .onComplete(ctx.succeeding(conAckMsg -> {
                    ctx.verify(() -> assertThat(conAckMsg.code()).isEqualTo(MqttConnectReturnCode.CONNECTION_ACCEPTED));
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that the adapter opens a connection to a registered device that authenticates
     * using a JSON Web Token.
     *
     * @param ctx The test context
     * @throws NoSuchAlgorithmException if the JVM does not support ECC cryptography.
     */
    @Test
    public void testConnectJwtSucceedsForRegisteredDevice(final VertxTestContext ctx) throws NoSuchAlgorithmException {

        final var generator = KeyPairGenerator.getInstance(CredentialsConstants.EC_ALG);
        final var keyPair = generator.generateKeyPair();
        final var rpkCredential = Credentials.createRPKCredential(deviceId, keyPair.getPublic());

        final var jws = Jwts.builder()
                .setAudience(CredentialsConstants.AUDIENCE_HONO_ADAPTER)
                .setIssuer(deviceId)
                .setSubject(deviceId)
                .claim(CredentialsConstants.CLAIM_TENANT_ID, tenantId)
                .setIssuedAt(Date.from(Instant.now()))
                .setExpiration(Date.from(Instant.now().plus(Duration.ofMinutes(10))))
                .setHeaderParam("typ", "JWT")
                .signWith(keyPair.getPrivate())
                .compact();

        helper.registry.addTenant(tenantId)
            .compose(res -> helper.registry.registerDevice(tenantId, deviceId))
            .compose(res -> helper.registry.addCredentials(tenantId, deviceId, Set.of(rpkCredential)))
            .compose(ok -> connectToAdapter("ignored", jws))
            .onComplete(ctx.succeeding(conAckMsg -> {
                ctx.verify(() -> assertThat(conAckMsg.code()).isEqualTo(MqttConnectReturnCode.CONNECTION_ACCEPTED));
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that the adapter opens a connection to a registered device that authenticates
     * using a Google IoT Core style JSON Web Token in conjunction with an MQTT connection
     * identifier that contains the tenant and authentication ID.
     *
     * @param ctx The test context
     * @throws NoSuchAlgorithmException if the JVM does not support ECC cryptography.
     */
    @Test
    public void testConnectGoogleIoTCoreJwtSucceedsForRegisteredDevice(final VertxTestContext ctx) throws NoSuchAlgorithmException {

        final var generator = KeyPairGenerator.getInstance(CredentialsConstants.EC_ALG);
        final var keyPair = generator.generateKeyPair();
        final var rpkCredential = Credentials.createRPKCredential(deviceId, keyPair.getPublic());

        final var jws = Jwts.builder()
                .setIssuedAt(Date.from(Instant.now()))
                .setExpiration(Date.from(Instant.now().plus(Duration.ofMinutes(10))))
                .setHeaderParam("typ", "JWT")
                .signWith(keyPair.getPrivate())
                .compact();
        final var options = new MqttClientOptions(defaultOptions)
                .setUsername("ignored")
                .setPassword(jws)
                .setClientId("tenants/%s/devices/%s".formatted(tenantId, deviceId));

        helper.registry.addTenant(tenantId)
            .compose(res -> helper.registry.registerDevice(tenantId, deviceId))
            .compose(res -> helper.registry.addCredentials(tenantId, deviceId, Set.of(rpkCredential)))
            .compose(ok -> connectToAdapter(options, IntegrationTestSupport.MQTT_HOST))
            .onComplete(ctx.succeeding(conAckMsg -> {
                ctx.verify(() -> assertThat(conAckMsg.code()).isEqualTo(MqttConnectReturnCode.CONNECTION_ACCEPTED));
                ctx.completeNow();
            }));
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
                .onComplete(ctx.succeeding(conAckMsg -> {
                    ctx.verify(() -> assertThat(conAckMsg.code()).isEqualTo(MqttConnectReturnCode.CONNECTION_ACCEPTED));
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that an attempt to open a connection using a valid X.509 client certificate succeeds
     * for a device belonging to a tenant that uses the same trust anchor as another tenant.
     *
     * @param ctx The test context
     */
    @Test
    @EnabledIfDnsRebindingIsSupported
    @EnabledIfRegistrySupportsFeatures(trustAnchorGroups = true)
    public void testConnectX509SucceedsUsingSni(final VertxTestContext ctx) {

        helper.getCertificate(deviceCert.certificatePath())
            .compose(cert -> {
                // GIVEN two tenants belonging to the same trust anchor group
                final var tenant = Tenants.createTenantForTrustAnchor(cert)
                        .setTrustAnchorGroup("test-group");
                // which both use the same CA
                return helper.registry.addTenant(helper.getRandomTenantId(), tenant)
                        // and a device belonging to one of the tenants
                        .compose(ok -> helper.registry.addDeviceForTenant(tenantId, tenant, deviceId, cert));
            })
            // WHEN the device connects to the adapter including its tenant ID in the host name
            .compose(ok -> connectToAdapter(
                    deviceCert,
                    IntegrationTestSupport.getSniHostname(IntegrationTestSupport.MQTT_HOST, tenantId)))
            .onComplete(ctx.succeeding(conAckMsg -> {
                // THEN the connection attempt succeeds
                ctx.verify(() -> assertThat(conAckMsg.code()).isEqualTo(MqttConnectReturnCode.CONNECTION_ACCEPTED));
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that an attempt to open a connection using a valid X.509 client certificate succeeds
     * for a device belonging to a tenant with a tenant alias.
     *
     * @param ctx The test context
     */
    @Test
    @EnabledIfDnsRebindingIsSupported
    @EnabledIfRegistrySupportsFeatures(trustAnchorGroups = true, tenantAlias = true)
    public void testConnectX509SucceedsUsingSniWithTenantAlias(final VertxTestContext ctx) {

        helper.getCertificate(deviceCert.certificatePath())
            // GIVEN two tenants belonging to the same trust anchor group
            // which both use the same CA
            .compose(cert -> helper.registry.addTenant(
                    helper.getRandomTenantId(),
                    Tenants.createTenantForTrustAnchor(cert).setTrustAnchorGroup("test-group"))
                .map(cert))
            // and a device belonging to one of the tenants which has an alias configured
            .compose(cert -> helper.registry.addDeviceForTenant(
                    tenantId,
                    Tenants.createTenantForTrustAnchor(cert)
                        .setTrustAnchorGroup("test-group")
                        .setAlias("test-alias"),
                    deviceId,
                    cert))
            // WHEN the device connects to the adapter including the tenant alias in the host name
            .compose(ok -> connectToAdapter(
                    deviceCert,
                    IntegrationTestSupport.getSniHostname(IntegrationTestSupport.MQTT_HOST, "test-alias")))
            .onComplete(ctx.succeeding(conAckMsg -> {
                // THEN the connection attempt succeeds
                ctx.verify(() -> assertThat(conAckMsg.code()).isEqualTo(MqttConnectReturnCode.CONNECTION_ACCEPTED));
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

        helper.getCertificate(deviceCert.certificatePath())
            // GIVEN two tenants belonging to the same trust anchor group
            // which both use the same CA
            .compose(cert -> helper.registry.addTenant(
                        helper.getRandomTenantId(),
                        Tenants.createTenantForTrustAnchor(cert).setTrustAnchorGroup("test-group"))
                    .map(cert))
            // and a device belonging to one of the tenants which has an alias configured
            .compose(cert -> helper.registry.addDeviceForTenant(
                    tenantId,
                    Tenants.createTenantForTrustAnchor(cert)
                        .setTrustAnchorGroup("test-group")
                        .setAlias("test-alias"),
                    deviceId,
                    cert))
            // WHEN the device connects to the adapter including a wrong tenant alias in the host name
            .compose(ok -> connectToAdapter(
                    deviceCert,
                    IntegrationTestSupport.getSniHostname(IntegrationTestSupport.MQTT_HOST, "wrong-alias")))
            .onComplete(ctx.failing(t -> {
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
     * Verifies that the adapter opens a connection if auto-provisioning is enabled for the device certificate, and it
     * is not configured auto-provisioning-device-id template and auth-id template.
     *
     * @param ctx The test context
     */
    @Test
    public void testConnectSucceedsWithAutoProvisioningWithoutTemplate(final VertxTestContext ctx) {

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
                .onComplete(ctx.failing(t -> {
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
        .onComplete(ctx.failing(t -> {
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
        .onComplete(ctx.failing(t -> {
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
        .onComplete(ctx.failing(t -> {
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
     * using credentials that contain a non-existing tenant.
     *
     * @param ctx The test context
     */
    @Test
    public void testConnectFailsForNonExistingTenant(final VertxTestContext ctx) {

        // GIVEN a registered device
        final Tenant tenant = new Tenant();

        helper.registry
                .addDeviceForTenant(tenantId, tenant, deviceId, password)
                // WHEN a device of a non-existing tenant tries to connect
                .compose(ok -> connectToAdapter(IntegrationTestSupport.getUsername(deviceId, "nonExistingTenant"), "secret"))
                .onComplete(ctx.failing(t -> {
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
                    final String authId = new X500Principal("CN=4711").getName(X500Principal.RFC2253);
                    final var credential = X509CertificateCredential.fromAuthId(authId, List.of(new X509CertificateSecret()));
                    return helper.registry.addCredentials(tenantId, deviceId, Collections.singleton(credential));
                })
                // WHEN the device tries to connect using a client certificate with an unknown subject DN
                .compose(ok -> connectToAdapter(deviceCert))
                .onComplete(ctx.failing(t -> {
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
                .onComplete(ctx.failing(t -> {
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
            .onComplete(ctx.failing(t -> {
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

        helper.registry
                .addTenant(tenantId)
                .compose(ok -> {
                    return helper.registry.registerDevice(tenantId, deviceId);
                })
                .compose(ok -> {
                    final PasswordCredential secret = Credentials.createPasswordCredential(deviceId, password);
                    secret.setEnabled(false);
                    return helper.registry.addCredentials(tenantId, deviceId, List.of(secret));
                })
                // WHEN a device connects using the correct credentials
                .compose(ok -> connectToAdapter(IntegrationTestSupport.getUsername(deviceId, tenantId), password))
                .onComplete(ctx.failing(t -> {
                    // THEN the connection is refused with a NOT_AUTHORIZED code
                    ctx.verify(() -> {
                        assertThat(t).isInstanceOf(MqttConnectionException.class);
                        assertThat(((MqttConnectionException) t).code())
                            .isEqualTo(MqttConnectReturnCode.CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD);
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
                    final PasswordCredential secret = Credentials.createPasswordCredential(deviceId, password);
                    return helper.registry.addCredentials(tenantId, deviceId, Collections.singleton(secret));
                })
                // WHEN a device connects using the correct credentials
                .compose(ok -> connectToAdapter(IntegrationTestSupport.getUsername(deviceId, tenantId), password))
                .onComplete(ctx.failing(t -> {
                    // THEN the connection is refused with a NOT_AUTHORIZED code
                    ctx.verify(() -> {
                        assertThat(t).isInstanceOf(MqttConnectionException.class);
                        assertThat(((MqttConnectionException) t).code())
                            .isEqualTo(MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED);
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
            .onComplete(ctx.failing(t -> {
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
        .onComplete(ctx.failing(t -> {
            // THEN the connection is refused with a NOT_AUTHORIZED code
            ctx.verify(() -> {
                assertThat(t).isInstanceOf(MqttConnectionException.class);
                assertThat(((MqttConnectionException) t).code()).isEqualTo(MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED);
            });
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
        testDeviceConnectionIsClosedOnDeviceOrTenantDisabledOrDeleted(ctx,
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
        final JsonObject updatedDeviceData = new JsonObject()
                .put(RegistrationConstants.FIELD_ENABLED, Boolean.FALSE);
        testDeviceConnectionIsClosedOnDeviceOrTenantDisabledOrDeleted(ctx,
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
        testDeviceConnectionIsClosedOnDeviceOrTenantDisabledOrDeleted(ctx,
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
        final Tenant updatedTenant = new Tenant().setEnabled(false);
        testDeviceConnectionIsClosedOnDeviceOrTenantDisabledOrDeleted(ctx,
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
        testDeviceConnectionIsClosedOnDeviceOrTenantDisabledOrDeleted(ctx,
                () -> helper.registry.deregisterDevicesOfTenant(tenantId));
    }

    private void testDeviceConnectionIsClosedOnDeviceOrTenantDisabledOrDeleted(
            final VertxTestContext ctx, 
            final Supplier<Future<?>> deviceRegistryChangeOperation) {

        final Promise<Void> connectionClosedPromise = Promise.promise();
        // GIVEN a connected device
        helper.registry
                .addDeviceForTenant(tenantId, new Tenant(), deviceId, password)
                .compose(ok -> connectToAdapter(
                        IntegrationTestSupport.getUsername(deviceId, tenantId), password))
                .compose(conAckMsg -> {
                    ctx.verify(() -> assertThat(conAckMsg.code()).isEqualTo(MqttConnectReturnCode.CONNECTION_ACCEPTED));
                    mqttClient.closeHandler(remoteClose -> connectionClosedPromise.complete());
                    // WHEN corresponding device/tenant is removed/disabled
                    return deviceRegistryChangeOperation.get();
                })
                // THEN the device connection is closed
                .compose(ok -> connectionClosedPromise.future())
                .onComplete(ctx.succeedingThenComplete());
    }
}
