/**
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.tests.amqp;

import org.eclipse.hono.tests.CommandEndpointConfiguration;

/**
 * Configuration properties for defining variants of Command &amp; Control
 * related test scenarios.
 *
 */
public class AmqpCommandEndpointConfiguration extends CommandEndpointConfiguration {

    /**
     * Creates a new configuration.
     *
     * @param subscriberRole The way in which to subscribe for commands.
     */
    public AmqpCommandEndpointConfiguration(final SubscriberRole subscriberRole) {
        super(subscriberRole);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return String.format("subscribe as: %s, southbound endpoint: %s, northbound endpoint: %s",
                getSubscriberRole(), getSouthboundEndpoint(), getNorthboundEndpoint());
    }

    /**
     * Gets the source address that devices use for subscribing to commands.
     *
     * @param tenantId The tenant identifier.
     * @param deviceId The device id to subscribe to if subscribing as a gateway for commands to a single device.
     *                 May be {@code null} otherwise.
     * @return The address.
     */
    public final String getSubscriptionAddress(final String tenantId, final String deviceId) {
        if (isSubscribeAsGatewayForSingleDevice()) {
            return getSouthboundEndpoint() + "/" + tenantId + "/" + deviceId;
        }
        return getSouthboundEndpoint();
    }
}
