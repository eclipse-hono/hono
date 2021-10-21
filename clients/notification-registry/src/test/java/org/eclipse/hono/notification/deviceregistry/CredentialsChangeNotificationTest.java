/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

import org.eclipse.hono.client.notification.Notification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;

/**
 * Tests verifying the behavior of {@link CredentialsChangeNotification}.
 *
 */
public class CredentialsChangeNotificationTest {

    private static final String TIMESTAMP = "2007-12-03T10:15:30Z";
    private static final String TENANT_ID = "my-tenant";
    private static final String DEVICE_ID = "my-device";

    private AbstractDeviceRegistryNotification notification;

    /**
     * Sets up the notification.
     */
    @BeforeEach
    public void setUp() {
        notification = new CredentialsChangeNotification(TENANT_ID, DEVICE_ID, Instant.parse(TIMESTAMP));
    }

    /**
     * Verifies that the expected properties are contained in the JSON.
     */
    @Test
    public void testThatValuesAreContainedInJson() {

        final JsonObject json = JsonObject.mapFrom(notification);

        assertThat(json.getString(Notification.FIELD_SOURCE))
                .isEqualTo(RegistryNotificationConstants.SOURCE_DEVICE_REGISTRY);
        assertThat(json.getInstant(Notification.FIELD_TIMESTAMP).toString()).isEqualTo(TIMESTAMP);

        assertThat(json.getString("type")).isEqualTo(CredentialsChangeNotification.TYPE);
        assertThat(json.getString(RegistryNotificationConstants.JSON_FIELD_TENANT_ID)).isEqualTo(TENANT_ID);
        assertThat(json.getString(RegistryNotificationConstants.JSON_FIELD_DEVICE_ID)).isEqualTo(DEVICE_ID);
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
        final int expectedPropertiesCount = 5;
        assertWithMessage("JSON contains unknown fields").that(json.size()).isEqualTo(expectedPropertiesCount);

        assertThat(json.getString("type")).isEqualTo("credentials-change-v1");
        assertThat(json.getString("source")).isEqualTo("device-registry");
        assertThat(json.getString("timestamp")).isEqualTo(TIMESTAMP);

        assertThat(json.getString("tenant-id")).isNotNull();
        assertThat(json.getString("device-id")).isNotNull();

    }

    /**
     * Verifies that a serialized notification is deserialized correctly.
     */
    @Test
    public void testDeserialization() {

        final AbstractDeviceRegistryNotification abstractNotification = Json
                .decodeValue(JsonObject.mapFrom(notification).toBuffer(), AbstractDeviceRegistryNotification.class);

        assertThat(abstractNotification).isNotNull();
        assertThat(abstractNotification).isInstanceOf(CredentialsChangeNotification.class);
        final CredentialsChangeNotification newNotification = (CredentialsChangeNotification) abstractNotification;

        assertThat(newNotification.getSource()).isEqualTo(RegistryNotificationConstants.SOURCE_DEVICE_REGISTRY);
        assertThat(newNotification.getTimestamp()).isEqualTo(Instant.parse(TIMESTAMP));

        assertThat(newNotification.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(newNotification.getDeviceId()).isEqualTo(DEVICE_ID);
    }

}
