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
 */

package org.eclipse.hono.client.impl;

import java.net.HttpURLConnection;
import java.util.Objects;

import org.apache.qpid.proton.amqp.messaging.Accepted;
import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.TelemetryConstants;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonSender;

/**
 * A Vertx-Proton based client for uploading telemetry data to a Hono server.
 */
public final class TelemetrySenderImpl extends AbstractSender {

    TelemetrySenderImpl(final ClientConfigProperties config, final ProtonSender sender, final String tenantId,
            final String targetAddress, final Context context) {
        super(config, sender, tenantId, targetAddress, context);
    }

    /**
     * Gets the AMQP <em>target</em> address to use for uploading data to Hono's telemetry endpoint.
     * 
     * @param tenantId The tenant to upload data for.
     * @param deviceId The device to upload data for. If {@code null}, the target address can be used
     *                 to upload data for arbitrary devices belonging to the tenant.
     * @return The target address.
     * @throws NullPointerException if tenant is {@code null}.
     */
    public static String getTargetAddress(final String tenantId, final String deviceId) {
        StringBuilder targetAddress = new StringBuilder(TelemetryConstants.TELEMETRY_ENDPOINT)
                .append("/").append(Objects.requireNonNull(tenantId));
        if (deviceId != null && deviceId.length() > 0) {
            targetAddress.append("/").append(deviceId);
        }
        return targetAddress.toString();
    }

    @Override
    public String getEndpoint() {
        return TelemetryConstants.TELEMETRY_ENDPOINT;
    }

    @Override
    protected String getTo(final String deviceId) {
        return getTargetAddress(tenantId, deviceId);
    }

    /**
     * Creates a new sender for publishing telemetry data to a Hono server.
     * 
     * @param context The vertx context to run all interactions with the server on.
     * @param clientConfig The configuration properties to use.
     * @param con The connection to the Hono server.
     * @param tenantId The tenant that the telemetry data will be uploaded for.
     * @param deviceId The device that the telemetry data will be uploaded for or {@code null}
     *                 if the data to be uploaded will be produced by arbitrary devices of the
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
                    new TelemetrySenderImpl(clientConfig, sender, tenantId, targetAddress, context));
        }).setHandler(creationHandler);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<ProtonDelivery> sendAndWaitForOutcome(final Message rawMessage) {

        Objects.requireNonNull(rawMessage);

        if (!isRegistrationAssertionRequired()) {
            MessageHelper.getAndRemoveRegistrationAssertion(rawMessage);
        }
        final Future<ProtonDelivery> result = Future.future();
        context.runOnContext(send -> {
            if (sender.sendQueueFull()) {
                result.fail(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE, "no credit available"));
            } else {
                sendMessageAndWaitForOutcome(rawMessage).setHandler(result.completer());
            }
        });
        return result;
    }

    /**
     * Sends an AMQP 1.0 message to the peer this client is configured for.
     * 
     * @param message The message to send.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will succeed if the message has been sent to the peer.
     *         The delivery contained in the future will represent the delivery
     *         state at the time the future has been succeeded, i.e. it will be
     *         locally <em>unsettled</em> without any outcome yet.
     *         <p>
     *         The future will be failed with a {@link ServiceInvocationException} if the
     *         message could not be sent.
     * @throws NullPointerException if the message is {@code null}.
     */
    @Override
    protected Future<ProtonDelivery> sendMessage(final Message message) {

        Objects.requireNonNull(message);

        final String messageId = String.format("%s-%d", getClass().getSimpleName(), MESSAGE_COUNTER.getAndIncrement());
        message.setMessageId(messageId);
        final ProtonDelivery result = sender.send(message, deliveryUpdated -> {
            if (deliveryUpdated.remotelySettled()) {
                if (Accepted.class.isInstance(deliveryUpdated.getRemoteState())) {
                    LOG.trace("message [message ID: {}] accepted by peer", messageId);
                } else if (Rejected.class.isInstance(deliveryUpdated.getRemoteState())) {
                    Rejected remoteState = (Rejected) deliveryUpdated.getRemoteState();
                    if (remoteState.getError() == null) {
                        LOG.debug("message [message ID: {}] rejected by peer", messageId);
                    } else {
                        LOG.debug("message [message ID: {}] rejected by peer: {}, {}", messageId,
                                remoteState.getError().getCondition(), remoteState.getError().getDescription());
                    }
                } else {
                    LOG.debug("message [message ID: {}] not accepted by peer: {}", messageId, deliveryUpdated.getRemoteState());
                }
            } else {
                LOG.warn("peer did not settle message [message ID: {}, remote state: {}]", messageId, deliveryUpdated.getRemoteState());
            }
        });
        LOG.trace("sent message [ID: {}], remaining credit: {}, queued messages: {}", messageId, sender.getCredit(), sender.getQueued());

        return Future.succeededFuture(result);
    }
}
