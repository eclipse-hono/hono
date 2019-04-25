/*******************************************************************************
 * Copyright (c) 2019, 2019 Contributors to the Eclipse Foundation
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

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;

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

    /**
     * Verifies a request matches the given pattern.
     *
     * @param context the corresponding context
     * @param requestPatternBuilder the request pattern to match
     */
    public static void verifyAsync(final TestContext context, final RequestPatternBuilder requestPatternBuilder) {
        try {
            WireMock.verify(requestPatternBuilder);
        } catch (final AssertionError e) {
            context.fail(e);
        }
    }

    /**
     * Verifies multiple requests match the given pattern.
     *
     * @param context the corresponding context
     * @param count the number of requests to match
     * @param requestPatternBuilder the request pattern to match
     */
    public static void verifyAsync(final TestContext context, final int count,
            final RequestPatternBuilder requestPatternBuilder) {
        try {
            WireMock.verify(count, requestPatternBuilder);
        } catch (final AssertionError e) {
            context.fail(e);
        }
    }
}
