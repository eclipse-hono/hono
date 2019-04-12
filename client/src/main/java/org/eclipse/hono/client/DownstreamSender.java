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


package org.eclipse.hono.client;

import java.util.Map;

import io.vertx.core.Future;
import io.vertx.proton.ProtonDelivery;

/**
 * A client for sending messages to Hono's
 * south bound Telemetry and Event APIs.
 *
 */
public interface DownstreamSender extends MessageSender {

    /**
     * Sends a message for a given device to the endpoint configured for this client.
     * 
     * @param deviceId The id of the device.
     *                 <p>
     *                 This parameter will be used as the value for the message's application property <em>device_id</em>.
     * @param payload The data to send.
     *                <p>
     *                The payload's byte representation will be contained in the message as an AMQP 1.0
     *                <em>Data</em> section.
     * @param contentType The content type of the payload.
     *                    <p>
     *                    This parameter will be used as the value for the message's <em>content-type</em> property.
     *                    If the content type specifies a particular character set, this character set will be used to
     *                    encode the payload to its byte representation. Otherwise, UTF-8 will be used.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be succeeded if the message has been sent to the endpoint.
     *         The delivery contained in the future represents the delivery state at the time
     *         the future has been succeeded, i.e. for telemetry data it will be locally
     *         <em>unsettled</em> without any outcome yet. For events it will be locally
     *         and remotely <em>settled</em> and will contain the <em>accepted</em> outcome.
     *         <p>
     *         The future will be failed with a {@link ServerErrorException} if the message
     *         could not be sent due to a lack of credit.
     *         If an event is sent which cannot be processed by the peer the future will
     *         be failed with either a {@code ServerErrorException} or a {@link ClientErrorException}
     *         depending on the reason for the failure to process the message.
     * @throws NullPointerException if any of the parameters is {@code null}.
     * @throws IllegalArgumentException if the content type specifies an unsupported character set.
     */
    Future<ProtonDelivery> send(String deviceId, String payload, String contentType);

    /**
     * Sends a message for a given device to the endpoint configured for this client.
     * 
     * @param deviceId The id of the device.
     *                 <p>
     *                 This parameter will be used as the value for the message's application property <em>device_id</em>.
     * @param payload The data to send.
     *                <p>
     *                The payload will be contained in the message as an AMQP 1.0 <em>Data</em> section.
     * @param contentType The content type of the payload.
     *                    <p>
     *                    This parameter will be used as the value for the message's <em>content-type</em> property.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be succeeded if the message has been sent to the endpoint.
     *         The delivery contained in the future represents the delivery state at the time
     *         the future has been succeeded, i.e. for telemetry data it will be locally
     *         <em>unsettled</em> without any outcome yet. For events it will be locally
     *         and remotely <em>settled</em> and will contain the <em>accepted</em> outcome.
     *         <p>
     *         The future will be failed with a {@link ServerErrorException} if the message
     *         could not be sent due to a lack of credit.
     *         If an event is sent which cannot be processed by the peer the future will
     *         be failed with either a {@code ServerErrorException} or a {@link ClientErrorException}
     *         depending on the reason for the failure to process the message.
     * @throws NullPointerException if any of the parameters is {@code null}.
     * @throws IllegalArgumentException if the content type specifies an unsupported character set.
     */
    Future<ProtonDelivery> send(String deviceId, byte[] payload, String contentType);

    /**
     * Sends a message for a given device to the endpoint configured for this client.
     *
     * @param deviceId The id of the device.
     *                 <p>
     *                 This parameter will be used as the value for the message's application property <em>device_id</em>.
     * @param properties The application properties.
     *                   <p>
     *                   AMQP application properties that can be used for carrying data in the message other than the payload
     * @param payload The data to send.
     *                <p>
     *                The payload's byte representation will be contained in the message as an AMQP 1.0
     *                <em>Data</em> section.
     * @param contentType The content type of the payload.
     *                    <p>
     *                    This parameter will be used as the value for the message's <em>content-type</em> property.
     *                    If the content type specifies a particular character set, this character set will be used to
     *                    encode the payload to its byte representation. Otherwise, UTF-8 will be used.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be succeeded if the message has been sent to the endpoint.
     *         The delivery contained in the future represents the delivery state at the time
     *         the future has been succeeded, i.e. for telemetry data it will be locally
     *         <em>unsettled</em> without any outcome yet. For events it will be locally
     *         and remotely <em>settled</em> and will contain the <em>accepted</em> outcome.
     *         <p>
     *         The future will be failed with a {@link ServerErrorException} if the message
     *         could not be sent due to a lack of credit.
     *         If an event is sent which cannot be processed by the peer the future will
     *         be failed with either a {@code ServerErrorException} or a {@link ClientErrorException}
     *         depending on the reason for the failure to process the message.
     * @throws NullPointerException if any of device id, payload, content type or registration assertion
     *                              is {@code null}.
     * @throws IllegalArgumentException if the content type specifies an unsupported character set.
     */
    Future<ProtonDelivery> send(
            String deviceId,
            Map<String, ?> properties,
            String payload,
            String contentType);

    /**
     * Sends a message for a given device to the endpoint configured for this client.
     *
     * @param deviceId The id of the device.
     *                 <p>
     *                 This parameter will be used as the value for the message's application property <em>device_id</em>.
     * @param properties The application properties.
     *                   <p>
     *                   AMQP application properties that can be used for carrying data in the message other than the payload
     * @param payload The data to send.
     *                <p>
     *                The payload will be contained in the message as an AMQP 1.0 <em>Data</em> section.
     * @param contentType The content type of the payload.
     *                    <p>
     *                    This parameter will be used as the value for the message's <em>content-type</em> property.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be succeeded if the message has been sent to the endpoint.
     *         The delivery contained in the future represents the delivery state at the time
     *         the future has been succeeded, i.e. for telemetry data it will be locally
     *         <em>unsettled</em> without any outcome yet. For events it will be locally
     *         and remotely <em>settled</em> and will contain the <em>accepted</em> outcome.
     *         <p>
     *         The future will be failed with a {@link ServerErrorException} if the message
     *         could not be sent due to a lack of credit.
     *         If an event is sent which cannot be processed by the peer the future will
     *         be failed with either a {@code ServerErrorException} or a {@link ClientErrorException}
     *         depending on the reason for the failure to process the message.
     * @throws NullPointerException if any of device id, payload, content type or registration assertion is {@code null}.
     * @throws IllegalArgumentException if the content type specifies an unsupported character set.
     */
    Future<ProtonDelivery> send(
            String deviceId,
            Map<String, ?> properties,
            byte[] payload,
            String contentType);
}
