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
import javax.inject.Inject;
import javax.inject.Singleton;

import org.eclipse.hono.util.AuthenticationConstants;
import org.eclipse.hono.util.Constants;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.extension.trace.jaeger.sampler.JaegerRemoteSamplerProvider;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.opentelemetry.runtime.tracing.TracerRuntimeConfig;

/**
 * A producer for a custom OpenTelemetry Sampler.
 */
@ApplicationScoped
public final class SamplerProducer {

    private static final Logger LOG = LoggerFactory.getLogger(SamplerProducer.class);

    private static final int DEFAULT_MAX_TRACES_PER_SECOND = 1;

    private static final String PROPERTY_OTEL_SERVICE_NAME = "otel.service.name";
    private static final String PROPERTY_OTEL_TRACES_SAMPLER = "otel.traces.sampler";
    private static final String PROPERTY_OTEL_TRACES_SAMPLER_ARG = "otel.traces.sampler.arg";
    private static final String PROPERTY_QUARKUS_APPLICATION_NAME = "quarkus.application.name";

    private static final List<String> EVENT_BUS_ADDRESS_PREFIXES_TO_IGNORE = List.of(
            Constants.EVENT_BUS_ADDRESS_NOTIFICATION_PREFIX,
            Constants.EVENT_BUS_ADDRESS_TENANT_TIMED_OUT,
            AuthenticationConstants.EVENT_BUS_ADDRESS_AUTHENTICATION_IN);

    private static final String INVALID_HTTP_REQUEST_SPAN_NAME = "/bad-request"; // see netty HttpRequestDecoder.createInvalidMessage()

    @Inject
    @ConfigProperty(name = PROPERTY_OTEL_TRACES_SAMPLER)
    Optional<String> otelTracesSampler;

    @Inject
    @ConfigProperty(name = PROPERTY_OTEL_TRACES_SAMPLER_ARG)
    Optional<String> otelTracesSamplerArg;

    private String serviceName;

    @Inject
    void setServiceName(
            @ConfigProperty(name = PROPERTY_OTEL_SERVICE_NAME)
            final Optional<String> otelServiceName,
            @ConfigProperty(name = PROPERTY_QUARKUS_APPLICATION_NAME)
            final String quarkusApplicationName) {
        this.serviceName = otelServiceName.orElse(quarkusApplicationName);
    }

    /**
     * Creates additional properties to be included in tracing information that is being reported to a Collector.
     * <p>
     * In particular, the properties include the <em>service.name</em> and <em>service.namespace</em> of the
     * reporting component.
     *
     * @return The additional properties.
     */
    @Singleton
    @Produces
    @IfBuildProperty(name = "quarkus.opentelemetry.enabled", stringValue = "true")
    Resource resource() {

        final var attributes = Attributes.of(
                ResourceAttributes.SERVICE_NAME, serviceName,
                ResourceAttributes.SERVICE_NAMESPACE, "org.eclipse.hono");
        if (LOG.isDebugEnabled()) {
            LOG.debug("using OTEL resources: {}", attributes);
        }
        return Resource.create(attributes);
    }

    /**
     * Creates a custom sampler based configuration properties.
     *
     * @param tracerRuntimeConfig The Quarkus specific OTEL configuration properties.
     * @return The sampler.
     */
    @Singleton
    @Produces
    @IfBuildProperty(name = "quarkus.opentelemetry.enabled", stringValue = "true")
    Sampler sampler(final TracerRuntimeConfig tracerRuntimeConfig) {
        final var samplerConfig = tracerRuntimeConfig.sampler;
        if (samplerConfig.parentBased) {
            LOG.warn("""
                    'quarkus.opentelemetry.tracer.sampler.parent-based' set to 'true' - \
                    custom Hono Sampler will not be applied to child spans
                    """);
        }
        LOG.debug("using OTEL configuration [{}: {}, {}: {}]",
                PROPERTY_OTEL_TRACES_SAMPLER, otelTracesSampler.orElse(null),
                PROPERTY_OTEL_TRACES_SAMPLER_ARG, otelTracesSamplerArg.orElse(null));
        final String samplerName = otelTracesSampler.orElse(samplerConfig.samplerName);

        Sampler sampler = getBaseSampler(samplerName, samplerConfig);
        sampler = Sampler.parentBased(sampler);
        sampler = new SamplingPrioritySampler(sampler);

        // drop spans for event bus message prefixes
        final List<String> prefixesOfSpansToDrop = new ArrayList<>(EVENT_BUS_ADDRESS_PREFIXES_TO_IGNORE);
        // drop "/bad-request" spans for invalid HTTP requests
        prefixesOfSpansToDrop.add(INVALID_HTTP_REQUEST_SPAN_NAME);
        return new DropBySpanNamePrefixSampler(sampler, prefixesOfSpansToDrop);
    }

    private Sampler getBaseSampler(final String samplerName, final TracerRuntimeConfig.SamplerConfig samplerConfig) {
        LOG.info("creating OpenTelemetry Sampler [type: {}]", samplerName);

        return switch (samplerName) {
            // see https://opentelemetry.io/docs/reference/specification/sdk-environment-variables/
            case "jaeger_remote" -> jaegerRemoteSampler();
            case "always_on", "on" -> Sampler.alwaysOn();
            case "always_off", "off" -> Sampler.alwaysOff();
            case "traceidratio", "ratio" -> traceIdRatioBasedSampler(samplerConfig);
            // also support rate-limiting sampler directly, i.e. without using Jaeger remote sampler
            case "rate_limiting", "rate-limiting" -> rateLimitingSampler();
            default -> Sampler.alwaysOn();
        };
    }

    private Sampler jaegerRemoteSampler() {

        // we want to use the SDK's capability to parse the sampler's configuration
        // from Java system properties
        System.setProperty(PROPERTY_OTEL_SERVICE_NAME, serviceName);
        otelTracesSampler.ifPresent(s -> System.setProperty(PROPERTY_OTEL_TRACES_SAMPLER, s));
        otelTracesSamplerArg.ifPresent(s -> System.setProperty(PROPERTY_OTEL_TRACES_SAMPLER_ARG, s));
        // but we do not want to register the SDK globally because that would conflict
        // with the Quarkus OpenTelemetry extension which also tries to do so
        final var sdkBuilder = AutoConfiguredOpenTelemetrySdk.builder().setResultAsGlobal(false);
        final var sdk = sdkBuilder.build();
        final var provider = new JaegerRemoteSamplerProvider();
        return provider.createSampler(sdk.getConfig());
    }

    private Sampler traceIdRatioBasedSampler(final TracerRuntimeConfig.SamplerConfig samplerConfig) {
        final Optional<Double> samplerArg = otelTracesSamplerArg
                .map(s -> {
                    try {
                        return Double.parseDouble(s);
                    } catch (final NumberFormatException e) {
                        LOG.warn("invalid sampler ratio config (will use 1.0)", e);
                        return 1.0d;
                    }
                })
                .or(() -> samplerConfig.ratio);

        return Sampler.traceIdRatioBased(samplerArg.orElse(1.0d));
    }

    private RateLimitingSampler rateLimitingSampler() {
        final int tracesPerSecond = otelTracesSamplerArg
                .map(s -> {
                    try {
                        return Integer.parseInt(s);
                    } catch (final NumberFormatException e) {
                        LOG.warn("invalid sampler rate-limit config (will use {})", DEFAULT_MAX_TRACES_PER_SECOND, e);
                        return DEFAULT_MAX_TRACES_PER_SECOND;
                    }
                })
                .orElse(DEFAULT_MAX_TRACES_PER_SECOND);
        return new RateLimitingSampler(tracesPerSecond);
    }
}
