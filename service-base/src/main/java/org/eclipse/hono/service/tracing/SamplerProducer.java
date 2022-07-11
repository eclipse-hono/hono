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

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Singleton;

import org.eclipse.hono.util.AuthenticationConstants;
import org.eclipse.hono.util.Constants;
import org.eclipse.microprofile.config.ConfigProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.quarkus.opentelemetry.runtime.tracing.TracerRuntimeConfig;
import io.smallrye.config.SmallRyeConfig;

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
        if (!tracerRuntimeConfig.suppressNonApplicationUris) {
            LOG.info("'quarkus.opentelemetry.tracer.suppress-non-application-uris' set to 'false' - will be ignored");
        }
        if (!samplerConfig.parentBased) {
            LOG.info("'quarkus.opentelemetry.tracer.sampler.parent-based' set to 'false' - will be ignored");
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

        final SmallRyeConfig config = ConfigProvider.getConfig().unwrap(SmallRyeConfig.class);
        final List<String> prefixesOfSpansToDrop = new ArrayList<>(getNonApplicationUriPaths(config));
        LOG.info("spans for non-application-URI HTTP requests will be dropped; relevant span name prefixes: {}", prefixesOfSpansToDrop);
        // drop spans for event bus message prefixes
        prefixesOfSpansToDrop.addAll(EVENT_BUS_ADDRESS_PREFIXES_TO_IGNORE);
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

    /**
     * Gets a list of the URI paths of the non-application endpoints provided by Quarkus, i.e. the paths
     * of the health-check and metrics endpoints.
     *
     * @param config The config object.
     * @return The list of URI paths.
     */
    static List<String> getNonApplicationUriPaths(final SmallRyeConfig config) {
        final List<String> paths = new ArrayList<>();

        // the logic here has been adopted from io.quarkus.vertx.http.deployment.NonApplicationRootPathBuildItem and its usage
        Optional.ofNullable(config.getRawValue("quarkus.http.root-path"))
                .map(path -> UriNormalizationUtil.toURI(path, true))
                .ifPresent(httpRootPath -> {
                    final URI nonAppRootPath = UriNormalizationUtil.normalizeWithBase(
                            httpRootPath,
                            config.getRawValue("quarkus.http.non-application-root-path"),
                            true);

                    addHealthPaths(paths, config, nonAppRootPath);
                    Optional.ofNullable(config.getRawValue("quarkus.micrometer.export.prometheus.path"))
                            .map(path -> resolveNonApplicationPath(nonAppRootPath, path))
                            .ifPresent(paths::add);
                });
        return paths;
    }

    private static void addHealthPaths(final List<String> pathsToAddTo, final SmallRyeConfig config, final URI nonAppRootPath) {
        Optional.ofNullable(config.getRawValue("quarkus.smallrye-health.root-path"))
                .map(path -> resolveNonApplicationPath(nonAppRootPath, path))
                .ifPresent(healthRootPath -> {
                    Optional.ofNullable(config.getRawValue("quarkus.smallrye-health.liveness-path"))
                            .map(subPath -> resolveNestedNonApplicationPath(nonAppRootPath, healthRootPath, subPath))
                            .ifPresent(pathsToAddTo::add);
                    Optional.ofNullable(config.getRawValue("quarkus.smallrye-health.readiness-path"))
                            .map(subPath -> resolveNestedNonApplicationPath(nonAppRootPath, healthRootPath, subPath))
                            .ifPresent(pathsToAddTo::add);
                    Optional.ofNullable(config.getRawValue("quarkus.smallrye-health.startup-path"))
                            .map(subPath -> resolveNestedNonApplicationPath(nonAppRootPath, healthRootPath, subPath))
                            .ifPresent(pathsToAddTo::add);
                });
    }

    private static String resolveNonApplicationPath(final URI nonAppRootPath, final String path) {
        if (path == null || path.trim().isEmpty()) {
            throw new IllegalArgumentException("Specified path can not be empty");
        }
        return UriNormalizationUtil.normalizeWithBase(nonAppRootPath, path, false).getPath();
    }

    private static String resolveNestedNonApplicationPath(final URI nonAppRootPath, final String path, final String subRoute) {
        if (path == null || path.trim().isEmpty()) {
            throw new IllegalArgumentException("Specified path can not be empty");
        }
        final URI base = UriNormalizationUtil.normalizeWithBase(nonAppRootPath, path, true);
        return UriNormalizationUtil.normalizeWithBase(base, subRoute, false).getPath();
    }
}
