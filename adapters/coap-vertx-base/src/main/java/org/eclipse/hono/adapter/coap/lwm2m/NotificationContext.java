/**
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter.coap.lwm2m;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.coap.Response;
import org.eclipse.hono.auth.Device;
import org.eclipse.hono.util.MapBasedTelemetryExecutionContext;
import org.eclipse.hono.util.QoS;
import org.eclipse.leshan.core.observation.Observation;
import org.eclipse.leshan.core.response.ObserveResponse;

import io.micrometer.core.instrument.Timer.Sample;
import io.opentracing.Span;
import io.vertx.core.buffer.Buffer;

/**
 * A dictionary of relevant information required during the processing of an observe notification contained in
 * a CoAP response message published by a LwM2M client (device).
 *
 */
public final class NotificationContext extends MapBasedTelemetryExecutionContext {

    private final Response response;
    private final Observation observation;
    private final Sample timer;

    private NotificationContext(
            final ObserveResponse response,
            final Device authenticatedDevice,
            final Sample timer,
            final Span span) {
        super(span, authenticatedDevice);
        this.response = (Response) response.getCoapResponse();
        this.observation = response.getObservation();
        this.timer = timer;
    }

    /**
     * Creates a new context for an observe notification.
     *
     * @param response The CoAP response containing the notification.
     * @param authenticatedDevice The authenticated device that the notification has been received from.
     * @param timer The object to use for measuring the time it takes to process the request.
     * @param span The <em>OpenTracing</em> root span that is used to track the processing of this context.
     * @return The context.
     * @throws NullPointerException if response, originDevice, span or timer are {@code null}.
     */
    public static NotificationContext fromResponse(
            final ObserveResponse response,
            final Device authenticatedDevice,
            final Sample timer,
            final Span span) {

        Objects.requireNonNull(response);
        Objects.requireNonNull(authenticatedDevice);
        Objects.requireNonNull(span);
        Objects.requireNonNull(timer);

        return new NotificationContext(response, authenticatedDevice, timer, span);
    }

    /**
     * Gets the tenant identifier.
     *
     * @return The tenant.
     */
    public String getTenantId() {
        return getAuthenticatedDevice().getTenantId();
    }

    /**
     * Gets the CoAP response.
     *
     * @return The response.
     */
    public Response getResponse() {
        return response;
    }

    /**
     * Gets the payload of the response.
     *
     * @return payload of response
     */
    public Buffer getPayload() {
        final byte[] payload = response.getPayload();
        if (payload == null || payload.length == 0) {
            return Buffer.buffer();
        } else {
            return Buffer.buffer(payload);
        }
    }

    /**
     * Gets the media type that corresponds to the <em>content-format</em> option of the CoAP response.
     * <p>
     * The media type is determined as follows:
     * <ol>
     * <li>If the response doesn't contain a <em>content-format</em> option, the media type
     * is {@code null}</li>
     * <li>Otherwise, if the content-format code is registered with IANA, the media type is the one
     * that has been registered for the code</li>
     * <li>Otherwise, the media type is <em>unknown/code</em> where code is the value of the content-format
     * option</li>
     * </ol>
     *
     * @return The media type or {@code null} if the response does not contain a content-format option.
     */
    public String getContentType() {
        if (response.getOptions().hasContentFormat()) {
            return MediaTypeRegistry.toString(response.getOptions().getContentFormat());
        } else {
            return null;
        }
    }

    /**
     * Gets the object used for measuring the time it takes to process this response.
     *
     * @return The timer or {@code null} if not set.
     */
    public Sample getTimer() {
        return timer;
    }

    /**
     * Checks if the response has been sent using a CONfirmable message.
     *
     * @return {@code true} if the response message is CONfirmable.
     */
    public boolean isConfirmable() {
        return response.isConfirmable();
    }

    @Override
    public QoS getRequestedQos() {
        return isConfirmable() ? QoS.AT_LEAST_ONCE : QoS.AT_MOST_ONCE;
    }

    /**
     * {@inheritDoc}
     *
     * @return An empty optional.
     */
    @Override
    public Optional<Duration> getTimeToLive() {
        return Optional.empty();
    }

    /**
     * {@inheritDoc}
     *
     * @return The observed resource path.
     */
    @Override
    public String getOrigAddress() {
        return observation.getPath().toString();
    }
}
