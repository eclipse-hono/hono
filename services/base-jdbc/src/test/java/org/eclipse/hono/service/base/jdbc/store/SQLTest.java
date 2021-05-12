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

package org.eclipse.hono.service.base.jdbc.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests for {@link SQL}.
 */
public class SQLTest {

    /**
     * Provide values for {@link #testTypeDetector(String, String)}.
     * @return Test values.
     */
    public static Stream<Arguments> typeDetectorValue() {
        return Stream.of(
                Arguments.of("jdbc:postgresql://localhost:1234/device-registry", "postgresql"),
                Arguments.of("jdbc:h2:~/test;ACCESS_MODE_DATA=rws", "h2"));
    }

    /**
     * Test if the correct database gets detected.
     * @param url The JDBC URL.
     * @param expected The expected database.
     */
    @ParameterizedTest
    @MethodSource("typeDetectorValue")
    public void testTypeDetector(final String url, final String expected) {
        assertEquals(expected, SQL.getDatabaseDialect(url));
    }

    /**
     * Verifies that <em>h2</em> and <em>postgresql</em> are considered as supported dialects.
     */
    @Test
    public void testIsSupportedDatabaseDialect() {
        assertTrue(SQL.isSupportedDatabaseDialect("h2"));
        assertTrue(SQL.isSupportedDatabaseDialect("postgresql"));
        assertFalse(SQL.isSupportedDatabaseDialect("foo"));
    }

}
