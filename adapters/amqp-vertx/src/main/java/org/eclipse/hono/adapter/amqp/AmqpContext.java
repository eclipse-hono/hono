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
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.ResourceIdentifier;

import io.vertx.core.buffer.Buffer;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonHelper;

/**
 * A class that contains context information used by the AMQP Adapter when uploading messages to Hono. An instance of
 * this class is created after link establishment to handle messages sent by client devices.
 */
public class AmqpContext {

    private final ProtonDelivery delivery;
    private final Message message;
    private final ResourceIdentifier resource;
    private final Device authenticatedDevice;

    AmqpContext(final ProtonDelivery delivery, final Message message, final ResourceIdentifier resource, final Device authenticatedDevice) {
        this.delivery = delivery;
        this.message = message;
        this.resource = resource;
        this.authenticatedDevice = authenticatedDevice;
    }

    /**
     * Gets the body of the AMQP 1.0 message.
     *
     * @return The body of the AMQP 1.0 message as a buffer object.
     */
    Buffer getMessagePayload() {
        return MessageHelper.getPayload(message);
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
     * Gets the tenant identifier of this context's resource.
     *
     * @return The tenant identifier.
     */
    String getTenantId() {
        return resource.getTenantId();
    }

    /**
     * Gets the device identifier of this context's resource.
     *
     * @return The device identifier.
     */
    String getDeviceId() {
        return resource.getResourceId();
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
     * Sets an AMQP 1.0 message delivery state to either RELEASED in the case of a <em>ServerErrorException</em> or REJECTED in the
     * case of a <em>ClientErrorException</em>. In the REJECTED case, the supplied exception will provide
     * the error condition value and description as reason for rejection.
     *
     * @param error The service invocation exception.
     * @throws NullPointerException if error is {@code null}.
     */
    void handleFailure(final ServiceInvocationException error) {
        Objects.requireNonNull(error);
        if (ServerErrorException.class.isInstance(error)) {
            ProtonHelper.released(delivery, true);
        } else {
            final ErrorCondition condition = getErrorCondition(error);
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
     * Creates an ErrorCondition using the given service invocation error to provide an error condition value
     * and description.
     *
     * @param error The service invocation error.
     * @return The ErrorCondition.
     */
    private ErrorCondition getErrorCondition(final ServiceInvocationException error) {
        switch (error.getErrorCode()) {
        case HttpURLConnection.HTTP_BAD_REQUEST:
            return ProtonHelper.condition(Constants.AMQP_BAD_REQUEST, error.getMessage());
        case HttpURLConnection.HTTP_FORBIDDEN:
            return ProtonHelper.condition(AmqpError.UNAUTHORIZED_ACCESS, error.getMessage());
        default:
            return ProtonHelper.condition(AmqpError.PRECONDITION_FAILED, error.getMessage());
        }
    }
}
