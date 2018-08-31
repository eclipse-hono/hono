/*******************************************************************************
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.service.metric;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;
import io.micrometer.graphite.GraphiteConfig;
import io.micrometer.graphite.GraphiteHierarchicalNameMapper;
import io.micrometer.graphite.GraphiteMeterRegistry;
import io.micrometer.spring.autoconfigure.MeterRegistryCustomizer;
import io.vertx.core.metrics.MetricsOptions;

/**
 * Configuration for using legacy style metrics.
 */
@Configuration
@ConditionalOnProperty(name = "hono.metrics.legacy", havingValue = "true")
@PropertySource("classpath:org/eclipse/hono/service/metric/legacy.properties")
public class LegacyMetricsConfig {

    /**
     * Provide Vert.x metrics options for the legacy metrics setup.
     * 
     * @return Metrics options for the legacy setup.
     */
    @Bean
    public MetricsOptions metricsOptions() {
        return new MetricsOptions();
    }

    /**
     * Create a new graphite meter registry, using the legacy format.
     * 
     * @param config The config to use.
     * @param clock The clock to use.
     * @return The new meter registry.
     */
    @Bean
    public GraphiteMeterRegistry graphiteMeterRegistry(final GraphiteConfig config, final Clock clock) {
        return new GraphiteMeterRegistry(config, clock,
                legacyGraphiteFormatMapper(new GraphiteHierarchicalNameMapper(config.tagsAsPrefix())));
    }

    /**
     * Meter registry customizer for the legacy metrics model.
     * 
     * @return The customizer for the legacy model.
     */
    @Bean
    public MeterRegistryCustomizer<GraphiteMeterRegistry> legacyMeterFilters() {
        return r -> r.config()
                .namingConvention(NamingConvention.dot)
                .meterFilter(MeterFilter.replaceTagValues("host", host -> host.replace('.', '_')))
                .meterFilter(MeterFilter.ignoreTags("component"))
                .meterFilter(meterTypeMapper());
    }

    private MeterFilter meterTypeMapper() {
        return new MeterFilter() {

            @Override
            public Meter.Id map(final Meter.Id id) {

                String name = id.getName();

                if (!name.startsWith("hono.")) {
                    return id;
                }

                name = name.substring("hono.".length());

                final List<Tag> newTags = new ArrayList<>(id.getTags());
                newTags.add(Tag.of("hono", "hono"));

                if ("connections.authenticated".equals(name)
                        || "connections.unauthenticated".equals(name)
                        || "messages.underliverable".equals(name)) {

                    newTags.add(Tag.of("meterType", "counter"));
                    newTags.add(Tag.of("typeSuffix", "count"));

                } else if (name.startsWith("messages.")) {

                    newTags.add(Tag.of("meterType", "meter"));
                    newTags.add(Tag.of("subName", name.substring("messages.".length())));
                    name = "messages";

                } else if (name.startsWith("payload.")
                        || name.startsWith("command.")) {

                    newTags.add(Tag.of("meterType", "meter"));

                }

                return new Meter.Id(name, newTags, id.getBaseUnit(), id.getDescription(),
                        id.getType());

            }
        };
    }

    private HierarchicalNameMapper legacyGraphiteFormatMapper(
            final GraphiteHierarchicalNameMapper defaultMapper) {
        return (id, convention) -> {

            if (id.getTag("hono") == null) {
                return defaultMapper.toHierarchicalName(id, convention);
            }

            return amendWithTags(id.getConventionName(convention), id,
                    new String[] { "host", "meterType", "hono", "protocol" },
                    new String[] { "type", "tenant", "subName", "typeSuffix" });
        };
    }

    private static String amendWithTags(final String name, final Meter.Id id,
            final String[] prefixTags, final String[] suffixTags) {

        final StringBuilder sb = new StringBuilder();

        addTags(sb, id, prefixTags);

        if (sb.length() > 0) {
            sb.append('.');
            sb.append(name);
        }

        addTags(sb, id, suffixTags);

        return sb.toString();
    }

    private static void addTags(final StringBuilder sb, final Meter.Id id, final String[] tags) {
        for (final String tag : tags) {
            final String value = id.getTag(tag);
            if (value != null) {
                if (sb.length() > 0) {
                    sb.append('.');
                }
                sb.append(value.replace(' ', '_'));
            }
        }
    }

}
