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

import io.opentracing.SpanContext;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.proton.ProtonDelivery;

/**
 * A client for sending event messages to Hono's AMQP adapter.
 */
public interface TraceableEventSender extends EventSender {

    /**
     * Sends a event message for a given device.
     *
     * @param deviceId The id of the device.
     * @param payload The data to send.
     *            <p>
     *            The payload will be contained in the message as an AMQP 1.0 <em>Data</em> section.
     * @param contentType The content type of the payload (may be {@code null}).
     *            <p>
     *            This parameter will be used as the value for the message's <em>content-type</em> property.
     * @param context The context to create the span in. If {@code null}, then the span is created without a parent.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will succeed if the message has been accepted (and settled) by the peer.
     *         <p>
     *         The future will be failed with a {@link org.eclipse.hono.client.ServerErrorException} if the message
     *         could not be sent due to a lack of credit. If an event is sent which cannot be processed by the peer
     *         the future will be failed with either a {@code org.eclipse.hono.client.ServerErrorException} or a
     *         {@link org.eclipse.hono.client.ClientErrorException} depending on the reason for the failure to
     *         process the message.
     * @throws NullPointerException if any of device-id or payload is {@code null}.
     */
    Future<ProtonDelivery> send(
            String deviceId,
            Buffer payload,
            String contentType,
            SpanContext context);
}
