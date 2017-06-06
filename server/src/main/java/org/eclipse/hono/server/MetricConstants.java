/**
 * Copyright (c) 2016, 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.server;

/**
 * Constants/metric names
 */
public class MetricConstants {

    public static String metricNameDownstreamConnections() {
        return "hono.server.connections.downstream";
    }

    public static String metricNameDownstreamLinkCredits(final String address) {
        return "hono.server.link.downstream.credits." + address;
    }

    public static String metricNameDownstreamSenders(final String address) {
        return "hono.server.senders.downstream." + address;
    }

    public static String metricNameUpstreamLinks(final String address) {
        return "hono.server.receivers.upstream.links." + address;
    }

    public static String metricNameProcessedMessages(final String address) {
        // prefix "meter" is used by spring boot actuator together with dropwizard metrics
        return "meter.hono.server.messages." + address + ".processed";
    }

    public static String metricNameDiscardedMessages(final String address) {
        return "hono.server.messages." + address + ".discarded";
    }

    public static String metricNameUndeliverableMessages(final String address) {
        return "hono.server.messages." + address + ".undeliverable";
    }

}
