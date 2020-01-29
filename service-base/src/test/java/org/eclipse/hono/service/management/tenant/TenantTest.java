/*******************************************************************************
 * Copyright (c) 2019, 2020 Contributors to the Eclipse Foundation
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyIterable;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.eclipse.hono.util.ResourceLimits;
import org.eclipse.hono.util.TenantConstants;
import org.eclipse.hono.util.TenantTracingConfig;
import org.eclipse.hono.util.TracingSamplingMode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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
        assertNull(tenant.isEnabled());
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
        assertNull(tenant.isEnabled());

        final var ext = tenant.getExtensions();
        assertNotNull(ext);
        assertEquals( "bar", ext.get("foo"));
    }

    /**
     * Decode "adapters" section.
     */
    @Test
    public void testDecodeAdapters() {
        final JsonArray adapterJson = new JsonArray().add(
                    new JsonObject()
                            .put(RegistryManagementConstants.FIELD_ADAPTERS_TYPE, "http")
                            .put(RegistryManagementConstants.FIELD_ENABLED, false)
                            .put(RegistryManagementConstants.FIELD_ADAPTERS_DEVICE_AUTHENTICATION_REQUIRED, true));

        final var tenant = new JsonObject()
                .put(RegistryManagementConstants.FIELD_ADAPTERS, adapterJson)
                .mapTo(Tenant.class);
        assertNotNull(tenant);
        assertNull(tenant.isEnabled());

        final var adapters = tenant.getAdapters();
        assertNotNull(adapters);
        assertEquals( "http", adapters.get(0).getType());
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
                        .put(TenantConstants.FIELD_MAX_CONNECTIONS, 100)
                        .put(TenantConstants.FIELD_MAX_TTL, 30)
                        .put(TenantConstants.FIELD_DATA_VOLUME, new JsonObject()
                                .put(TenantConstants.FIELD_MAX_BYTES, 20_000_000)
                                .put(TenantConstants.FIELD_EFFECTIVE_SINCE, "2019-04-25T14:30:00+02:00")
                                .put(TenantConstants.FIELD_PERIOD, new JsonObject()
                                        .put(TenantConstants.FIELD_PERIOD_MODE, "days")
                                        .put(TenantConstants.FIELD_PERIOD_NO_OF_DAYS, 90)))
                        .put(TenantConstants.FIELD_CONNECTION_DURATION, new JsonObject()
                                .put(TenantConstants.FIELD_MAX_MINUTES, 20_000_000)
                                .put(TenantConstants.FIELD_EFFECTIVE_SINCE, "2019-04-25T14:30:00+02:00")
                                .put(TenantConstants.FIELD_PERIOD, new JsonObject()
                                        .put(TenantConstants.FIELD_PERIOD_MODE, "monthly"))));

        final Tenant tenant = tenantSpec.mapTo(Tenant.class);
        assertNotNull(tenant);
        assertNull(tenant.isEnabled());

        final ResourceLimits limits = tenant.getResourceLimits();
        assertNotNull(limits);
        assertEquals(100, limits.getMaxConnections());
        assertEquals(30, limits.getMaxTtl());
        assertNotNull(limits.getDataVolume());
        assertEquals(
                DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse("2019-04-25T14:30:00+02:00", OffsetDateTime::from).toInstant(),
                limits.getDataVolume().getEffectiveSince());
        assertEquals(20_000_000, limits.getDataVolume().getMaxBytes());
        assertNotNull(limits.getDataVolume().getPeriod());
        assertEquals("days", limits.getDataVolume().getPeriod().getMode());
        assertEquals(90, limits.getDataVolume().getPeriod().getNoOfDays());
        assertNotNull(limits.getConnectionDuration());
        assertEquals(
                DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse("2019-04-25T14:30:00+02:00", OffsetDateTime::from)
                        .toInstant(),
                limits.getConnectionDuration().getEffectiveSince());
        assertEquals(20_000_000, limits.getConnectionDuration().getMaxMinutes());
        assertNotNull(limits.getConnectionDuration().getPeriod());
        assertEquals("monthly", limits.getConnectionDuration().getPeriod().getMode());
    }

    /**
     * Encode "resource-limits" section.
     */
    @Test
    public void testEncodeResourceLimitsDoesNotIncludeDefaultValues() {

        final ResourceLimits limits = new ResourceLimits();
        final JsonObject json = JsonObject.mapFrom(limits);
        assertFalse(json.containsKey(TenantConstants.FIELD_MAX_CONNECTIONS));
        final ResourceLimits deserializedLimits = json.mapTo(ResourceLimits.class);
        assertThat(deserializedLimits.getMaxConnections(), is(-1));
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
     * Encode with absent "enabled" flag.
     */
    @Test
    public void testEncodeDefault() {
        final var json = JsonObject.mapFrom(new Tenant());
        assertThat(json, is(emptyIterable()));
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
     * Encode tenant with "minimum-message-size=4096".
     */
    @Test
    public void testEncodeMinimumMessageSize() {
        final var tenant = new Tenant();
        tenant.setMinimumMessageSize(4096);
        final var json = JsonObject.mapFrom(tenant);
        assertNotNull(json);
        assertEquals(4096, json.getInteger(RegistryManagementConstants.FIELD_MINIMUM_MESSAGE_SIZE));
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
        final JsonObject tracingConfigJson = json.getJsonObject(TenantConstants.FIELD_TRACING);
        assertNotNull(tracingConfigJson);
        assertEquals(TracingSamplingMode.ALL.getFieldValue(), tracingConfigJson.getString(TenantConstants.FIELD_TRACING_SAMPLING_MODE));
        final JsonObject traceSamplingModePerAuthIdJson = tracingConfigJson.getJsonObject(TenantConstants.FIELD_TRACING_SAMPLING_MODE_PER_AUTH_ID);
        assertNotNull(traceSamplingModePerAuthIdJson);
        assertEquals(TracingSamplingMode.ALL.getFieldValue(), traceSamplingModePerAuthIdJson.getString("authId1"));
        assertEquals(TracingSamplingMode.DEFAULT.getFieldValue(), traceSamplingModePerAuthIdJson.getString("authId2"));
    }

    /**
     * Verify that a Tenant instance containing multiple "adapters" can be serialized to Json.
     */
    @Test
    public void testSerializeAdapters() {

        final Tenant tenant = new Tenant();
        tenant.setEnabled(true);
        tenant
            .addAdapterConfig(new Adapter(Constants.PROTOCOL_ADAPTER_TYPE_HTTP)
                    .setEnabled(false)
                    .setDeviceAuthenticationRequired(true))
            .addAdapterConfig(new Adapter(Constants.PROTOCOL_ADAPTER_TYPE_MQTT)
                    .setEnabled(true)
                    .setDeviceAuthenticationRequired(true));

        final JsonArray result = JsonObject.mapFrom(tenant).getJsonArray(TenantConstants.FIELD_ADAPTERS);
        assertNotNull(result);
        assertEquals(Constants.PROTOCOL_ADAPTER_TYPE_HTTP, result.getJsonObject(0).getString(TenantConstants.FIELD_ADAPTERS_TYPE));
        assertEquals(Constants.PROTOCOL_ADAPTER_TYPE_MQTT, result.getJsonObject(1).getString(TenantConstants.FIELD_ADAPTERS_TYPE));
        assertEquals(false, result.getJsonObject(0).getBoolean(TenantConstants.FIELD_ENABLED));
        assertEquals(true, result.getJsonObject(0).getBoolean(TenantConstants.FIELD_ADAPTERS_DEVICE_AUTHENTICATION_REQUIRED));
    }
}
