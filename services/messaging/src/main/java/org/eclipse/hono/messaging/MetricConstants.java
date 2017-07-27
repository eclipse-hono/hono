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

package org.eclipse.hono.messaging;

import java.util.Objects;

/**
 * Constants/metric names
 */
public class MetricConstants {

    static final String PREFIX = "hono.messaging";

    public static String metricNameDownstreamConnections() {
        return PREFIX + ".connections.downstream";
    }

    public static String metricNameDownstreamLinkCredits(final String address) {
        return PREFIX + ".link.downstream.credits." + normalizeAddress(address);
    }

    public static String metricNameDownstreamSenders(final String address) {
        return PREFIX + ".senders.downstream." + normalizeAddress(address);
    }

    public static String metricNameUpstreamLinks(final String address) {
        return PREFIX + ".receivers.upstream.links." + normalizeAddress(address);
    }

    public static String metricNameProcessedMessages(final String address) {
        // prefix "meter" is used by spring boot actuator together with dropwizard metrics
        return "meter." + PREFIX + ".messages." + normalizeAddress(address) + ".processed";
    }

    public static String metricNameDiscardedMessages(final String address) {
        return PREFIX + ".messages." + normalizeAddress(address) + ".discarded";
    }

    public static String metricNameUndeliverableMessages(final String address) {
        return PREFIX + ".messages." + normalizeAddress(address) + ".undeliverable";
    }

    private static String normalizeAddress(final String address) {
        Objects.requireNonNull(address);
        return address.replace('/','.');
    }

}
