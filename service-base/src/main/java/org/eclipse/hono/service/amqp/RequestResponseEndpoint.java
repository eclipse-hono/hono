/*******************************************************************************
 * Copyright (c) 2016, 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.service.amqp;

import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.apache.qpid.proton.amqp.transport.AmqpError;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.auth.HonoUser;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.service.auth.AuthorizationService;
import org.eclipse.hono.service.auth.ClaimsBasedAuthorizationService;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.EventBusMessage;
import org.eclipse.hono.util.HonoProtonHelper;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RequestResponseApiConstants;
import org.eclipse.hono.util.ResourceIdentifier;
import org.springframework.beans.factory.annotation.Autowired;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.tag.Tags;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.ReplyException;
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

    private final Map<String, ProtonSender> replyToSenderMap = new HashMap<>();

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
     * Gets the address on the vert.x event bus to which service invocation
     * requests should be sent.
     * 
     * @return The address.
     */
    protected abstract String getEventBusServiceAddress();

    /**
     * Creates the message to send to the service implementation
     * via the vert.x event bus in order to invoke an operation.
     * 
     * @param requestMessage The AMQP message representing the service invocation request.
     * @param targetAddress The address the message is sent to.
     * @param clientPrincipal The principal representing the client identity and its authorities.
     * @return A future indicating the outcome of the operation.
     */
    protected abstract Future<EventBusMessage> createEventBusRequestMessage(
            Message requestMessage,
            ResourceIdentifier targetAddress,
            HonoUser clientPrincipal);

    /**
     * Creates an AMQP message for a service response.
     *
     * @param response The response to create the AMQP message for.
     * @return The AMQP message.
     * @throws NullPointerException If response is {@code null}.
     */
    protected abstract Message getAmqpReply(EventBusMessage response);

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
     * Handles a client's request to establish a link for sending service invocation requests.
     * <p>
     * Configure and check the receiver link of the endpoint.
     * The remote link of the receiver must not demand the AT_MOST_ONCE QoS (not supported).
     * The receiver link itself is configured with the AT_LEAST_ONCE QoS and grants the configured credits
     * ({@link ServiceConfigProperties#getReceiverLinkCredit()}) with autoAcknowledge.
     * <p>
     * Handling of request messages is delegated to
     * {@link #handleRequestMessage(ProtonConnection, ProtonReceiver, ResourceIdentifier, ProtonDelivery, Message)}.
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

            logger.debug("establishing link for receiving request messages from client [{}]", receiver.getName());

            receiver.setQoS(ProtonQoS.AT_LEAST_ONCE);
            receiver.setAutoAccept(true); // settle received messages if the handler succeeds
            receiver.setTarget(receiver.getRemoteTarget());
            receiver.setSource(receiver.getRemoteSource());
            // We do manual flow control, credits are replenished after responses have been sent.
            receiver.setPrefetch(0);

            // set up handlers

            receiver.handler((delivery, message) -> {
                try {
                    handleRequestMessage(con, receiver, targetAddress, delivery, message);
                } catch (final Exception ex) {
                    logger.warn("error handling message", ex);
                    ProtonHelper.released(delivery, true);
                }
            });
            HonoProtonHelper.setCloseHandler(receiver, remoteClose -> onLinkDetach(receiver));
            HonoProtonHelper.setDetachHandler(receiver, remoteDetach -> onLinkDetach(receiver));

            // acknowledge the remote open
            receiver.open();

            // send out initial credits, after opening
            logger.debug("flowing {} credits to client", config.getReceiverLinkCredit());
            receiver.flow(config.getReceiverLinkCredit());
        }
    }

    /**
     * Handles a request message received from a client.
     * <p>
     * The message gets rejected if
     * <ul>
     * <li>the message does not pass {@linkplain #passesFormalVerification(ResourceIdentifier, Message) formal
     * verification} or</li>
     * <li>the client is not {@linkplain #isAuthorized(HonoUser, ResourceIdentifier, Message) authorized to execute the
     * operation} indicated by the message's <em>subject</em> or</li>
     * <li>its payload cannot be parsed</li>
     * </ul>
     * 
     * @param con The connection with the client.
     * @param receiver The link over which the message has been received.
     * @param targetAddress The address the message is sent to.
     * @param delivery The message's delivery status.
     * @param requestMessage The request message.
     */
    protected final void handleRequestMessage(
            final ProtonConnection con,
            final ProtonReceiver receiver,
            final ResourceIdentifier targetAddress,
            final ProtonDelivery delivery,
            final Message requestMessage) {

        final HonoUser clientPrincipal = Constants.getClientPrincipal(con);
        final String replyTo = requestMessage.getReplyTo();
        final SpanContext spanContext = TracingHelper.extractSpanContext(tracer, requestMessage);
        final Span currentSpan = TracingHelper.buildChildSpan(tracer, spanContext, "process request message")
                .ignoreActiveSpan()
                .withTag(Tags.COMPONENT.getKey(), getName())
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER)
                .withTag(Tags.HTTP_METHOD.getKey(), requestMessage.getSubject())
                .withTag(Tags.MESSAGE_BUS_DESTINATION.getKey(), targetAddress.toString())
                .start();

        if (!passesFormalVerification(targetAddress, requestMessage)) {
            MessageHelper.rejected(delivery, new ErrorCondition(Constants.AMQP_BAD_REQUEST, "malformed request message"));
            flowCreditToRequestor(receiver, replyTo);
            TracingHelper.logError(currentSpan, "malformed request message");
            currentSpan.finish();
            return;
        }

        ProtonHelper.accepted(delivery, true);
        currentSpan.log("request message accepted");

        final Future<ProtonSender> sender = getSenderForConnection(con, replyTo);
        final Future<EventBusMessage> request = createEventBusRequestMessage(requestMessage, targetAddress, clientPrincipal);

        CompositeFuture.all(request, sender)
        .compose(ok -> isAuthorized(clientPrincipal, targetAddress, requestMessage))
        .map(authorized -> {

            logger.debug("client [{}] is {}authorized to {}:{}", clientPrincipal.getName(),
                    authorized ? "" : "not ", targetAddress, requestMessage.getSubject());

            if (authorized) {
                return authorized;
            } else {
                throw new ClientErrorException(HttpURLConnection.HTTP_FORBIDDEN, "not authorized to invoke operation");
            }
        })
        .compose(authorized -> {
            final Promise<io.vertx.core.eventbus.Message<Object>> reply = Promise.promise();
            final DeliveryOptions options = createEventBusMessageDeliveryOptions(config.getSendTimeOut(),
                    currentSpan.context());
            vertx.eventBus().request(
                    getEventBusServiceAddress(),
                    request.result().toJson(),
                    options,
                    reply);
            return reply.future();
        })
        .map(reply -> extractResponse(reply))
        .compose(eventBusMessage -> filterResponse(clientPrincipal, eventBusMessage))
        .map(filteredResponse -> {
            Tags.HTTP_STATUS.set(currentSpan, filteredResponse.getStatus());
            return getAmqpReply(filteredResponse);
        })
        .otherwise(t -> {

            logger.debug("error processing request [resource: {}, op: {}]: {}", targetAddress,
                    requestMessage.getSubject(), t.getMessage());
            currentSpan.log("error processing request");
            TracingHelper.logError(currentSpan, t);

            final ServiceInvocationException ex = getServiceInvocationException(t);
            Tags.HTTP_STATUS.set(currentSpan, ex.getErrorCode());
            return RequestResponseApiConstants.getErrorMessage(ex.getErrorCode(), ex.getMessage(), requestMessage);
        })
        .map(amqpMessage -> {
            if (sender.result().isOpen()) {
                final ProtonDelivery responseDelivery = sender.result().send(amqpMessage);
                logger.debug("sent response message to client  [correlation-id: {}, content-type: {}]",
                        amqpMessage.getCorrelationId(), amqpMessage.getContentType());
                currentSpan.log("sent response message to client");
                return responseDelivery;
            } else {
                TracingHelper.logError(currentSpan, "cannot send response, reply-to link is closed");
                return null;
            }
        })
        .setHandler(s -> {
            // allow client to send another request
            flowCreditToRequestor(receiver, replyTo);
            currentSpan.finish();
        });
    }

    private ServiceInvocationException getServiceInvocationException(final Throwable error) {

        if (error instanceof ServiceInvocationException) {
            return (ServiceInvocationException) error;
        } else if (error instanceof ReplyException) {
            final ReplyException ex = (ReplyException) error;
            switch (ex.failureType()) {
            case TIMEOUT:
                return new ServerErrorException(
                        HttpURLConnection.HTTP_UNAVAILABLE,
                        "request could not be processed at the moment");
            default:
                return new ServerErrorException(HttpURLConnection.HTTP_INTERNAL_ERROR);
            }
        } else {
            return new ServerErrorException(HttpURLConnection.HTTP_INTERNAL_ERROR);
        }
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

    private EventBusMessage extractResponse(final io.vertx.core.eventbus.Message<Object> response) {

        if (response.body() instanceof JsonObject) {

            final JsonObject body = (JsonObject) response.body();
            return EventBusMessage.fromJson(body);
        } else {
            throw new ServerErrorException(HttpURLConnection.HTTP_INTERNAL_ERROR);
        }
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
     * Handles a client's request to establish a link for receiving responses to service invocations.
     * <p>
     * This method registers a consumer on the vert.x event bus for the given reply-to address. Response messages
     * received over the event bus are transformed into AMQP messages using the {@link #getAmqpReply(EventBusMessage)}
     * method and sent to the client over the established link.
     *
     * @param con The AMQP connection that the link is part of.
     * @param sender The link to establish.
     * @param replyToAddress The reply-to address to create a consumer on the event bus for.
     */
    @Override
    public final void onLinkAttach(final ProtonConnection con, final ProtonSender sender,
            final ResourceIdentifier replyToAddress) {

        if (!isValidReplyToAddress(replyToAddress)) {
            logger.debug("client [{}] provided invalid reply-to address", sender.getName());
            sender.setCondition(ProtonHelper.condition(AmqpError.INVALID_FIELD,
                    String.format("reply-to address must have the following format %s/<tenant>/<reply-address>",
                            getName())));
            sender.close();
            return;
        }

        final String replyTo = replyToAddress.toString();

        if (this.replyToSenderMap.containsKey(replyTo)) {
            logger.debug("client [{}] wanted to subscribe to already subscribed reply-to address [{}]",
                    sender.getName(), replyTo);
            sender.setCondition(ProtonHelper.condition(AmqpError.ILLEGAL_STATE,
                    String.format("reply-to address [%s] is already subscribed", replyTo)));
            sender.close();
            return;
        }

        logger.debug("establishing response sender link with client [{}]", sender.getName());
        sender.setQoS(ProtonQoS.AT_LEAST_ONCE);
        sender.setSource(sender.getRemoteSource());
        sender.setTarget(sender.getRemoteTarget());
        registerSenderForReplyTo(replyTo, sender);


        HonoProtonHelper.setCloseHandler(sender, remoteClose -> {
            logger.debug("client [{}] closed sender link", sender.getName());
            unregisterSenderForReplyTo(replyTo);
            sender.close();
        });
        HonoProtonHelper.setDetachHandler(sender, remoteDetach -> {
            logger.debug("client [{}] detached sender link", sender.getName());
            unregisterSenderForReplyTo(replyTo);
            sender.close();
        });

        sender.open();
    }

    @Override
    public void onConnectionClosed(final ProtonConnection connection) {

        Objects.requireNonNull(connection);
        deallocateAllSendersForConnection(connection);
    }

    private Future<ProtonSender> getSenderForConnection(final ProtonConnection con, final String replytoAddress) {

        final Promise<ProtonSender> result = Promise.promise();
        final ProtonSender sender = replyToSenderMap.get(replytoAddress);
        if (sender != null && sender.isOpen() && sender.getSession().getConnection() == con) {
            result.complete(sender);
        } else {
            result.fail(new ClientErrorException(
                    HttpURLConnection.HTTP_PRECON_FAILED,
                    "must open receiver link for reply-to address first"));
        }
        return result.future();
    }

    private void registerSenderForReplyTo(final String replyTo, final ProtonSender sender) {

        final ProtonSender oldSender = replyToSenderMap.put(replyTo, sender);

        if (oldSender == null || oldSender == sender) {
            logger.debug("registered sender [{}] for replies to [{}]", sender, replyTo);
        } else {
            logger.info("replaced existing sender [{}] for replies to [{}] with sender [{}]",
                    oldSender, replyTo, sender);
        }
    }

    private void unregisterSenderForReplyTo(final String replyTo) {

        final ProtonSender sender = replyToSenderMap.remove(replyTo);
        if (sender == null) {
            logger.warn("sender was not allocated for replyTo address [{}]", replyTo);
        } else {
            logger.debug("deallocated sender [{}] for replies to [{}]", sender.getName(), replyTo);
        }

    }

    private void deallocateAllSendersForConnection(final ProtonConnection connection) {
        replyToSenderMap
                .entrySet()
                .removeIf(entry -> entry.getValue().getSession().getConnection() == connection);
    }

    private void flowCreditToRequestor(final ProtonReceiver receiver, final String replyTo) {

        receiver.flow(1);
        logger.trace("replenished client [reply-to: {}, current credit: {}]", replyTo,
                receiver.getCredit());
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
     * uses a different reply-to address format.
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
