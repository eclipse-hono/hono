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

    /**
     * The tag that holds the name of the host that the component
     * reporting a metric is running on.
     */
    public static final String TAG_HOST = "host";
    /**
     * The name of the tag that holds the type of component that
     * reports a metric.
     */
    public static final String TAG_COMPONENT_TYPE = "component-type";
    public static final String TAG_PROTOCOL = "protocol";
    public static final String TAG_SERVICE = "service";
    public static final String TAG_TENANT = "tenant";
    /**
     * The name of the tag that holds the type of message
     * that a metric has been reported for.
     */
    public static final String TAG_TYPE = "type";
    /**
     * The component type indicating a protocol adapter.
     */
    public static final String VALUE_COMPONENT_TYPE_ADAPTER = "adapter";
    /**
     * The component type indicating a service component.
     */
    public static final String VALUE_COMPONENT_TYPE_SERVICE = "service";

    public static final String VALUE_PROTOCOL_AMQP = "ampq";
    public static final String VALUE_PROTOCOL_COAP = "coap";
    public static final String VALUE_PROTOCOL_HTTP = "http";
    public static final String VALUE_PROTOCOL_MQTT = "mqtt";

    public static final String VALUE_SERVICE_AUTH = "auth";
    public static final String VALUE_SERVICE_MESSAGING = "messaging";
    public static final String VALUE_SERVICE_REGISTRY = "registry";

    private MetricsTags() {
    }

    /**
     * Creates the default tag set for a protocol adapter.
     * 
     * @param name The name of the protocol adapter.
     * @return A ready to use tag set.
     */
    public static Tags forProtocolAdapter(final String name) {
        return Tags.of(
                Tag.of(MetricsTags.TAG_HOST, Hostnames.getHostname()),
                Tag.of(MetricsTags.TAG_COMPONENT_TYPE, MetricsTags.VALUE_COMPONENT_TYPE_ADAPTER),
                Tag.of(MetricsTags.TAG_PROTOCOL, name));
    }

    /**
     * Creates the default tag set for a service.
     * 
     * @param name The name of the service.
     * @return A ready to use tag set.
     */
    public static Tags forService(final String name) {
        return Tags.of(
                Tag.of(MetricsTags.TAG_HOST, Hostnames.getHostname()),
                Tag.of(MetricsTags.TAG_COMPONENT_TYPE, MetricsTags.VALUE_COMPONENT_TYPE_SERVICE),
                Tag.of(MetricsTags.TAG_SERVICE, name));
    }

}
