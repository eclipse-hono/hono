/**
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.hono.adapter.client.command.amqp;

import java.util.Objects;
import java.util.Optional;

import org.apache.qpid.proton.amqp.messaging.Accepted;
import org.apache.qpid.proton.amqp.messaging.Modified;
import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.amqp.messaging.Released;
import org.apache.qpid.proton.amqp.transport.DeliveryState;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.adapter.client.command.Command;
import org.eclipse.hono.adapter.client.command.CommandContext;
import org.eclipse.hono.adapter.client.command.InternalCommandSender;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.SendMessageSampler;
import org.eclipse.hono.client.StatusCodeMapper;
import org.eclipse.hono.client.amqp.SenderCachingServiceClient;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.MessageHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.vertx.core.Future;
import io.vertx.proton.ProtonHelper;

/**
 * A vertx-proton based sender for command messages to the internal Command and Control API endpoint provided by
 * protocol adapters.
 */
public class ProtonBasedInternalCommandSender extends SenderCachingServiceClient implements InternalCommandSender {

    private static final Logger LOG = LoggerFactory.getLogger(ProtonBasedInternalCommandSender.class);

    /**
     * Creates a new sender for a connection.
     *
     * @param connection The connection to the Hono service.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public ProtonBasedInternalCommandSender(final HonoConnection connection) {
        super(connection, SendMessageSampler.Factory.noop(), false);
    }

    @Override
    public Future<Void> sendCommand(final CommandContext commandContext, final String adapterInstanceId) {
        Objects.requireNonNull(commandContext);
        return getOrCreateSenderLink(getTargetAddress(adapterInstanceId))
                .recover(thr -> Future.failedFuture(StatusCodeMapper.toServerError(thr)))
                .compose(sender -> {
                    final Span span = newChildSpan(commandContext.getTracingContext(), "delegate Command request");
                    final Command command = commandContext.getCommand();
                    final Message message = adoptOrCreateMessage(command);
                    TracingHelper.setDeviceTags(span, command.getTenant(), command.getDeviceId());
                    if (command.isTargetedAtGateway()) {
                        MessageHelper.addProperty(message, MessageHelper.APP_PROPERTY_CMD_VIA, command.getGatewayId());
                        TracingHelper.TAG_GATEWAY_ID.set(span, command.getGatewayId());
                    }
                    return sender.sendAndWaitForRawOutcome(message, span);
                })
                .map(delivery -> {
                    final DeliveryState remoteState = delivery.getRemoteState();
                    LOG.trace("command [{}] sent to downstream peer; remote state of delivery: {}",
                            commandContext.getCommand(), remoteState);
                    if (Accepted.class.isInstance(remoteState)) {
                        commandContext.accept();
                    } else if (Rejected.class.isInstance(remoteState)) {
                        final Rejected rejected = (Rejected) remoteState;
                        commandContext.reject(Optional.ofNullable(rejected.getError())
                                .map(ErrorCondition::getDescription)
                                .orElse(null));
                    } else if (Released.class.isInstance(remoteState)) {
                        commandContext.release();
                    } else if (Modified.class.isInstance(remoteState)) {
                        final Modified modified = (Modified) remoteState;
                        commandContext.modify(modified.getDeliveryFailed(), modified.getUndeliverableHere());
                    }
                    return (Void) null;
                })
                .recover(thr -> {
                    LOG.debug("failed to send command [{}] to downstream peer", commandContext.getCommand(), thr);
                    TracingHelper.logError(commandContext.getTracingSpan(),
                            "failed to send command message to downstream peer: " + thr);
                    commandContext.release();
                    return Future.succeededFuture();
                });
    }

    /**
     * Gets the AMQP <em>target</em> address to use for sending the delegated command messages to.
     *
     * @param adapterInstanceId The protocol adapter instance id.
     * @return The target address.
     * @throws NullPointerException if adapterInstanceId is {@code null}.
     */
    static String getTargetAddress(final String adapterInstanceId) {
        return CommandConstants.INTERNAL_COMMAND_ENDPOINT + "/" + Objects.requireNonNull(adapterInstanceId);
    }

    private Message adoptOrCreateMessage(final Command command) {
        final Message msg;
        if (command instanceof ProtonBasedCommand) {
            // copy and adapt original message
            msg = MessageHelper.getShallowCopy(((ProtonBasedCommand) command).getMessage());
        } else {
            // create new message and adopt command properties
            msg = ProtonHelper.message();
            final byte[] payloadBytesOrNull = command.getPayload() != null ? command.getPayload().getBytes() : null;
            MessageHelper.setPayload(msg, command.getContentType(), payloadBytesOrNull);
            msg.setAddress(getCommandMessageAddress(command));
            msg.setSubject(command.getName());
            if (command.getContentType() != null) {
                msg.setContentType(command.getContentType());
            }
        }
        // explicitly set correlation id (possibly coming from the message-id property in case of a copied message)
        if (command.getCorrelationId() != null) {
            msg.setCorrelationId(command.getCorrelationId());
        }
        if (!command.isOneWay()) {
            msg.setReplyTo(getReplyToAddress(command));
        }
        return msg;
    }

    private String getCommandMessageAddress(final Command command) {
        // message address contains the "command" endpoint (the "command_internal" endpoint is used in the link address)
        return String.format("%s/%s/%s", CommandConstants.COMMAND_ENDPOINT,
                command.getTenant(), command.getDeviceId());
    }

    private String getReplyToAddress(final Command command) {
        return command.isOneWay() ? null
                : String.format("%s/%s/%s", CommandConstants.NORTHBOUND_COMMAND_RESPONSE_ENDPOINT,
                command.getTenant(), command.getReplyToId());
    }

    @Override
    public String toString() {
        return ProtonBasedInternalCommandSender.class.getName()
                + " via AMQP 1.0 Messaging Network";
    }
}
