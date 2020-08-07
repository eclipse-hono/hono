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
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        .put("", "baz")
        .put("fooObj", new JsonObject().put("foo", "bar").put("", "baz"))
        .put("fooIntArray", new JsonArray().add(1).add(2).add(3))
        .put("fooObjArray", new JsonArray()
                .add(new JsonObject().put("obj", "first"))
                .add(new JsonObject().put("obj", "second"))
        );

    /**
     * Get the whole Json Object using it's json pointer.
     */
    @Test
    public void testGetWholeObject() {

        final String pointer = "";
        final JsonObject value = JsonHelper.getValueFromJsonPointer(json, pointer, JsonObject.class, new JsonObject());

        assertFalse(value.isEmpty());
        assertEquals(json, value);
    }

    /**
     * Get a root-level empty key using it's json pointer.
     */
    @Test
    public void testGetRootLevelEmptyKey() {

        final String pointer = "/";
        final String value = JsonHelper.getValueFromJsonPointer(json, pointer, String.class, "default");

        assertFalse(value.isEmpty());
        assertEquals("baz", value);
    }

    /**
     * Get a root-level value using it's json pointer.
     */
    @Test
    public void testGetRootLevelValue() {

        final String path = "/foo";
        final String value = JsonHelper.getValueFromJsonPointer(json, path, String.class, "default");

        assertNotNull(value);
        assertEquals("bar", value);
    }

    /**
     * Get a root-level value using it's json pointer.
     */
    @Test
    public void testReturnsDefaultValueForInvalidPath() {

        final String path = "/fee";
        final String value = JsonHelper.getValueFromJsonPointer(json, path, String.class, "default");

        assertNotNull(value);
        assertEquals("default", value);
    }

    /**
     * Get a root-level object using it's json pointer.
     */
    @Test
    public void testGetRootLevelJsonObject() {
        final String path = "/fooObj";
        final JsonObject value = JsonHelper.getValueFromJsonPointer(json, path, JsonObject.class, new JsonObject());

        assertNotNull(value);
        assertEquals(json.getJsonObject("fooObj"), value);
    }

    /**
     * Get a root-level array using it's json pointer.
     */
    @Test
    public void testGetRootLevelJsonArray() {
        final String path = "/fooIntArray";
        final JsonArray value = JsonHelper.getValueFromJsonPointer(json, path, JsonArray.class, new JsonArray());

        assertNotNull(value);
        assertEquals(json.getJsonArray("fooIntArray"), value);
    }

    /**
     * Get a nested (1 level) value.
     */
    @Test
    public void testGetNestedString() {
        final String path = "/fooObj/foo";
        final String value = JsonHelper.getValueFromJsonPointer(json, path, String.class, "default");

        assertNotNull(value);
        assertEquals("bar", value);
    }

    /**
     * Get a nested (1 level) value that has an empty key using it's json pointer.
     */
    @Test
    public void testGetNestedEmptyKey() {

        final String pointer = "/fooObj/";
        final String value = JsonHelper.getValueFromJsonPointer(json, pointer, String.class, "default");

        assertFalse(value.isEmpty());
        assertEquals("baz", value);
    }

    /**
     * Get a value contained in an array.
     */
    @Test
    public void testGetValueInArray() {
        final String path = "/fooIntArray/1";
        final int value = JsonHelper.getValueFromJsonPointer(json, path, Integer.class, 9);

        assertNotNull(value);
        assertEquals(2, value);
    }

    /**
     * Verifies that a too large index for an array returns default value.
     */
    @Test
    public void testInvalidArrayIndexReturnsDefaultValue() {
        final String path = "/fooIntArray/5";
        final int value = JsonHelper.getValueFromJsonPointer(json, path, Integer.class, 9);

        assertNotNull(value);
        assertEquals(9, value);
    }

    /**
     * Verifies that pointing to a non existing array returns default value.
     */
    @Test
    public void testInvalidArrayNameReturnsDefaultValue() {
        final String path = "/InvalidArray/1";
        final int value = JsonHelper.getValueFromJsonPointer(json, path, Integer.class, 9);

        assertNotNull(value);
        assertEquals(9, value);
    }

    /**
     * Get a value contained in a Json object that is an array.
     */
    @Test
    public void testGetValueInObjectInArray() {
        final String path = "/fooObjArray/1/obj";
        final String value = JsonHelper.getValueFromJsonPointer(json, path, String.class, "default");

        assertNotNull(value);
        assertEquals("second", value);
    }

    /**
     * Get a nest json object designated by en empty key.
     */
    @Test
    public void testGetValueFromJsonObjectWithParentEmptyKey() {

        json.put("", new JsonObject().put("foo", "bar"));
        final String path = "//foo";
        final String value = JsonHelper.getValueFromJsonPointer(json, path, String.class, "default");

        assertNotNull(value);
        assertEquals("bar", value);
    }

    /**
     * Get a value that has key containing a /.
     */
    @Test
    public void testGetValueWithSlashInKey() {

        json.put("b/ar", "baz");
        final String pointer = "/b~1ar";
        final String value = JsonHelper.getValueFromJsonPointer(json, pointer, String.class, "default");

        assertFalse(value.isEmpty());
        assertEquals("baz", value);
    }

    /**
     * Get a value that has key containing a ~.
     */
    @Test
    public void testGetValueWithTildeInKey() {

        json.put("b~ar", "baz");
        final String pointer = "/b~0ar";
        final String value = JsonHelper.getValueFromJsonPointer(json, pointer, String.class, "default");

        assertFalse(value.isEmpty());
        assertEquals("baz", value);
    }

}
