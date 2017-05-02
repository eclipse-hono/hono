/**
 * Copyright (c) 2016, 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */
package org.eclipse.hono.service.registration;

import static io.vertx.proton.ProtonHelper.condition;
import static org.eclipse.hono.util.MessageHelper.*;
import static org.eclipse.hono.util.RegistrationConstants.APP_PROPERTY_CORRELATION_ID;
import static org.eclipse.hono.util.RegistrationConstants.EVENT_BUS_ADDRESS_REGISTRATION_IN;

import java.net.HttpURLConnection;
import java.util.Objects;

import org.apache.qpid.proton.amqp.transport.AmqpError;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.service.amqp.BaseEndpoint;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.ResourceIdentifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import io.vertx.core.Vertx;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.ProtonSender;

/**
 * An {@code Endpoint} for managing device registration information.
 * <p>
 * This endpoint implements Hono's <a href="https://www.eclipse.org/hono/api/Device-Registration-API/">Device Registration API</a>.
 * It receives AMQP 1.0 messages representing requests and sends them to an address on the vertx
 * event bus for processing. The outcome is then returned to the peer in a response message.
 */
@Component
@Qualifier("registration")
public final class RegistrationEndpoint extends BaseEndpoint {

    /**
     * Creates a new registration endpoint for a vertx instance.
     * 
     * @param vertx The vertx instance to use.
     */
    @Autowired
    public RegistrationEndpoint(final Vertx vertx) {
        super(Objects.requireNonNull(vertx));
    }

    @Override
    public String getName() {
        return RegistrationConstants.REGISTRATION_ENDPOINT;
    }

    @Override
    public void onLinkAttach(final ProtonReceiver receiver, final ResourceIdentifier targetAddress) {
        if (ProtonQoS.AT_MOST_ONCE.equals(receiver.getRemoteQoS())) {
            logger.debug("client wants to use AT MOST ONCE delivery mode for registration endpoint, this is not supported.");
            receiver.setCondition(condition(AmqpError.PRECONDITION_FAILED.toString(), "endpoint requires AT_LEAST_ONCE QoS"));
            receiver.close();
        } else {
    
            logger.debug("establishing link for receiving registration messages from client [{}]", MessageHelper.getLinkName(receiver));
            receiver
                .setQoS(ProtonQoS.AT_LEAST_ONCE)
                .setAutoAccept(true) // settle received messages if the handler succeeds
                .setPrefetch(20)
                .handler((delivery, message) -> {
                    if (RegistrationMessageFilter.verify(targetAddress, message)) {
                        try {
                            processRequest(message);
                        } catch (DecodeException e) {
                            MessageHelper.rejected(delivery, AmqpError.DECODE_ERROR.toString(), "malformed payload");
                        }
                    } else {
                        MessageHelper.rejected(delivery, AmqpError.DECODE_ERROR.toString(), "malformed registration message");
                        // we close the link if the client sends a message that does not comply with the API spec
                        onLinkDetach(receiver, condition(AmqpError.DECODE_ERROR.toString(), "invalid message received"));
                    }
                }).closeHandler(clientDetached -> onLinkDetach(clientDetached.result()))
                .open();
        }
    }

    @Override
    public void onLinkAttach(final ProtonSender sender, final ResourceIdentifier targetResource) {
        /* note: we "misuse" deviceId part of the resource as reply address here */
        if (targetResource.getResourceId() == null) {
            logger.debug("link target provided in client's link ATTACH does not match pattern \"registration/<tenant>/<reply-address>\"");
            sender.setCondition(condition(AmqpError.INVALID_FIELD.toString(),
                    "link target must have the following format registration/<tenant>/<reply-address>"));
            sender.close();
        } else {
            logger.debug("establishing sender link with client [{}]", MessageHelper.getLinkName(sender));
            final MessageConsumer<JsonObject> replyConsumer = vertx.eventBus().consumer(targetResource.toString(), message -> {
                // TODO check for correct session here...?
                logger.trace("forwarding reply to client: {}", message.body());
                final Message amqpReply = RegistrationConstants.getAmqpReply(message);
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

    private void onLinkDetach(final ProtonReceiver client) {
        onLinkDetach(client, null);
    }

    private void onLinkDetach(final ProtonReceiver client, final ErrorCondition condition) {
        logger.debug("closing receiver for client [{}]", getLinkName(client));
        client.close();
    }

    private void processRequest(final Message msg) {

        final JsonObject registrationMsg = RegistrationConstants.getRegistrationMsg(msg);
        vertx.eventBus().send(EVENT_BUS_ADDRESS_REGISTRATION_IN, registrationMsg,
                result -> {
                    JsonObject response = null;
                    if (result.succeeded()) {
                        // TODO check for correct session here...?
                        response = (JsonObject) result.result().body();
                    } else {
                        logger.debug("failed to process request [msg ID: {}] due to {}", msg.getMessageId(), result.cause());
                        // we need to inform client about failure
                        response = RegistrationConstants.getReply(
                                HttpURLConnection.HTTP_INTERNAL_ERROR,
                                MessageHelper.getTenantIdAnnotation(msg),
                                MessageHelper.getDeviceIdAnnotation(msg),
                                null);
                    }
                    addHeadersToResponse(msg, response);
                    vertx.eventBus().send(msg.getReplyTo(), response);
                });
    }

    private void addHeadersToResponse(final Message request, final JsonObject message) {
        final boolean isApplicationCorrelationId = MessageHelper.getXOptAppCorrelationId(request);
        logger.debug("registration request [{}] uses application specific correlation ID: {}", request.getMessageId(), isApplicationCorrelationId);
        if (isApplicationCorrelationId) {
            message.put(ANNOTATION_X_OPT_APP_CORRELATION_ID, isApplicationCorrelationId);
        }
        final JsonObject correlationIdJson = encodeIdToJson(getCorrelationId(request));
        message.put(APP_PROPERTY_CORRELATION_ID, correlationIdJson);
    }

    /**
    * @param request the request message from which to extract the correlationId
    * @return The ID used to correlate the given request message. This can either be the provided correlationId
    * (Correlation ID Pattern) or the messageId of the request (Message ID Pattern, if no correlationId is provided).
    */
    private Object getCorrelationId(final Message request) {
        /* if a correlationId is provided, we use it to correlate the response -> Correlation ID Pattern */
        if (request.getCorrelationId() != null) {
            return request.getCorrelationId();
        } else {
           /* otherwise we use the message id -> Message ID Pattern */
            return request.getMessageId();
        }
    }
}
