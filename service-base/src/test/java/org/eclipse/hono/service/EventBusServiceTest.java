/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.service;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import org.eclipse.hono.util.EventBusMessage;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;


/**
 * Tests verifying behavior of {@link EventBusService}.
 *
 */
public class EventBusServiceTest {

    private static EventBusService<Object> service;

    /**
     * Sets up the fixture.
     */
    @BeforeClass
    public static void setUp() {
        service = new EventBusService<Object>() {

            @Override
            public void setConfig(final Object configuration) {
            }

            @Override
            protected String getEventBusAddress() {
                return null;
            }

            @Override
            protected Future<EventBusMessage> processRequest(final EventBusMessage request) {
                return null;
            }
        };
    }

    /**
     * Verifies that a non-boolean value of a request's <em>enabled</em>
     * field is replaced with {@code true}.
     */
    @Test
    public void getRequestPayloadHandlesNonBooleanEnabledField() {

        final JsonObject device = new JsonObject().put("device-id", "someValue").put("enabled", "notABoolean");
        final JsonObject result = service.getRequestPayload(device);
        assertThat(result.getBoolean("enabled"), is(Boolean.TRUE));
    }

    /**
     * Verifies that a default <em>enabled</em> field is added to a request.
     */
    @Test
    public void getRequestPayloadHandlesMissingEnabledField() {

        final JsonObject device = new JsonObject().put("device-id", "someValue");
        final JsonObject result = service.getRequestPayload(device);
        assertThat(result.getBoolean("enabled"), is(Boolean.TRUE));
    }

    /**
     * Verify that a valid type works.
     */
    @Test
    public void testValidType() {
        final JsonObject payload = new JsonObject()
                .put("booleanValue", true)
                .put("stringValue", "foo")
                .put("intValue", 42);

        final String stringValue = EventBusService.getTypesafeValueForField(String.class, payload, "stringValue");
        Assert.assertEquals("foo", stringValue);

        final Integer intValue = EventBusService.getTypesafeValueForField(Integer.class, payload, "intValue");
        Assert.assertEquals(Integer.valueOf(42), intValue);

        final Boolean booleanValue = EventBusService.getTypesafeValueForField(Boolean.class, payload, "booleanValue");
        Assert.assertEquals(Boolean.TRUE, booleanValue);
    }

    /**
     * Verify that the method actually returns null when the type does not match.
     */
    @Test
    public void testInvalidType() {
        final JsonObject device = new JsonObject().put("device-id", "someValue");

        final String stringValue = EventBusService.getTypesafeValueForField(String.class, device, "device-id");
        Assert.assertEquals("someValue", stringValue);

        final Integer intValue = EventBusService.getTypesafeValueForField(Integer.class, device, "device-id");
        Assert.assertNull(intValue);
    }

    /**
     * Verify that the method returns null, when the value is null.
     */
    @Test
    public void testGetNull() {
        final JsonObject device = new JsonObject().put("device-id", (String) null);

        final String value = EventBusService.getTypesafeValueForField(String.class, device, "device-id");
        Assert.assertNull(value);
    }

}
