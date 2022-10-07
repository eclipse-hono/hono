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


package org.eclipse.hono.service.tracing;

import static com.google.common.truth.Truth.assertThat;

import org.junit.jupiter.api.Test;


/**
 * Verifies behavior of {@link SamplerProducer}.
 *
 */
class SamplerProducerTest {

    @Test
    void testParsingOfOtelConfig() {
        final var config = new SamplerProducer.OtelConfigProperties(
                "test_service",
                "jaeger_remote",
                "endpoint=http://localhost:1717,foo=bar");
        assertThat(config.getString(SamplerProducer.PROPERTY_OTEL_SERVICE_NAME)).isEqualTo("test_service");
        assertThat(config.getString(SamplerProducer.PROPERTY_OTEL_TRACES_SAMPLER)).isEqualTo("jaeger_remote");
        final var args = config.getMap(SamplerProducer.PROPERTY_OTEL_TRACES_SAMPLER_ARG);
        assertThat(args.get("endpoint")).isEqualTo("http://localhost:1717");
        assertThat(args.get("foo")).isEqualTo("bar");
    }
}
