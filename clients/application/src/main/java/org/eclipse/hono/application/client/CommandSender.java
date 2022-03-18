/**
 * Copyright (c) 2021, 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.application.client;

import java.time.Duration;

import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.util.Lifecycle;

import io.opentracing.SpanContext;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;

/**
 * A client for sending commands and receiving command responses.
 *
 * @param <T> The type of context that command response messages are being received in.
 * @see <a href="https://www.eclipse.org/hono/docs/api/command-and-control/">Command &amp; Control API for AMQP 1.0
 *      Specification</a>
 * @see <a href="https://www.eclipse.org/hono/docs/api/command-and-control-kafka/">Command &amp; Control API for Kafka
 *      Specification</a>
 */
public interface CommandSender<T extends MessageContext> extends Lifecycle {

    /**
     * Sends a request-response command to a device.
     * <p>
     * This method expects client code to already have established a messaging infrastructure specific response channel
     * corresponding to the given <em>reply ID</em>. It is also the client code's responsibility to correlate any
     * response message(s) sent by the device via that channel to the request message, e.g. by means of the given
     * <em>correlation ID</em>.
     * <p>
     * A device needs to be (successfully) registered before a client can upload any data for it. The device also needs
     * to be connected to one of Hono's protocol adapters in order for the command to be delivered successfully.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The device to send the command to.
     * @param command The command name.
     * @param correlationId The identifier to use for correlating the device's response to the request. The identifier
     *            should ideally be hard to guess in order to prevent malicious devices from creating false responses
     *            if the same response channel is shared by multiple devices. A good option is to use a
     *            {@linkplain java.util.UUID#randomUUID() UUID} as the correlation ID.
     * @param replyId An arbitrary string which will be used to create the <em>reply-to</em> address that is included
     *            in the command message sent to the device. The underlying messaging infrastructure specific Command
     *            &amp; Control implementation may ignore this value if it uses a fixed addressing scheme for command
     *            responses.
     * @param data The command's input data to send to the device or {@code null} if the command requires no input data.
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
    default Future<Void> sendAsyncCommand(
            final String tenantId,
            final String deviceId,
            final String command,
            final String correlationId,
            final String replyId,
            final Buffer data) {
        return sendAsyncCommand(tenantId, deviceId, command, correlationId, replyId, data, null);
    }

    /**
     * Sends a request-response command to a device.
     * <p>
     * A device needs to be (successfully) registered before a client can upload any data for it. The device also needs
     * to be connected to one of Hono's protocol adapters in order for the command to be delivered successfully.
     * <p>
     * This method expects client code to already have established a messaging infrastructure specific response channel
     * corresponding to the given <em>reply ID</em>. It is also the client code's responsibility to correlate any
     * response message(s) sent by the device via that channel to the request message, e.g. by means of the given
     * <em>correlation ID</em>.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The device to send the command to.
     * @param command The command name.
     * @param correlationId The identifier to use for correlating the device's response to the request. The identifier
     *            should ideally be hard to guess in order to prevent malicious devices from creating false responses
     *            if the same response channel is shared by multiple devices. A good option is to use a
     *            {@linkplain java.util.UUID#randomUUID() UUID} as the correlation ID.
     * @param replyId An arbitrary string which will be used to create the <em>reply-to</em> address that is included
     *            in the command message sent to the device. The underlying messaging infrastructure specific Command
     *            &amp; Control implementation may ignore this value if it uses a fixed addressing scheme for command
     *            responses.
     * @param data The command's input data to send to the device or {@code null} if the command requires no input data.
     * @param contentType The type of the data submitted as part of the command or {@code null} if unknown.
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
    default Future<Void> sendAsyncCommand(
            final String tenantId,
            final String deviceId,
            final String command,
            final String correlationId,
            final String replyId,
            final Buffer data,
            final String contentType) {
        return sendAsyncCommand(tenantId, deviceId, command, correlationId, replyId, data, contentType, null);
    }

    /**
     * Sends a request-response command to a device.
     * <p>
     * A device needs to be (successfully) registered before a client can upload any data for it. The device also needs
     * to be connected to one of Hono's protocol adapters in order for the command to be delivered successfully.
     * <p>
     * This method expects client code to already have established a messaging infrastructure specific response channel
     * corresponding to the given <em>reply ID</em>. It is also the client code's responsibility to correlate any
     * response message(s) sent by the device via that channel to the request message, e.g. by means of the given
     * <em>correlation ID</em>.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The device to send the command to.
     * @param command The command name.
     * @param correlationId The identifier to use for correlating the device's response to the request. The identifier
     *            should ideally be hard to guess in order to prevent malicious devices from creating false responses
     *            if the same response channel is shared by multiple devices. A good option is to use a
     *            {@linkplain java.util.UUID#randomUUID() UUID} as the correlation ID.
     * @param replyId An arbitrary string which will be used to create the <em>reply-to</em> address that is included
     *            in the command message sent to the device. The underlying messaging infrastructure specific Command
     *            &amp; Control implementation may ignore this value if it uses a fixed addressing scheme for command
     *            responses.
     * @param data The command's input data to send to the device or {@code null} if the command requires no input data.
     * @param contentType The type of the data submitted as part of the command or {@code null} if unknown.
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
            String replyId,
            Buffer data,
            String contentType,
            SpanContext context);

    /**
     * Sends a one-way command to a device.
     * <p>
     * A device needs to be (successfully) registered before a client can upload any data for it. The device also needs
     * to be connected to one of Hono's protocol adapters in order for the command to be delivered successfully.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The device to send the command to.
     * @param command The name of the command.
     * @param data The command's input data to send to the device or {@code null} if the command requires no input data.
     * @return A future indicating the outcome of sending the command message.
     *         <p>
     *         The future will be succeeded if the messaging infrastructure has accepted the command for delivery. Note
     *         that this does not necessarily mean that the device has received and/or processed the command. If it is
     *         important to know if the device has received/processed a command, then a request-response command should
     *         be used instead of a one-way command.
     *         <p>
     *         The future will fail with a {@link ServiceInvocationException} if the messaging infrastructure did not
     *         accept the command for delivery.
     * @throws NullPointerException if any of tenantId, deviceId or command are {@code null}.
     */
    default Future<Void> sendOneWayCommand(
            final String tenantId,
            final String deviceId,
            final String command,
            final Buffer data) {
        return sendOneWayCommand(tenantId, deviceId, command, data, null, null);
    }

    /**
     * Sends a one-way command to a device.
     * <p>
     * A device needs to be (successfully) registered before a client can upload any data for it. The device also needs
     * to be connected to one of Hono's protocol adapters in order for the command to be delivered successfully.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The device to send the command to.
     * @param command The name of the command.
     * @param data The command's input data to send to the device or {@code null} if the command requires no input data.
     * @param contentType The type of the data submitted as part of the one-way command or {@code null} if unknown.
     * @param context The currently active OpenTracing span context that is used to trace the execution of this
     *            operation or {@code null} if no span is currently active.
     * @return A future indicating the result of the operation:
     *         <p>
     *         If the one-way command was accepted, the future will succeed.
     *         <p>
     *         The future will fail with a {@link ServiceInvocationException} if the one-way command could 
     *         not be forwarded to the device.
     * @throws NullPointerException if any of tenantId, deviceId or command are {@code null}.
     */
    Future<Void> sendOneWayCommand(
            String tenantId,
            String deviceId,
            String command,
            Buffer data,
            String contentType,
            SpanContext context);

    /**
     * Sends a request-response command to a device.
     * <p>
     * A device needs to be (successfully) registered before a client can upload any data for it. The device also needs
     * to be connected to one of Hono's protocol adapters in order for the command to be delivered successfully.
     * <p>
     * Implementors are responsible for establishing/using a messaging infrastructure specific channel for receiving the
     * device's response within a reasonable amount of time.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The device to send the command to.
     * @param command The name of the command.
     * @param data The command's input data to send to the device or {@code null} if the command requires no input data.
     * @return A future indicating the result of the operation.
     *         <p>
     *         The future will be completed with the response message received from the device if it has a status code
     *         in the 2xx range.
     *         <p>
     *         Otherwise, the future will be failed with a {@link ServiceInvocationException} containing an error status
     *         code as defined in Hono's
     *         <a href="https://www.eclipse.org/hono/docs/api/command-and-control">Command and Control API</a>.
     * @throws NullPointerException if any of tenantId, deviceId or command are {@code null}.
     */
    default Future<DownstreamMessage<T>> sendCommand(
            final String tenantId,
            final String deviceId,
            final String command,
            final Buffer data) {
        return sendCommand(tenantId, deviceId, command, data, null);
    }

    /**
     * Sends a request-response command to a device.
     * <p>
     * A device needs to be (successfully) registered before a client can upload any data for it. The device also needs
     * to be connected to one of Hono's protocol adapters in order for the command to be delivered successfully.
     * <p>
     * Implementors are responsible for establishing/using a messaging infrastructure specific channel for receiving the
     * device's response within a reasonable amount of time.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The device to send the command to.
     * @param command The name of the command.
     * @param data The command's input data to send to the device or {@code null} if the command requires no input data.
     * @param contentType The type of the data submitted as part of the command or {@code null} if unknown.
     * @return A future indicating the result of the operation.
     *         <p>
     *         The future will be completed with the response message received from the device if it has a status code
     *         in the 2xx range.
     *         <p>
     *         Otherwise, the future will be failed with a {@link ServiceInvocationException} containing an error status
     *         code as defined in Hono's
     *         <a href="https://www.eclipse.org/hono/docs/api/command-and-control">Command and Control API</a>.
     * @throws NullPointerException if any of tenantId, deviceId or command are {@code null}.
     */
    default Future<DownstreamMessage<T>> sendCommand(
            final String tenantId,
            final String deviceId,
            final String command,
            final Buffer data,
            final String contentType) {
        return sendCommand(tenantId, deviceId, command, data, contentType, null, null, null);
    }

    /**
     * Sends a request-response command to a device.
     * <p>
     * A device needs to be (successfully) registered before a client can upload any data for it. The device also needs
     * to be connected to one of Hono's protocol adapters in order for the command to be delivered successfully.
     * <p>
     * Implementors are responsible for establishing/using a messaging infrastructure specific channel for receiving the
     * device's response within a reasonable amount of time.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The device to send the command to.
     * @param command The name of the command.
     * @param data The command's input data to send to the device or {@code null} if the command requires no input data.
     * @param contentType The type of the data submitted as part of the command or {@code null} if unknown.
     * @param replyId An arbitrary string which will be used to create the reply-to address to be included in commands
     *                sent to devices of the tenant. If the messaging network specific Command &amp; Control 
     *                implementation does not require a replyId, the specified value will be ignored.
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
    Future<DownstreamMessage<T>> sendCommand(
            String tenantId,
            String deviceId,
            String command,
            Buffer data,
            String contentType,
            String replyId,
            Duration timeout,
            SpanContext context);
}
