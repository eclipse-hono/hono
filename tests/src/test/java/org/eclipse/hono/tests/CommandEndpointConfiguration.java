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

    private final SubscriberRole subscriberRole;
    private final boolean legacySouthboundEndpoint;
    private final boolean legacyNorthboundEndpoint;

    /**
     * Defines the different ways in which to subscribe for commands.
     */
    public enum SubscriberRole {
        /**
         * Subscribe as device.
         */
        DEVICE,
        /**
         * Subscribe as gateway for all devices connected to the gateway.
         */
        GATEWAY_FOR_ALL_DEVICES,
        /**
         * Subscribe as gateway for a single device connected to the gateway.
         */
        GATEWAY_FOR_SINGLE_DEVICE
    }

    /**
     * Creates a new configuration.
     * 
     * @param subscriberRole The way in which to subscribe for commands.
     * @param useLegacySouthboundEndpoint {@code true} if the device uses the legacy command endpoint name.
     * @param useLegacyNorthboundEndpoint {@code true} if the application uses the legacy command endpoint name.
     */
    public CommandEndpointConfiguration(
            final SubscriberRole subscriberRole,
            final boolean useLegacySouthboundEndpoint,
            final boolean useLegacyNorthboundEndpoint) {

        this.subscriberRole = subscriberRole;
        this.legacySouthboundEndpoint = useLegacySouthboundEndpoint;
        this.legacyNorthboundEndpoint = useLegacyNorthboundEndpoint;
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
     * Gets the name of the command endpoint used by devices.
     * 
     * @return The command endpoint name.
     */
    public final String getSouthboundEndpoint() {
        return legacySouthboundEndpoint ? CommandConstants.COMMAND_LEGACY_ENDPOINT : CommandConstants.COMMAND_ENDPOINT;
    }

    /**
     * Gets the way in which to subscribe for commands.
     *
     * @return The subscriber role.
     */
    public SubscriberRole getSubscriberRole() {
        return subscriberRole;
    }

    /**
     * Checks whether command subscription shall be done as a gateway.
     *
     * @return {@code true} if to subscribe as a gateway.
     */
    public boolean isSubscribeAsGateway() {
        return subscriberRole == SubscriberRole.GATEWAY_FOR_ALL_DEVICES
                || subscriberRole == SubscriberRole.GATEWAY_FOR_SINGLE_DEVICE;
    }

    /**
     * Checks whether command subscription shall be done as a gateway for all connected devices.
     *
     * @return {@code true} if to subscribe as a gateway for all connected devices.
     */
    public boolean isSubscribeAsGatewayForAllDevices() {
        return subscriberRole == SubscriberRole.GATEWAY_FOR_ALL_DEVICES;
    }

    /**
     * Checks whether command subscription shall be done as a gateway on behalf of a single device.
     *
     * @return {@code true} if to subscribe as a gateway on behalf of a single device.
     */
    public boolean isSubscribeAsGatewayForSingleDevice() {
        return subscriberRole == SubscriberRole.GATEWAY_FOR_SINGLE_DEVICE;
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
