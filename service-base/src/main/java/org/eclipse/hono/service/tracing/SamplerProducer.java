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

import java.util.List;
import java.util.Optional;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Singleton;

import org.eclipse.hono.util.AuthenticationConstants;
import org.eclipse.hono.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.quarkus.opentelemetry.runtime.tracing.TracerRuntimeConfig;

/**
 * A producer for an OpenTelemetry Sampler.
 */
@ApplicationScoped
public class SamplerProducer {

    private static final Logger LOG = LoggerFactory.getLogger(SamplerProducer.class);

    @Singleton
    @Produces
    Sampler sampler(final TracerRuntimeConfig tracerRuntimeConfig) {
        final TracerRuntimeConfig.SamplerConfig samplerConfig = tracerRuntimeConfig.sampler;
        if (!tracerRuntimeConfig.suppressNonApplicationUris) {
            LOG.info("'quarkus.opentelemetry.tracer.suppress-non-application-uris' set to 'false' - will be ignored");
        }
        if (!samplerConfig.parentBased) {
            LOG.info("'quarkus.opentelemetry.tracer.sampler.parent-based' set to 'false' - will be ignored");
        }

        Sampler sampler = getBaseSampler(samplerConfig.samplerName, samplerConfig.ratio);
        sampler = Sampler.parentBased(sampler);
        sampler = new SamplingPrioritySampler(sampler);
        // Drop all HTTP request spans created by the quarkus OpenTelemetryVertxTracer for now.
        // Without http-server metrics enabled, these spans will get the HTTP request path as span name, including
        // variable parts like device-id, making span selection in the Tracing UI unusable.
        // Dropping all requests spans also makes sure there are no liveness/readiness request spans created.
        // Suppression of these spans via "quarkus.opentelemetry.tracer.suppress-non-application-uris" doesn't work
        // if the non-application-root-path is the same as the overall root path.
        sampler = new DropHttpRequestSpansSampler(sampler);
        // drop spans for the given event bus message prefixes
        return new DropBySpanNamePrefixSampler(
                sampler,
                List.of(Constants.EVENT_BUS_ADDRESS_NOTIFICATION_PREFIX, Constants.EVENT_BUS_ADDRESS_TENANT_TIMED_OUT,
                        AuthenticationConstants.EVENT_BUS_ADDRESS_AUTHENTICATION_IN));
    }

    private static Sampler getBaseSampler(final String samplerName, final Optional<Double> ratio) {
        LOG.info("using OpenTelemetry tracing sampler mode '{}' [ratio {}]", samplerName, ratio.orElse(null));
        return switch (samplerName) {
            case "on" -> Sampler.alwaysOn();
            case "off" -> Sampler.alwaysOff();
            case "ratio" -> Sampler.traceIdRatioBased(ratio.orElse(1.0d));
            default -> throw new IllegalArgumentException("Unrecognized value for sampler: " + samplerName);
        };
    }
}
