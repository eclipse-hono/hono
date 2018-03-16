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

import static org.eclipse.hono.util.Strings.isNullOrEmpty;

import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for {@link Strings}
 *
 */
public class StringsTest {

    private static class Mock {

        private String value;

        public Mock(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return value;
        }
    }

    /**
     * Test primary null value
     */
    @Test
    public void testNull() {
        Assert.assertTrue(isNullOrEmpty(null));
    }

    /**
     * Test empty string
     */
    @Test
    public void testEmpty() {
        Assert.assertTrue(isNullOrEmpty(""));
    }

    /**
     * Test non-empty string
     */
    @Test
    public void testNonEmpty() {
        Assert.assertFalse(isNullOrEmpty("foo"));
    }

    /**
     * Test object returning non-empty string in toString()
     */
    @Test
    public void testNonEmptyNonString() {
        Assert.assertFalse(isNullOrEmpty(new Mock("foo")));
    }

    /**
     * Test object returning empty string in toString()
     */
    @Test
    public void testEmptyNonString() {
        Assert.assertTrue(isNullOrEmpty(new Mock("")));
    }

    /**
     * Test object returning null in toString().
     * 
     * This is a special case where the actual value is non-null but the toString method returns null
     */
    @Test
    public void testNullNonString() {
        Assert.assertTrue(isNullOrEmpty(new Mock(null)));
    }
}
