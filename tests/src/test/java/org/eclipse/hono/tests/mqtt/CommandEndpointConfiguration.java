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


package org.eclipse.hono.tests.mqtt;

import org.eclipse.hono.util.CommandConstants;

/**
 * A CommandEndpointConfiguration.
 *
 */
class CommandEndpointConfiguration {

    private final boolean gatewayDevice;
    private final boolean legacySouthboundEndpoint;
    private final boolean legacyNorthboundEndpoint;

    CommandEndpointConfiguration(
            final boolean useGatewayDevice,
            final boolean useLegacySouthboundEndpoint,
            final boolean useLegacyNorthboundEndpoint) {

        this.gatewayDevice = useGatewayDevice;
        this.legacySouthboundEndpoint = useLegacySouthboundEndpoint;
        this.legacyNorthboundEndpoint = useLegacyNorthboundEndpoint;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return String.format("gateway device: %s, southbound endpoint: %s, northbound endpoint: %s",
                gatewayDevice, getSouthboundEndpoint(), getNorthboundEndpoint());
    }

    String getSouthboundEndpoint() {
        return legacySouthboundEndpoint ? CommandConstants.COMMAND_LEGACY_ENDPOINT : CommandConstants.COMMAND_ENDPOINT;
    }

    boolean isGatewayDevice() {
        return gatewayDevice;
    }

    boolean isLegacyNorthboundEndpoint() {
        return legacyNorthboundEndpoint;
    }

    String getNorthboundEndpoint() {
        return legacyNorthboundEndpoint ? CommandConstants.COMMAND_LEGACY_ENDPOINT : CommandConstants.COMMAND_ENDPOINT;
    }

    String getCommandTopicFilter() {
        return String.format(
                "%s/%s/req/#", getSouthboundEndpoint(), "+/+");
    }

    String getCommandMessageAddress(final String tenantId, final String deviceId) {

        return String.format("%s/%s/%s", getNorthboundEndpoint(), tenantId, deviceId);
    }

    String getSenderLinkTargetAddress(final String tenantId, final String deviceId) {

        if (legacyNorthboundEndpoint) {
            return String.format("%s/%s/%s", CommandConstants.COMMAND_LEGACY_ENDPOINT, tenantId, deviceId);
        } else {
            return String.format("%s/%s", CommandConstants.COMMAND_ENDPOINT, tenantId);
        }
    }
}
