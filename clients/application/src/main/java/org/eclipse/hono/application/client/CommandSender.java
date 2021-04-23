/**
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
import java.util.Map;

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
     * Sends an async command to a device, i.e. there is no immediate response expected from the device, but
     * asynchronously via a separate consumer.
     * <p>
     * A device needs to be (successfully) registered before a client can upload any data for it. The device also needs
     * to be connected for a successful delivery.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The device to send the command to.
     * @param command The command name.
     * @param data The command data to send to the device or {@code null} if the command has no input data.
     * @param correlationId The identifier to use for correlating the response with the request. Note: This parameter is
     *            security sensitive. To ensure secure request response mapping choose correlationId carefully, e.g.
     *            {@link java.util.UUID#randomUUID()}.
     * @param replyId An arbitrary string which will be used to create the reply-to address to be included in commands
     *            sent to devices of the tenant. If the messaging network specific Command &amp; Control implementation does
     *            not require a replyId, the specified value will be ignored.
     * @return A future indicating the result of the operation.
     *         <p>
     *         If the command was accepted, the future will succeed.
     *         <p>
     *         The future will fail with a {@link ServiceInvocationException} if the command could not be forwarded
     *         upstream.
     * @throws NullPointerException if tenantId, deviceId, command or correlationId is {@code null}.
     *                              Also if the replyId is {@code null} provided that the messaging 
     *                              network specific Command &amp; Control implementation requires it.
     */
    default Future<Void> sendAsyncCommand(
            final String tenantId,
            final String deviceId,
            final String command,
            final Buffer data,
            final String correlationId,
            final String replyId) {
        return sendAsyncCommand(tenantId, deviceId, command, null, data, correlationId, replyId, null);
    }

    /**
     * Sends an async command to a device, i.e. there is no immediate response expected from the device, but
     * asynchronously via a separate consumer.
     * <p>
     * A device needs to be (successfully) registered before a client can upload any data for it. The device also needs
     * to be connected for a successful delivery.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The device to send the command to.
     * @param command The command name.
     * @param contentType The type of the data submitted as part of the command or {@code null} if unknown.
     * @param data The command data to send to the device or {@code null} if the command has no input data.
     * @param correlationId The identifier to use for correlating the response with the request. Note: This parameter is
     *            security sensitive. To ensure secure request response mapping choose correlationId carefully, e.g.
     *            {@link java.util.UUID#randomUUID()}.
     * @param replyId An arbitrary string which will be used to create the reply-to address to be included in commands
     *            sent to devices of the tenant. If the messaging network specific Command &amp; Control implementation does
     *            not require a replyId, the specified value will be ignored.
     * @param properties The headers to include in the command message.
     * @return A future indicating the result of the operation.
     *         <p>
     *         If the command was accepted, the future will succeed.
     *         <p>
     *         The future will fail with a {@link ServiceInvocationException} if the command could not be forwarded
     *         upstream.
     * @throws NullPointerException if tenantId, deviceId, command or correlationId is {@code null}.
     *                              Also if the replyId is {@code null} provided that the messaging 
     *                              network specific Command &amp; Control implementation requires it.
     */
    default Future<Void> sendAsyncCommand(
            final String tenantId,
            final String deviceId,
            final String command,
            final String contentType,
            final Buffer data,
            final String correlationId,
            final String replyId,
            final Map<String, Object> properties) {
        return sendAsyncCommand(tenantId, deviceId, command, contentType, data, correlationId, replyId, properties,
                null);
    }

    /**
     * Sends an async command to a device, i.e. there is no immediate response expected from the device, but
     * asynchronously via a separate consumer.
     * <p>
     * A device needs to be (successfully) registered before a client can upload any data for it. The device also needs
     * to be connected for a successful delivery.
     * <p>
     *
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The device to send the command to.
     * @param command The command name.
     * @param contentType The type of the data submitted as part of the command or {@code null} if unknown.
     * @param data The command data to send to the device or {@code null} if the command has no input data.
     * @param correlationId The identifier to use for correlating the response with the request. Note: This parameter is
     *            security sensitive. To ensure secure request response mapping choose correlationId carefully, e.g.
     *            {@link java.util.UUID#randomUUID()}.
     * @param replyId An arbitrary string which will be used to create the reply-to address to be included in commands
     *            sent to devices of the tenant. If the messaging network specific Command &amp; Control implementation does
     *            not require a replyId, the specified value will be ignored.
     * @param properties The headers to include in the command message.
     * @param context The currently active OpenTracing span context that is used to trace the execution of this
     *            operation or {@code null} if no span is currently active.
     * @return A future indicating the result of the operation.
     *         <p>
     *         If the command was accepted, the future will succeed.
     *         <p>
     *         The future will fail with a {@link ServiceInvocationException} if the command could not be forwarded
     *         upstream.
     * @throws NullPointerException if tenantId, deviceId, command or correlationId is {@code null}.
     *                              Also if the replyId is {@code null} provided that the messaging 
     *                              network specific Command &amp; Control implementation requires it.
     */
    Future<Void> sendAsyncCommand(
            String tenantId,
            String deviceId,
            String command,
            String contentType,
            Buffer data,
            String correlationId,
            String replyId,
            Map<String, Object> properties,
            SpanContext context);

    /**
     * Sends a <em>one-way command</em> to a device, i.e. there is no response expected from the device.
     * <p>
     * A device needs to be (successfully) registered before a client can upload
     * any data for it. The device also needs to be connected to a protocol adapter
     * and needs to have indicated its intent to receive commands.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The device to send the command to.
     * @param command The name of the command.
     * @param data The input data to the command or {@code null} if the command has no input data.
     * @return A future indicating the result of the operation.
     *         <p>
     *         If the one-way command was accepted, the future will succeed.
     *         <p>
     *         The future will fail with a {@link ServiceInvocationException} if the one-way command could 
     *         not be forwarded to the device.
     * @throws NullPointerException if any of tenantId, deviceId or command are {@code null}.
     */
    default Future<Void> sendOneWayCommand(
            final String tenantId,
            final String deviceId,
            final String command,
            final Buffer data) {
        return sendOneWayCommand(tenantId, deviceId, command, null, data, null, null);
    }

    /**
     * Sends a <em>one-way command</em> to a device, i.e. there is no response from the device expected.
     * <p>
     * A device needs to be (successfully) registered before a client can upload
     * any data for it. The device also needs to be connected to a protocol adapter
     * and needs to have indicated its intent to receive commands.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The device to send the command to.
     * @param command The name of the command.
     * @param contentType The type of the data submitted as part of the one-way command or {@code null} if unknown.
     * @param data The input data to the command or {@code null} if the command has no input data.
     * @param properties The headers to include in the one-way command message.
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
            String contentType,
            Buffer data,
            Map<String, Object> properties,
            SpanContext context);

    /**
     * Sends a command to a device and expects a response.
     * <p>
     * A device needs to be (successfully) registered before a client can upload
     * any data for it. The device also needs to be connected to a protocol adapter
     * and needs to have indicated its intent to receive commands.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The device to send the command to.
     * @param command The name of the command.
     * @param data The command data to send to the device or {@code null} if the command has no input data.
     * @return A future indicating the result of the operation.
     *         <p>
     *         The future will succeed if a response with status 2xx has been received from the device.
     *         If the response has no payload, the future will complete with a DownstreamMessage that has a {@code null} payload.
     *         <p>
     *         Otherwise, the future will fail with a {@link ServiceInvocationException} containing
     *         the (error) status code. Status codes are defined at
     *         <a href="https://www.eclipse.org/hono/docs/api/command-and-control">Command and Control API</a>.
     * @throws NullPointerException if any of tenantId, deviceId or command are {@code null}.
     */
    default Future<DownstreamMessage<T>> sendCommand(
            final String tenantId,
            final String deviceId,
            final String command,
            final Buffer data) {
        return sendCommand(tenantId, deviceId, command, null, data, null);
    }

    /**
     * Sends a command to a device and expects a response.
     * <p>
     * A device needs to be (successfully) registered before a client can upload
     * any data for it. The device also needs to be connected to a protocol adapter
     * and needs to have indicated its intent to receive commands.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The device to send the command to.
     * @param command The name of the command.
     * @param contentType The type of the data submitted as part of the command or {@code null} if unknown.
     * @param data The command data to send to the device or {@code null} if the command has no input data.
     * @param properties The headers to include in the command message.
     * @return A future indicating the result of the operation.
     *         <p>
     *         The future will succeed if a response with status 2xx has been received from the device.
     *         If the response has no payload, the future will complete with a DownstreamMessage that has a {@code null} payload.
     *         <p>
     *         Otherwise, the future will fail with a {@link ServiceInvocationException} containing
     *         the (error) status code. Status codes are defined at 
     *         <a href="https://www.eclipse.org/hono/docs/api/command-and-control">Command and Control API</a>.
     * @throws NullPointerException if any of tenantId, deviceId or command are {@code null}.
     */
    default Future<DownstreamMessage<T>> sendCommand(
            final String tenantId,
            final String deviceId,
            final String command,
            final String contentType,
            final Buffer data,
            final Map<String, Object> properties) {
        return sendCommand(tenantId, deviceId, command, contentType, data, null, properties, null, null);
    }

    /**
     * Sends a command to a device and expects a response.
     * <p>
     * A device needs to be (successfully) registered before a client can upload
     * any data for it. The device also needs to be connected to a protocol adapter
     * and needs to have indicated its intent to receive commands.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The device to send the command to.
     * @param command The name of the command.
     * @param contentType The type of the data submitted as part of the command or {@code null} if unknown.
     * @param data The command data to send to the device or {@code null} if the command has no input data.
     * @param replyId An arbitrary string which will be used to create the reply-to address to be included in commands
     *                sent to devices of the tenant. If the messaging network specific Command &amp; Control 
     *                implementation does not require a replyId, the specified value will be ignored.
     * @param properties The headers to include in the command message.
     * @param timeout The duration after which the send command request times out.
     * @param context The currently active OpenTracing span context that is used to trace the execution of this
     *            operation or {@code null} if no span is currently active.
     * @return A future indicating the result of the operation.
     *         <p>
     *         The future will succeed if a response with status 2xx has been received from the device.
     *         If the response has no payload, the future will complete with a DownstreamMessage that has a {@code null} payload.
     *         <p>
     *         Otherwise, the future will fail with a {@link ServiceInvocationException} containing
     *         the (error) status code. Status codes are defined at 
     *         <a href="https://www.eclipse.org/hono/docs/api/command-and-control">Command and Control API</a>.
     * @throws NullPointerException if any of tenantId, deviceId or command are {@code null}.
     */
    Future<DownstreamMessage<T>> sendCommand(
            String tenantId,
            String deviceId,
            String command,
            String contentType,
            Buffer data,
            String replyId,
            Map<String, Object> properties,
            Duration timeout,
            SpanContext context);
}
