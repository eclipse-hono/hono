/**
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
 */


package org.eclipse.hono.util;

import static com.google.common.truth.Truth.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;


/**
 * Tests verifying behavior of {@link CacheDirective}.
 *
 */
class CacheDirectiveTest {

    /**
     * Test method for {@link org.eclipse.hono.util.CacheDirective#from(java.lang.String)}.
     */
    @ParameterizedTest
    @ValueSource(strings = {"   max-age=  16453", "max-age  =16453  ", "max-age=16453"})
    void testFromCreatesMaxAgeDirective(final String header) {
        final var directive = CacheDirective.from(header);
        assertThat(directive.isCachingAllowed()).isTrue();
        assertThat(directive.getMaxAge()).isEqualTo(16453);
    }

    /**
     * Test method for {@link org.eclipse.hono.util.CacheDirective#from(java.lang.String)}.
     */
    @ParameterizedTest
    @ValueSource(strings = {"   no-cache", "no-cache   ", "no-cache"})
    void testFromCreatesNoCacheDirective(final String header) {
        final var directive = CacheDirective.from(header);
        assertThat(directive.isCachingAllowed()).isFalse();
        assertThat(directive.getMaxAge()).isEqualTo(0);
    }
}
