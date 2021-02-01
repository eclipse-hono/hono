/*******************************************************************************
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
 *******************************************************************************/
package org.eclipse.hono.commandrouter.impl.kafka;

import java.net.HttpURLConnection;
import java.util.Objects;

import org.eclipse.hono.adapter.client.command.Command;
import org.eclipse.hono.adapter.client.command.CommandContext;
import org.eclipse.hono.adapter.client.command.kafka.KafkaBasedCommand;
import org.eclipse.hono.adapter.client.command.kafka.KafkaBasedCommandContext;
import org.eclipse.hono.adapter.client.command.kafka.KafkaBasedInternalCommandSender;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.client.impl.CommandConsumer;
import org.eclipse.hono.client.kafka.tracing.KafkaTracingHelper;
import org.eclipse.hono.commandrouter.CommandTargetMapper;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.DeviceConnectionConstants;
import org.eclipse.hono.util.Lifecycle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;

/**
 * Handler for commands received at the tenant-specific topic.
 */
public class MappingAndDelegatingCommandHandler implements Lifecycle {
    private static final Logger log = LoggerFactory.getLogger(MappingAndDelegatingCommandHandler.class);  
    private final CommandTargetMapper commandTargetMapper;
    private final KafkaBasedInternalCommandSender internalCommandSender;
    private final Tracer tracer;

    /**
     * Creates a new MappingAndDelegatingCommandHandler instance.
     *
     * @param commandTargetMapper The mapper component to determine the command target.
     * @param internalCommandSender The command sender to publish commands to the internal command topic.
     * @param tracer The tracer instance.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public MappingAndDelegatingCommandHandler(
            final CommandTargetMapper commandTargetMapper,
            final KafkaBasedInternalCommandSender internalCommandSender,
            final Tracer tracer) {
        Objects.requireNonNull(commandTargetMapper);
        Objects.requireNonNull(internalCommandSender);
        Objects.requireNonNull(tracer);

        this.commandTargetMapper = commandTargetMapper;
        this.internalCommandSender = internalCommandSender;
        this.tracer = tracer;
    }

    @Override
    public Future<Void> start() {
        return internalCommandSender.start();
    }

    @Override
        public Future<Void> stop() {
        return internalCommandSender.stop();
    }

    /**
     * Delegates an incoming command to the protocol adapter instance that the target
     * device is connected to.
     * <p>
     * Determines the target gateway (if applicable) and protocol adapter instance for an incoming command
     * and delegates the command to the resulting protocol adapter instance.
     *
     * @param consumerRecord The consumer record corresponding to the command.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public void mapAndDelegateIncomingCommandMessage(final KafkaConsumerRecord<String, Buffer> consumerRecord) {
        Objects.requireNonNull(consumerRecord);

        final KafkaBasedCommand command;
        try {
            command = KafkaBasedCommand.from(consumerRecord);
        } catch (final IllegalArgumentException exception) {
            log.debug("command record is invalid", exception);
            return;
        }

        final SpanContext spanContext = KafkaTracingHelper.extractSpanContext(tracer, consumerRecord);
        final Span currentSpan = CommandConsumer.createSpan("map and delegate command", command.getTenant(),
                command.getDeviceId(), null, tracer, spanContext);
        final KafkaBasedCommandContext commandContext = new KafkaBasedCommandContext(command, currentSpan);

        command.logToSpan(currentSpan);
        if (command.isValid()) {
            log.trace("received valid command record [{}]", command);
            mapAndDelegateIncomingCommand(commandContext);
        } else {
            log.debug("received invalid command record [{}]", command);
            commandContext.reject("malformed command message");
        }
    }

    private void mapAndDelegateIncomingCommand(final CommandContext commandContext) {
        final Command command = commandContext.getCommand();

        // determine last used gateway device id
        log.trace("determine command target gateway/adapter for [{}]", command);
        commandTargetMapper
                .getTargetGatewayAndAdapterInstance(command.getTenant(), command.getDeviceId(), commandContext.getTracingContext())
                .onSuccess(result -> {
                    final String targetAdapterInstanceId = result
                            .getString(DeviceConnectionConstants.FIELD_ADAPTER_INSTANCE_ID);
                    final String targetDeviceId = result.getString(DeviceConnectionConstants.FIELD_PAYLOAD_DEVICE_ID);
                    final String targetGatewayId = targetDeviceId.equals(command.getDeviceId()) ? null : targetDeviceId;

                    if (Objects.isNull(targetGatewayId)) {
                        log.trace("command not mapped to gateway, use original device id [{}]", command.getDeviceId());
                    } else {
                        command.setGatewayId(targetGatewayId);
                        log.trace("determined target gateway [{}] for device [{}]", targetGatewayId,
                                command.getDeviceId());
                        commandContext.getTracingSpan().log("determined target gateway [" + targetGatewayId + "]");
                    }

                    log.trace("delegate command to target adapter instance '{}' [command: {}]", targetAdapterInstanceId,
                            commandContext.getCommand());
                    internalCommandSender.sendCommand(commandContext, targetAdapterInstanceId);
                })
                .onFailure(cause -> {
                    final String errorMsg;

                    if (ServiceInvocationException.extractStatusCode(cause) == HttpURLConnection.HTTP_NOT_FOUND) {
                        errorMsg = "no target adapter instance found for command with device id "
                                + command.getDeviceId();
                    } else {
                        errorMsg = "error getting target gateway and adapter instance for command with device id"
                                + command.getDeviceId();
                    }
                    log.debug(errorMsg, cause);
                    TracingHelper.logError(commandContext.getTracingSpan(), errorMsg, cause);
                    commandContext.release();
                });
    }
}
