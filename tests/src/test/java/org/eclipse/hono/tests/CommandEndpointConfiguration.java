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


package org.eclipse.hono.tests;

import org.eclipse.hono.util.CommandConstants;

/**
 * Configuration properties for defining variants of Command &amp; Control
 * related test scenarios.
 *
 */
public class CommandEndpointConfiguration {

    private final boolean gatewayDevice;
    private final boolean legacySouthboundEndpoint;
    private final boolean legacyNorthboundEndpoint;

    /**
     * Creates a new configuration.
     * 
     * @param useGatewayDevice {@code true} if the device connecting to the adapter is a gateway.
     * @param useLegacySouthboundEndpoint {@code true} if the device uses the legacy command endpoint name.
     * @param useLegacyNorthboundEndpoint {@code true} if the application uses the legacy command endpoint name.
     */
    public CommandEndpointConfiguration(
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

    /**
     * Gets the name of the command endpoint used by devices.
     * 
     * @return The command endpoint name.
     */
    public final String getSouthboundEndpoint() {
        return legacySouthboundEndpoint ? CommandConstants.COMMAND_LEGACY_ENDPOINT : CommandConstants.COMMAND_ENDPOINT;
    }

    /**
     * Checks if the device connecting to the adapter is a gateway.
     * 
     * @return {@code true} if the device is a gateway.
     */
    public final boolean isGatewayDevice() {
        return gatewayDevice;
    }

    /**
     * Checks if applications use the legacy command endpoint.
     * 
     * @return {@code true} if applications send commands via the legacy endpoint.
     */
    public final boolean isLegacyNorthboundEndpoint() {
        return legacyNorthboundEndpoint;
    }

    /**
     * Gets the name of the endpoint that applications use for sending
     * commands to devices.
     * 
     * @return The endpoint name.
     */
    public final String getNorthboundEndpoint() {
        return legacyNorthboundEndpoint ? CommandConstants.COMMAND_LEGACY_ENDPOINT : CommandConstants.COMMAND_ENDPOINT;
    }

    /**
     * Gets the target address to use in command messages sent to devices.
     * 
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The device identifier.
     * @return The target address.
     */
    public final String getCommandMessageAddress(final String tenantId, final String deviceId) {

        return String.format("%s/%s/%s", getNorthboundEndpoint(), tenantId, deviceId);
    }

    /**
     * Gets the target address to use in a sender link for sending commands
     * to devices.
     * 
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The device identifier.
     * @return The target address.
     */
    public final String getSenderLinkTargetAddress(final String tenantId, final String deviceId) {

        if (legacyNorthboundEndpoint) {
            return String.format("%s/%s/%s", CommandConstants.COMMAND_LEGACY_ENDPOINT, tenantId, deviceId);
        } else {
            return String.format("%s/%s", CommandConstants.COMMAND_ENDPOINT, tenantId);
        }
    }
}
