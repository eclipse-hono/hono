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
     * @param useGatewayDevice {@code true} if the device connecting to the adapter is a gateway.
     * @param useLegacySouthboundEndpoint {@code true} if the device uses the legacy command endpoint name.
     * @param useLegacyNorthboundEndpoint {@code true} if the application uses the legacy command endpoint name.
     */
    public AmqpCommandEndpointConfiguration(
            final boolean useGatewayDevice,
            final boolean useLegacySouthboundEndpoint,
            final boolean useLegacyNorthboundEndpoint) {

        super(useGatewayDevice, useLegacySouthboundEndpoint, useLegacyNorthboundEndpoint);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return String.format("gateway device: %s, southbound endpoint: %s, northbound endpoint: %s",
                isGatewayDevice(), getSouthboundEndpoint(), getNorthboundEndpoint());
    }

    /**
     * Gets the source address that devices use for subscribing to commands.
     * 
     * @return The address.
     */
    public final String getSubscriptionAddress() {
        return getSouthboundEndpoint();
    }
}
