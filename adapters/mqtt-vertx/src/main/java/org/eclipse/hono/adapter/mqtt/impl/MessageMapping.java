/*******************************************************************************
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter.mqtt.impl;

import org.eclipse.hono.util.ExecutionContext;
import org.eclipse.hono.util.ResourceIdentifier;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

/**
 * This component requests mapping from another server if configured properly. The
 * headers are overwritten with the result of the mapper (which includes the resourceId).
 * E.g.: when the deviceId is in the payload of the message, the deviceId can be deducted in the custom mapper and
 * the payload can be changed accordingly to the payload originally received by the gateway.
 *
 * @param <T> The type of execution context supported by this mapping service.
 */
public interface MessageMapping<T extends ExecutionContext> {

    /**
     * Maps a message uploaded by a device.
     *
     * @param ctx The context in which the message has been uploaded.
     * @param targetAddress The downstream address that the message will be forwarded to.
     * @param registrationInfo The information included in the registration assertion for
     *                         the authenticated device that has uploaded the message.y
     * @return A future indicating the outcome of the operation.
     *         The future will be completed with the mapped message or failed with
     *         a {@link org.eclipse.hono.client.ServiceInvocationException} if the
     *         message could not be mapped.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    Future<MappedMessage> mapMessage(
            T ctx,
            ResourceIdentifier targetAddress,
            JsonObject registrationInfo);
}
