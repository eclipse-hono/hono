/*******************************************************************************
 * Copyright (c) 2020, 2021 Contributors to the Eclipse Foundation
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
 * Unit tests for Pair.
 */
public class PairTest {

    /**
     * Test the argument order.
     */
    @Test
    public void testOrderOfArguments() {
        final Pair<String, String> t1 = Pair.of("Foo", "Bar");

        Assertions.assertEquals("Foo", t1.one());
        Assertions.assertEquals("Bar", t1.two());
    }

    /**
     * Test equality having the same type.
     */
    @Test
    public void testEqualsSameType() {
        final Pair<String, String> t1 = Pair.of("Foo", "Bar");
        final Pair<String, String> t2 = Pair.of("Foo", "Bar");

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
                Pair.of("Foo", "42"),
                Pair.of("Foo", 42));
    }

    /**
     * Test non-equal pairs.
     */
    @Test
    public void testNotEqual1() {
        expectNotEqual(
                Pair.of("Foo", "Bar"),
                Pair.of("FooX", "Bar"));
    }

    /**
     * Test non-equal pairs.
     */
    @Test
    public void testNotEqual2() {
        expectNotEqual(
                Pair.of("Foo", "Bar"),
                Pair.of("Foo", "BarX"));
    }

    /**
     * Test all values are null.
     */
    @Test
    public void testAllNull() {
        assertThrows(IllegalArgumentException.class, () -> Pair.of(null, null));
    }

    private void expectNotEqual(final Pair<?, ?> t1, final Pair<?, ?> t2) {
        Assertions.assertNotEquals(t1, t2);
        Assertions.assertNotEquals(t2, t1);

        // Note: we are not testing the hashCode, as this might, in theory, actually be the same.
    }
}
