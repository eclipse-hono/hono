/*******************************************************************************
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client.impl;

import java.util.function.Function;

import org.apache.qpid.proton.amqp.messaging.Modified;
import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.eclipse.hono.client.Command;
import org.eclipse.hono.client.CommandContext;
import org.eclipse.hono.client.DelegatedCommandSender;
import org.eclipse.hono.tracing.TracingHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.proton.ProtonDelivery;

/**
 * Handler for command messages that delegates command handling either to a given command handler or
 * to a matching consumer via the downstream peer.
 */
public class DelegatingCommandHandler implements Handler<CommandContext> {

    private static final Logger LOG = LoggerFactory.getLogger(DelegatingCommandHandler.class);

    private final Function<String, Handler<CommandContext>> deviceSpecificCommandHandlerSupplier;
    private final Function<String, Future<DelegatedCommandSender>> delegatedCommandSenderSupplier;

    /**
     * Creates a new DelegatingCommandHandler.
     *
     * @param deviceSpecificCommandHandlerSupplier Function to get an existing command handler. The function parameter
     *            is the device id.
     * @param delegatedCommandSenderSupplier Function to get a Future with a sender to send the delegated command via
     *            the downstream peer. The function parameter is the tenant id. The function is supposed to get or
     *            create such a sender and, if successful, succeed the returned Future with it. If sender creation
     *            failed, a failed Future is to be returned.
     */
    public DelegatingCommandHandler(final Function<String, Handler<CommandContext>> deviceSpecificCommandHandlerSupplier,
                                    final Function<String, Future<DelegatedCommandSender>> delegatedCommandSenderSupplier) {
        this.deviceSpecificCommandHandlerSupplier = deviceSpecificCommandHandlerSupplier;
        this.delegatedCommandSenderSupplier = delegatedCommandSenderSupplier;
    }


    @Override
    public void handle(final CommandContext commandContext) {
        final String deviceId = commandContext.getCommand().getDeviceId();
        final Handler<CommandContext> commandHandler = deviceSpecificCommandHandlerSupplier.apply(deviceId);
        if (commandHandler != null) {
            // delegate to existing local device-specific handler
            LOG.trace("use existing command handler for device {}", deviceId);
            commandHandler.handle(commandContext);
        } else {
            // delegate to matching consumer via downstream peer
            LOG.trace("delegate command for device {} to matching consumer via downstream peer", deviceId);
            delegateReceivedCommandMessageViaDownstreamPeer(commandContext);
        }
    }

    private void delegateReceivedCommandMessageViaDownstreamPeer(final CommandContext commandContext) {

        final Command command = commandContext.getCommand();
        final String tenantId = command.getTenant();
        final String deviceId = command.getDeviceId();
        // send message to AMQP network
        final Future<DelegatedCommandSender> delegatedCommandSender = delegatedCommandSenderSupplier.apply(tenantId);
        delegatedCommandSender.setHandler(cmdSenderResult -> {
            if (cmdSenderResult.succeeded()) {
                final DelegatedCommandSender sender = cmdSenderResult.result();
                sender.sendCommandMessage(command, commandContext.getTracingContext()).setHandler(sendResult -> {
                    if (sendResult.succeeded()) {
                        // send succeeded - handle outcome
                        final ProtonDelivery delegatedMsgDelivery = sendResult.result();
                        LOG.trace("command for device {} sent to downstream peer; remote state of delivery: {}",
                                deviceId, delegatedMsgDelivery.getRemoteState());
                        applyDelegatedMessageDeliveryResultToCommandContext(delegatedMsgDelivery, commandContext);
                    } else {
                        // failed to send message
                        LOG.error("failed to send command message to downstream peer", sendResult.cause());
                        TracingHelper.logError(commandContext.getCurrentSpan(),
                                "failed to send command message to downstream peer: " + sendResult.cause());
                        commandContext.release();
                    }
                });
            } else {
                // failed to create sender
                LOG.error("failed to create sender for sending command message to downstream peer", cmdSenderResult.cause());
                TracingHelper.logError(commandContext.getCurrentSpan(),
                        "failed to create sender for sending command message to downstream peer: " + cmdSenderResult.cause());
                commandContext.release();
            }
        });
    }

    private void applyDelegatedMessageDeliveryResultToCommandContext(final ProtonDelivery delegatedMsgDelivery, final CommandContext commandContext) {
        switch (delegatedMsgDelivery.getRemoteState().getType()) {
            case Accepted:
                commandContext.accept();
                break;
            case Rejected:
                final Rejected rejected = (Rejected) delegatedMsgDelivery.getRemoteState();
                commandContext.reject(rejected.getError());
                break;
            case Modified:
                final Modified modified = (Modified) delegatedMsgDelivery.getRemoteState();
                commandContext.modify(modified.getDeliveryFailed(), modified.getUndeliverableHere(), 0);
                break;
            case Released:
                commandContext.release();
                break;
            default:
                LOG.warn("got unexpected delivery outcome; remote state: {}", delegatedMsgDelivery.getRemoteState());
                TracingHelper.logError(commandContext.getCurrentSpan(), "got unexpected delivery outcome; remote state: "
                        + delegatedMsgDelivery.getRemoteState());
                commandContext.release();
                break;
        }
    }
}
