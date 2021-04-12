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

import io.opentracing.SpanContext;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;

/**
 * A client for sending asynchronous request response commands.
 * <p>
 * An instance of this interface is always scoped to a specific tenant and device.
 *
 * @deprecated Use {@code org.eclipse.hono.application.client.ApplicationClient} instead.
 */
@Deprecated
public interface AsyncCommandClient extends MessageSender {

    /**
     * Sends an async command to a device, i.e. there is no immediate response expected from the device, but
     * asynchronously via a separate consumer.
     * <p>
     * A device needs to be (successfully) registered before a client can upload any data for it. The device also needs
     * to be connected for a successful delivery.
     *
     * @param deviceId The device to send the command to.
     * @param command The command name.
     * @param data The command data to send to the device or {@code null} if the command has no input data.
     * @param correlationId The identifier to use for correlating the response with the request. Note: This parameter is
     *            security sensitive. To ensure secure request response mapping choose correlationId carefully, e.g.
     *            {@link java.util.UUID#randomUUID()}.
     * @param replyId An arbitrary string which gets used for the response link address in the form of
     *            <em>command_response/${tenantId}/${replyId}</em>. Must match the {@code replyId} passed to the
     *            command response receiver, see also below.
     * @return A future indicating the result of the operation.
     *         <p>
     *         If the command was accepted, the future will succeed.
     *         <p>
     *         The future will fail with a {@link ServiceInvocationException} if the command could not be forwarded to
     *         the device.
     * @throws NullPointerException if command, correlationId or replyId is {@code null}.
     * @see ApplicationClientFactory#createAsyncCommandResponseConsumer(String, String,
     *      java.util.function.Consumer, io.vertx.core.Handler)
     * @see ApplicationClientFactory#createAsyncCommandResponseConsumer(String, String,
     *      java.util.function.BiConsumer, io.vertx.core.Handler)
     */
    Future<Void> sendAsyncCommand(String deviceId, String command, Buffer data, String correlationId, String replyId);

    /**
     * Sends an async command to a device, i.e. there is no immediate response expected from the device, but
     * asynchronously via a separate consumer.
     * <p>
     * A device needs to be (successfully) registered before a client can upload any data for it. The device also needs
     * to be connected for a successful delivery.
     *
     * @param deviceId The device to send the command to.
     * @param command The command name.
     * @param contentType The type of the data submitted as part of the command or {@code null} if unknown.
     * @param data The command data to send to the device or {@code null} if the command has no input data.
     * @param correlationId The identifier to use for correlating the response with the request. Note: This parameter is
     *            security sensitive. To ensure secure request response mapping choose correlationId carefully, e.g.
     *            {@link java.util.UUID#randomUUID()}.
     * @param replyId An arbitrary string which gets used for response link address in the form of
     *            <em>command_response/${tenantId}/${replyId}</em>. Must match the {@code replyId} passed to the command
     *            response receiver, see also below.
     * @param properties The headers to include in the command message as AMQP application properties.
     * @return A future indicating the result of the operation.
     *         <p>
     *         If the command was accepted, the future will succeed.
     *         <p>
     *         The future will fail with a {@link ServiceInvocationException} if the command could not be forwarded to
     *         the device.
     * @throws NullPointerException if command, correlationId or replyId is {@code null}.
     * @see ApplicationClientFactory#createAsyncCommandResponseConsumer(String, String,
     *      java.util.function.Consumer, io.vertx.core.Handler)
     * @see ApplicationClientFactory#createAsyncCommandResponseConsumer(String, String,
     *      java.util.function.BiConsumer, io.vertx.core.Handler)
     */
    Future<Void> sendAsyncCommand(String deviceId, String command, String contentType, Buffer data, String correlationId, String replyId,
            Map<String, Object> properties);

    /**
     * Sends an async command to a device, i.e. there is no immediate response expected from the device, but
     * asynchronously via a separate consumer.
     * <p>
     * A device needs to be (successfully) registered before a client can upload any data for it. The device also needs
     * to be connected for a successful delivery.
     * <p>
     * This default implementation simply returns the result of {@link #sendAsyncCommand(String, String, String, Buffer, String, String, Map)}.
     *
     * @param deviceId The device to send the command to.
     * @param command The command name.
     * @param contentType The type of the data submitted as part of the command or {@code null} if unknown.
     * @param data The command data to send to the device or {@code null} if the command has no input data.
     * @param correlationId The identifier to use for correlating the response with the request. Note: This parameter is
     *            security sensitive. To ensure secure request response mapping choose correlationId carefully, e.g.
     *            {@link java.util.UUID#randomUUID()}.
     * @param replyId An arbitrary string which gets used for response link address in the form of
     *            <em>command_response/${tenantId}/${replyId}</em>. Must match the {@code replyId} passed to the command
     *            response receiver, see also below.
     * @param properties The headers to include in the command message as AMQP application properties.
     * @param context The currently active OpenTracing span. An implementation should use this as the parent for any
     *            span it creates for tracing the execution of this operation.
     * @return A future indicating the result of the operation.
     *         <p>
     *         If the command was accepted, the future will succeed.
     *         <p>
     *         The future will fail with a {@link ServiceInvocationException} if the command could not be forwarded to
     *         the device.
     * @throws NullPointerException if command, correlationId or replyId is {@code null}.
     * @see ApplicationClientFactory#createAsyncCommandResponseConsumer(String, String, java.util.function.Consumer,
     *      io.vertx.core.Handler)
     * @see ApplicationClientFactory#createAsyncCommandResponseConsumer(String, String, java.util.function.BiConsumer,
     *      io.vertx.core.Handler)
     */
    default Future<Void> sendAsyncCommand(final String deviceId, final String command, final String contentType,
            final Buffer data, final String correlationId, final String replyId, final Map<String, Object> properties,
            final SpanContext context) {
        return sendAsyncCommand(deviceId, command, contentType, data, correlationId, replyId, properties);
    }
}
