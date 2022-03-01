/*******************************************************************************
 * Copyright (c) 2020, 2022 Contributors to the Eclipse Foundation
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

import org.eclipse.hono.client.command.Command;
import org.eclipse.hono.util.ExecutionContext;
import org.eclipse.hono.util.RegistrationAssertion;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;

/**
 * A service for either processing messages uploaded by devices before they are being
 * forwarded downstream or mapping payload for commands to be sent to devices.
 *
 * @param <T> The type of execution context supported by this mapping service.
 */
public interface MessageMapping<T extends ExecutionContext> {

    /**
     * Maps a message uploaded by a device.
     * <p>
     * If a mapping service is not configured for a gateway or a protocol adapter, this method returns
     * a successful future containing the original device payload and target address. If a mapping service is configured for a gateway
     * or a protocol adapter and the mapping service returns a 200 OK HTTP status code, then this method returns a successful
     * future containing the mapped payload.
     * <p>
     * For all other 2XX HTTP status codes, this method returns a mapped message containing the original device payload and 
     * target address. In all other cases, this method returns a failed future with a {@link org.eclipse.hono.client.ServiceInvocationException}.
     *
     * @param ctx              The context in which the message has been uploaded.
     * @param tenantId         The tenant that the device that uploaded the message belongs to.
     * @param registrationInfo The information included in the registration assertion for
     *                         the device that has uploaded the message.
     * @return                 A successful future containing the mapped message.
     *                         Otherwise, the future will be failed with a {@link org.eclipse.hono.client.ServiceInvocationException}
     *                         if the message could not be mapped.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    Future<MappedMessage> mapDownstreamMessage(
            T ctx,
            String tenantId,
            RegistrationAssertion registrationInfo);

    /**
     * Maps a command to be sent to a device/gateway.
     * <p>
     * If a command mapping service is not configured for a gateway or a protocol adapter, this method returns
     * a successful future containing the original command payload. If a command mapping service is configured for a gateway
     * or a protocol adapter and the mapping service returns a 200 OK HTTP status code, then this method returns a successful
     * future containing the mapped command.
     * <p>
     * For all other 2XX HTTP status codes, this method returns the original command payload.
     * In all other cases, this method returns a failed future with a {@link org.eclipse.hono.client.ServiceInvocationException}.
     *
     * @param registrationInfo The information included in the registration assertion for
     *                         the gateway/device to which the command needs to be sent.
     * @param command The original command to be mapped.
     * @return                 A successful future containing the mapped command.
     *                         Otherwise, the future will be failed with a {@link org.eclipse.hono.client.ServiceInvocationException}
     *                         if the command could not be mapped.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    Future<Buffer> mapUpstreamMessage(
        RegistrationAssertion registrationInfo,
        Command command);
}
