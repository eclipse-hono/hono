/*******************************************************************************
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.test;


/**
 * Utility methods for implementing JUnit tests.
 */
public final class JUnitTests {

    /**
     * Pattern used for the <em>name</em> field of the {@code @ParameterizedTest} annotation.
     */
    public static final String PARAMETERIZED_TEST_NAME_PATTERN = "{displayName} [{index}]; parameters: {argumentsWithNames}";

    private JUnitTests() {
        // prevent instantiation
    }
}
