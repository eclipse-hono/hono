/*******************************************************************************
 * Copyright (c) 2020, 2022 Contributors to the Eclipse Foundation
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
import org.eclipse.hono.client.amqp.connection.AmqpUtils;
import org.eclipse.hono.client.command.InternalCommandSender;
import org.eclipse.hono.client.command.amqp.ProtonBasedCommand;
import org.eclipse.hono.client.command.amqp.ProtonBasedCommandContext;
import org.eclipse.hono.client.registry.TenantClient;
import org.eclipse.hono.commandrouter.CommandRouterMetrics;
import org.eclipse.hono.commandrouter.CommandTargetMapper;
import org.eclipse.hono.commandrouter.impl.AbstractMappingAndDelegatingCommandHandler;
import org.eclipse.hono.util.MessagingType;
import org.eclipse.hono.util.ResourceIdentifier;
import org.eclipse.hono.util.Strings;

import io.micrometer.core.instrument.Timer;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.proton.ProtonDelivery;

/**
 * Handler for commands received at the tenant-specific address.
 */
public class ProtonBasedMappingAndDelegatingCommandHandler
        extends AbstractMappingAndDelegatingCommandHandler<ProtonBasedCommandContext> {

    /**
     * Creates a new ProtonBasedMappingAndDelegatingCommandHandler instance.
     *
     * @param vertx The Vert.x instance to use.
     * @param tenantClient The Tenant service client.
     * @param commandQueue The command queue to use for keeping track of the command processing.
     * @param internalCommandSender The internal command sender for forwarding the command.
     * @param commandTargetMapper The mapper component to determine the command target.
     * @param metrics The component to use for reporting metrics.
     * @param tracer The Tracer instance to use.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public ProtonBasedMappingAndDelegatingCommandHandler(
            final Vertx vertx,
            final TenantClient tenantClient,
            final ProtonBasedCommandProcessingQueue commandQueue,
            final InternalCommandSender internalCommandSender,
            final CommandTargetMapper commandTargetMapper,
            final CommandRouterMetrics metrics,
            final Tracer tracer) {
        super(vertx, tenantClient, commandQueue, commandTargetMapper, internalCommandSender, metrics, tracer);
    }

    @Override
    protected final MessagingType getMessagingType() {
        return MessagingType.amqp;
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
     * @return A future indicating the outcome of the operation.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public Future<Void> mapAndDelegateIncomingCommandMessage(
            final String tenantId,
            final ProtonDelivery messageDelivery,
            final Message message) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(messageDelivery);
        Objects.requireNonNull(message);

        final Timer.Sample timer = getMetrics().startTimer();

        // this is the place where a command message on the "command/${tenant}" address arrives *first*
        if (!ResourceIdentifier.isValid(message.getAddress())) {
            log.debug("command message has no valid address");
            final Rejected rejected = new Rejected();
            rejected.setError(new ErrorCondition(AmqpUtils.AMQP_BAD_REQUEST, "missing or invalid command target address"));
            messageDelivery.disposition(rejected, true);
            return Future.failedFuture("command message address is invalid");
        }
        final ResourceIdentifier targetAddress = ResourceIdentifier.fromString(message.getAddress());
        final String deviceId = targetAddress.getResourceId();
        if (!tenantId.equals(targetAddress.getTenantId())) {
            log.debug("command message address contains invalid tenant [expected: {}, found: {}]",
                    tenantId, targetAddress.getTenantId());
            final Rejected rejected = new Rejected();
            rejected.setError(new ErrorCondition(AmqpError.UNAUTHORIZED_ACCESS, "unauthorized to send command to tenant"));
            messageDelivery.disposition(rejected, true);
            return Future.failedFuture("command message address contains invalid tenant");
        } else if (Strings.isNullOrEmpty(deviceId)) {
            log.debug("invalid command message address: {}", message.getAddress());
            final Rejected rejected = new Rejected();
            rejected.setError(new ErrorCondition(AmqpUtils.AMQP_BAD_REQUEST, "invalid command target address"));
            messageDelivery.disposition(rejected, true);
            return Future.failedFuture("command message address is invalid");
        }

        final ProtonBasedCommand command = ProtonBasedCommand.from(message);
        final SpanContext spanContext = AmqpUtils.extractSpanContext(tracer, message);
        final Span currentSpan = createSpan(tenantId, deviceId, spanContext);
        command.logToSpan(currentSpan);
        final ProtonBasedCommandContext commandContext = new ProtonBasedCommandContext(command, messageDelivery, currentSpan);
        if (!command.isValid()) {
            log.debug("received invalid command message: {}", command);
            commandContext.reject("malformed command message");
            reportInvalidCommand(commandContext, timer);
            return Future.failedFuture("command message is invalid");
        }
        log.trace("received valid command message: {}", command);
        return mapAndDelegateIncomingCommand(commandContext, timer);
    }
}
