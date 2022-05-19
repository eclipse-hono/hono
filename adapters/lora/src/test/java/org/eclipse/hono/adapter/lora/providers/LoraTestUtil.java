/*******************************************************************************
 * Copyright (c) 2019, 2020 Contributors to the Eclipse Foundation
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

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.hono.adapter.lora.LoraMessageType;

import io.vertx.core.buffer.Buffer;

/**
 * Utility methods for testing functionality of LoRa providers.
 */
public final class LoraTestUtil {

    private LoraTestUtil() {
        // Prevent instantiation
    }

    /**
     * Loads a test file from the payload directory.
     *
     * @param providerName The name of the provider to load the file for.
     * @param type The type of message to load.
     * @param classifiers additional classifiers of the test file.
     * @return the contents of the file.
     * @throws IOException if the test file could not be loaded.
     */
    public static Buffer loadTestFile(final String providerName, final LoraMessageType type, final String... classifiers) throws IOException {
        Objects.requireNonNull(providerName);
        Objects.requireNonNull(type);
        final String name = Stream
                .<String>concat(
                        Stream.of(providerName, type.name().toLowerCase()),
                        Arrays.stream(classifiers))
                .collect(Collectors.joining("."));
        try {
            final URL location = LoraTestUtil.class.getResource(String.format("/payload/%s.json", name));
            return Buffer.buffer(Files.readAllBytes(Paths.get(location.toURI())));
        } catch (final URISyntaxException e) {
            // cannot happen because the URL is created by the class loader
            return null;
        }
    }

}
