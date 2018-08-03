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
}
