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

import java.io.InputStream;
import java.util.Properties;

import picocli.CommandLine.IVersionProvider;


/**
 * Reads the command version from a properties file on the class path.
 *
 */
public final class PropertiesVersionProvider implements IVersionProvider {

    /**
     * {@inheritDoc}
     */
    @Override
    public String[] getVersion() throws Exception {
        final var url = getClass().getResource("/hono-cli-build.properties");
        if (url == null) {
            return new String[] {"unknown"};
        }
        final var properties = new Properties();
        try (InputStream inputStream = url.openStream()) {
            properties.load(inputStream);
        }
        return new String[] {
                "hono-cli %s".formatted(properties.getProperty("version")),
                "running on ${os.name} ${os.version} ${os.arch}"
                };
    }
}
