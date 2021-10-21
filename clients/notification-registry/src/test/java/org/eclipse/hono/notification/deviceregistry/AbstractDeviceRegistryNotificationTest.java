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

import org.eclipse.hono.client.notification.NotificationConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;

/**
 * Tests verifying the behavior of {@link AbstractDeviceRegistryNotification}.
 *
 */
public class AbstractDeviceRegistryNotificationTest {

    private static final String SOURCE = "the-component";
    private static final String TIMESTAMP = "2007-12-03T10:15:30Z";
    private static final String TYPE = "test-notification";
    private static final String TOPIC = "test-topic";

    private AbstractDeviceRegistryNotification notification;

    /**
     * Sets up the notification.
     */
    @BeforeEach
    public void setUp() {
        notification = new AbstractDeviceRegistryNotification(SOURCE, Instant.parse(TIMESTAMP)) {

            @Override
            public String getType() {
                return TYPE;
            }

            @Override
            public String getAddress() {
                return TOPIC;
            }
        };
    }

    /**
     * Verifies that the expected properties are contained in the JSON.
     */
    @Test
    public void testThatValuesAreContainedInJson() {

        final JsonObject json = JsonObject.mapFrom(notification);

        assertThat(json.getString(NotificationConstants.JSON_FIELD_SOURCE)).isEqualTo(SOURCE);
        assertThat(json.getInstant(NotificationConstants.JSON_FIELD_TIMESTAMP).toString()).isEqualTo(TIMESTAMP);
        assertThat(json.getString(NotificationConstants.JSON_FIELD_TYPE)).isEqualTo(TYPE);

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
        final int expectedPropertiesCount = 3;
        assertWithMessage("JSON contains unknown fields").that(json.size()).isEqualTo(expectedPropertiesCount);

        assertThat(json.getString(NotificationConstants.JSON_FIELD_TYPE)).isNotNull();
        assertThat(json.getString("source")).isNotNull();
        assertThat(json.getInstant("timestamp")).isNotNull();

    }

}
