/*******************************************************************************
 * Copyright (c) 2018, 2023 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.adapter.amqp;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.adapter.MapBasedTelemetryExecutionContext;
import org.eclipse.hono.client.amqp.connection.AmqpUtils;
import org.eclipse.hono.service.auth.DeviceUser;
import org.eclipse.hono.service.metric.MetricsTags.EndpointType;
import org.eclipse.hono.util.QoS;
import org.eclipse.hono.util.ResourceIdentifier;

import io.micrometer.core.instrument.Timer.Sample;
import io.opentracing.Span;
import io.vertx.core.buffer.Buffer;
import io.vertx.proton.ProtonDelivery;

/**
 * A class that contains context information used by the AMQP Adapter when uploading messages to Hono. An instance of
 * this class is created after link establishment to handle messages sent by client devices.
 */
public class AmqpContext extends MapBasedTelemetryExecutionContext {

    private ProtonDelivery delivery;
    private Message message;
    private ResourceIdentifier address;
    private Buffer payload;
    private EndpointType endpoint;
    private Sample timer;

    private AmqpContext(final Span span, final DeviceUser authenticatedDevice) {
        super(span, authenticatedDevice);
    }

    /**
     * Creates an AmqpContext instance using the specified delivery, message and authenticated device.
     * <p>
     * This constructor <b>does not</b> validate the message address. It is the responsibility of the caller to make
     * sure that the message address is valid.
     *
     * @param delivery The delivery of the message.
     * @param message The AMQP 1.0 message. The message must contain a valid address.
     * @param span The <em>OpenTracing</em> root span that is used to track the processing of this context.
     * @param authenticatedDevice The device that authenticates to the adapter or {@code null} if the device is unauthenticated.
     * @return The context.
     * @throws NullPointerException if any of the parameters except authenticatedDevice is null {@code null}.
     */
    public static AmqpContext fromMessage(
            final ProtonDelivery delivery,
            final Message message,
            final Span span,
            final DeviceUser authenticatedDevice) {

        Objects.requireNonNull(delivery);
        Objects.requireNonNull(message);
        Objects.requireNonNull(span);

        final AmqpContext ctx = new AmqpContext(span, authenticatedDevice);
        ctx.delivery = delivery;
        ctx.message = message;
        ctx.payload = AmqpUtils.getPayload(message);
        if (ResourceIdentifier.isValid(message.getAddress())) {
            ctx.address = ResourceIdentifier.fromString(message.getAddress());
            ctx.endpoint = EndpointType.fromString(ctx.address.getEndpoint());
        }
        return ctx;
    }

    /**
     * Gets the body of the AMQP 1.0 message.
     *
     * @return The body of the AMQP 1.0 message as a buffer object.
     */
    public final Buffer getMessagePayload() {
        return payload;
    }

    /**
     * Gets the size of the message's payload.
     *
     * @return The size in bytes.
     */
    public final int getPayloadSize() {
        return payload == null ? 0 : payload.length();
    }

    /**
     * Gets the content type of the AMQP 1.0 message.
     *
     * @return The content type or {@code null} if not set.
     */
    public final String getMessageContentType() {
        return message.getContentType();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Optional<Duration> getTimeToLive() {
        if (endpoint == EndpointType.EVENT && message.getTtl() > 0) {
            // make sure it is at least one second
            return Optional.of(Duration.ofMillis(Math.max(message.getTtl(), 1000L)));
        }
        return Optional.empty();
    }

    /**
     * Gets the delivery state.
     *
     * @return The delivery state of this context.
     */
    public final ProtonDelivery delivery() {
        return delivery;
    }

    /**
     * Gets the AMQP 1.0 message sent by the client.
     *
     * @return The AMQP 1.0 message.
     */
    public final Message getMessage() {
        return message;
    }

    /**
     * Gets the endpoint that this context's message is targeted at.
     *
     * @return The endpoint name.
     */
    public final EndpointType getEndpoint() {
        return endpoint;
    }

    /**
     * Gets the address of this context's message.
     *
     * @return The resource.
     */
    public final ResourceIdentifier getAddress() {
        return address;
    }

    /**
     * Whether the delivery was settled by the device.
     *
     * @return True if the device sends the message settled, false otherwise.
     */
    public final boolean isRemotelySettled() {
        return delivery.remotelySettled();
    }

    /**
     * Sets the object to use for measuring the time it takes to
     * process this request.
     *
     * @param timer The timer.
     */
    public final void setTimer(final Sample timer) {
        this.timer = timer;
    }

    /**
     * Gets the object used for measuring the time it takes to
     * process this request.
     *
     * @return The timer or {@code null} if not set.
     */
    public final Sample getTimer() {
        return timer;
    }

    @Override
    public QoS getRequestedQos() {
        return isRemotelySettled() ? QoS.AT_MOST_ONCE : QoS.AT_LEAST_ONCE;
    }

    /**
     * {@inheritDoc}
     *
     * @return The value of the message's <em>to</em> property.
     */
    @Override
    public String getOrigAddress() {
        return message.getAddress();
    }
}
