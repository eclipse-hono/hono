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

import static org.eclipse.hono.util.TenantConstants.FIELD_ADAPTERS_TYPE;
import static org.eclipse.hono.util.TenantConstants.FIELD_ADAPTERS;
import static org.eclipse.hono.util.TenantConstants.FIELD_ADAPTERS_DEVICE_AUTHENTICATION_REQUIRED;
import static org.eclipse.hono.util.TenantConstants.FIELD_ENABLED;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import io.vertx.core.json.JsonArray;

import java.nio.charset.StandardCharsets;

import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;

import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.hamcrest.collection.IsEmptyIterable;
import org.junit.jupiter.api.Test;

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
                            .put("type", "http")
                            .put("enabled", false)
                            .put("device-authentication-required", true));

        final var tenant = Json.decodeValue( new JsonObject().put("adapters", adapterJson).toString(), Tenant.class);
        assertNotNull(tenant);
        assertNull(tenant.getEnabled());

        final var adapters = tenant.getAdapters();
        assertNotNull(adapters);
        assertEquals( "http", adapters.get(0).getType());
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
                        .put(RegistryManagementConstants.FIELD_RESOURCE_LIMITS_MAX_CONNECTIONS, 100));

        final Tenant tenant = tenantSpec.mapTo(Tenant.class);
        assertNotNull(tenant);
        assertNull(tenant.getEnabled());

        final var limits = tenant.getResourceLimits();
        assertNotNull(limits);
        assertEquals(100, limits.getMaxConnections());
    }

    /**
     * Decode "trusted-ca" section.
     */
    @Test
    public void testDecodeTrustedCA() {
        final JsonObject ca = new JsonObject()
                .put("subject-dn", "org.eclipse")
                .put("public-key", "abc123".getBytes(StandardCharsets.UTF_8))
                .put("algorithm", "def456")
                .put("cert", "xyz789".getBytes(StandardCharsets.UTF_8));

        final var tenant = Json.decodeValue( new JsonObject().put("trusted-ca", ca).toString(), Tenant.class);
        assertNotNull(tenant);
        assertNull(tenant.getEnabled());

        final var storedCa = tenant.getTrustedCertificateAuthority();
        assertNotNull(storedCa);
        assertEquals("org.eclipse", storedCa.getSubjectDn());
        assertArrayEquals("abc123".getBytes(StandardCharsets.UTF_8), storedCa.getPublicKey());
        assertArrayEquals("xyz789".getBytes(StandardCharsets.UTF_8), storedCa.getCertificate());
        assertEquals("def456", storedCa.getKeyAlgorithm());
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

        final JsonArray result = JsonObject.mapFrom(tenant).getJsonArray(FIELD_ADAPTERS);
        assertNotNull(result);
        assertEquals(Constants.PROTOCOL_ADAPTER_TYPE_HTTP, result.getJsonObject(0).getString(FIELD_ADAPTERS_TYPE));
        assertEquals(Constants.PROTOCOL_ADAPTER_TYPE_MQTT, result.getJsonObject(1).getString(FIELD_ADAPTERS_TYPE));
        assertEquals(false, result.getJsonObject(0).getBoolean(FIELD_ENABLED));
        assertEquals(true, result.getJsonObject(0).getBoolean(FIELD_ADAPTERS_DEVICE_AUTHENTICATION_REQUIRED));
    }
}
