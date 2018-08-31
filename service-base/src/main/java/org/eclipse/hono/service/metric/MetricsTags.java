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

import org.eclipse.hono.util.Hostnames;

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;

/**
 * Common definition of metrics tags.
 */
public final class MetricsTags {

    public static final String TAG_HOST = "host";
    public static final String TAG_COMPONENT = "component";
    public static final String TAG_PROTOCOL = "protocol";
    public static final String TAG_SERVICE = "service";

    public static final String VALUE_COMPONENT_ADAPTER = "adapter";
    public static final String VALUE_COMPONENT_SERVICE = "service";

    public static final String VALUE_PROTOCOL_AMQP = "ampq";
    public static final String VALUE_PROTOCOL_COAP = "coap";
    public static final String VALUE_PROTOCOL_HTTP = "http";
    public static final String VALUE_PROTOCOL_MQTT = "mqtt";

    public static final String VALUE_SERVICE_REGISTRY = "registry";
    public static final String VALUE_SERVICE_MESSAGING = "messaging";

    private MetricsTags() {
    }

    /**
     * Create default tag set for a protocol adapter.
     * 
     * @param name The name of the protocol adapter.
     * @return A ready to use tag set.
     */
    public static Tags forProtocolAdapter(final String name) {
        return Tags.of(
                Tag.of(MetricsTags.TAG_HOST, Hostnames.getHostname()),
                Tag.of(MetricsTags.TAG_COMPONENT, MetricsTags.VALUE_COMPONENT_ADAPTER),
                Tag.of(MetricsTags.TAG_PROTOCOL, name));
    }

    /**
     * Create default tag set for a service.
     * 
     * @param name The name of the service.
     * @return A ready to use tag set.
     */
    public static Tags forService(final String name) {
        return Tags.of(
                Tag.of(MetricsTags.TAG_HOST, Hostnames.getHostname()),
                Tag.of(MetricsTags.TAG_COMPONENT, MetricsTags.VALUE_COMPONENT_SERVICE),
                Tag.of(MetricsTags.TAG_SERVICE, name));
    }

}
