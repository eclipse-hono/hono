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

import java.util.ArrayList;
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

    private static final String SAMPLER_NAME_PROPERTY = "OTEL_TRACES_SAMPLER";
    private static final String SAMPLER_ARG_PROPERTY = "OTEL_TRACES_SAMPLER_ARG";
    private static final int DEFAULT_MAX_TRACES_PER_SECOND = 1;

    private static final List<String> EVENT_BUS_ADDRESS_PREFIXES_TO_IGNORE = List.of(
            Constants.EVENT_BUS_ADDRESS_NOTIFICATION_PREFIX,
            Constants.EVENT_BUS_ADDRESS_TENANT_TIMED_OUT,
            AuthenticationConstants.EVENT_BUS_ADDRESS_AUTHENTICATION_IN);

    private static final String INVALID_HTTP_REQUEST_SPAN_NAME = "/bad-request"; // see netty HttpRequestDecoder.createInvalidMessage()

    @Singleton
    @Produces
    Sampler sampler(final TracerRuntimeConfig tracerRuntimeConfig) {
        final TracerRuntimeConfig.SamplerConfig samplerConfig = tracerRuntimeConfig.sampler;
        if (samplerConfig.parentBased) {
            LOG.warn("'quarkus.opentelemetry.tracer.sampler.parent-based' set to 'true' - custom Hono Sampler will not be applied to child spans");
        }

        final String samplerName = Optional.ofNullable(getProperty(SAMPLER_NAME_PROPERTY))
                .orElse(samplerConfig.samplerName);
        final Optional<Object> samplerArg = Optional.ofNullable((Object) getProperty(SAMPLER_ARG_PROPERTY))
                .or(() -> {
                    if (samplerName.equals("ratio")) {
                        return samplerConfig.ratio;
                    }
                    return Optional.empty();
                });
        Sampler sampler = getBaseSampler(samplerName, samplerArg);
        sampler = Sampler.parentBased(sampler);
        sampler = new SamplingPrioritySampler(sampler);

        // drop spans for event bus message prefixes
        final List<String> prefixesOfSpansToDrop = new ArrayList<>(EVENT_BUS_ADDRESS_PREFIXES_TO_IGNORE);
        // drop "/bad-request" spans for invalid HTTP requests
        prefixesOfSpansToDrop.add(INVALID_HTTP_REQUEST_SPAN_NAME);
        return new DropBySpanNamePrefixSampler(sampler, prefixesOfSpansToDrop);
    }

    private static String getProperty(final String name) {
        return System.getProperty(name, System.getenv(name));
    }

    private static Sampler getBaseSampler(final String samplerName, final Optional<Object> samplerArg) {
        LOG.info("using OpenTelemetry tracing sampler mode '{}' [arg: {}]", samplerName, samplerArg.orElse(null));
        return switch (samplerName) {
            case "on" -> Sampler.alwaysOn();
            case "off" -> Sampler.alwaysOff();
            case "ratio" -> Sampler.traceIdRatioBased(mapSamplerRatio(samplerArg));
            case "rate-limiting" -> new RateLimitingSampler(mapSamplerMaxTracesPerSecond(samplerArg));
            default -> throw new IllegalArgumentException("Unrecognized value for sampler: " + samplerName);
        };
    }

    private static Double mapSamplerRatio(final Optional<Object> samplerArg) {
        return samplerArg.map(arg -> {
            if (arg instanceof Double doubleArg) {
                return doubleArg;
            }
            try {
                return Double.parseDouble(arg.toString());
            } catch (final NumberFormatException e) {
                LOG.warn("invalid sampler ratio config (will use 1.0)", e);
                return 1.0d;
            }
        }).orElse(1.0d);
    }

    private static Integer mapSamplerMaxTracesPerSecond(final Optional<Object> samplerArg) {
        return samplerArg.map(arg -> {
            if (arg instanceof Integer intArg) {
                return intArg;
            }
            try {
                return Integer.parseInt(arg.toString());
            } catch (final NumberFormatException e) {
                LOG.warn("invalid sampler rate-limit config (will use {})", DEFAULT_MAX_TRACES_PER_SECOND, e);
                return DEFAULT_MAX_TRACES_PER_SECOND;
            }
        }).orElse(DEFAULT_MAX_TRACES_PER_SECOND);
    }
}
