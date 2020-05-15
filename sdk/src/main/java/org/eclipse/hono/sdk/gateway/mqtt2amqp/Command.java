/*******************************************************************************
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.sdk.gateway.mqtt2amqp;

import java.util.Objects;

import io.vertx.core.buffer.Buffer;

/**
 * The command message that will be published via MQTT to a device.
 * <p>
 * Devices can use wildcards in the topic filter, so there is no easy way to uniquely identify the corresponding
 * subscription and thus the required Qos. To ensure this, the topic filter must match exactly the one the device has
 * subscribed to.
 */
public class Command {

    private final String topic;
    private final String topicFilter;
    private final Buffer payload;

    /**
     * Creates an instance.
     * 
     * @param topic The topic on which the command should be sent to the device.
     * @param topicFilter The topic filter to which the device has subscribed.
     * @param payload The payload of the command.
     */
    public Command(final String topic, final String topicFilter, final Buffer payload) {
        Objects.requireNonNull(topic);
        Objects.requireNonNull(topicFilter);
        Objects.requireNonNull(payload);

        this.topic = topic;
        this.topicFilter = topicFilter;
        this.payload = payload;
    }

    /**
     * Gets the topic on which the command should be sent to the device.
     * 
     * @return The topic.
     */
    public String getTopic() {
        return topic;
    }

    /**
     * Returns the topic filter to which the device has subscribed.
     * 
     * @return The topic filter.
     */
    public String getTopicFilter() {
        return topicFilter;
    }

    /**
     * Returns the payload to be published to the device.
     * 
     * @return The payload.
     */
    public Buffer getPayload() {
        return payload;
    }

}
