/*******************************************************************************
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;



/**
 * Unit tests for {@code JsonHelper}.
 */
public class JsonHelperTest {


    final JsonObject json = new JsonObject()
        .put("foo", "bar")
        .put("fooObj", new JsonObject().put("foo", "bar"))
        .put("fooIntArray", new JsonArray().add(1).add(2).add(3))
        .put("fooObjArray", new JsonArray()
                .add(new JsonObject().put("obj", "first"))
                .add(new JsonObject().put("obj", "second"))
        );

    /**
     * Get a root-level value using it's json path.
     */
    @Test
    public void testGetRootLevelValue() {

        final String path = "foo";
        final String value = JsonHelper.getValueFromJsonPath(json, path, String.class, "default");

        assertNotNull(value);
        assertEquals("bar", value);
    }

    /**
     * Get a root-level value using it's json path.
     */
    @Test
    public void testReturnsDefaultValueForInvalidPath() {

        final String path = "fee";
        final String value = JsonHelper.getValueFromJsonPath(json, path, String.class, "default");

        assertNotNull(value);
        assertEquals("default", value);
    }

    /**
     * Get a root-level object using it's json path.
     */
    @Test
    public void testGetRootLevelJsonObject() {
        final String path = "fooObj";
        final JsonObject value = JsonHelper.getValueFromJsonPath(json, path, JsonObject.class, new JsonObject());

        assertNotNull(value);
        assertEquals(json.getJsonObject("fooObj"), value);
    }

    /**
     * Get a root-level array using it's json path.
     */
    @Test
    public void testGetRootLevelJsonArray() {
        final String path = "fooIntArray";
        final JsonArray value = JsonHelper.getValueFromJsonPath(json, path, JsonArray.class, new JsonArray());

        assertNotNull(value);
        assertEquals(json.getJsonArray("fooIntArray"), value);
    }

    /**
     * Get a nested (1 level) value.
     */
    @Test
    public void testGetNestedString() {
        final String path = "fooObj.foo";
        final String value = JsonHelper.getValueFromJsonPath(json, path, String.class, "default");

        assertNotNull(value);
        assertEquals("bar", value);
    }

    /**
     * Get a value contained in an array.
     */
    @Test
    public void testGetValueInArray() {
        final String path = "fooIntArray[1]";
        final int value = JsonHelper.getValueFromJsonPath(json, path, Integer.class, 9);

        assertNotNull(value);
        assertEquals(2, value);
    }

    /**
     * Get a value contained in a Json object that is an array.
     */
    @Test
    public void testGetValueInObjectInArray() {
        final String path = "fooObjArray[1].obj";
        final String value = JsonHelper.getValueFromJsonPath(json, path, String.class, "default");

        assertNotNull(value);
        assertEquals("second", value);
    }

}
