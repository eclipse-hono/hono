/**
 * Copyright (c) 2016, 2018 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 *    Red Hat Inc
 *
 */

package org.eclipse.hono.client.impl;

import java.net.HttpURLConnection;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.qpid.proton.amqp.messaging.Accepted;
import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.util.EventConstants;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonSender;

/**
 * A Vertx-Proton based client for publishing event messages to a Hono server.
 */
public final class EventSenderImpl extends AbstractSender {

    private static final AtomicLong MESSAGE_COUNTER = new AtomicLong();

    EventSenderImpl(final ClientConfigProperties config, final ProtonSender sender, final String tenantId,
            final String targetAddress, final Context context) {
        super(config, sender, tenantId, targetAddress, context);
    }

    /**
     * Gets the AMQP <em>target</em> address to use for sending messages to Hono's event endpoint.
     * 
     * @param tenantId The tenant to send events for.
     * @param deviceId The device to send events for. If {@code null}, the target address can be used
     *                 to send events for arbitrary devices belonging to the tenant.
     * @return The target address.
     * @throws NullPointerException if tenant is {@code null}.
     */
    public static String getTargetAddress(final String tenantId, final String deviceId) {
        StringBuilder address = new StringBuilder(EventConstants.EVENT_ENDPOINT).append("/").append(tenantId);
        if (deviceId != null && deviceId.length() > 0) {
            address.append("/").append(deviceId);
        }
        return address.toString();
    }

    @Override
    public String getEndpoint() {
        return EventConstants.EVENT_ENDPOINT;
    }

    @Override
    protected String getTo(final String deviceId) {
        return getTargetAddress(tenantId, deviceId);
    }

    /**
     * Creates a new sender for publishing events to a Hono server.
     * 
     * @param context The vertx context to run all interactions with the server on.
     * @param clientConfig The configuration properties to use.
     * @param con The connection to the Hono server.
     * @param tenantId The tenant that the events will be published for.
     * @param deviceId The device that the events will be published for or {@code null}
     *                 if the events are going to be be produced by arbitrary devices of the
     *                 tenant.
     * @param closeHook The handler to invoke when the Hono server closes the sender. The sender's
     *                  target address is provided as an argument to the handler.
     * @param creationHandler The handler to invoke with the result of the creation attempt.
     * @throws NullPointerException if any of context, connection, tenant or handler is {@code null}.
     * @throws IllegalArgumentException if waitForInitialCredits is {@code < 1}.
     */
    public static void create(
            final Context context,
            final ClientConfigProperties clientConfig,
            final ProtonConnection con,
            final String tenantId,
            final String deviceId,
            final Handler<String> closeHook,
            final Handler<AsyncResult<MessageSender>> creationHandler) {

        Objects.requireNonNull(context);
        Objects.requireNonNull(con);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(creationHandler);

        final String targetAddress = getTargetAddress(tenantId, deviceId);
        createSender(context, clientConfig, con, targetAddress, ProtonQoS.AT_LEAST_ONCE, closeHook).compose(sender -> {
            return Future.<MessageSender> succeededFuture(
                    new EventSenderImpl(clientConfig, sender, tenantId, targetAddress, context));
        }).setHandler(creationHandler);
    }

    /**
     * Sets the <em>durable</em> message property to {@code true}.
     */
    @Override
    protected void addEndpointSpecificProperties(final Message msg, final String deviceId) {
        msg.setDurable(true);
    }

    @Override
    protected Future<ProtonDelivery> sendMessage(final Message message) {

        Objects.requireNonNull(message);

        final Future<ProtonDelivery> result = Future.future();
        final String messageId = String.format("%s-%d", getClass().getSimpleName(), MESSAGE_COUNTER.getAndIncrement());
        message.setMessageId(messageId);
        sender.send(message, deliveryUpdated -> {
            if (deliveryUpdated.remotelySettled()) {
                if (Accepted.class.isInstance(deliveryUpdated.getRemoteState())) {
                    LOG.trace("event [message ID: {}] accepted by peer", messageId);
                    result.complete(deliveryUpdated);
                } else if (Rejected.class.isInstance(deliveryUpdated.getRemoteState())) {
                    Rejected rejected = (Rejected) deliveryUpdated.getRemoteState();
                    if (rejected.getError() == null) {
                        LOG.debug("event [message ID: {}] rejected by peer", messageId);
                        result.fail(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
                    } else {
                        LOG.debug("event [message ID: {}] rejected by peer: {}, {}", messageId,
                                rejected.getError().getCondition(), rejected.getError().getDescription());
                        result.fail(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST, rejected.getError().getDescription()));
                    }
                } else {
                    LOG.debug("event [message ID: {}] not accepted by peer: {}", messageId, deliveryUpdated.getRemoteState());
                    result.fail(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
                }
            } else {
                LOG.warn("peer did not settle event, failing delivery [new remote state: {}]", deliveryUpdated.getRemoteState());
                result.fail(new ServerErrorException(HttpURLConnection.HTTP_INTERNAL_ERROR));
            }
        });
        LOG.trace("sent event [ID: {}], remaining credit: {}, queued messages: {}", messageId, sender.getCredit(), sender.getQueued());

        return result;
    }

}
