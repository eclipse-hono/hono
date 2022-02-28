/*******************************************************************************
 * Copyright (c) 2020, 2021 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client.device.amqp;

import java.util.Map;

import io.vertx.core.Future;
import io.vertx.proton.ProtonDelivery;

/**
 * A client for sending telemetry messages to Hono's AMQP adapter.
 */
public interface TelemetrySender extends AmqpSenderLink {

    /**
     * Sends a telemetry message for a given device.
     *
     * @param deviceId The id of the device.
     * @param payload The data to send.
     *            <p>
     *            The payload will be contained in the message as an AMQP 1.0 <em>Data</em> section.
     * @param contentType The content type of the payload (may be {@code null}).
     *            <p>
     *            This parameter will be used as the value for the message's <em>content-type</em> property.
     * @param properties Optional application properties (may be {@code null}).
     *            <p>
     *            AMQP application properties that can be used for carrying data in the message other than the payload.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will succeed if the message has been sent to the peer. The delivery contained in the future
     *         will represent the delivery state at the time the future has been succeeded, i.e. it will be locally
     *         <em>unsettled</em> without any outcome yet.
     *         <p>
     *         The future will be failed with a {@link org.eclipse.hono.client.ServerErrorException} if the message
     *         could not be sent due to a lack of credit. If an event is sent which cannot be processed by the peer
     *         the future will be failed with either a {@code org.eclipse.hono.client.ServerErrorException} or a
     *         {@link org.eclipse.hono.client.ClientErrorException} depending on the reason for the
     *         failure to process the message.
     * @throws NullPointerException if any of device-id or payload is {@code null}.
     * @throws IllegalArgumentException if the properties contain a value of type list, map or array.
     */
    Future<ProtonDelivery> send(
            String deviceId,
            byte[] payload,
            String contentType,
            Map<String, ?> properties);

    /**
     * Sends a telemetry message for a given device and waits for the disposition indicating the outcome of the
     * transfer.
     *
     * @param deviceId The id of the device.
     * @param payload The data to send.
     *            <p>
     *            The payload will be contained in the message as an AMQP 1.0 <em>Data</em> section.
     * @param contentType The content type of the payload (may be {@code null}).
     *            <p>
     *            This parameter will be used as the value for the message's <em>content-type</em> property.
     * @param properties Optional application properties (may be {@code null}).
     *            <p>
     *            AMQP application properties that can be used for carrying data in the message other than the payload.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will succeed if the message has been accepted (and settled) by the peer.
     *         <p>
     *         The future will be failed with a {@link org.eclipse.hono.client.ServerErrorException} if the message
     *         could not be sent due to a lack of credit. If an event is sent which cannot be processed by the peer
     *         the future will be failed with either a {@code org.eclipse.hono.client.ServerErrorException} or a
     *         {@link org.eclipse.hono.client.ClientErrorException} depending on the reason for the
     *         failure to process the message.
     * @throws NullPointerException if any of device-id or payload is {@code null}.
     * @throws IllegalArgumentException if the properties contain a value of type list, map or array.
     */
    Future<ProtonDelivery> sendAndWaitForOutcome(
            String deviceId,
            byte[] payload,
            String contentType,
            Map<String, ?> properties);

}
