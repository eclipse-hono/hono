/**
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 1.0 which is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * SPDX-License-Identifier: EPL-1.0
 */


package org.eclipse.hono.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Tests Constants utility methods.
 */
public class ConstantsTest {

    /**
     * Verifies that two strings can be combined together.
     */
    @Test
    public void testCombineTwoSimpleStrings() {

        final String simpleCombine = Constants.combineTwoStrings("abc", "def");
        assertNotNull(simpleCombine);
        assertTrue(simpleCombine.length() == 8);
        assertTrue(simpleCombine.startsWith("03"));
    }

    /**
     * Verifies that two null strings can be combined together.
     */
    @Test
    public void testCombineTwoNullStrings() {

        final String nullCombine = Constants.combineTwoStrings(null, null);
        assertNotNull(nullCombine);
        assertEquals("00", nullCombine);
    }

    /**
     * Verifies that two combined strings can be split again.
     */
    @Test
    public void testDecombineTwoSimpleStrings() {

        final String simpleCombine = Constants.combineTwoStrings("abc", "def");
        assertNotNull(simpleCombine);
        final String[] decombinedStrings = Constants.splitTwoStrings(simpleCombine);
        assertNotNull(decombinedStrings);
        assertEquals(decombinedStrings.length, 2);
        assertEquals(decombinedStrings[0], "abc");
        assertEquals(decombinedStrings[1], "def");
    }

    /**
     * Verifies that combined Strings can be split again when the first String is null.
     */
    @Test
    public void testDecombineStringsWithFirstNull() {

        final String simpleCombine = Constants.combineTwoStrings(null, "def");
        assertNotNull(simpleCombine);
        final String[] decombinedStrings = Constants.splitTwoStrings(simpleCombine);
        assertNotNull(decombinedStrings);
        assertEquals(decombinedStrings.length, 2);
        assertEquals(decombinedStrings[0], "");
        assertEquals(decombinedStrings[1], "def");
    }

    /**
     * Verifies that combined Strings can be split again when the second is null.
     */
    @Test
    public void testDecombineStringsWithSecondNull() {

        final String simpleCombine = Constants.combineTwoStrings("abc", null);
        assertNotNull(simpleCombine);
        final String[] decombinedStrings = Constants.splitTwoStrings(simpleCombine);
        assertNotNull(decombinedStrings);
        assertEquals(decombinedStrings.length, 2);
        assertEquals(decombinedStrings[0], "abc");
        assertEquals(decombinedStrings[1], "");
    }

    /**
     * Verifies that combined Strings can be split again when both are null.
     */
    @Test
    public void testDecombineStringsWithBothNull() {

        final String simpleCombine = Constants.combineTwoStrings(null, null);
        assertNotNull(simpleCombine);
        final String[] decombinedStrings = Constants.splitTwoStrings(simpleCombine);
        assertNotNull(decombinedStrings);
        assertEquals(decombinedStrings.length, 2);
        assertEquals(decombinedStrings[0], "");
        assertEquals(decombinedStrings[1], "");
    }

    /**
     * Verifies that a String that does not contain a hex number as first two chars at the beginning.
     */
    @Test
    public void testDecombineIncorrectStringReturnsNullForInvalidNumberAtBeginning() {

        final String[] decombinedStrings = Constants.splitTwoStrings("Z13illegal# invalid content");
        assertTrue(decombinedStrings == null);
    }

    /**
     * Verifies that a String that does not contain a hex number as first two chars at the beginning.
     */
    @Test
    public void testDecombineIncorrectStringReturnsNullForTooBigNumberAtBeginning() {

        final String[] decombinedStrings = Constants.splitTwoStrings("FFstringsContainedAreNotLongEnough");
        assertTrue(decombinedStrings == null);
    }

}
