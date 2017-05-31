/**
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */
package org.eclipse.hono.service.amqp;

import io.vertx.core.Vertx;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.ProtonSender;
import org.apache.qpid.proton.amqp.transport.AmqpError;
import org.apache.qpid.proton.codec.DecodeException;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.ResourceIdentifier;

import java.util.Objects;

import static io.vertx.proton.ProtonHelper.condition;

/**
 * An abstract base class for implementing endpoints that implement a request response pattern.
 * <p>
 * It is used e.g. in the implementation of the device registration and the credentials API endpoints.
 */
public abstract class RequestResponseEndpoint<T extends ServiceConfigProperties> extends BaseEndpoint<T> {
    private static final int REQUEST_RESPONSE_ENDPOINT_DEFAULT_CREDITS = 20;

    private int receiverLinkCredit = REQUEST_RESPONSE_ENDPOINT_DEFAULT_CREDITS;

    /**
     * Creates an endpoint for a Vertx instance.
     *
     * @param vertx The Vertx instance to use.
     * @throws NullPointerException if vertx is {@code null};
     */
    protected RequestResponseEndpoint(final Vertx vertx) {
        super(Objects.requireNonNull(vertx));
    }

    /**
     * Process the received AMQP message.
     *
     * @param message The Message to process.
     */
    protected abstract void processRequest(final Message message);

    /**
     * Construct an AMQP reply message that is send back to the caller.
     *
     * @param message The Message as JsonObject from which a reply message is constructed.
     * @return Message The reply message that shall be sent to the client.
     */
    protected abstract Message getAmqpReply(final io.vertx.core.eventbus.Message<JsonObject> message);

    /**
     * Gets the number of message credits this endpoint grants as a receiver.
     *
     * @return The number of credits granted.
     */
    public final int getReceiverLinkCredit() {
        return receiverLinkCredit;
    }

    /**
     * Sets the number of message credits this endpoint grants as a receiver.
     * They are replenished automatically after messages are processed.

     * @param receiverLinkCredit The number of credits to grant.
     */
    protected final void setReceiverLinkCredit(final int receiverLinkCredit) {
        this.receiverLinkCredit = receiverLinkCredit;
    }

    /**
     * Configure and check the receiver link of the endpoint.
     * The remote link of the receiver must not demand the AT_MOST_ONCE QoS (not supported).
     * The receiver link itself is configured with the AT_LEAST_ONCE QoS and grants the configured credits ({@link #setReceiverLinkCredit(int)})
     * with autoAcknowledge.
     * <p>
     * Incoming messages are verified by the abstract method {@link #passesFormalVerification(ResourceIdentifier, Message)} and is then processed by
     * the abstract method {@link #processRequest}. Both methods are endpoint specific and need to be implemented by the subclass.
     *
     * @param receiver The ProtonReceiver that has already been created for this endpoint.
     * @param targetAddress The resource identifier for this endpoint (see {@link ResourceIdentifier} for details).
     */
    @Override
    public final void onLinkAttach(final ProtonReceiver receiver, final ResourceIdentifier targetAddress) {
        if (ProtonQoS.AT_MOST_ONCE.equals(receiver.getRemoteQoS())) {
            logger.debug("client wants to use AT MOST ONCE delivery mode for {} endpoint, this is not supported.",getName());
            receiver.setCondition(condition(AmqpError.PRECONDITION_FAILED.toString(), "endpoint requires AT_LEAST_ONCE QoS"));
            receiver.close();
        } else {

            logger.debug("establishing link for receiving messages from client [{}]", MessageHelper.getLinkName(receiver));
            receiver
                    .setQoS(ProtonQoS.AT_LEAST_ONCE)
                    .setAutoAccept(true) // settle received messages if the handler succeeds
                    .setPrefetch(receiverLinkCredit)
                    .handler((delivery, message) -> {
                        if (passesFormalVerification(targetAddress, message)) {
                            try {
                                processRequest(message);
                            } catch (DecodeException e) {
                                MessageHelper.rejected(delivery, AmqpError.DECODE_ERROR.toString(), "malformed payload");
                            }
                        } else {
                            MessageHelper.rejected(delivery, AmqpError.DECODE_ERROR.toString(), "malformed message");
                            // we close the link if the client sends a message that does not comply with the API spec
                            onLinkDetach(receiver, condition(AmqpError.DECODE_ERROR.toString(), "invalid message received"));
                        }
                    }).closeHandler(clientDetached -> onLinkDetach(clientDetached.result()))
                    .open();
        }
    }

    /**
     * Configure and check the sender link of the endpoint.
     * The sender link is used for the response to a received request and is driven by the vertx event bus.
     * It listens to the provided resource identifier of the endpoint as vertx event address and then sends the
     * constructed response.
     * Since the response is endpoint specific, it is an abstract method {@link #getAmqpReply(io.vertx.core.eventbus.Message)} and needs to be implemented
     * by the subclass.
     *
     * @param sender The ProtonSender that has already been created for this endpoint.
     * @param targetResource The resource identifier for the responses of this endpoint (see {@link ResourceIdentifier} for details).
     *                      Note that the reply address is different for each client and is passed in during link creation.
     */
    @Override
    public final void onLinkAttach(final ProtonSender sender, final ResourceIdentifier targetResource) {
        if (targetResource.getResourceId() == null) {
            logger.debug("link target provided in client's link ATTACH must not be null, but must match pattern \"{}/<tenant>/<reply-address>\" instead",getName());
            sender.setCondition(condition(AmqpError.INVALID_FIELD.toString(),
                    String.format("link target must not be null but must have the following format %s/<tenant>/<reply-address>",getName())));
            sender.close();
        } else {
            logger.debug("establishing sender link with client [{}]", MessageHelper.getLinkName(sender));
            final MessageConsumer<JsonObject> replyConsumer = vertx.eventBus().consumer(targetResource.toString(), message -> {
                // TODO check for correct session here...?
                logger.trace("forwarding reply to client: {}", message.body());
                final Message amqpReply = getAmqpReply(message);
                sender.send(amqpReply);
            });

            sender.closeHandler(senderClosed -> {
                replyConsumer.unregister();
                senderClosed.result().close();
                final String linkName = MessageHelper.getLinkName(sender);
                logger.debug("receiver closed link [{}], removing associated event bus consumer [{}]", linkName, replyConsumer.address());
            });

            sender.setQoS(ProtonQoS.AT_LEAST_ONCE).open();
        }
    }
}
