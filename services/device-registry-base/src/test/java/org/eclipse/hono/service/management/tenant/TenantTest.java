/*******************************************************************************
 * Copyright (c) 2019, 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.service.management.tenant;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static com.google.common.truth.Truth.assertThat;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import javax.security.auth.x500.X500Principal;

import org.eclipse.hono.deviceregistry.util.DeviceRegistryUtils;
import org.eclipse.hono.util.Adapter;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.eclipse.hono.util.ResourceLimits;
import org.eclipse.hono.util.ResourceLimitsPeriod.PeriodMode;
import org.eclipse.hono.util.TenantTracingConfig;
import org.eclipse.hono.util.TracingSamplingMode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.SelfSignedCertificate;

/**
 * Verifies behavior of {@link Tenant}.
 */
public class TenantTest {

    private static X509Certificate certificate;

    /**
     * Sets up class fixture.
     * @throws GeneralSecurityException if the self signed certificate cannot be created.
     * @throws IOException if the self signed certificate cannot be read.
     */
    @BeforeAll
    public static void setUp() throws GeneralSecurityException, IOException {
        final SelfSignedCertificate selfSignedCert = SelfSignedCertificate.create("eclipse.org");
        final CertificateFactory factory = CertificateFactory.getInstance("X.509");
        certificate = (X509Certificate) factory.generateCertificate(new FileInputStream(selfSignedCert.certificatePath()));
    }

    /**
     * Decode empty Tenant without any properties set.
     */
    @Test
    public void testDecodeDefault() {

        final var tenant = new JsonObject().mapTo(Tenant.class);
        assertNotNull(tenant);
        assertTrue(tenant.isEnabled());
    }

    /**
     * Decode tenant with unknown property fails.
     */
    @Test
    public void testDecodeFailsForUnknownProperties() {
        assertThrows(DecodeException.class, () -> Json.decodeValue("{\"unexpected\": \"property\"}", Tenant.class));
    }

    /**
     * Decode tenant with "enabled=false".
     */
    @Test
    public void testDecodeDisabled() {
        final var tenant = new JsonObject().put(RegistryManagementConstants.FIELD_ENABLED, false).mapTo(Tenant.class);
        assertNotNull(tenant);
        assertFalse(tenant.isEnabled());
    }

    /**
     * Decode tenant with "enabled=true".
     */
    @Test
    public void testDecodeEnabled() {
        final var tenant = new JsonObject().put(RegistryManagementConstants.FIELD_ENABLED, true).mapTo(Tenant.class);
        assertNotNull(tenant);
        assertTrue(tenant.isEnabled());
    }

    /**
     * Decode "ext" section.
     */
    @Test
    public void testDecodeExt() {
        final var tenant = new JsonObject()
                .put(RegistryManagementConstants.FIELD_EXT, new JsonObject().put("foo", "bar"))
                .mapTo(Tenant.class);
        assertNotNull(tenant);
        assertTrue(tenant.isEnabled());

        final var ext = tenant.getExtensions();
        assertNotNull(ext);
        assertEquals( "bar", ext.get("foo"));
    }

    /**
     * Decode "adapters" section.
     */
    @Test
    public void testDecodeAdapters() {
        final JsonArray adapterJson = new JsonArray()
                .add(new JsonObject()
                        .put(RegistryManagementConstants.FIELD_ADAPTERS_TYPE, "http")
                        .put(RegistryManagementConstants.FIELD_ENABLED, true)
                        .put(RegistryManagementConstants.FIELD_ADAPTERS_DEVICE_AUTHENTICATION_REQUIRED, false))
                .add(new JsonObject()
                        .put(RegistryManagementConstants.FIELD_ADAPTERS_TYPE, "mqtt"));

        final var tenant = new JsonObject()
                .put(RegistryManagementConstants.FIELD_ADAPTERS, adapterJson)
                .mapTo(Tenant.class);
        assertNotNull(tenant);
        assertTrue(tenant.isEnabled());

        final var adapters = tenant.getAdapters();
        assertNotNull(adapters);
        assertEquals( "http", adapters.get(0).getType());
        assertTrue(adapters.get(0).isEnabled());
        assertFalse(adapters.get(0).isDeviceAuthenticationRequired());
        assertEquals( "mqtt", adapters.get(1).getType());
        assertFalse(adapters.get(1).isEnabled());
        assertTrue(adapters.get(1).isDeviceAuthenticationRequired());
    }

    /**
     * Verifies that deserialization of a tenant containing configuration for multiple adapters
     * of the same type fails.
     */
    @Test
    public void testDecodeAdaptersFailsForDuplicateType() {

        final JsonArray adapterJson = new JsonArray()
                .add(new JsonObject()
                        .put(RegistryManagementConstants.FIELD_ADAPTERS_TYPE, "http")
                        .put(RegistryManagementConstants.FIELD_ENABLED, false)
                        .put(RegistryManagementConstants.FIELD_ADAPTERS_DEVICE_AUTHENTICATION_REQUIRED, true))
                .add(new JsonObject()
                        .put(RegistryManagementConstants.FIELD_ADAPTERS_TYPE, "http")
                        .put(RegistryManagementConstants.FIELD_ENABLED, true)
                        .put(RegistryManagementConstants.FIELD_ADAPTERS_DEVICE_AUTHENTICATION_REQUIRED, false));
        final JsonObject tenant = new JsonObject()
                .put(RegistryManagementConstants.FIELD_ADAPTERS, adapterJson);
        assertThrows(IllegalArgumentException.class, () -> tenant.mapTo(Tenant.class));
    }

    /**
     * Verify that a Tenant instance containing multiple "adapters" can be serialized to Json.
     */
    @Test
    public void testEncodeAdapters() {

        final Tenant tenant = new Tenant();
        tenant.setEnabled(true);
        tenant
            .addAdapterConfig(new Adapter(Constants.PROTOCOL_ADAPTER_TYPE_HTTP))
            .addAdapterConfig(new Adapter(Constants.PROTOCOL_ADAPTER_TYPE_MQTT)
                    .setEnabled(true)
                    .setDeviceAuthenticationRequired(false));

        final JsonArray result = JsonObject.mapFrom(tenant).getJsonArray(RegistryManagementConstants.FIELD_ADAPTERS);
        assertNotNull(result);
        final JsonObject httpAdapter = result.getJsonObject(0);
        final JsonObject mqttAdapter = result.getJsonObject(1);
        assertEquals(Constants.PROTOCOL_ADAPTER_TYPE_HTTP, httpAdapter.getString(RegistryManagementConstants.FIELD_ADAPTERS_TYPE));
        assertFalse(httpAdapter.getBoolean(RegistryManagementConstants.FIELD_ENABLED));
        assertTrue(httpAdapter.getBoolean(RegistryManagementConstants.FIELD_ADAPTERS_DEVICE_AUTHENTICATION_REQUIRED));
        assertEquals(Constants.PROTOCOL_ADAPTER_TYPE_MQTT, mqttAdapter.getString(RegistryManagementConstants.FIELD_ADAPTERS_TYPE));
        assertTrue(mqttAdapter.getBoolean(RegistryManagementConstants.FIELD_ENABLED));
        assertFalse(mqttAdapter.getBoolean(RegistryManagementConstants.FIELD_ADAPTERS_DEVICE_AUTHENTICATION_REQUIRED));
    }

    /**
     * Verifies that decoding of a tenant object with empty adapters list fails.
     */
    @Test
    public void testDecodeEmptyAdaptersListFails() {
        final JsonObject tenantJson = new JsonObject()
                .put(RegistryManagementConstants.FIELD_ADAPTERS, new JsonArray());
        assertThrows(IllegalArgumentException.class, () -> {
            tenantJson.mapTo(Tenant.class);
        });
    }

    /**
     * Verifies that decoding of a tenant object having more than one adapter of the same type fails.
     */
    @Test
    public void testWithMultipleAdapterEntriesOfSameType() {
        final JsonObject httpAdapterConfig = new JsonObject()
                .put(RegistryManagementConstants.FIELD_ADAPTERS_TYPE, "http")
                .put(RegistryManagementConstants.FIELD_ENABLED, false)
                .put(RegistryManagementConstants.FIELD_ADAPTERS_DEVICE_AUTHENTICATION_REQUIRED, true);
        final JsonArray adaptersConfig = new JsonArray()
                .add(httpAdapterConfig)
                .add(httpAdapterConfig);
        final var tenant = new JsonObject()
                .put(RegistryManagementConstants.FIELD_ADAPTERS, adaptersConfig);

        assertThrows(IllegalArgumentException.class, () -> tenant.mapTo(Tenant.class));
    }

    /**
     * Verifies that adding an adapter fails, if the adapter's type is same as that of any
     * already existing adapters.
     */
    @Test
    public void testAddAdapterOfAlreadyExistingType() {
        final Tenant tenant = new Tenant();
        tenant.setEnabled(true);
        tenant.addAdapterConfig(new Adapter(Constants.PROTOCOL_ADAPTER_TYPE_HTTP)
                .setEnabled(false)
                .setDeviceAuthenticationRequired(true));
        assertThrows(IllegalArgumentException.class, () -> tenant
                .addAdapterConfig(new Adapter(Constants.PROTOCOL_ADAPTER_TYPE_HTTP)
                        .setEnabled(false)
                        .setDeviceAuthenticationRequired(true)));
    }

    /**
     * Decode tenant with "minimum-message-size=4096".
     */
    @Test
    public void testDecodeMinimumMessageSize() {
        final JsonObject json = new JsonObject().put(RegistryManagementConstants.FIELD_MINIMUM_MESSAGE_SIZE, 4096);
        final var tenant = json.mapTo(Tenant.class);
        assertNotNull(tenant);
        assertEquals(4096, tenant.getMinimumMessageSize());
    }

    /**
     * Decode Tenant without setting "minimum-message-size".
     */
    @Test
    public void testDecodeWithoutMinimumMessageSize() {
        final var tenant = new JsonObject().mapTo(Tenant.class);
        assertNotNull(tenant);
        assertEquals(RegistryManagementConstants.DEFAULT_MINIMUM_MESSAGE_SIZE, tenant.getMinimumMessageSize());
    }

    /**
     * Decode "resource-limits" section.
     */
    @Test
    public void testDecodeResourceLimits() {

        final JsonObject tenantSpec = new JsonObject()
                .put(RegistryManagementConstants.FIELD_RESOURCE_LIMITS, new JsonObject()
                        .put(RegistryManagementConstants.FIELD_MAX_CONNECTIONS, 100)
                        .put(RegistryManagementConstants.FIELD_MAX_TTL, 60)
                        .put(RegistryManagementConstants.FIELD_MAX_TTL_COMMAND_RESPONSE, 40)
                        .put(RegistryManagementConstants.FIELD_MAX_TTL_TELEMETRY_QOS0, 10)
                        .put(RegistryManagementConstants.FIELD_MAX_TTL_TELEMETRY_QOS1, 30)
                        .put(RegistryManagementConstants.FIELD_DATA_VOLUME, new JsonObject()
                                .put(RegistryManagementConstants.FIELD_MAX_BYTES, 20_000_000)
                                .put(RegistryManagementConstants.FIELD_EFFECTIVE_SINCE, "2019-04-25T14:30:00+02:00")
                                .put(RegistryManagementConstants.FIELD_PERIOD, new JsonObject()
                                        .put(RegistryManagementConstants.FIELD_PERIOD_MODE, "days")
                                        .put(RegistryManagementConstants.FIELD_PERIOD_NO_OF_DAYS, 90)))
                        .put(RegistryManagementConstants.FIELD_CONNECTION_DURATION, new JsonObject()
                                .put(RegistryManagementConstants.FIELD_MAX_MINUTES, 20_000_000)
                                .put(RegistryManagementConstants.FIELD_EFFECTIVE_SINCE, "2019-04-25T14:30:00+02:00")
                                .put(RegistryManagementConstants.FIELD_PERIOD, new JsonObject()
                                        .put(RegistryManagementConstants.FIELD_PERIOD_MODE, "monthly"))));

        final Tenant tenant = tenantSpec.mapTo(Tenant.class);
        assertNotNull(tenant);
        assertTrue(tenant.isEnabled());

        final ResourceLimits limits = tenant.getResourceLimits();
        assertNotNull(limits);
        assertEquals(100, limits.getMaxConnections());
        assertEquals(60, limits.getMaxTtl());
        assertEquals(40, limits.getMaxTtlCommandResponse());
        assertEquals(10, limits.getMaxTtlTelemetryQoS0());
        assertEquals(30, limits.getMaxTtlTelemetryQoS1());
        assertNotNull(limits.getDataVolume());
        assertEquals(
                DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse("2019-04-25T14:30:00+02:00", OffsetDateTime::from).toInstant(),
                limits.getDataVolume().getEffectiveSince());
        assertEquals(20_000_000, limits.getDataVolume().getMaxBytes());
        assertNotNull(limits.getDataVolume().getPeriod());
        assertEquals(PeriodMode.days, limits.getDataVolume().getPeriod().getMode());
        assertEquals(90, limits.getDataVolume().getPeriod().getNoOfDays());
        assertNotNull(limits.getConnectionDuration());
        assertEquals(
                DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse("2019-04-25T14:30:00+02:00", OffsetDateTime::from)
                        .toInstant(),
                limits.getConnectionDuration().getEffectiveSince());
        assertEquals(20_000_000, limits.getConnectionDuration().getMaxMinutes());
        assertNotNull(limits.getConnectionDuration().getPeriod());
        assertEquals(PeriodMode.monthly, limits.getConnectionDuration().getPeriod().getMode());
    }

    /**
     * Encode "resource-limits" section.
     */
    @Test
    public void testEncodeResourceLimitsDoesNotIncludeDefaultValues() {

        final ResourceLimits limits = new ResourceLimits();
        final JsonObject json = JsonObject.mapFrom(limits);
        assertFalse(json.containsKey(RegistryManagementConstants.FIELD_MAX_CONNECTIONS));
        final ResourceLimits deserializedLimits = json.mapTo(ResourceLimits.class);
        assertThat(deserializedLimits.getMaxConnections()).isEqualTo(-1);
    }

    /**
     * Encode "registration-limits" section.
     */
    @Test
    public void testEncodeRegistrationLimits() {
        final var tenant = new Tenant()
                .setRegistrationLimits(new RegistrationLimits()
                        .setMaxNumberOfDevices(100)
                        .setMaxCredentialsPerDevice(5));
        final var json = JsonObject.mapFrom(tenant);
        final var limits = json.getJsonObject(RegistryManagementConstants.FIELD_REGISTRATION_LIMITS);
        assertNotNull(limits);
        assertEquals(100, limits.getInteger(RegistryManagementConstants.FIELD_MAX_DEVICES));
        assertEquals(5,  limits.getInteger(RegistryManagementConstants.FIELD_MAX_CREDENTIALS_PER_DEVICE));
    }

    /**
     * Decode "registration-limits" section.
     */
    @Test
    public void testDecodeRegistrationLimits() {
        final JsonObject tenantSpec = new JsonObject()
                .put(RegistryManagementConstants.FIELD_REGISTRATION_LIMITS, new JsonObject()
                        .put(RegistryManagementConstants.FIELD_MAX_DEVICES, 100)
                        .put(RegistryManagementConstants.FIELD_MAX_CREDENTIALS_PER_DEVICE, 5));

        final Tenant tenant = tenantSpec.mapTo(Tenant.class);
        assertNotNull(tenant);
        assertTrue(tenant.isEnabled());
        assertEquals(100, tenant.getRegistrationLimits().getMaxNumberOfDevices());
        assertEquals(5, tenant.getRegistrationLimits().getMaxCredentialsPerDevice());
    }

    /**
     * Verify that the trust anchor IDs are unique.
     */
    @Test
    public void verifyAssertTrustAnchorIdUniqueness() {
        final X500Principal subjectDn = new X500Principal("O=Eclipse, OU=Hono, CN=ca");
        // GIVEN a tenant with two trust anchor entries having same IDs
        final JsonObject tenantJson = new JsonObject()
                .put(RegistryManagementConstants.FIELD_PAYLOAD_TRUSTED_CA, new JsonArray()
                        // Tenant Anchor 1
                        .add(new JsonObject()
                                .put(RegistryManagementConstants.FIELD_ID, "NON-UNIQUE-ID")
                                .put(RegistryManagementConstants.FIELD_PAYLOAD_SUBJECT_DN,
                                        subjectDn.getName(X500Principal.RFC2253))
                                .put(RegistryManagementConstants.FIELD_PAYLOAD_PUBLIC_KEY,
                                        "NOTAPUBLICKEY".getBytes(StandardCharsets.UTF_8)))
                        // Tenant Anchor 2
                        .add(new JsonObject()
                                .put(RegistryManagementConstants.FIELD_ID, "NON-UNIQUE-ID")
                                .put(RegistryManagementConstants.FIELD_PAYLOAD_SUBJECT_DN,
                                        subjectDn.getName(X500Principal.RFC2253))
                                .put(RegistryManagementConstants.FIELD_PAYLOAD_PUBLIC_KEY,
                                        "NOTAPUBLICKEY".getBytes(StandardCharsets.UTF_8))));

        final Tenant tenant = tenantJson.mapTo(Tenant.class);
        // The trust anchors IDs are not unique, thereby assertion fails
        // with an IllegalStateException.
        assertThrows(IllegalStateException.class, tenant::assertTrustAnchorIdUniquenessAndCreateMissingIds);
    }

    /**
     * Verify that IDs are generated for the trust anchor entries with no IDs.
     */
    @Test
    public void verifyCreateMissingTrustAnchorIds() {
        final String trustedAnchorId1 = DeviceRegistryUtils.getUniqueIdentifier();
        final X500Principal subjectDn = new X500Principal("O=Eclipse, OU=Hono, CN=ca");
        // GIVEN a tenant with two trust anchor entries
        final JsonObject tenantJson = new JsonObject()
                .put(RegistryManagementConstants.FIELD_PAYLOAD_TRUSTED_CA, new JsonArray()
                        // Tenant Anchor 1
                        .add(new JsonObject()
                                .put(RegistryManagementConstants.FIELD_ID, trustedAnchorId1)
                                .put(RegistryManagementConstants.FIELD_PAYLOAD_SUBJECT_DN,
                                        subjectDn.getName(X500Principal.RFC2253))
                                .put(RegistryManagementConstants.FIELD_PAYLOAD_PUBLIC_KEY,
                                        "NOTAPUBLICKEY".getBytes(StandardCharsets.UTF_8)))
                        // Tenant Anchor 2
                        .add(new JsonObject()
                                .put(RegistryManagementConstants.FIELD_PAYLOAD_SUBJECT_DN,
                                        subjectDn.getName(X500Principal.RFC2253))
                                .put(RegistryManagementConstants.FIELD_PAYLOAD_PUBLIC_KEY,
                                        "NOTAPUBLICKEY".getBytes(StandardCharsets.UTF_8))));

        final Tenant tenant = tenantJson.mapTo(Tenant.class);
        // Assert and generate missing trust anchor ids
        tenant.assertTrustAnchorIdUniquenessAndCreateMissingIds();

        assertNotNull(tenant);
        assertNotNull(tenant.getTrustedCertificateAuthorities());
        assertEquals(2, tenant.getTrustedCertificateAuthorities().size());
        // Verify trust anchor ids
        assertNotNull(tenant.getTrustedCertificateAuthorities().get(0).getId());
        assertEquals(trustedAnchorId1, tenant.getTrustedCertificateAuthorities().get(0).getId());
        assertNotNull(tenant.getTrustedCertificateAuthorities().get(1).getId());
    }

    /**
     * Decode "trusted-ca" section for an X.509 certificate.
     *
     * @throws CertificateException if the self signed certificate cannot be encoded.
     */
    @Test
    public void testDecodeTrustedCAUsingCert() throws CertificateException {

        final JsonObject ca = new JsonObject()
                .put(RegistryManagementConstants.FIELD_PAYLOAD_CERT, certificate.getEncoded());
        final JsonObject tenantJson = new JsonObject()
                .put(RegistryManagementConstants.FIELD_PAYLOAD_TRUSTED_CA, new JsonArray().add(ca));

        final Tenant tenant = tenantJson.mapTo(Tenant.class);
        assertTrue(tenant.isValid());
    }

    /**
     * Verifies that decoding of a tenant object fails, when a trusted ca entry has an invalid auth-id template.
     *
     * @throws CertificateEncodingException if the self-signed certificate cannot be encoded.
     */
    @Test
    void testDecodeWithInvalidAuthIdTemplateFails() throws CertificateEncodingException {
        final JsonObject trustedCa = new JsonObject()
                .put(RegistryManagementConstants.FIELD_PAYLOAD_CERT, certificate.getEncoded())
                .put(RegistryManagementConstants.FIELD_AUTH_ID_TEMPLATE, "id-{{unsupported-placeholder}}");
        final JsonObject tenantJson = new JsonObject()
                .put(RegistryManagementConstants.FIELD_PAYLOAD_TRUSTED_CA, new JsonArray().add(trustedCa));

        assertThrows(IllegalArgumentException.class, () -> tenantJson.mapTo(Tenant.class));
    }

    /**
     * Decode tenant with a "tracing" property set.
     */
    @Test
    public void testDecodeTraceSampling() {


        final JsonObject tracingConfigJson = new JsonObject()
                .put(RegistryManagementConstants.FIELD_TRACING_SAMPLING_MODE, TracingSamplingMode.ALL.getFieldValue())
                .put(RegistryManagementConstants.FIELD_TRACING_SAMPLING_MODE_PER_AUTH_ID, new JsonObject()
                        .put("authId1", TracingSamplingMode.ALL.getFieldValue())
                        .put("authId2", TracingSamplingMode.DEFAULT.getFieldValue()));

        final JsonObject tenantJson = new JsonObject();
        tenantJson.put(RegistryManagementConstants.FIELD_TRACING, tracingConfigJson);

        final var tenant = tenantJson.mapTo(Tenant.class);
        assertNotNull(tenant);
        final TenantTracingConfig tracingConfig = tenant.getTracing();
        assertNotNull(tracingConfig);
        assertEquals(TracingSamplingMode.ALL, tracingConfig.getSamplingMode());
        assertEquals(TracingSamplingMode.ALL, tracingConfig.getSamplingModePerAuthId().get("authId1"));
        assertEquals(TracingSamplingMode.DEFAULT, tracingConfig.getSamplingModePerAuthId().get("authId2"));
    }

    /**
     * Verifies that a serialized default tenant object has no properties.
     */
    @Test
    public void testEncodeDefault() {
        final var json = JsonObject.mapFrom(new Tenant());
        assertThat(json).isEmpty();
    }

    /**
     * Encode tenant with alias.
     */
    @Test
    public void testEncodeWithAlias() {
        final var tenant = new Tenant();
        tenant.setAlias("the-alias-1");
        final var json = JsonObject.mapFrom(tenant);
        assertThat(json.getString(RegistryManagementConstants.FIELD_ALIAS)).isEqualTo("the-alias-1");
    }

    /**
     * Verifies that the alias property cannot be set to a non-LDH label.
     */
    @Test
    public void testSetAliasAcceptsLdhLabelOnly() {
        final var tenant = new Tenant();
        assertAll(
                () -> assertThrows(NullPointerException.class, () -> tenant.setAlias(null)),
                () -> assertThrows(IllegalArgumentException.class, () -> tenant.setAlias("NOT_a-valid=Alias")));
    }

    /**
     * Encode tenant with "enabled=true".
     */
    @Test
    public void testEncodeEnabled() {
        final var tenant = new Tenant();
        tenant.setEnabled(true);
        final var json = JsonObject.mapFrom(tenant);
        assertNotNull(json);
        assertTrue(json.getBoolean(RegistryManagementConstants.FIELD_ENABLED));
        assertNull(json.getJsonObject(RegistryManagementConstants.FIELD_EXT));
    }

    /**
     * Encode tenant with "enabled=false".
     */
    @Test
    public void testEncodeDisabled() {
        final var tenant = new Tenant();
        tenant.setEnabled(false);
        final var json = JsonObject.mapFrom(tenant);
        assertNotNull(json);
        assertFalse(json.getBoolean(RegistryManagementConstants.FIELD_ENABLED));
        assertNull(json.getJsonObject(RegistryManagementConstants.FIELD_EXT));
    }

    /**
     * Encode tenant with non-default <em>minimum-message-size</em>.
     */
    @Test
    public void testEncodeMinimumMessageSize() {
        final var tenant = new Tenant();
        tenant.setMinimumMessageSize(RegistryManagementConstants.DEFAULT_MINIMUM_MESSAGE_SIZE + 10);
        final var json = JsonObject.mapFrom(tenant);
        assertNotNull(json);
        assertNotNull(json.getInteger(RegistryManagementConstants.FIELD_MINIMUM_MESSAGE_SIZE));
    }

    /**
     * Encode tenant with a "tracing" value set.
     */
    @Test
    public void testEncodeTraceSamplingModePerAuthId() {
        final var tenant = new Tenant();
        final TenantTracingConfig tracingConfig = new TenantTracingConfig();
        tracingConfig.setSamplingMode(TracingSamplingMode.ALL);
        tracingConfig.setSamplingModePerAuthId(
                Map.of("authId1", TracingSamplingMode.ALL, "authId2", TracingSamplingMode.DEFAULT));
        tenant.setTracing(tracingConfig);
        final var json = JsonObject.mapFrom(tenant);
        assertNotNull(json);
        final JsonObject tracingConfigJson = json.getJsonObject(RegistryManagementConstants.FIELD_TRACING);
        assertNotNull(tracingConfigJson);
        assertEquals(TracingSamplingMode.ALL.getFieldValue(), tracingConfigJson.getString(RegistryManagementConstants.FIELD_TRACING_SAMPLING_MODE));
        final JsonObject traceSamplingModePerAuthIdJson = tracingConfigJson.getJsonObject(RegistryManagementConstants.FIELD_TRACING_SAMPLING_MODE_PER_AUTH_ID);
        assertNotNull(traceSamplingModePerAuthIdJson);
        assertEquals(TracingSamplingMode.ALL.getFieldValue(), traceSamplingModePerAuthIdJson.getString("authId1"));
        assertEquals(TracingSamplingMode.DEFAULT.getFieldValue(), traceSamplingModePerAuthIdJson.getString("authId2"));
    }

}
