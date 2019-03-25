/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter.lora.providers;

import java.net.URL;
import java.nio.file.Files;
import java.util.List;

import io.vertx.core.json.JsonObject;

/**
 * Utility methods for testing functionality of LoRa providers.
 */
public class LoraTestUtil {

    private LoraTestUtil() {
        // Prevent instantiation
    }

    /**
     * Loads a json test file from the payload directory.
     *
     * @param name the test file name to load
     * @return the contents of the Json
     * @throws RuntimeException if the test file could not be loaded
     */
    public static JsonObject loadTestFile(final String name) throws RuntimeException {
        try {
            final URL url = LoraTestUtil.class.getResource("/payload/" + name + ".json");
            final List<String> lines = Files.readAllLines(java.nio.file.Paths.get(url.toURI()));
            final String fullJson = String.join("", lines);
            return new JsonObject(fullJson);
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }
}
