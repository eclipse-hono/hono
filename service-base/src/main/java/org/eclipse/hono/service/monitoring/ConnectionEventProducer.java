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
package org.eclipse.hono.service.monitoring;

import org.eclipse.hono.service.auth.device.Device;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

/**
 * Produces connection events.
 * <p>
 * The interface is intended to be implemented by setups which are interested in receiving technical connection events.
 * Which might not make sense for all protocols adapters. But mostly for connection oriented ones like MQTT or AMQP 1.0.
 * <p>
 * The protocol adapters may call {@link #connected(String, String, Device, JsonObject)} and
 * {@link #disconnected(String, String, Device, JsonObject)} as they see fit. The whole process is a "best effort"
 * process and intended to be used for monitoring/debugging.
 * <p>
 * When a protocol adapter calls into this producer it must provide some kind of connection ID, which might have a
 * different meaning for different protocol adapters. This should be as identifiable as possible but there is no
 * requirement for this to be unique. However for each connection the protocol adapter must call
 * {@link #disconnected(String, String, Device, JsonObject)} with the same information as it called
 * {@link #connected(String, String, Device, JsonObject)}.
 */
public interface ConnectionEventProducer {

    /**
     * Produce an event for a new connection.
     * 
     * @param remoteId The ID of the remote endpoint which connected (e.g. a remote address, port, client id, ...).
     * @param protocolAdapter The name of the protocol adapter sending this event. Must not be {@code null}.
     * @param authenticatedDevice The optional authenticated device associated with the connection. May be {@code null}
     *            if the connection is from an unauthenticated device.
     * @param data Additional, protocol adapter specific data
     * @return A future which indicates the result of the event production
     * @throws NullPointerException If either the remote ID or the protocol adapter argument are {@code null}.
     */
    Future<?> connected(String remoteId, String protocolAdapter, Device authenticatedDevice, JsonObject data);

    /**
     * Produce an event for a closed connection.
     * 
     * @param remoteId The ID of the remote endpoint which disconnected. The same ID used in the call to
     *            {@link #connected(String, String, Device, JsonObject)}
     * @param protocolAdapter The name of the protocol adapter sending this event. Must not be {@code null}.
     * @param authenticatedDevice The optional authenticated device associated with the connection. May be {@code null}
     *            if the connection is from an unauthenticated device.
     * @param data Additional, protocol adapter specific data
     * @return A future which indicates the result of the event production
     * @throws NullPointerException If either the remote ID or the protocol adapter argument are {@code null}.
     */
    Future<?> disconnected(String remoteId, String protocolAdapter, Device authenticatedDevice, JsonObject data);
}
