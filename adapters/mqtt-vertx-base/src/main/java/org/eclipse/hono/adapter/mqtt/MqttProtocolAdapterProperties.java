/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
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

import org.eclipse.hono.config.ProtocolAdapterProperties;

/**
 * Properties for configuring an Mqtt based protocol adapter.
 *
 */
public class MqttProtocolAdapterProperties extends ProtocolAdapterProperties {

    /**
     * The default number of milliseconds to wait for PUBACK.
     */
    protected static final long DEFAULT_COMMAND_ACKNOWLEDGEMENT_TIME_OUT = 100L;
    private long commandAcknowledgementTimeOut = DEFAULT_COMMAND_ACKNOWLEDGEMENT_TIME_OUT;

    /**
     * Gets the waiting for acknowledgement time out in milliseconds for commands published with QoS 1.
     * <p>
     * This time out is used by the mqtt adapter for commands published with QoS 1. If there is no acknowledgement
     * within this time limit, then the command is settled with the the <em>released</em> outcome.
     * <p>
     * The default value is {@link #DEFAULT_COMMAND_ACKNOWLEDGEMENT_TIME_OUT}.
     *
     * @return The time out in milliseconds.
     */
    public final long getCommandAcknowledgementTimeOut() {
        return commandAcknowledgementTimeOut;
    }

    /**
     * Sets the waiting for acknowledgement time out in milliseconds for commands published with QoS 1.
     * <p>
     * This time out is used by the mqtt adapter for commands published with QoS 1. If there is no acknowledgement
     * within this time limit, then the command is settled with the the <em>released</em> outcome.
     * <p>
     *
     * @param commandAcknowledgementTimeOut The time out in milliseconds.
     */
    public final void setCommandAcknowledgementTimeOut(final long commandAcknowledgementTimeOut) {
        this.commandAcknowledgementTimeOut = commandAcknowledgementTimeOut;
    }
}
