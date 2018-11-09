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

package org.eclipse.hono.client;

import org.eclipse.hono.util.BufferResult;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;

import java.util.Map;

/**
 * A client for accessing Hono's Command and Control API.
 * <p>
 * An instance of this interface is always scoped to a specific tenant and device.
 * </p>
 */
public interface CommandClient extends RequestResponseClient {

    /**
     * Sends a command to a device and expects a response.
     * <p>
     * A device needs to be (successfully) registered before a client can upload
     * any data for it. The device also needs to be connected for a successful delivery.
     *
     * @param command The command name.
     * @param data The command data to send to the device or {@code null} if the command has no input data.
     * @return A future indicating the result of the operation.
     *         <p>
     *         The future will succeed if a response with status 2xx has been received from the device.
     *         If the response has no payload, the future will complete with {@code null}.
     *         <p>
     *         Otherwise, the future will fail with a {@link ServiceInvocationException} containing
     *         the (error) status code. Status codes are defined at <a href="https://www.eclipse.org/hono/api/command-and-control-api">Command and Control API</a>.
     * @throws NullPointerException if command is {@code null}.
     * @see RequestResponseClient#setRequestTimeout(long)
     */
    Future<BufferResult> sendCommand(String command, Buffer data);

    /**
     * Sends a command to a device and expects a response.
     * <p>
     * A device needs to be (successfully) registered before a client can upload
     * any data for it. The device also needs to be connected for a successful delivery.
     *
     * @param command The command name.
     * @param contentType The type of the data submitted as part of the command or {@code null} if unknown.
     * @param data The command data to send to the device or {@code null} if the command has no input data.
     * @param properties The headers to include in the command message as AMQP application properties.
     * @return A future indicating the result of the operation.
     *         <p>
     *         The future will succeed if a response with status 2xx has been received from the device. If the response has no payload, the future will complete with {@code null}.
     *         <p>
     *         Otherwise, the future will fail with a {@link ServiceInvocationException} containing
     *         the (error) status code. Status codes are defined at <a href="https://www.eclipse.org/hono/api/command-and-control-api">Command and Control API</a>.
     * @throws NullPointerException if command is {@code null}.
     * @see RequestResponseClient#setRequestTimeout(long)
     */
    Future<BufferResult> sendCommand(String command, String contentType, Buffer data, Map<String, Object> properties);

    /**
     * Sends a <em>one-way command</em> to a device, i.e. there is no response expected from the device.
     * <p>
     * A device needs to be (successfully) registered before a client can upload
     * any data for it. The device also needs to be connected for a successful delivery.
     *
     * @param command The one-way command name.
     * @param data The command data to send to the device or {@code null} if the one-way command has no input data.
     * @return A future indicating the result of the operation.
     *         <p>
     *         If the one-way command was accepted, the future will succeed.
     *         <p>
     *         The future will fail with a {@link ServiceInvocationException} if the one-way command could not be forwarded to the device.
     * @throws NullPointerException if command is {@code null}.
     * @see RequestResponseClient#setRequestTimeout(long)
     */
    Future<Void> sendOneWayCommand(String command, Buffer data);

    /**
     * Sends a <em>one-way command</em> to a device, i.e. there is no response from the device expected.
     * <p>
     * A device needs to be (successfully) registered before a client can upload
     * any data for it. The device also needs to be connected for a successful delivery.
     *
     * @param command The one-way command name.
     * @param contentType The type of the data submitted as part of the one-way command or {@code null} if unknown.
     * @param data The command data to send to the device or {@code null} if the command has no input data.
     * @param properties The headers to include in the one-way command message as AMQP application properties.
     * @return A future indicating the result of the operation:
     *         <p>
     *         If the one-way command was accepted, the future will succeed.
     *         <p>
     *         The future will fail with a {@link ServiceInvocationException} if the one-way command could not be forwarded to the device.
     * @throws NullPointerException if command is {@code null}.
     * @see RequestResponseClient#setRequestTimeout(long)
     */
    Future<Void> sendOneWayCommand(String command, String contentType, Buffer data, Map<String, Object> properties);
}
