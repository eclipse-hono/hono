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

package org.eclipse.hono.config;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;


/**
 * Tests verifying behavior of {@link ClientConfigProperties}.
 *
 */
public class ClientConfigPropertiesTest {

    private final Path resources = Paths.get("src/test/resources");

    /**
     * Verifies that username and password are read from a credentials
     * property file.
     */
    @Test
    public void testLoadCrdentialsFromPropertiesFile() {

        final String path = resources.resolve("credentials").toString();
        final ClientConfigProperties props = new ClientConfigProperties();
        props.setCredentialsPath(path);
        assertThat(props.getUsername(), is("foo"));
        assertThat(props.getPassword(), is("bar"));
    }

}
