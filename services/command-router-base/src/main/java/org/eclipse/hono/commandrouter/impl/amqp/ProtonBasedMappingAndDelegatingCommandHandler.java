/*******************************************************************************
 * Copyright (c) 2020, 2021 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.commandrouter.impl.amqp;

import java.util.Objects;

import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.amqp.transport.AmqpError;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.adapter.client.command.amqp.ProtonBasedCommand;
import org.eclipse.hono.adapter.client.command.amqp.ProtonBasedCommandContext;
import org.eclipse.hono.adapter.client.command.amqp.ProtonBasedInternalCommandSender;
import org.eclipse.hono.adapter.client.registry.TenantClient;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.impl.CommandConsumer;
import org.eclipse.hono.commandrouter.CommandTargetMapper;
import org.eclipse.hono.commandrouter.impl.AbstractMappingAndDelegatingCommandHandler;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.ResourceIdentifier;
import org.eclipse.hono.util.Strings;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.vertx.proton.ProtonDelivery;

/**
 * Handler for commands received at the tenant-specific address.
 */
public class ProtonBasedMappingAndDelegatingCommandHandler extends AbstractMappingAndDelegatingCommandHandler {
    private final Tracer tracer;

    /**
     * Creates a new ProtonBasedMappingAndDelegatingCommandHandler instance.
     *
     * @param tenantClient The Tenant service client.
     * @param connection The connection to the AMQP network.
     * @param commandTargetMapper The mapper component to determine the command target.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public ProtonBasedMappingAndDelegatingCommandHandler(
            final TenantClient tenantClient,
            final HonoConnection connection,
            final CommandTargetMapper commandTargetMapper) {
        super(tenantClient, commandTargetMapper, new ProtonBasedInternalCommandSender(connection));
        this.tracer = connection.getTracer();
    }

    /**
     * Delegates an incoming command to the protocol adapter instance that the target
     * device is connected to.
     * <p>
     * Determines the target gateway (if applicable) and protocol adapter instance for an incoming command
     * and delegates the command to the resulting protocol adapter instance.
     *
     * @param tenantId The tenant that the command target must belong to.
     * @param messageDelivery The delivery of the command message.
     * @param message The command message.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public void mapAndDelegateIncomingCommandMessage(
            final String tenantId,
            final ProtonDelivery messageDelivery,
            final Message message) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(messageDelivery);
        Objects.requireNonNull(message);

        // this is the place where a command message on the "command/${tenant}" address arrives *first*
        if (!ResourceIdentifier.isValid(message.getAddress())) {
            log.debug("command message has no valid address");
            final Rejected rejected = new Rejected();
            rejected.setError(new ErrorCondition(Constants.AMQP_BAD_REQUEST, "missing or invalid command target address"));
            messageDelivery.disposition(rejected, true);
            return;
        }
        final ResourceIdentifier targetAddress = ResourceIdentifier.fromString(message.getAddress());
        final String deviceId = targetAddress.getResourceId();
        if (!tenantId.equals(targetAddress.getTenantId())) {
            log.debug("command message address contains invalid tenant [expected: {}, found: {}]", tenantId, targetAddress.getTenantId());
            final Rejected rejected = new Rejected();
            rejected.setError(new ErrorCondition(AmqpError.UNAUTHORIZED_ACCESS, "unauthorized to send command to tenant"));
            messageDelivery.disposition(rejected, true);
            return;
        } else if (Strings.isNullOrEmpty(deviceId)) {
            log.debug("invalid command message address: {}", message.getAddress());
            final Rejected rejected = new Rejected();
            rejected.setError(new ErrorCondition(Constants.AMQP_BAD_REQUEST, "invalid command target address"));
            messageDelivery.disposition(rejected, true);
            return;
        }

        final ProtonBasedCommand command = ProtonBasedCommand.from(message);
        if (command.isValid()) {
            log.trace("received valid command message: {}", command);
        } else {
            log.debug("received invalid command message: {}", command);
        }
        final SpanContext spanContext = TracingHelper.extractSpanContext(tracer, message);
        final Span currentSpan = CommandConsumer.createSpan("map and delegate command", tenantId, deviceId, null,
                tracer, spanContext);
        command.logToSpan(currentSpan);
        final ProtonBasedCommandContext commandContext = new ProtonBasedCommandContext(command, messageDelivery, currentSpan);
        if (command.isValid()) {
            mapAndDelegateIncomingCommand(commandContext);
        } else {
            // command message is invalid
            commandContext.reject("malformed command message");
        }
    }
}
