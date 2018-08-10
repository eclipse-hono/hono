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
     * @return A future indicating the result of the operation.
     *         <p>
     *         The future will succeed if a response with status 2xx has been received from the device. If the response has no payload, the future will complete with {@code null}.
     *         <p>
     *         Otherwise, the future will fail with a {@link ServiceInvocationException} containing
     *         the (error) status code. Status codes are defined at <a href="https://www.eclipse.org/hono/api/command-and-control-api">Command and Control API</a>.
     * @throws NullPointerException if command is {@code null}.
     * @see RequestResponseClient#setRequestTimeout(long)
     */
    Future<BufferResult> sendCommand(String command, String contentType, Buffer data);
}
