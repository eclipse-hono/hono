/*******************************************************************************
 * Copyright (c) 2018, 2021 Contributors to the Eclipse Foundation
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

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for TriTuple.
 */
public class TriTupleTest {

    /**
     * Test the argument order.
     */
    @Test
    public void testOrderOfArguments() {
        final TriTuple<String, String, String> t1 = TriTuple.of("Foo", "Bar", "Buz");

        Assertions.assertEquals("Foo", t1.one());
        Assertions.assertEquals("Bar", t1.two());
        Assertions.assertEquals("Buz", t1.three());
    }

    /**
     * Test equality having the same type.
     */
    @Test
    public void testEqualsSameType() {
        final TriTuple<String, String, String> t1 = TriTuple.of("Foo", "Bar", "Baz");
        final TriTuple<String, String, String> t2 = TriTuple.of("Foo", "Bar", "Baz");

        Assertions.assertEquals(t1, t2);
        Assertions.assertEquals(t2, t1);

        Assertions.assertEquals(t1.hashCode(), t2.hashCode());
    }

    /**
     * Test equality having a different type.
     */
    @Test
    public void testEqualsDifferentType() {
        expectNotEqual(
                TriTuple.of("Foo", "42", "Baz"),
                TriTuple.of("Foo", 42, "Baz"));
    }

    /**
     * Test non-equal tuples.
     */
    @Test
    public void testNotEqual1() {
        expectNotEqual(
                TriTuple.of("Foo", "Bar", "Baz"),
                TriTuple.of("FooX", "Bar", "Baz"));
    }

    /**
     * Test non-equal tuples.
     */
    @Test
    public void testNotEqual2() {
        expectNotEqual(
                TriTuple.of("Foo", "Bar", "Baz"),
                TriTuple.of("Foo", "BarX", "Baz"));
    }

    /**
     * Test non-equal tuples.
     */
    @Test
    public void testNotEqual3() {
        expectNotEqual(
                TriTuple.of("Foo", "Bar", "Baz"),
                TriTuple.of("Foo", "Bar", "BazX"));
    }

    /**
     * Test all values are null.
     */
    @Test
    public void testAllNull() {
        assertThrows(IllegalArgumentException.class, () -> TriTuple.of(null, null, null));
    }

    private void expectNotEqual(final TriTuple<?, ?, ?> t1, final TriTuple<?, ?, ?> t2) {
        Assertions.assertNotEquals(t1, t2);
        Assertions.assertNotEquals(t2, t1);

        // Note: we are not testing the hashCode, as this might, in theory, actually be the same.
    }
}
