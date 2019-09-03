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

import java.util.function.BiFunction;

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
 * Handler for command messages that delegates command handling by setting a device-specific message target address and
 * sending the message to the downstream peer.
 * <p>
 * That way, further command handling is delegated to the {@link DeviceSpecificCommandConsumer} eventually.
 */
public class DelegateViaDownstreamPeerCommandHandler implements Handler<CommandContext> {

    private static final Logger LOG = LoggerFactory.getLogger(DelegateViaDownstreamPeerCommandHandler.class);

    private final BiFunction<String, String, Future<DelegatedCommandSender>> delegatedCommandSenderSupplier;

    /**
     * Creates a new DelegateViaDownstreamPeerCommandHandler.
     *
     * @param delegatedCommandSenderSupplier Function to get a Future with a sender to send the delegated command via
     *            the downstream peer. The first function parameter is the tenant id, the second is the device id.
     *            The function is supposed to create such a sender and, if successful, succeed the returned Future
     *            with it. If sender creation failed, a failed Future is to be returned.
     */
    public DelegateViaDownstreamPeerCommandHandler(final BiFunction<String, String, Future<DelegatedCommandSender>> delegatedCommandSenderSupplier) {
        this.delegatedCommandSenderSupplier = delegatedCommandSenderSupplier;
    }

    @Override
    public void handle(final CommandContext commandContext) {

        final Command command = commandContext.getCommand();
        final String tenantId = command.getTenant();
        final String deviceId = command.getDeviceId();
        LOG.trace("delegate command for device {} to matching consumer via downstream peer", deviceId);

        // send message to AMQP network
        final Future<DelegatedCommandSender> delegatedCommandSender = delegatedCommandSenderSupplier.apply(tenantId, deviceId);
        delegatedCommandSender.setHandler(cmdSenderResult -> {
            if (cmdSenderResult.succeeded()) {
                final DelegatedCommandSender sender = cmdSenderResult.result();
                sender.sendCommandMessage(command, commandContext.getTracingContext()).setHandler(sendResult -> {
                    if (sendResult.succeeded()) {
                        // send succeeded - handle outcome
                        final ProtonDelivery delegatedMsgDelivery = sendResult.result();
                        LOG.trace("command for device {} sent to downstream peer; remote state of delivery: {}",
                                deviceId, delegatedMsgDelivery.getRemoteState());
                        commandContext.disposition(delegatedMsgDelivery.getRemoteState());
                    } else {
                        // failed to send message
                        LOG.error("failed to send command message to downstream peer", sendResult.cause());
                        TracingHelper.logError(commandContext.getCurrentSpan(),
                                "failed to send command message to downstream peer: " + sendResult.cause());
                        commandContext.release();
                    }
                    sender.close(c -> {});
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
}
