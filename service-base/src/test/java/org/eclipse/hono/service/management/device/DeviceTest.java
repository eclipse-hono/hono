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

package org.eclipse.hono.service.management.device;

import static org.junit.Assert.assertThat;

import org.hamcrest.collection.IsEmptyIterable;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.hamcrest.core.IsNull.nullValue;
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
        assertThat(device, notNullValue());
        assertThat(device.getEnabled(), nullValue());
    }


    /**
     * Decode device with "enabled=false".
     */
    @Test
    public void testDecodeDisabled() {
        final var device = Json.decodeValue("{\"enabled\": false}", Device.class);
        assertThat(device, notNullValue());
        assertThat(device.getEnabled(), is(false));
    }

    /**
     * Decode device with "enabled=true".
     */
    @Test
    public void testDecodeEnabled() {
        final var device = Json.decodeValue("{\"enabled\": true}", Device.class);
        assertThat(device, notNullValue());
        assertThat(device.getEnabled(), is(true));
    }

    /**
     * Decode "ext" section.
     */
    @Test
    public void testDecodeExt() {
        final var device = Json.decodeValue("{\"ext\": {\"foo\": \"bar\"}}", Device.class);
        assertThat(device, notNullValue());
        assertThat(device.getEnabled(), nullValue());

        final var ext = device.getExtensions();
        assertThat(ext, notNullValue());
        assertThat(ext.get("foo"), is("bar"));
    }

    /**
     * Encode with absent "enabled" flag.
     */
    @Test
    public void testEncodeDefault() {
        final var json = JsonObject.mapFrom(new Device());
        assertThat(json, notNullValue());
        assertThat(json.getBoolean("enabled"), nullValue());
        assertThat(json.getJsonObject("ext"), nullValue());
        assertThat(json, IsEmptyIterable.emptyIterable());
    }

    /**
     * Encode device with "enabled=true".
     */
    @Test
    public void testEncodeEnabled() {
        final var device = new Device();
        device.setEnabled(true);
        final var json = JsonObject.mapFrom(device);
        assertThat(json, notNullValue());
        assertThat(json.getBoolean("enabled"), is(true));
        assertThat(json.getJsonObject("ext"), nullValue());
    }

    /**
     * Encode device with "enabled=false".
     */
    @Test
    public void testEncodeDisabled() {
        final var device = new Device();
        device.setEnabled(false);
        final var json = JsonObject.mapFrom(device);
        assertThat(json, notNullValue());
        assertThat(json.getBoolean("enabled"), is(false));
        assertThat(json.getJsonObject("ext"), nullValue());
    }

}
