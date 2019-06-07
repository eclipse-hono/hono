/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.util;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Unit tests for {@link JsonBackedValueObject}.
 */
public class JsonBackedValueObjectTest {

    /**
     * Get a boolean default value, when the value is missing.
     */
    @Test
    public void testBooleanDefaultForMissingValue() {
        final JsonBackedValueObject json = new JsonBackedValueObject() {
        };

        assertNull(json.getProperty("foo", Boolean.class));
        assertTrue(json.getProperty("foo", Boolean.class, true));
    }

    /**
     * Get a boolean default value, when a null value is present.
     */
    @Test
    public void testBooleanDefaultForNullValue() {
        final JsonBackedValueObject json = new JsonBackedValueObject() {
        };

        json.json.put("foo", (Boolean) null);

        assertNull(json.getProperty("foo", Boolean.class));
        assertTrue(json.getProperty("foo", Boolean.class, true));
    }

    /**
     * Get a boolean default value, when a wrong type is present.
     */
    @Test
    public void testBooleanDefaultForWrongType() {
        final JsonBackedValueObject json = new JsonBackedValueObject() {
        };

        json.json.put("foo", "bar");

        assertNull(json.getProperty("foo", Boolean.class));
        assertTrue(json.getProperty("foo", Boolean.class, true));
    }

}
