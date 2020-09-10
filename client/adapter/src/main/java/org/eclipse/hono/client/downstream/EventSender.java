/**
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
 */


package org.eclipse.hono.client.downstream;

import java.util.Map;

import io.opentracing.SpanContext;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.proton.ProtonDelivery;

/**
 * A client for publishing events originating from devices to downstream consumers.
 */
public interface EventSender {

    /**
     * Sends an event originating from a device to downstream consumers.
     *
     * @param tenantId The ID of the tenant that the device belongs to.
     * @param deviceId The ID of the device that the data originates from.
     * @param payload The data to send.
     * @param contentType The content type of the data.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be succeeded if the event has been sent downstream .
     *         <p>
     *         The future will be failed with a {@link ServerErrorException} if the event
     *         could not be sent. The error code contained in the exception indicates the
     *         cause of the failure.
     * @throws NullPointerException tenant ID, device ID or contentType are {@code null}.
     */
    Future<Void> sendEvent(
            String tenantId,
            String deviceId,
            Buffer payload,
            String contentType,
            SpanContext context);

    /**
     * Sends an event originating from a device to downstream consumers.
     *
     * @param tenantId The ID of the tenant that the device belongs to.
     * @param deviceId The ID of the device that the data originates from.
     * @param properties Additional meta data that should be included in the downstream message.
     * @param payload The data to send.
     * @param contentType The content type of the data.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be succeeded if the event has been sent downstream.
     *         <p>
     *         The future will be failed with a {@link ServerErrorException} if the data
     *         could not be sent. The error code contained in the exception indicates the
     *         cause of the failure.
     * @throws NullPointerException tenant ID, device ID or contentType are {@code null}.
     */
    Future<ProtonDelivery> sendEvent(
            String tenantId,
            String deviceId,
            Map<String, ?> properties,
            Buffer payload,
            String contentType,
            SpanContext context);
}
