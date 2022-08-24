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

package org.eclipse.hono.client.device.amqp;

import org.eclipse.hono.util.QoS;

import io.opentracing.SpanContext;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.proton.ProtonDelivery;

/**
 * A client for sending telemetry messages to Hono's AMQP adapter.
 *
 * @see <a href="https://www.eclipse.org/hono/docs/user-guide/amqp-adapter/#publishing-telemetry-data">AMQP Adapter User Guide</a>
 */
public interface TelemetrySender {

    /**
     * Sends a telemetry message.
     *
     * @param qos The delivery semantics to use.
     * @param payload The data to send.
     *            <p>
     *            The payload, if not {@code null}, will be added to the message as an AMQP 1.0 <em>Data</em> section.
     * @param contentType The content type of the payload or {@code null} if unknown.
     *            <p>
     *            This parameter will be used as the value for the message's <em>content-type</em> property.
     * @param tenantId The tenant that the device belongs to or {@code null} to determine the tenant from 
     *                 the device that has authenticated to the AMQP adapter.
     *                 Unauthenticated clients must provide a non-{@code null} value to indicate the tenant of the
     *                 device that the message originates from.
     * @param deviceId The identifier of the device that the message originates from or {@code null} if the
     *                 message originates from the device that has authenticated to the AMQP adapter.
     *                 Authenticated gateway devices can use this parameter to send a message on behalf of
     *                 another device.
     *                 Unauthenticated clients must provide a non-{@code null} value to indicate the device that
     *                 the message  originates from.
     * @param context The OpenTracing context to use for tracking the execution of the operation (may be {@code null}).
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will succeed if the message has been sent to the peer. The delivery contained in the future
     *         will represent the delivery state at the time that the transfer has been settled.
     *         <p>
     *         The future will be failed with a {@link org.eclipse.hono.client.ServerErrorException} if the message
     *         could not be sent due to a lack of credit. If the message cannot be processed by the peer, the future
     *         will be failed with either a {@code org.eclipse.hono.client.ServerErrorException} or a
     *         {@link org.eclipse.hono.client.ClientErrorException} depending on the reason for the
     *         failure to process the message.
     * @throws NullPointerException if quality-of-service is {@code null}.
     * @throws IllegalArgumentException if tenant ID is not {@code null} but device ID is {@code null}.
     */
    Future<ProtonDelivery> sendTelemetry(
            QoS qos,
            Buffer payload,
            String contentType,
            String tenantId,
            String deviceId,
            SpanContext context);
}
