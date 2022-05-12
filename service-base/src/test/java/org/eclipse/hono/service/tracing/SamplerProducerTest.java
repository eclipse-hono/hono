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

import java.util.List;

import org.junit.jupiter.api.Test;

import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;

/**
 * Verifies the behavior of {@link SamplerProducer}.
 *
 */
public class SamplerProducerTest {

    /**
     * Verifies that the Quarkus default non-application endpoint URI paths are determined correctly.
     */
    @Test
    public void testGetNonApplicationPathUrisWithQuarkusDefaultPaths() {

        final SmallRyeConfig config = new SmallRyeConfigBuilder()
                .withDefaultValue("quarkus.http.root-path", "/")
                .withDefaultValue("quarkus.http.non-application-root-path", "q")
                .withDefaultValue("quarkus.smallrye-health.root-path", "health")
                .withDefaultValue("quarkus.smallrye-health.liveness-path", "live")
                .withDefaultValue("quarkus.smallrye-health.readiness-path", "ready")
                .withDefaultValue("quarkus.smallrye-health.startup-path", "started")
                .withDefaultValue("quarkus.micrometer.export.prometheus.path", "metrics")
                .build();

        final List<String> paths = SamplerProducer.getNonApplicationUriPaths(config);
        assertThat(paths).containsExactly("/q/health/live", "/q/health/ready", "/q/health/started", "/q/metrics");
    }

    /**
     * Verifies that the Hono default non-application endpoint URI paths are determined correctly.
     */
    @Test
    public void testGetNonApplicationPathUrisWithHonoDefaultPaths() {

        final SmallRyeConfig config = new SmallRyeConfigBuilder()
                .withDefaultValue("quarkus.http.root-path", "/")
                .withDefaultValue("quarkus.http.non-application-root-path", "/")
                .withDefaultValue("quarkus.smallrye-health.root-path", "/")
                .withDefaultValue("quarkus.smallrye-health.liveness-path", "liveness")
                .withDefaultValue("quarkus.smallrye-health.readiness-path", "readiness")
                .withDefaultValue("quarkus.smallrye-health.startup-path", "started")
                .withDefaultValue("quarkus.micrometer.export.prometheus.path", "/prometheus")
                .build();

        final List<String> paths = SamplerProducer.getNonApplicationUriPaths(config);
        assertThat(paths).containsExactly("/liveness", "/readiness", "/started", "/prometheus");
    }

}
