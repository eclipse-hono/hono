/**
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.application.client.kafka;

import java.time.Duration;
import java.util.Map;

import org.eclipse.hono.application.client.CommandSender;
import org.eclipse.hono.application.client.DownstreamMessage;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.client.kafka.KafkaRecordHelper;

import io.opentracing.SpanContext;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;


/**
 * A client for sending commands via a Kafka broker.
 *
 */
public interface KafkaCommandSender extends CommandSender<KafkaMessageContext> {

    /**
     * Sends a request-response command to a device.
     * <p>
     * A device needs to be (successfully) registered before a client can upload any data for it. The device also needs
     * to be connected to one of Hono's protocol adapters in order for the command to be delivered successfully.
     * <p>
     * This method expects a tenant specific response Kafka topic as defined by Hono's
     * <a href="https://www.eclipse.org/hono/docs/api/command-and-control-kafka/#send-a-requestresponse-command">Kafka
     * based Command &amp; Control API</a> to exist.
     * It is the client code's responsibility to correlate any response message(s) sent by the device via that topic
     * to the request message, e.g. by means of the given <em>correlation ID</em>.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The device to send the command to.
     * @param command The command name.
     * @param correlationId The identifier to use for correlating the device's response to the request. The identifier
     *            should ideally be hard to guess in order to prevent malicious devices from creating false responses
     *            if the same response channel is shared by multiple devices. A good option is to use a
     *            {@linkplain java.util.UUID#randomUUID() UUID} as the correlation ID.
     * @param data The command's input data to send to the device or {@code null} if the command requires no input data.
     * @param contentType The type of the data submitted as part of the command or {@code null} if unknown.
     * @param failureNotificationMetadata Additional key/value pairs that should be included as headers in a response
     *         message indicating a failure to forward the command to the device. The keys included in the response will
     *         be prefixed with {@value KafkaRecordHelper#DELIVERY_FAILURE_NOTIFICATION_METADATA_PREFIX}{@code .},
     *         the values will be copied verbatim. This property may be {@code null}.
     * @param context The currently active OpenTracing span context that is used to trace the execution of this
     *            operation or {@code null} if no span is currently active.
     * @return A future indicating the outcome of sending the command message.
     *         <p>
     *         The future will be succeeded if the messaging infrastructure has accepted the command for delivery. Note
     *         that this does not necessarily mean that the device has received and/or processed the command. The latter
     *         can only be safely determined based on the device's response message.
     *         <p>
     *         The future will fail with a {@link ServiceInvocationException} if the messaging infrastructure did not
     *         accept the command for delivery.
     * @throws NullPointerException if tenant ID, device ID, command, correlation ID or reply ID are {@code null}.
     */
    Future<Void> sendAsyncCommand(
            String tenantId,
            String deviceId,
            String command,
            String correlationId,
            Buffer data,
            String contentType,
            Map<String, Object> failureNotificationMetadata,
            SpanContext context);

    /**
     * Sends a request-response command to a device.
     * <p>
     * A device needs to be (successfully) registered before a client can upload any data for it. The device also needs
     * to be connected to one of Hono's protocol adapters in order for the command to be delivered successfully.
     * <p>
     * This method expects a tenant specific response Kafka topic as defined by Hono's
     * <a href="https://www.eclipse.org/hono/docs/api/command-and-control-kafka/#send-a-requestresponse-command">Kafka
     * based Command &amp; Control API</a> to exist.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The device to send the command to.
     * @param command The name of the command.
     * @param data The command's input data to send to the device or {@code null} if the command requires no input data.
     * @param contentType The type of the data submitted as part of the command or {@code null} if unknown.
     * @param failureNotificationMetadata Additional key/value pairs that should be included as headers in a response
     *         message indicating a failure to forward the command to the device. The keys included in the response will
     *         be prefixed with {@value KafkaRecordHelper#DELIVERY_FAILURE_NOTIFICATION_METADATA_PREFIX}{@code .},
     *         the values will be copied verbatim. This property may be {@code null}.
     * @param timeout The duration after which the send command request times out or {@code null} if a default timeout
     *                should be used. If the duration is set to 0 then the command request will not time out at all.
     * @param context The currently active OpenTracing span context that is used to trace the execution of this
     *            operation or {@code null} if no span is currently active.
     * @return A future indicating the result of the operation.
     *         <p>
     *         The future will be completed with the response message received from the device if it has a status code
     *         in the 2xx range.
     *         <p>
     *         Otherwise, the future will be failed with a {@link ServiceInvocationException} containing an error status
     *         code as defined in Hono's
     *         <a href="https://www.eclipse.org/hono/docs/api/command-and-control">Command and Control API</a>.
     * @throws NullPointerException if any of tenantId, deviceId or command are {@code null}.
     * @throws IllegalArgumentException if the timeout's duration is negative.
     */
    Future<DownstreamMessage<KafkaMessageContext>> sendCommand(
            String tenantId,
            String deviceId,
            String command,
            Buffer data,
            String contentType,
            Map<String, Object> failureNotificationMetadata,
            Duration timeout,
            SpanContext context);
}
