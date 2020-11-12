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

package org.eclipse.hono.service.management.device;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;

import org.eclipse.hono.util.RegistryManagementConstants;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;

/**
 * Verifies {@link Device}.
 */
public class DeviceTest {

    /**
     * Decode device with absent "enabled" flag.
     */
    @Test
    public void testDecodeDefault() {
        final var device = Json.decodeValue("{}", Device.class);
        assertThat(device).isNotNull();
        assertThat(device.isEnabled());
    }

    /**
     * Decode device with unknown property succeeds.
     */
    @Test
    public void testDecodeFailsForUnknownProperties() {
        assertThatThrownBy(() -> Json.decodeValue("{\"unexpected\": \"property\"}", Device.class))
            .isInstanceOf(DecodeException.class);
    }

    /**
     * Decode device with "enabled=false".
     */
    @Test
    public void testDecodeDisabled() {
        final var device = Json.decodeValue("{\"enabled\": false}", Device.class);
        assertThat(device).isNotNull();
        assertThat(device.isEnabled()).isFalse();
    }

    /**
     * Decode device with "enabled=true".
     */
    @Test
    public void testDecodeEnabled() {
        final var device = Json.decodeValue("{\"enabled\": true}", Device.class);
        assertThat(device).isNotNull();
        assertThat(device.isEnabled());
    }

    /**
     * Decode "ext" section.
     */
    @Test
    public void testDecodeExt() {
        final var device = Json.decodeValue("{\"ext\": {\"foo\": \"bar\"}}", Device.class);
        assertThat(device).isNotNull();
        assertThat(device.isEnabled());

        final var ext = device.getExtensions();
        assertThat(ext).isNotNull();
        assertThat(ext.get("foo")).isEqualTo("bar");
    }

    /**
     * Encode with absent "enabled" flag.
     */
    @Test
    public void testEncodeDefault() {
        final var json = JsonObject.mapFrom(new Device());
        assertThat(json).isNotNull();
        assertThat(json.getBoolean("enabled")).isNull();
        assertThat(json.getJsonObject("ext")).isNull();
        assertThat(json).isEmpty();
    }

    /**
     * Encode device with "enabled=true".
     */
    @Test
    public void testEncodeEnabled() {
        final var device = new Device();
        device.setEnabled(true);
        final var json = JsonObject.mapFrom(device);
        assertThat(json).isNotNull();
        assertThat(json.getBoolean("enabled")).isTrue();
        assertThat(json.getJsonObject("ext")).isNull();
    }

    /**
     * Encode device with "enabled=false".
     */
    @Test
    public void testEncodeDisabled() {
        final var device = new Device();
        device.setEnabled(false);
        final var json = JsonObject.mapFrom(device);
        assertThat(json).isNotNull();
        assertThat(json.getBoolean("enabled")).isFalse();
        assertThat(json.getJsonObject("ext")).isNull();
    }

    /**
     * Check whether 'via' cannot be set while 'memberOf' is set.
     */
    @Test
    public void testSettingMemberOfAndVia() {
        final var device = new Device();
        final ArrayList<String> list = new ArrayList<>();
        list.add("a");
        device.setMemberOf(list);
        Assertions.assertThrows(IllegalArgumentException.class, () -> device.setVia(list),
                "Property 'memberOf' and 'via' must not be set at the same time");
    }

    /**
     * Check whether 'viaGroups' cannot be set while 'memberOf' is set.
     */
    @Test
    public void testSettingMemberOfAndViaGroups() {
        final var device = new Device();
        final ArrayList<String> list = new ArrayList<>();
        list.add("a");
        device.setMemberOf(list);
        Assertions.assertThrows(IllegalArgumentException.class, () -> device.setViaGroups(list),
                "Property 'memberOf' and 'viaGroups' must not be set at the same time");
    }

    /**
     * Check whether 'memberOf' cannot be set while 'via' is set.
     */
    @Test
    public void testSettingViaAndMemberOf() {
        final var device = new Device();
        final ArrayList<String> list = new ArrayList<>();
        list.add("a");
        device.setVia(list);
        Assertions.assertThrows(IllegalArgumentException.class, () -> device.setMemberOf(list),
                "Property 'via' and 'memberOf' must not be set at the same time");
    }

    /**
     * Check whether 'memberOf' cannot be set while 'viaGroups' is set.
     */
    @Test
    public void testSettingViaGroupsAndMemberOf() {
        final var device = new Device();
        final ArrayList<String> list = new ArrayList<>();
        list.add("a");
        device.setViaGroups(list);
        Assertions.assertThrows(IllegalArgumentException.class, () -> device.setMemberOf(list),
                "Property 'viaGroups' and 'memberOf' must not be set at the same time");
    }

    /**
     * Encode device with "mapper=test".
     */
    @Test
    public void testEncodeMapper() {
        final var device = new Device();
        device.setMapper("test");
        final var json = JsonObject.mapFrom(device);
        assertThat(json).isNotNull();
        assertThat(json.getString("mapper")).isEqualTo("test");
    }

    /**
     * Tests that the status property is serialized to JSON.
     */
    @Test
    public void testEncodeStatus() {
        final var device = new Device();
        device.setStatus(new DeviceStatus()
                .setAutoProvisioned(true)
                .setAutoProvisioningNotificationSent(true)
                .setCreationTime(Instant.now()));

        final var json = JsonObject.mapFrom(device);

        assertThat(json).isNotNull();

        final JsonObject status = json.getJsonObject(RegistryManagementConstants.FIELD_STATUS);
        assertThat(status).isNotNull();
        assertThat(status.getString(RegistryManagementConstants.FIELD_STATUS_CREATION_DATE)).isNotEmpty();
        assertThat(status.getBoolean(RegistryManagementConstants.FIELD_AUTO_PROVISIONED)).isTrue();
        assertThat(status.getBoolean(RegistryManagementConstants.FIELD_AUTO_PROVISIONING_NOTIFICATION_SENT)).isTrue();
    }

    /**
     * Tests that the status property is ignored on deserialization, since it should not be editable by a user of the
     * device management API.
     */
    @Test
    public void testStatusIsIgnoredWhenDecoding() {
        final String deviceJson = "{\"enabled\": true, \"status\": { \"created\": \"2020-10-05T14:58:39Z\"}}";
        final var device = Json.decodeValue(deviceJson, Device.class);

        assertThat(device).isNotNull();
        assertThat(device.getStatus()).isNull();
    }
}
