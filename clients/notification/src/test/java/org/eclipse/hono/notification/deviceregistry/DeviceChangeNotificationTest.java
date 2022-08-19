/*
 * Copyright (c) 2021, 2022 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.hono.notification.deviceregistry;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import java.time.Instant;

import org.eclipse.hono.notification.AbstractNotification;
import org.eclipse.hono.notification.NotificationConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;

/**
 * Tests verifying the behavior of {@link DeviceChangeNotification}.
 *
 */
public class DeviceChangeNotificationTest {

    private static final String CREATION_TIME = "2007-12-03T10:15:30Z";
    private static final String TENANT_ID = "my-tenant";
    private static final String DEVICE_ID = "my-device";
    private static final LifecycleChange CHANGE = LifecycleChange.CREATE;
    private static final boolean ENABLED = false;

    private AbstractNotification notification;

    /**
     * Sets up the notification.
     */
    @BeforeEach
    public void setUp() {
        notification = new DeviceChangeNotification(CHANGE, TENANT_ID, DEVICE_ID, Instant.parse(CREATION_TIME), ENABLED);
    }

    /**
     * Verifies that the expected properties are contained in the JSON.
     */
    @Test
    public void testThatValuesAreContainedInJson() {

        final JsonObject json = JsonObject.mapFrom(notification);

        assertThat(json.getString(NotificationConstants.JSON_FIELD_SOURCE))
                .isEqualTo(NotificationConstants.SOURCE_DEVICE_REGISTRY);
        assertThat(json.getInstant(NotificationConstants.JSON_FIELD_CREATION_TIME).toString()).isEqualTo(CREATION_TIME);

        assertThat(json.getString(NotificationConstants.JSON_FIELD_TYPE)).isEqualTo(DeviceChangeNotification.TYPE_NAME);
        assertThat(json.getString(NotificationConstants.JSON_FIELD_DATA_CHANGE)).isEqualTo(CHANGE.toString());
        assertThat(json.getString(NotificationConstants.JSON_FIELD_TENANT_ID)).isEqualTo(TENANT_ID);
        assertThat(json.getString(NotificationConstants.JSON_FIELD_DEVICE_ID)).isEqualTo(DEVICE_ID);
        assertThat(json.getBoolean(NotificationConstants.JSON_FIELD_DATA_ENABLED)).isEqualTo(ENABLED);
    }

    /**
     * Verifies that the serialization did not change: the JSON contains the static values and only the expected keys.
     */
    @Test
    public void testThatSerializationIsStable() {

        final JsonObject json = JsonObject.mapFrom(notification);
        assertThat(json).isNotNull();

        // When adding new properties to the data object, make sure not to break the existing API because messages might
        // be persisted. For breaking changes add a new object mapper class instead.
        final int expectedPropertiesCount = 7;
        assertWithMessage("JSON contains unknown fields").that(json.size()).isEqualTo(expectedPropertiesCount);

        assertThat(json.getString("type")).isEqualTo("device-change-v1");
        assertThat(json.getString("source")).isEqualTo("device-registry");
        assertThat(json.getString("creation-time")).isEqualTo(CREATION_TIME);

        assertThat(json.getString("change")).isEqualTo("CREATE");
        assertThat(json.getString("tenant-id")).isNotNull();
        assertThat(json.getString("device-id")).isNotNull();
        assertThat(json.getString("enabled")).isNotNull();

    }

    /**
     * Verifies that a serialized notification is deserialized correctly.
     */
    @Test
    public void testDeserialization() {

        final JsonObject json = new JsonObject()
                .put(NotificationConstants.JSON_FIELD_TYPE, "device-change-v1")
                .put(NotificationConstants.JSON_FIELD_SOURCE, NotificationConstants.SOURCE_DEVICE_REGISTRY)
                .put(NotificationConstants.JSON_FIELD_CREATION_TIME, CREATION_TIME)
                .put(NotificationConstants.JSON_FIELD_DATA_CHANGE, LifecycleChange.DELETE)
                .put(NotificationConstants.JSON_FIELD_TENANT_ID, TENANT_ID)
                .put(NotificationConstants.JSON_FIELD_DEVICE_ID, DEVICE_ID)
                .put(NotificationConstants.JSON_FIELD_DATA_ENABLED, true);
        final AbstractNotification abstractNotification = Json.decodeValue(json.toBuffer(), AbstractNotification.class);

        assertThat(abstractNotification).isNotNull();
        assertThat(abstractNotification).isInstanceOf(DeviceChangeNotification.class);
        final DeviceChangeNotification newNotification = (DeviceChangeNotification) abstractNotification;

        assertThat(newNotification.getSource()).isEqualTo(NotificationConstants.SOURCE_DEVICE_REGISTRY);
        assertThat(newNotification.getCreationTime()).isEqualTo(Instant.parse(CREATION_TIME));

        assertThat(newNotification.getChange()).isEqualTo(LifecycleChange.DELETE);
        assertThat(newNotification.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(newNotification.getDeviceId()).isEqualTo(DEVICE_ID);
        assertThat(newNotification.isDeviceEnabled()).isTrue();
    }

}
