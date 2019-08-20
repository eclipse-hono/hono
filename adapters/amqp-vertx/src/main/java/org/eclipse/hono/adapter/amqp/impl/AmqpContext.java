/*******************************************************************************
 * Copyright (c) 2018, 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.adapter.amqp.impl;

import java.util.Objects;
import java.util.OptionalInt;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.auth.Device;
import org.eclipse.hono.service.metric.MetricsTags.EndpointType;
import org.eclipse.hono.util.MapBasedExecutionContext;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.ResourceIdentifier;

import io.micrometer.core.instrument.Timer.Sample;
import io.vertx.core.buffer.Buffer;
import io.vertx.proton.ProtonDelivery;

/**
 * A class that contains context information used by the AMQP Adapter when uploading messages to Hono. An instance of
 * this class is created after link establishment to handle messages sent by client devices.
 */
public class AmqpContext extends MapBasedExecutionContext {

    private ProtonDelivery delivery;
    private Message message;
    private ResourceIdentifier address;
    private Device authenticatedDevice;
    private Buffer payload;
    private EndpointType endpoint;
    private Sample timer;
    private OptionalInt traceSamplingPriority = OptionalInt.empty();

    /**
     * Creates an AmqpContext instance using the specified delivery, message and authenticated device.
     * <p>
     * This constructor <b>does not</b> validate the message address. It is the responsibility of the caller to make
     * sure that the message address is valid.
     * 
     * @param delivery The delivery of the message.
     * @param message The AMQP 1.0 message. The message must contain a valid address.
     * @param authenticatedDevice The device that authenticates to the adapter or {@code null} if the device is unauthenticated.
     * @return The context.
     * @throws NullPointerException if the delivery, the message or the message's address are {@code null}.
     * @throws IllegalArgumentException if the message's address is not a valid resource identifier.
     */
    static AmqpContext fromMessage(final ProtonDelivery delivery, final Message message, final Device authenticatedDevice) {
        Objects.requireNonNull(delivery);
        Objects.requireNonNull(message);
        final AmqpContext ctx = new AmqpContext();
        ctx.delivery = delivery;
        ctx.message = message;
        ctx.authenticatedDevice = authenticatedDevice;
        ctx.payload = MessageHelper.getPayload(message);
        if (message.getAddress() != null) {
            try {
                ctx.address = ResourceIdentifier.fromString(message.getAddress());
                ctx.endpoint = EndpointType.fromString(ctx.address.getEndpoint());
            } catch (final IllegalArgumentException e) {
                // malformed address
            }
        }
        return ctx;
    }

    /**
     * Gets the body of the AMQP 1.0 message.
     *
     * @return The body of the AMQP 1.0 message as a buffer object.
     */
    final Buffer getMessagePayload() {
        return payload;
    }

    /**
     * Gets the size of the message's payload.
     * 
     * @return The size in bytes.
     */
    final int getPayloadSize() {
        return payload == null ? 0 : payload.length();
    }

    /**
     * Gets the content type of the AMQP 1.0 message.
     *
     * @return The content type of the AMQP 1.0 message.
     */
    final String getMessageContentType() {
        return message.getContentType();
    }

    /**
     * Gets the delivery state.
     *
     * @return The delivery state of this context.
     */
    final ProtonDelivery delivery() {
        return delivery;
    }

    /**
     * Gets the AMQP 1.0 message sent by the client.
     * 
     * @return The AMQP 1.0 message.
     */
    final Message getMessage() {
        return message;
    }

    /**
     * Gets the endpoint that this context's message is targeted at.
     *
     * @return The endpoint name.
     */
    final EndpointType getEndpoint() {
        return endpoint;
    }

    /**
     * Gets the address of this context's message.
     *
     * @return The resource.
     */
    final ResourceIdentifier getAddress() {
        return address;
    }

    /**
     * Gets the authenticated device created after a successful SASL authentication.
     *
     * @return The authenticated device or {@code null} for an unauthenticated device.
     */
    final Device getAuthenticatedDevice() {
        return authenticatedDevice;
    }

    /**
     * Determines if the AMQP 1.0 device is authenticated to the adapter.
     * 
     * @return True if the device is authenticated or false otherwise.
     */
    final boolean isDeviceAuthenticated() {
        return authenticatedDevice != null;
    }

    /**
     * Whether the delivery was settled by the device.
     *
     * @return True if the device sends the message settled, false otherwise.
     */
    final boolean isRemotelySettled() {
        return delivery.remotelySettled();
    }

    /**
     * Sets the object to use for measuring the time it takes to
     * process this request.
     * 
     * @param timer The timer.
     */
    final void setTimer(final Sample timer) {
        this.timer = timer;
    }

    /**
     * Gets the object used for measuring the time it takes to
     * process this request.
     * 
     * @return The timer or {@code null} if not set.
     */
    final Sample getTimer() {
        return timer;
    }

    /**
     * Gets the value for the <em>sampling.priority</em> span tag to be used for OpenTracing spans created in connection
     * with this AmqpContext.
     *
     * @return An <em>OptionalInt</em> containing the value for the <em>sampling.priority</em> span tag or an empty
     *         <em>OptionalInt</em> if no priority should be set.
     */
    final OptionalInt getTraceSamplingPriority() {
        return traceSamplingPriority;
    }

    /**
     * Sets the value for the <em>sampling.priority</em> span tag to be used for OpenTracing spans created in connection
     * with this AmqpContext.
     *
     * @param traceSamplingPriority The <em>OptionalInt</em> containing the <em>sampling.priority</em> span tag value or
     *            an empty <em>OptionalInt</em> if no priority should be set.
     * @throws NullPointerException if traceSamplingPriority is {@code null}.
     */
    final void setTraceSamplingPriority(final OptionalInt traceSamplingPriority) {
        this.traceSamplingPriority = Objects.requireNonNull(traceSamplingPriority);
    }
}
