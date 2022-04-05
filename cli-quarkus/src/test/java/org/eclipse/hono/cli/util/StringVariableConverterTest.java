/**
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.cli.util;

import static com.google.common.truth.Truth.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;


/**
 * Tests verifying behavior of {@code StringVariableConverter}.
 *
 */
class StringVariableConverterTest {

    @Test
    void testGetResolvedValueFindsVariableReferences() throws Exception {
        final var variables = Map.of(
                "MY_TENANT", "my-tenant",
                "MY_DEVICE", "my-device");
        final var converter = new StringVariableConverter();
        final String value = converter.getResolvedValue(
                "This ${ is ${MY_DEVICE} of ${MY_TENANT} but not ${other_stuff",
                variables);
        assertThat(value).isEqualTo("This ${ is my-device of my-tenant but not ${other_stuff");
    }

}
