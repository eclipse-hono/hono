/*******************************************************************************
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

import static org.hamcrest.Matchers.is;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;

import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.eclipse.hono.util.ResourceLimits;
import org.eclipse.hono.util.TenantConstants;
import org.eclipse.hono.util.TenantTracingConfig;
import org.eclipse.hono.util.TracingSamplingMode;
import org.hamcrest.collection.IsEmptyIterable;
import org.junit.jupiter.api.Test;


import io.vertx.core.json.JsonArray;

/**
 * Verifies {@link Tenant}.
 */
class TenantTest {

    /**
     * Decode Tenant with absent "enabled" flag.
     */
    @Test
    public void testDecodeDefault() {
        final var tenant = Json.decodeValue("{}", Tenant.class);
        assertNotNull(tenant);
        assertNull(tenant.getEnabled());
    }


    /**
     * Decode tenant with "enabled=false".
     */
    @Test
    public void testDecodeDisabled() {
        final var tenant = Json.decodeValue("{\"enabled\": false}", Tenant.class);
        assertNotNull(tenant);
        assertFalse(tenant.getEnabled());
    }

    /**
     * Decode tenant with "enabled=true".
     */
    @Test
    public void testDecodeEnabled() {
        final var tenant = Json.decodeValue("{\"enabled\": true}", Tenant.class);
        assertNotNull(tenant);
        assertTrue(tenant.getEnabled());
    }

    /**
     * Decode "ext" section.
     */
    @Test
    public void testDecodeExt() {
        final var tenant = Json.decodeValue("{\"ext\": {\"foo\": \"bar\"}}", Tenant.class);
        assertNotNull(tenant);
        assertNull(tenant.getEnabled());

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

        final var tenant = Json.decodeValue(new JsonObject().put(RegistryManagementConstants.FIELD_ADAPTERS, adapterJson).toString(), Tenant.class);
        assertNotNull(tenant);
        assertNull(tenant.getEnabled());

        final var adapters = tenant.getAdapters();
        assertNotNull(adapters);
        assertEquals("http", adapters.get(0).getType());
    }

    /**
     * Decode tenant with "minimum-message-size=4096".
     */
    @Test
    public void testDecodeMinimumMessageSize() {
        final var tenant = Json.decodeValue("{\"minimum-message-size\": 4096}", Tenant.class);
        assertNotNull(tenant);
        assertEquals(4096, tenant.getMinimumMessageSize());
    }

    /**
     * Decode Tenant without setting "minimum-message-size".
     */
    @Test
    public void testDecodeWithoutMinimumMessageSize() {
        final var tenant = Json.decodeValue("{}", Tenant.class);
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
                                .put(TenantConstants.FIELD_EFFECTIVE_SINCE, "2019-04-25T14:30:00Z")
                                .put(TenantConstants.FIELD_PERIOD, new JsonObject()
                                        .put(TenantConstants.FIELD_PERIOD_MODE, "days")
                                        .put(TenantConstants.FIELD_PERIOD_NO_OF_DAYS, 90))));

        final Tenant tenant = tenantSpec.mapTo(Tenant.class);
        assertNotNull(tenant);
        assertNull(tenant.getEnabled());

        final ResourceLimits limits = tenant.getResourceLimits();
        assertNotNull(limits);
        assertEquals(100, limits.getMaxConnections());
        assertEquals(30, limits.getMaxTtl());
        assertNotNull(limits.getDataVolume());
        assertEquals(
                DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse("2019-04-25T14:30:00Z", OffsetDateTime::from).toInstant(),
                limits.getDataVolume().getEffectiveSince());
        assertEquals(20_000_000, limits.getDataVolume().getMaxBytes());
        assertNotNull(limits.getDataVolume().getPeriod());
        assertEquals("days", limits.getDataVolume().getPeriod().getMode());
        assertEquals(90, limits.getDataVolume().getPeriod().getNoOfDays());
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
     * Decode "trusted-ca" section.
     */
    @Test
    public void testDecodeTrustedCA() {

        final var notBefore = LocalDateTime.of(2015,  Month.JUNE, 05, 18, 00, 05)
                .atOffset(ZoneOffset.of("-02:00"));
        final var notAfter = LocalDateTime.of(2020,  Month.JUNE, 05, 18, 00, 05)
                .atOffset(ZoneOffset.of("-05:00"));

        final JsonArray trustedCaJson = new JsonArray().add(
                new JsonObject()
                        .put(TenantConstants.FIELD_PAYLOAD_SUBJECT_DN, "org.eclipse")
                        .put(TenantConstants.FIELD_PAYLOAD_PUBLIC_KEY, "abc123".getBytes(StandardCharsets.UTF_8))
                        .put(TenantConstants.FIELD_PAYLOAD_NOT_BEFORE, "2015-06-05T20:00:05Z")
                        .put(TenantConstants.FIELD_PAYLOAD_NOT_AFTER, "2020-06-05T23:00:05Z")
                        .put(TenantConstants.FIELD_PAYLOAD_KEY_ALGORITHM, "def456"));

        final var tenant = Json.decodeValue(
                new JsonObject().put(RegistryManagementConstants.FIELD_PAYLOAD_TRUSTED_CA, trustedCaJson).toString(),
                Tenant.class);
        assertNotNull(tenant);
        assertNull(tenant.getEnabled());

        final var storedAuthorities = tenant.getTrustedAuthorities();
        assertNotNull(storedAuthorities);
        assertEquals(1, storedAuthorities.size());

        final var storedCa = storedAuthorities.get(0);
        assertEquals("org.eclipse", storedCa.getSubjectDn());
        assertArrayEquals("abc123".getBytes(StandardCharsets.UTF_8), storedCa.getPublicKey());
        assertEquals(notBefore.toInstant(), storedCa.getNotBefore());
        assertEquals(notAfter.toInstant(), storedCa.getNotAfter());
        assertEquals("def456", storedCa.getKeyAlgorithm());
    }

    /**
     * Decode tenant with a "tracing" property set.
     */
    @Test
    public void testDecodeTraceSampling() {
        final JsonObject tenantJson = new JsonObject();
        final JsonObject tracingConfigJson = new JsonObject();
        tracingConfigJson.put("sampling-mode", "all");
        final JsonObject samplingModePerAuthIdMap = new JsonObject()
                .put("authId1", "all")
                .put("authId2", "default");
        tracingConfigJson.put("sampling-mode-per-auth-id", samplingModePerAuthIdMap);
        tenantJson.put("tracing", tracingConfigJson);
        final var tenant = Json.decodeValue(tenantJson.toString(), Tenant.class);
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
        assertNotNull(json);
        assertNull(json.getBoolean("enabled"));
        assertNull(json.getJsonObject("ext"));
        assertThat(json, IsEmptyIterable.emptyIterable());
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
        assertTrue(json.getBoolean("enabled"));
        assertNull(json.getJsonObject("ext"));
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
        assertFalse(json.getBoolean("enabled"));
        assertNull(json.getJsonObject("ext"));
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
        assertEquals(4096, json.getInteger("minimum-message-size"));
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

    /**
     * Verify that a Tenant instance containing multiple trust configurations can be serialized to JSON.
     *
     */
    @Test
    public void testSerializeTrustedAuthorities() {

        final var notBefore = LocalDateTime.of(2015, Month.JANUARY, 01, 15, 00, 00)
                .atOffset(ZoneOffset.of("+02:00"));

        final var notAfter = LocalDateTime.of(2025,  Month.JANUARY, 01, 15, 00, 00)
                .atOffset(ZoneOffset.of("+05:00"));

        final TrustedCertificateAuthority ca1 = new TrustedCertificateAuthority()
                .setSubjectDn("subjectDn-one")
                .setPublicKey("aGFsbG8gT21hCg==".getBytes())
                .setNotBefore(notBefore.toInstant())
                .setNotAfter(notAfter.toInstant());

        final TrustedCertificateAuthority ca2 = new TrustedCertificateAuthority()
                .setSubjectDn("subjectDn-two")
                .setPublicKey("aGFsbG8gT21hCg==".getBytes())
                .setNotBefore(notBefore.toInstant())
                .setNotAfter(notAfter.toInstant());

        final List<TrustedCertificateAuthority> trustAuthorities = new ArrayList<>();
        trustAuthorities.add(ca1);
        trustAuthorities.add(ca2);

        final Tenant tenant = new Tenant();
        tenant.setEnabled(true);
        tenant.setTrustedAuthorities(trustAuthorities);

        final JsonArray jsonArray = JsonObject.mapFrom(tenant).getJsonArray(RegistryManagementConstants.FIELD_PAYLOAD_TRUSTED_CA);
        assertNotNull(jsonArray);
        assertEquals("subjectDn-one", jsonArray.getJsonObject(0).getString(TenantConstants.FIELD_PAYLOAD_SUBJECT_DN));
        assertEquals("subjectDn-two", jsonArray.getJsonObject(1).getString(TenantConstants.FIELD_PAYLOAD_SUBJECT_DN));

        assertEquals("2015-01-01T13:00:00Z",
                jsonArray.getJsonObject(0).getString(TenantConstants.FIELD_PAYLOAD_NOT_BEFORE));
        assertEquals("2025-01-01T10:00:00Z",
                jsonArray.getJsonObject(1).getString(TenantConstants.FIELD_PAYLOAD_NOT_AFTER));

    }

    /**
     * Verifies that a trusted certificate authority can be decoded from a trusted-ca
     * JSON payload in which only the <em>cert</em> property is set.
     * 
     * @throws Exception if the test certificate cannot be created.
     */
    @Test
    public void testDecodeTrustedCaWithCertProperty() throws Exception {
        final SelfSignedCertificate cert = new SelfSignedCertificate("hono.eclipse.org");
        final JsonObject config = new JsonObject()
                .put(RegistryManagementConstants.FIELD_PAYLOAD_CERT, cert.cert().getEncoded());
        final TrustedCertificateAuthority trustedCa = config.mapTo(TrustedCertificateAuthority.class);
        assertNotNull(trustedCa);
        assertThat(trustedCa.getSubjectDn(), is(cert.cert().getSubjectDN().getName()));
        assertThat(trustedCa.getPublicKey(), is(cert.cert().getPublicKey().getEncoded()));
        assertThat(trustedCa.getKeyAlgorithm(), is(cert.cert().getPublicKey().getAlgorithm()));
        assertThat(trustedCa.getNotBefore(), is(cert.cert().getNotBefore().toInstant()));
        assertThat(trustedCa.getNotAfter(), is(cert.cert().getNotAfter().toInstant()));
    }
}
