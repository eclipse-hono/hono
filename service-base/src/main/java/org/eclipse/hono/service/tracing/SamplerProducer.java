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

    private static final List<String> EVENT_BUS_ADDRESS_PREFIXES_TO_IGNORE = List.of(
            Constants.EVENT_BUS_ADDRESS_NOTIFICATION_PREFIX,
            Constants.EVENT_BUS_ADDRESS_TENANT_TIMED_OUT,
            AuthenticationConstants.EVENT_BUS_ADDRESS_AUTHENTICATION_IN);

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

        final SmallRyeConfig config = ConfigProvider.getConfig().unwrap(SmallRyeConfig.class);
        final List<String> prefixesOfSpansToDrop = new ArrayList<>(getNonApplicationUriPaths(config));
        LOG.info("spans for non-application-URI HTTP requests will be dropped; relevant span name prefixes: {}", prefixesOfSpansToDrop);
        // drop spans for event bus message prefixes
        prefixesOfSpansToDrop.addAll(EVENT_BUS_ADDRESS_PREFIXES_TO_IGNORE);
        return new DropBySpanNamePrefixSampler(sampler, prefixesOfSpansToDrop);
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
