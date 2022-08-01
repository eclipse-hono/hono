/*******************************************************************************
 * Copyright (c) 2016, 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.adapter.monitoring;


import org.eclipse.hono.auth.Device;
import org.eclipse.hono.client.registry.TenantClient;
import org.eclipse.hono.client.telemetry.EventSender;

import io.opentracing.SpanContext;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

/**
 * Produces connection events.
 * <p>
 * The interface is intended to be implemented by setups which are interested in receiving technical connection events.
 * Which might not make sense for all protocols adapters. But mostly for connection oriented ones like MQTT or AMQP 1.0.
 * <p>
 * The protocol adapters may call {@link #connected(Context, String, String, Device, JsonObject, SpanContext)} and
 * {@link #disconnected(Context, String, String, Device, JsonObject, SpanContext)} as they see fit. The whole process is a "best
 * effort" process and intended to be used for monitoring/debugging.
 * <p>
 * When a protocol adapter calls into this producer it must provide some kind of connection ID, which might have a
 * different meaning for different protocol adapters. This should be as identifiable as possible but there is no
 * requirement for this to be unique. However for each connection the protocol adapter must call
 * {@link #disconnected(Context, String, String, Device, JsonObject, SpanContext)} with the same information as it called
 * {@link #connected(Context, String, String, Device, JsonObject, SpanContext)}.
 */
public interface ConnectionEventProducer {

    /**
     * Context object for the connection events producers.
     * <p>
     * This context is only valid for one call and its values must not be stored by the implementation of the
     * <em>connection event producer</em>.
     */
    interface Context {

        /**
         * Gets the client for sending connection events downstream.
         * <p>
         * If no client is available in this context, {@code null} is returned. 
         * <p>
         * A returned client here is required to be initialized and started.
         *
         * @return The instance of the message sender client which the {@link ConnectionEventProducer} should
         *         use or {@code null} if no client is available. 
         */
        EventSender getMessageSenderClient();
        /**
         * Provides the tenant client which the {@link ConnectionEventProducer} should use to lookup the tenant
         * that the device connecting to a protocol adapter belongs to.
         *
         * @return The tenant client instance.
         */
        TenantClient getTenantClient();
    }

    /**
     * Produce an event for a new connection.
     *
     * @param context Protocol adapter context.
     * @param remoteId The ID of the remote endpoint which connected (e.g. a remote address, port, client id, ...).
     * @param protocolAdapter The name of the protocol adapter sending this event. Must not be {@code null}.
     * @param authenticatedDevice The optional authenticated device associated with the connection. May be {@code null}
     *            if the connection is from an unauthenticated device.
     * @param data Additional, protocol adapter specific data.
     * @param spanContext The currently active OpenTracing span context or {@code null}.
     * @return A future which indicates the result of the event production.
     * @throws NullPointerException If either the remote ID or the protocol adapter argument are {@code null}.
     */
    Future<Void> connected(Context context, String remoteId, String protocolAdapter, Device authenticatedDevice,
            JsonObject data, SpanContext spanContext);

    /**
     * Produce an event for a closed connection.
     *
     * @param context Protocol adapter context.
     * @param remoteId The ID of the remote endpoint which disconnected. The same ID used in the call to
     *            {@link #connected(Context, String, String, Device, JsonObject, SpanContext)}
     * @param protocolAdapter The name of the protocol adapter sending this event. Must not be {@code null}.
     * @param authenticatedDevice The optional authenticated device associated with the connection. May be {@code null}
     *            if the connection is from an unauthenticated device.
     * @param data Additional, protocol adapter specific data.
     * @param spanContext The currently active OpenTracing span context or {@code null}.
     * @return A future which indicates the result of the event production.
     * @throws NullPointerException If either the remote ID or the protocol adapter argument are {@code null}.
     */
    Future<Void> disconnected(Context context, String remoteId, String protocolAdapter, Device authenticatedDevice,
            JsonObject data, SpanContext spanContext);
}
