/**
 * Copyright (c) 2021, 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.client.command.kafka;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.command.Command;
import org.eclipse.hono.client.command.CommandContext;
import org.eclipse.hono.client.command.InternalCommandSender;
import org.eclipse.hono.client.kafka.HonoTopic;
import org.eclipse.hono.client.kafka.KafkaRecordHelper;
import org.eclipse.hono.client.kafka.producer.AbstractKafkaBasedMessageSender;
import org.eclipse.hono.client.kafka.producer.KafkaProducerFactory;
import org.eclipse.hono.client.kafka.producer.MessagingKafkaProducerConfigProperties;
import org.eclipse.hono.util.CommandConstants;

import io.opentracing.Span;
import io.opentracing.Tracer;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.kafka.client.producer.KafkaHeader;

/**
 * A Kafka based sender for sending commands to an internal command topic
 * (<em>hono.command_internal.${adapterInstanceId}</em>).
 * Protocol adapters consume commands by subscribing to this topic.
 */
public class KafkaBasedInternalCommandSender extends AbstractKafkaBasedMessageSender<Buffer> implements InternalCommandSender {

    /**
     * Creates a new Kafka based internal command sender.
     *
     * @param producerFactory The factory to use for creating Kafka producers.
     * @param producerConfig The Kafka producer configuration.
     * @param tracer The tracer instance.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public KafkaBasedInternalCommandSender(
            final KafkaProducerFactory<String, Buffer> producerFactory,
            final MessagingKafkaProducerConfigProperties producerConfig,
            final Tracer tracer) {
        super(producerFactory, "internal-cmd-sender", producerConfig, tracer);
    }

    @Override
    public Future<Void> sendCommand(
            final CommandContext commandContext,
            final String adapterInstanceId) {

        Objects.requireNonNull(commandContext);
        Objects.requireNonNull(adapterInstanceId);

        final Command command = commandContext.getCommand();
        if (!(command instanceof KafkaBasedCommand)) {
            commandContext.release();
            log.error("command is not an instance of KafkaBasedCommand");
            throw new IllegalArgumentException("command is not an instance of KafkaBasedCommand");
        }

        final String topicName = getInternalCommandTopic(adapterInstanceId);
        final Span currentSpan = startChildSpan(
                CommandConstants.INTERNAL_COMMAND_SPAN_OPERATION_NAME,
                topicName,
                command.getTenant(),
                command.getDeviceId(),
                commandContext.getTracingContext());

        return sendAndWaitForOutcome(
                topicName,
                command.getTenant(),
                command.getDeviceId(),
                command.getPayload(),
                getHeaders((KafkaBasedCommand) command),
                currentSpan)
            .onSuccess(v -> commandContext.accept())
            .onFailure(thr -> commandContext.release(new ServerErrorException(
                    command.getTenant(),
                    HttpURLConnection.HTTP_UNAVAILABLE,
                    "failed to publish command message on internal command topic",
                    thr)))
            .onComplete(ar -> currentSpan.finish());
    }

    private static String getInternalCommandTopic(final String adapterInstanceId) {
        return new HonoTopic(HonoTopic.Type.COMMAND_INTERNAL, adapterInstanceId).toString();
    }

    private static List<KafkaHeader> getHeaders(final KafkaBasedCommand command) {
        final List<KafkaHeader> headers = new ArrayList<>(command.getRecord().headers());

        headers.add(KafkaRecordHelper.createTenantIdHeader(command.getTenant()));
        Optional.ofNullable(command.getGatewayId())
                .ifPresent(id -> headers.add(KafkaRecordHelper.createViaHeader(id)));

        headers.add(KafkaRecordHelper.createOriginalPartitionHeader(command.getRecord().partition()));
        headers.add(KafkaRecordHelper.createOriginalOffsetHeader(command.getRecord().offset()));
        return headers;
    }
}
