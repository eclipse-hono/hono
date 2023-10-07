/**
 * Copyright (c) 2022, 2023 Contributors to the Eclipse Foundation
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

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.hono.util.AuthenticationConstants;
import org.eclipse.hono.util.Constants;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.extension.trace.jaeger.sampler.JaegerRemoteSamplerProvider;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import io.quarkus.opentelemetry.runtime.config.build.SamplerType;
import io.quarkus.opentelemetry.runtime.config.runtime.OTelRuntimeConfig;
import io.quarkus.opentelemetry.runtime.config.runtime.TracesRuntimeConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * A producer for a custom OpenTelemetry Sampler.
 */
@ApplicationScoped
public final class SamplerProducer {

    static final String PROPERTY_OTEL_SERVICE_NAME = "otel.service.name";
    static final String PROPERTY_OTEL_TRACES_SAMPLER = "otel.traces.sampler";
    static final String PROPERTY_OTEL_TRACES_SAMPLER_ARG = "otel.traces.sampler.arg";
    static final String PROPERTY_QUARKUS_APPLICATION_NAME = "quarkus.application.name";

    private static final Logger LOG = LoggerFactory.getLogger(SamplerProducer.class);

    private static final int DEFAULT_MAX_TRACES_PER_SECOND = 1;

    private static final List<String> EVENT_BUS_ADDRESS_PREFIXES_TO_IGNORE = List.of(
            Constants.EVENT_BUS_ADDRESS_NOTIFICATION_PREFIX,
            Constants.EVENT_BUS_ADDRESS_TENANT_TIMED_OUT,
            AuthenticationConstants.EVENT_BUS_ADDRESS_AUTHENTICATION_IN);

    private static final String INVALID_HTTP_REQUEST_SPAN_NAME = "/bad-request"; // see netty HttpRequestDecoder.createInvalidMessage()

    private ConfigProperties otelConfig;
    private String serviceName;

    static class OtelConfigProperties implements ConfigProperties {

        private final String otelServiceName;
        private final String otelTracesSampler;
        private final String otelTracesSamplerArg;

        OtelConfigProperties(
                final String serviceName,
                final String tracesSampler,
                final String tracesSamplerArg) {
            this.otelServiceName = serviceName;
            this.otelTracesSampler = tracesSampler;
            this.otelTracesSamplerArg = tracesSamplerArg;
            LOG.debug("using OTEL configuration [{}: {}, {}: {}]",
                    PROPERTY_OTEL_TRACES_SAMPLER, tracesSampler,
                    PROPERTY_OTEL_TRACES_SAMPLER_ARG, tracesSamplerArg);
        }


        @Override
        public String getString(final String name) {
            switch (name) {
            case PROPERTY_OTEL_SERVICE_NAME:
                return otelServiceName;
            case PROPERTY_OTEL_TRACES_SAMPLER:
                return otelTracesSampler;
            default:
                return null;
            }
        }

        @Override
        public Map<String, String> getMap(final String name) {
            switch (name) {
            case PROPERTY_OTEL_TRACES_SAMPLER_ARG:
                return Optional.ofNullable(otelTracesSamplerArg)
                        .map(s -> {
                            final HashMap<String, String> map = new HashMap<>();
                            final var kvPairs = s.split(",", -1);
                            for (String kv : kvPairs) {
                                final int idx = kv.indexOf("=");
                                if (idx > 0 && kv.length() > idx) {
                                    map.put(kv.substring(0, idx), kv.substring(idx + 1));
                                }
                            }
                            return map;
                        })
                        .orElseGet(HashMap::new);
            default:
                return Map.of();
            }
        }

        @Override
        public Long getLong(final String name) {
            return null;
        }

        @Override
        public List<String> getList(final String name) {
            return null;
        }

        @Override
        public Integer getInt(final String name) {
            return null;
        }

        @Override
        public Duration getDuration(final String name) {
            return null;
        }

        @Override
        public Double getDouble(final String name) {
            return null;
        }

        @Override
        public Boolean getBoolean(final String name) {
            return null;
        }
    }

    @Inject
    SamplerProducer(
            @ConfigProperty(name = PROPERTY_OTEL_SERVICE_NAME)
            final Optional<String> otelServiceName,
            @ConfigProperty(name = PROPERTY_OTEL_TRACES_SAMPLER)
            final Optional<String> otelTracesSampler,
            @ConfigProperty(name = PROPERTY_OTEL_TRACES_SAMPLER_ARG)
            final Optional<String> otelTracesSamplerArg,
            @ConfigProperty(name = PROPERTY_QUARKUS_APPLICATION_NAME)
            final String quarkusApplicationName) {
        this.serviceName = otelServiceName.orElse(quarkusApplicationName);
        this.otelConfig = new OtelConfigProperties(
                otelServiceName.orElse(null),
                otelTracesSampler.orElse(null),
                otelTracesSamplerArg.orElse(null));
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
     * @param otelRuntimeConfig The Quarkus specific OTEL configuration properties.
     * @return The sampler.
     */
    @Singleton
    @Produces
    Sampler sampler(final OTelRuntimeConfig otelRuntimeConfig) {
        final String samplerName = Optional.ofNullable(otelConfig.getString(PROPERTY_OTEL_TRACES_SAMPLER))
                .orElse(SamplerType.ALWAYS_ON.getValue());

        Sampler sampler = getBaseSampler(samplerName, otelRuntimeConfig.traces());
        sampler = Sampler.parentBased(sampler);
        if (LOG.isInfoEnabled()) {
            LOG.info("using OpenTelemetry Sampler [{}]", sampler.toString());
        }
        sampler = new SamplingPrioritySampler(sampler);

        // drop spans for event bus message prefixes
        final List<String> prefixesOfSpansToDrop = new ArrayList<>(EVENT_BUS_ADDRESS_PREFIXES_TO_IGNORE);
        // drop "/bad-request" spans for invalid HTTP requests
        prefixesOfSpansToDrop.add(INVALID_HTTP_REQUEST_SPAN_NAME);
        return new DropBySpanNamePrefixSampler(sampler, prefixesOfSpansToDrop);
    }

    private Sampler getBaseSampler(final String samplerName, final TracesRuntimeConfig tracesRuntimeConfig) {
        switch (samplerName) {
        // see https://opentelemetry.io/docs/reference/specification/sdk-environment-variables/
        case "jaeger_remote", "parentbased_jaeger_remote":
            return jaegerRemoteSampler();
        case "always_on", "parentbased_always_on", "on":
            return Sampler.alwaysOn();
        case "always_off", "parentbased_always_off", "off":
            return Sampler.alwaysOff();
        case "traceidratio", "parentbased_traceidratio", "ratio":
            return traceIdRatioBasedSampler(tracesRuntimeConfig);
        // also support rate-limiting sampler directly, i.e. without using Jaeger remote sampler
        case "rate_limiting", "parentbased_rate_limiting", "rate-limiting":
            return rateLimitingSampler();
        default:
            LOG.warn("unsupported sampler type [{}], falling back to always_on sampler", samplerName);
            return Sampler.alwaysOn();
        }
    }

    private Sampler jaegerRemoteSampler() {
        return new JaegerRemoteSamplerProvider().createSampler(otelConfig);
    }

    private Sampler traceIdRatioBasedSampler(final TracesRuntimeConfig tracesRuntimeConfig) {
        final Optional<Double> samplerArg = Optional.ofNullable(otelConfig.getString(PROPERTY_OTEL_TRACES_SAMPLER_ARG))
                .map(s -> {
                    try {
                        return Double.parseDouble(s);
                    } catch (final NumberFormatException e) {
                        LOG.warn("invalid sampler ratio config (will use 1.0)", e);
                        return 1.0d;
                    }
                })
                .or(() -> tracesRuntimeConfig.samplerArg());

        return Sampler.traceIdRatioBased(samplerArg.orElse(1.0d));
    }

    private RateLimitingSampler rateLimitingSampler() {
        final int tracesPerSecond = Optional.ofNullable(otelConfig.getString(PROPERTY_OTEL_TRACES_SAMPLER_ARG))
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
