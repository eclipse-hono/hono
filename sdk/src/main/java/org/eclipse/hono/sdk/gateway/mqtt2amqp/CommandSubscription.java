/**
 * Copyright (c) 2018, 2020 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.hono.sdk.gateway.mqtt2amqp;

import java.util.Objects;

import io.netty.handler.codec.mqtt.MqttQoS;

/**
 * The MQTT subscription of devices, to get commands.
 *
 */
public class CommandSubscription {

    private final String topicFilter;
    private final MqttQoS qos;
    private final String clientId;

    /**
     * Creates a command subscription object for the given topic filter.
     *
     * @param topicFilter The topic filter in the subscription request from device.
     * @param qos The MQTT QoS of the subscription.
     * @param clientId The client identifier as provided by the remote MQTT client.
     * @throws NullPointerException if one of the arguments is {@code null}.
     * @throws IllegalArgumentException if the topic filter does not match the rules or any of the arguments is not
     *             valid.
     **/
    public CommandSubscription(final String topicFilter, final MqttQoS qos, final String clientId) {
        Objects.requireNonNull(topicFilter);
        Objects.requireNonNull(qos);
        Objects.requireNonNull(clientId);

        this.topicFilter = topicFilter;
        this.qos = qos;
        this.clientId = clientId;
    }

    /**
     * Gets the QoS of the subscription.
     *
     * @return The QoS value.
     */
    public MqttQoS getQos() {
        return qos;
    }

    /**
     * Gets the clientId of the Mqtt subscription.
     *
     * @return The clientId.
     */
    public String getClientId() {
        return clientId;
    }

    /**
     * Gets the subscription topic filter.
     *
     * @return The topic filter.
     */
    public String getTopicFilter() {
        return topicFilter;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final CommandSubscription that = (CommandSubscription) o;
        return topicFilter.equals(that.topicFilter) &&
                qos == that.qos &&
                clientId.equals(that.clientId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(topicFilter, qos, clientId);
    }
}
