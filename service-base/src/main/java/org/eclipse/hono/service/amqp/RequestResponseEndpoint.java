/**
 * Copyright (c) 2017, 2018 Bosch Software Innovations GmbH.
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

import java.net.HttpURLConnection;
import java.util.Objects;
import java.util.Optional;

import org.apache.qpid.proton.amqp.transport.AmqpError;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.auth.HonoUser;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.service.auth.AuthorizationService;
import org.eclipse.hono.service.auth.ClaimsBasedAuthorizationService;
import org.eclipse.hono.util.AmqpErrorException;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.EventBusMessage;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.ResourceIdentifier;
import org.springframework.beans.factory.annotation.Autowired;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.ProtonSender;

/**
 * An abstract base class for implementing endpoints that implement a request response pattern.
 * <p>
 * It is used e.g. in the implementation of the device registration and the credentials API endpoints.
 * 
 * @param <T> The type of configuration properties this endpoint uses.
 */
public abstract class RequestResponseEndpoint<T extends ServiceConfigProperties> extends AbstractAmqpEndpoint<T> {

    private static final int REQUEST_RESPONSE_ENDPOINT_DEFAULT_CREDITS = 20;

    private int receiverLinkCredit = REQUEST_RESPONSE_ENDPOINT_DEFAULT_CREDITS;
    private AuthorizationService authorizationService = new ClaimsBasedAuthorizationService();

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
     * Processes an AMQP message received from a client.
     *
     * @param request The Message to process. Must not be null.
     * @param targetAddress The address the message is sent to.
     * @param clientPrincipal The principal representing the client identity and its authorities.
     * @throws DecodeException if the message's payload does not contain a valid JSON string.
     * @throws NullPointerException if message is {@code null}.
     */
    public abstract void processRequest(Message request, ResourceIdentifier targetAddress, HonoUser clientPrincipal);

    /**
     * Creates an AMQP message for a service response.
     *
     * @param response The response to create the AMQP message for.
     * @return The AMQP message.
     * @throws NullPointerException If response is {@code null}.
     */
    protected abstract Message getAmqpReply(EventBusMessage response);

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
     * @throws IllegalArgumentException if the credit is &lt;= 0.
     */
    public final void setReceiverLinkCredit(final int receiverLinkCredit) {
        if (receiverLinkCredit <= 0) {
            throw new IllegalArgumentException("receiver link credit must be at least 1");
        }
        this.receiverLinkCredit = receiverLinkCredit;
    }

    /**
     * Gets the object to use for making authorization decisions.
     * 
     * @return The service.
     */
    public final AuthorizationService getAuthorizationService() {
        return authorizationService;
    }

    /**
     * Sets the object to use for making authorization decisions.
     * <p>
     * If not set a {@link ClaimsBasedAuthorizationService} instance is used.
     * 
     * @param authService The service.
     */
    @Autowired(required = false)
    public final void setAuthorizationService(final AuthorizationService authService) {
        this.authorizationService = authService;
    }

    /**
     * Configure and check the receiver link of the endpoint.
     * The remote link of the receiver must not demand the AT_MOST_ONCE QoS (not supported).
     * The receiver link itself is configured with the AT_LEAST_ONCE QoS and grants the configured credits ({@link #setReceiverLinkCredit(int)})
     * with autoAcknowledge.
     * <p>
     * Handling of received messages is delegated to {@link #handleMessage(ProtonConnection, ProtonReceiver, ResourceIdentifier, ProtonDelivery, Message)}.
     *
     * @param con The AMQP connection that the link is part of.
     * @param receiver The ProtonReceiver that has already been created for this endpoint.
     * @param targetAddress The resource identifier for this endpoint (see {@link ResourceIdentifier} for details).
     */
    @Override
    public final void onLinkAttach(final ProtonConnection con, final ProtonReceiver receiver, final ResourceIdentifier targetAddress) {
        if (ProtonQoS.AT_MOST_ONCE.equals(receiver.getRemoteQoS())) {
            logger.debug("client wants to use unsupported AT MOST ONCE delivery mode for endpoint [{}], closing link ...", getName());
            receiver.setCondition(ProtonHelper.condition(AmqpError.PRECONDITION_FAILED.toString(), "endpoint requires AT_LEAST_ONCE QoS"));
            receiver.close();
        } else {

            logger.debug("establishing link for receiving messages from client [{}]", receiver.getName());
            receiver
                    .setQoS(ProtonQoS.AT_LEAST_ONCE)
                    .setAutoAccept(true) // settle received messages if the handler succeeds
                    .setPrefetch(receiverLinkCredit)
                    .handler((delivery, message) -> {
                        handleMessage(con, receiver, targetAddress, delivery, message);
                    }).closeHandler(clientDetached -> onLinkDetach(receiver))
                    .open();
        }
    }

    /**
     * Handles a request message received from a client.
     * <p>
     * The message gets rejected if
     * <ul>
     * <li>the message does not pass {@linkplain #passesFormalVerification(ResourceIdentifier, Message) formal verification}
     * or</li>
     * <li>the client is not {@linkplain #isAuthorized(HonoUser, ResourceIdentifier, Message) authorized to execute the operation}
     * indicated by the message's <em>subject</em> or</li>
     * <li>its payload cannot be parsed</li>
     * </ul>
     * 
     * @param con The connection with the client.
     * @param receiver The link over which the message has been received.
     * @param targetAddress The address the message is sent to.
     * @param delivery The message's delivery status.
     * @param message The message.
     */
    protected final void handleMessage(final ProtonConnection con, final ProtonReceiver receiver,
            final ResourceIdentifier targetAddress, final ProtonDelivery delivery, final Message message) {

        final Future<Void> formalCheck = Future.future();
        if (passesFormalVerification(targetAddress, message)) {
            formalCheck.complete();
        } else {
            formalCheck.fail(new AmqpErrorException(AmqpError.DECODE_ERROR, "malformed payload"));
        }
        final HonoUser clientPrincipal = Constants.getClientPrincipal(con);
        formalCheck.compose(ok -> isAuthorized(clientPrincipal, targetAddress, message)).compose(authorized -> {

            logger.debug("client [{}] is {}authorized to {}:{}", clientPrincipal.getName(), authorized ? "" : "not ",
                    targetAddress, message.getSubject());
            if (authorized) {
                try {
                    processRequest(message, targetAddress, clientPrincipal);
                    ProtonHelper.accepted(delivery, true);
                    return Future.succeededFuture();
                } catch (DecodeException e) {
                    return Future.failedFuture(new AmqpErrorException(AmqpError.DECODE_ERROR, "malformed payload"));
                }
            } else {
                return Future.failedFuture(new AmqpErrorException(AmqpError.UNAUTHORIZED_ACCESS, "unauthorized"));
            }

        }).otherwise(t -> {

            if (t instanceof AmqpErrorException) {
                AmqpErrorException cause = (AmqpErrorException) t;
                MessageHelper.rejected(delivery, cause.asErrorCondition());
            } else {
                logger.debug("error processing request [resource: {}, op: {}]: {}", targetAddress, message.getSubject(), t.getMessage());
                MessageHelper.rejected(delivery, ProtonHelper.condition(AmqpError.INTERNAL_ERROR, "internal error"));
            }
            return null;
        });
    }

    /**
     * Checks if the client is authorized to execute a given operation.
     * 
     * This method is invoked for every request message received from a client.
     * <p>
     * This default implementation simply delegates to {@link AuthorizationService#isAuthorized(HonoUser, ResourceIdentifier, String)}.
     * <p>
     * Subclasses may override this method in order to do more sophisticated checks.
     * 
     * @param clientPrincipal The client.
     * @param resource The resource the message belongs to.
     * @param message The message for which the authorization shall be checked.
     * @return A future indicating the outcome of the check.
     *         The future will be succeeded if the client is authorized to execute the operation.
     *         Otherwise the future will be failed with a {@link ServiceInvocationException}.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    protected Future<Boolean> isAuthorized(final HonoUser clientPrincipal, final ResourceIdentifier resource, final Message message) {

        Objects.requireNonNull(message);
        return getAuthorizationService().isAuthorized(clientPrincipal, resource, message.getSubject());
    }

    /**
     * Applies arbitrary filters on the response before it is sent to the client.
     * <p>
     * Subclasses may override this method in order to e.g. filter the payload based on
     * the client's authorities.
     * <p>
     * This default implementation simply returns a succeeded future containing the
     * original response.
     * 
     * @param clientPrincipal The client's identity and authorities.
     * @param response The response to send to the client.
     * @return A future indicating the outcome.
     *         If the future succeeds it will contain the (filtered) response to be sent to the client.
     *         Otherwise the future will fail with a {@link ServiceInvocationException} indicating the
     *         problem.
     */
    protected Future<EventBusMessage> filterResponse(final HonoUser clientPrincipal, final EventBusMessage response) {

        return Future.succeededFuture(Objects.requireNonNull(response));
    }

    /**
     * Handles a client's request to establish a link for receiving responses
     * to service invocations.
     * <p>
     * This method registers a consumer on the vert.x event bus for the given reply-to address.
     * Response messages received over the event bus are transformed into AMQP messages using
     * the {@link #getAmqpReply(EventBusMessage)} method and sent to the client over the established
     * link.
     *
     * @param con The AMQP connection that the link is part of.
     * @param sender The link to establish.
     * @param replyToAddress The reply-to address to create a consumer on the event bus for.
     */
    @Override
    public final void onLinkAttach(final ProtonConnection con, final ProtonSender sender, final ResourceIdentifier replyToAddress) {

        if (isValidReplyToAddress(replyToAddress)) {
            logger.debug("establishing sender link with client [{}]", sender.getName());
            final MessageConsumer<JsonObject> replyConsumer = vertx.eventBus().consumer(replyToAddress.toString(), message -> {
                // TODO check for correct session here...?
                if (logger.isTraceEnabled()) {
                    logger.trace("forwarding reply to client [{}]: {}", sender.getName(), message.body().encodePrettily());
                }
                final EventBusMessage response = EventBusMessage.fromJson(message.body());
                filterResponse(Constants.getClientPrincipal(con), response)
                    .recover(t -> {
                        final int status = Optional.of(t).map(cause -> {
                            if (cause instanceof ServiceInvocationException) {
                                return ((ServiceInvocationException) cause).getErrorCode();
                            } else {
                                return null;
                            }
                        }).orElse(HttpURLConnection.HTTP_INTERNAL_ERROR);
                        return Future.succeededFuture(response.getResponse(status));
                    }).map(filteredResponse -> {
                        final Message amqpReply = getAmqpReply(filteredResponse);
                        sender.send(amqpReply);
                        return null;
                    });
            });

            sender.setQoS(ProtonQoS.AT_LEAST_ONCE);
            sender.closeHandler(senderClosed -> {
                logger.debug("client [{}] closed sender link, removing associated event bus consumer [{}]", sender.getName(), replyConsumer.address());
                replyConsumer.unregister();
                if (senderClosed.succeeded()) {
                    senderClosed.result().close();
                }
            });
            sender.open();
        } else {
            logger.debug("client [{}] provided invalid reply-to address", sender.getName());
            sender.setCondition(ProtonHelper.condition(AmqpError.INVALID_FIELD,
                    String.format("reply-to address must have the following format %s/<tenant>/<reply-address>", getName())));
            sender.close();
        }
    }

    /**
     * Checks if a resource identifier constitutes a valid reply-to address
     * for this service endpoint.
     * <p>
     * This method is invoked during establishment of the reply-to link between
     * the client and this endpoint. The link will only be established if this method
     * returns {@code true}.
     * <p>
     * This default implementation verifies that the address consists of three
     * segments: an endpoint identifier, a tenant identifier and a resource identifier.
     * <p>
     * Subclasses should override this method if the service they provide an endpoint for
     * use a different reply-to address format.
     * 
     * @param replyToAddress The address to check.
     * @return {@code true} if the address is valid.
     */
    protected boolean isValidReplyToAddress(final ResourceIdentifier replyToAddress) {

        if (replyToAddress == null) {
            return false;
        } else {
            return replyToAddress.getResourcePath().length >= 3;
        }
    }
}
