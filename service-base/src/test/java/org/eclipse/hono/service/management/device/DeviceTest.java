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

import org.junit.jupiter.api.Test;

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
        assertThat(device.getEnabled()).isNull();
    }


    /**
     * Decode device with "enabled=false".
     */
    @Test
    public void testDecodeDisabled() {
        final var device = Json.decodeValue("{\"enabled\": false}", Device.class);
        assertThat(device).isNotNull();
        assertThat(device.getEnabled()).isFalse();
    }

    /**
     * Decode device with "enabled=true".
     */
    @Test
    public void testDecodeEnabled() {
        final var device = Json.decodeValue("{\"enabled\": true}", Device.class);
        assertThat(device).isNotNull();
        assertThat(device.getEnabled()).isTrue();
    }

    /**
     * Decode "ext" section.
     */
    @Test
    public void testDecodeExt() {
        final var device = Json.decodeValue("{\"ext\": {\"foo\": \"bar\"}}", Device.class);
        assertThat(device).isNotNull();
        assertThat(device.getEnabled()).isNull();

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

}
