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
package org.eclipse.hono.adapter.amqp;

import java.net.HttpURLConnection;
import java.util.Objects;

import org.apache.qpid.proton.amqp.transport.AmqpError;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.service.auth.device.Device;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.MapBasedExecutionContext;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.ResourceIdentifier;

import io.vertx.core.buffer.Buffer;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonHelper;

/**
 * A class that contains context information used by the AMQP Adapter when uploading messages to Hono. An instance of
 * this class is created after link establishment to handle messages sent by client devices.
 */
public class AmqpContext extends MapBasedExecutionContext {

    private final ProtonDelivery delivery;
    private final Message message;
    private final ResourceIdentifier resource;
    private final Device authenticatedDevice;
    private final Buffer payload;

    /**
     * Creates an AmqpContext instance using the specified delivery, message and authenticated device.
     * <p>
     * This constructor <b>does not</b> validate the message address. It is the responsibility of the caller to make
     * sure that the message address is valid i.e matches the pattern {@code endpointName/tenantId/deviceId}.
     * 
     * @param delivery The delivery of the message.
     * @param message The AMQP 1.0 message. The message must contain a valid address.
     * @param authenticatedDevice The device that authenticates to the adapter or {@code null} if the device is unauthenticated.
     * @throws NullPointerException if the delivery or message is null.
     */
    AmqpContext(final ProtonDelivery delivery, final Message message, final Device authenticatedDevice) {
        this.delivery = Objects.requireNonNull(delivery);
        this.message = Objects.requireNonNull(message);
        this.authenticatedDevice = authenticatedDevice;
        this.resource = ResourceIdentifier.fromString(message.getAddress());
        this.payload = MessageHelper.getPayload(message);
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
     * Gets the tenant identifier for this context.
     *
     * @return The tenant identifier.
     */
    String getTenantId() {
        return isDeviceAuthenticated() ? authenticatedDevice.getTenantId() : resource.getTenantId();
    }

    /**
     * Gets the device identifier for this context.
     *
     * @return The device identifier.
     */
    String getDeviceId() {
        return isDeviceAuthenticated() ? authenticatedDevice.getDeviceId() : resource.getResourceId();
    }

    /**
     * Gets the endpoint name of the context's resource.
     *
     * @return The endpoint name.
     */
    String getEndpoint() {
        return resource.getEndpoint();
    }

    /**
     * Gets the resource of this context.
     *
     * @return The resource.
     */
    ResourceIdentifier getResourceIdentifier() {
        return resource;
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
     * Sets an AMQP 1.0 message delivery state to either RELEASED in the case of a <em>ServerErrorException</em> or REJECTED in the
     * case of a <em>ClientErrorException</em>. In the REJECTED case, the supplied exception will provide
     * the error condition value and description as reason for rejection.
     *
     * @param t The service invocation exception.
     * @throws NullPointerException if error is {@code null}.
     */
    void handleFailure(final Throwable t) {
        Objects.requireNonNull(t);
        final ErrorCondition condition = getErrorCondition(t);
        if (ServiceInvocationException.class.isInstance(t)) {
            final ServiceInvocationException error = (ServiceInvocationException) t;
            if (ServerErrorException.class.isInstance(error)) {
                ProtonHelper.released(delivery, true);
            } else {
                MessageHelper.rejected(delivery, condition);
            }
        } else {
            MessageHelper.rejected(delivery, condition);
        }
    }

    /**
     * Settles and accepts the delivery by applying the ACCEPTED disposition state.
     *
     * @return The proton delivery.
     */
    ProtonDelivery accept() {
        return ProtonHelper.accepted(delivery, true);
    }

    /**
     * Updates this context's delivery state and settlement using the specified delivery.
     *
     * @param newDelivery The new delivery use to update this context's delivery.
     *
     * @return The new proton delivery.
     */
    ProtonDelivery updateDelivery(final ProtonDelivery newDelivery) {
        return delivery.disposition(newDelivery.getRemoteState(), newDelivery.remotelySettled());
    }

    /**
     * Whether the delivery was settled by the device.
     *
     * @return True if the device sends the message settled, false otherwise.
     */
    boolean isRemotelySettled() {
        return delivery.remotelySettled();
    }

    //---------------------------------------------< private methods >---
    /**
     * Creates an ErrorCondition using the given throwable to provide an error condition value and
     * description. All throwables that are not service invocation exceptions will be mapped to {@link AmqpError#PRECONDITION_FAILED}.
     *
     * @param t The throwable to map to an error condition.
     * @return The ErrorCondition.
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
