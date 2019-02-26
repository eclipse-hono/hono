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
package org.eclipse.hono.adapter.amqp;

import java.net.HttpURLConnection;
import java.util.Objects;

import org.apache.qpid.proton.amqp.transport.AmqpError;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.auth.Device;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.service.metric.MetricsTags.EndpointType;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.MapBasedExecutionContext;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.ResourceIdentifier;

import io.micrometer.core.instrument.Timer.Sample;
import io.vertx.core.buffer.Buffer;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonHelper;

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
    Buffer getMessagePayload() {
        return payload;
    }

    /**
     * Gets the size of the message's payload.
     * 
     * @return The size in bytes.
     */
    int getPayloadSize() {
        return payload == null ? 0 : payload.length();
    }

    /**
     * Gets the content type of the AMQP 1.0 message.
     *
     * @return The content type of the AMQP 1.0 message.
     */
    String getMessageContentType() {
        return message.getContentType();
    }

    /**
     * Gets the delivery state.
     *
     * @return The delivery state of this context.
     */
    ProtonDelivery delivery() {
        return delivery;
    }

    /**
     * Gets the AMQP 1.0 message sent by the client.
     * 
     * @return The AMQP 1.0 message.
     */
    Message getMessage() {
        return message;
    }

    /**
     * Gets the endpoint that this context's message is targeted at.
     *
     * @return The endpoint name.
     */
    EndpointType getEndpoint() {
        return endpoint;
    }

    /**
     * Gets the address of this context's message.
     *
     * @return The resource.
     */
    ResourceIdentifier getAddress() {
        return address;
    }

    /**
     * Gets the authenticated device created after a successful SASL authentication.
     *
     * @return The authenticated device or {@code null} for an unauthenticated device.
     */
    Device getAuthenticatedDevice() {
        return authenticatedDevice;
    }

    /**
     * Determines if the AMQP 1.0 device is authenticated to the adapter.
     * 
     * @return True if the device is authenticated or false otherwise.
     */
    boolean isDeviceAuthenticated() {
        return authenticatedDevice != null;
    }

    /**
     * Whether the delivery was settled by the device.
     *
     * @return True if the device sends the message settled, false otherwise.
     */
    boolean isRemotelySettled() {
        return delivery.remotelySettled();
    }

    /**
     * Sets the object to use for measuring the time it takes to
     * process this request.
     * 
     * @param timer The timer.
     */
    void setTimer(final Sample timer) {
        this.timer = timer;
    }

    /**
     * Gets the object used for measuring the time it takes to
     * process this request.
     * 
     * @return The timer or {@code null} if not set.
     */
    Sample getTimer() {
        return timer;
    }

    /**
     * Creates an AMQP error condition for an throwable.
     * <p>
     * Non {@link ServiceInvocationException} instances are mapped to {@link AmqpError#PRECONDITION_FAILED}.
     *
     * @param t The throwable to map to an error condition.
     * @return The error condition.
     */
    static ErrorCondition getErrorCondition(final Throwable t) {
        if (ServiceInvocationException.class.isInstance(t)) {
            final ServiceInvocationException error = (ServiceInvocationException) t;
            switch (error.getErrorCode()) {
            case HttpURLConnection.HTTP_BAD_REQUEST:
                return ProtonHelper.condition(Constants.AMQP_BAD_REQUEST, error.getMessage());
            case HttpURLConnection.HTTP_FORBIDDEN:
                return ProtonHelper.condition(AmqpError.UNAUTHORIZED_ACCESS, error.getMessage());
            default:
                return ProtonHelper.condition(AmqpError.PRECONDITION_FAILED, error.getMessage());
            }
        } else {
            return ProtonHelper.condition(AmqpError.PRECONDITION_FAILED, t.getMessage());
        }
    }
}
