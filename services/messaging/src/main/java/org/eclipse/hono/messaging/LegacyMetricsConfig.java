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
package org.eclipse.hono.messaging;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.hono.service.metric.AbstractLegacyMetricsConfig;
import org.eclipse.hono.service.metric.MetricsTags;
import org.eclipse.hono.service.metric.MicrometerBasedMetrics;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.config.MeterFilter;

/**
 * Configuration for using legacy style metrics.
 */
@Configuration
@ConditionalOnProperty(name = "hono.metrics.legacy", havingValue = "true")
@PropertySource("classpath:org/eclipse/hono/service/metric/legacy.properties")
public class LegacyMetricsConfig extends AbstractLegacyMetricsConfig {

    /**
     * Creates a filter that adds tags to metrics reported by Hono Messaging
     * which are required for creating the hierarchical Graphite meter name.
     * 
     * @return The filter.
     */
    @Override
    protected MeterFilter meterTypeMapper() {

        return new MeterFilter() {

            @Override
            public Meter.Id map(final Meter.Id id) {

                String name = id.getName();

                if (!name.startsWith("hono.")) {
                    return id;
                }

                name = name.substring("hono.".length());

                final List<Tag> newTags = new ArrayList<>(id.getTags());
                newTags.add(Tag.of(TAG_HONO, "hono"));

                if (MicrometerBasedMessagingMetrics.METER_UPSTREAM_LINKS.equals(id.getName())
                        || MicrometerBasedMessagingMetrics.METER_DOWNSTREAM_SENDERS.equals(id.getName())
                        || MicrometerBasedMessagingMetrics.METER_CONNECTIONS_DOWNSTREAM.equals(id.getName())) {

                    newTags.add(Tag.of(TAG_METER_TYPE, "counter"));
                    mapComponentName(id, newTags);
                    // we need to add the type suffix because the underlying
                    // Micrometer meter is a Gauge and the Graphite
                    // exporter will not automatically append the suffix
                    // itself
                    newTags.add(Tag.of(TAG_TYPE_SUFFIX, "count"));

                } else if (MicrometerBasedMessagingMetrics.METER_DOWNSTREAM_LINK_CREDITS.equals(id.getName())) {

                    newTags.add(Tag.of(TAG_METER_TYPE, "gauge"));
                    mapComponentName(id, newTags);

                } else if (MicrometerBasedMetrics.METER_MESSAGES_PROCESSED.equals(id.getName())) {

                    newTags.add(Tag.of(TAG_METER_TYPE, "meter"));
                    mapComponentName(id, newTags);
                    // extract the "sub-name" into a separate tag
                    // so that we can later add it to the meter name
                    // AFTER the type and tenant. This is necessary
                    // because the InfluxDB Graphite tag template
                    // expects it to find there
                    newTags.add(Tag.of(TAG_SUB_NAME, name.substring("messages.".length())));
                    name = "messages";

                } else if (name.startsWith("messages.")) {

                    newTags.add(Tag.of(TAG_METER_TYPE, "counter"));
                    mapComponentName(id, newTags);
                    newTags.add(Tag.of(TAG_SUB_NAME, name.substring("messages.".length())));
                    name = "messages";

                }

                return new Meter.Id(name, newTags, id.getBaseUnit(), id.getDescription(),
                        id.getType());
            }

            private void mapComponentName(final Meter.Id id, final List<Tag> newTags) {
                final String componentName = id.getTag(MetricsTags.TAG_COMPONENT_NAME);
                if (componentName != null) {
                    final String protocol = getProtocolForComponentName(componentName);
                    newTags.add(Tag.of(TAG_PROTOCOL, protocol));
                }
            }
        };
    }
}
