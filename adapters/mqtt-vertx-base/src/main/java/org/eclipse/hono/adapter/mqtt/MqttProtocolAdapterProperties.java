/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter.mqtt;

import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.config.ProtocolAdapterProperties;

/**
 * Properties for configuring an MQTT based protocol adapter.
 *
 */
public class MqttProtocolAdapterProperties extends ProtocolAdapterProperties {

    /**
     * The default number of milliseconds to wait for PUBACK.
     */
    protected static final int DEFAULT_COMMAND_ACK_TIMEOUT = 100;

    private int commandAckTimeout = DEFAULT_COMMAND_ACK_TIMEOUT;
    private long sendMessageToDeviceTimeout;


    /**
     * Create new MQTT adapter properties with default values based on the given client configuration properties where
     * applicable.
     *
     * @param clientConfigProperties Client configuration properties whose values shall be set as default.
     */
    public MqttProtocolAdapterProperties(final ClientConfigProperties clientConfigProperties) {
        this.sendMessageToDeviceTimeout = clientConfigProperties.getSendMessageTimeout();
    }

    /**
     * Gets the waiting for acknowledgement time out in milliseconds for commands published with QoS 1.
     * <p>
     * This time out is used by the MQTT adapter for commands published with QoS 1. If there is no acknowledgement
     * within this time limit, then the command is settled with the <em>released</em> outcome.
     * <p>
     * The default value is {@link #DEFAULT_COMMAND_ACK_TIMEOUT}.
     *
     * @deprecated Use {@link #getSendMessageToDeviceTimeout()} instead.
     *
     * @return The time out in milliseconds.
     */
    @Deprecated(forRemoval = true)
    public final int getCommandAckTimeout() {
        return commandAckTimeout;
    }

    /**
     * Sets the waiting for acknowledgement time out in milliseconds for commands published with QoS 1.
     * <p>
     * This time out is used by the MQTT adapter for commands published with QoS 1. If there is no acknowledgement
     * within this time limit, then the command is settled with the <em>released</em> outcome.
     * <p>
     * The default value is {@link #DEFAULT_COMMAND_ACK_TIMEOUT}.
     *
     * @deprecated Use {@link #setSendMessageToDeviceTimeout(long)} ()} instead.
     *
     * @param timeout The time out in milliseconds.
     * @throws IllegalArgumentException if the timeout is negative.
     */
    @Deprecated(forRemoval = true)
    public final void setCommandAckTimeout(final int timeout) {
        if (timeout < 0) {
            throw new IllegalArgumentException("timeout must not be negative");
        }
        this.commandAckTimeout = timeout;
    }

    /**
     * Gets the waiting for acknowledgement time out in milliseconds for commands published with QoS 1.
     * <p>
     * This time out is used by the MQTT adapter for commands published with QoS 1. If there is no acknowledgement
     * within this time limit, then the command is settled with the <em>released</em> outcome.
     * <p>
     * The default value of this property is {@link ClientConfigProperties#getSendMessageTimeout()}.
     *
     * @return The time out in milliseconds.
     */
    public long getSendMessageToDeviceTimeout() {
        return sendMessageToDeviceTimeout;
    }

    /**
     * Sets the waiting for acknowledgement time out in milliseconds for commands published with QoS 1.
     * <p>
     * This time out is used by the MQTT adapter for commands published with QoS 1. If there is no acknowledgement
     * within this time limit, then the command is settled with the <em>released</em> outcome.
     * <p>
     * The default value of this property is {@link ClientConfigProperties#getSendMessageTimeout()}.
     *
     * @param sendMessageToDeviceTimeout The time out in milliseconds.
     * @throws IllegalArgumentException if the timeout is negative.
     */
    public void setSendMessageToDeviceTimeout(final long sendMessageToDeviceTimeout) {
        if (sendMessageToDeviceTimeout < 0) {
            throw new IllegalArgumentException("timeout must not be negative");
        }

        this.sendMessageToDeviceTimeout = sendMessageToDeviceTimeout;
    }
}
