/**
 * Copyright (c) 2019, 2022 Contributors to the Eclipse Foundation
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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import org.eclipse.hono.tests.CommandEndpointConfiguration;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.ResourceIdentifier;

/**
 * Configuration properties for defining variants of Command &amp; Control
 * related test scenarios.
 *
 */
public class MqttCommandEndpointConfiguration extends CommandEndpointConfiguration {

    /**
     * Creates a new configuration.
     *
     * @param subscriberRole The way in which to subscribe for commands.
     */
    public MqttCommandEndpointConfiguration(final SubscriberRole subscriberRole) {

        super(subscriberRole);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return String.format(
                "subscribe as: %s, southbound endpoint: %s, northbound endpoint: %s, topic filter: %s",
                getSubscriberRole(),
                getSouthboundEndpoint(),
                getNorthboundEndpoint(),
                getCommandTopicFilter("${deviceId}"));
    }

    /**
     * Gets the topic filter that devices use for subscribing to commands.
     *
     * @param deviceId The device id to subscribe to if subscribing as a gateway for commands to a single device.
     *                 May be {@code null} otherwise.
     * @return The filter.
     * @throws IllegalStateException if the subscriber role is unsupported.
     */
    public final String getCommandTopicFilter(final String deviceId) {
        switch (getSubscriberRole()) {
        case DEVICE:
            return String.format("%s///req/#", getSouthboundEndpoint());
        case GATEWAY_FOR_ALL_DEVICES:
            return String.format("%s//+/req/#", getSouthboundEndpoint());
        case GATEWAY_FOR_SINGLE_DEVICE:
            return String.format("%s//%s/req/#", getSouthboundEndpoint(), deviceId);
        default:
            throw new IllegalStateException("unknown role");
        }
    }

    /**
     * Gets the name of the topic that a device uses for publishing the response to a command.
     *
     * @param msgNo The response message number.
     * @param deviceId The identifier of the device.
     * @param requestId The request identifier from the command.
     * @param status The status code indicating the outcome of processing the command.
     * @return The topic name.
     */
    public final String getResponseTopic(
            final int msgNo,
            final String deviceId,
            final String requestId,
            final int status) {
        final boolean useShortNames = msgNo % 2 == 0;
        final var ep = useShortNames ? CommandConstants.COMMAND_ENDPOINT_SHORT : CommandConstants.COMMAND_ENDPOINT;
        final var resSegment = useShortNames ? "s" : "res";
        if (isSubscribeAsGateway()) {
            return String.format("%s//%s/%s/%s/%d",
                    ep,
                    deviceId,
                    resSegment,
                    requestId,
                    status);
        }
        return String.format("%s///%s/%s/%d", ep, resSegment, requestId, status);
    }

    void assertCommandPublishTopicStructure(
            final ResourceIdentifier topic,
            final String expectedCommandTarget,
            final boolean isOneWayCommand,
            final String expectedCommandName) {

        assertThat(topic).isNotNull();

        assertWithMessage("command topic endpoint")
                .that(topic.getEndpoint())
                .isEqualTo(getSouthboundEndpoint());

        assertWithMessage("command topic tenant ID")
                .that(topic.getTenantId())
                .isNull();


        switch (getSubscriberRole()) {
        case UNAUTHENTICATED_DEVICE:
            // TODO: anything to assert in this case?
            break;
        case DEVICE:
            assertWithMessage("command topic device ID")
                    .that(topic.getResourceId())
                    .isNull();
            break;
        case GATEWAY_FOR_ALL_DEVICES:
            // fall through
        case GATEWAY_FOR_SINGLE_DEVICE:
            assertWithMessage("command topic device ID")
                    .that(topic.getResourceId())
                    .isEqualTo(expectedCommandTarget);
            break;
        }

        if (isOneWayCommand) {
            assertWithMessage("request ID part of one-way command topic")
                    .that(topic.elementAt(4))
                    .isNull();
        } else {
            assertWithMessage("request ID part of command topic")
                    .that(topic.elementAt(4))
                    .isNotNull();
        }

        assertWithMessage("command name in command topic")
                .that(topic.elementAt(5))
                .isEqualTo(expectedCommandName);
    }
}
