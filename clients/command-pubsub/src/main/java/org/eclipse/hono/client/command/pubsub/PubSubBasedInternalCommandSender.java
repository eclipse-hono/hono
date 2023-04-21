/**
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hono.client.command.pubsub;

import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.command.Command;
import org.eclipse.hono.client.command.CommandContext;
import org.eclipse.hono.client.command.InternalCommandSender;
import org.eclipse.hono.client.pubsub.AbstractPubSubBasedMessageSender;
import org.eclipse.hono.client.pubsub.PubSubMessageHelper;
import org.eclipse.hono.client.pubsub.publisher.PubSubPublisherFactory;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.MessageHelper;

import io.opentracing.References;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.vertx.core.Future;

/**
 * A Pub/Sub based sender for sending commands to an internal command topic
 * (<em>${adapterInstanceId}.command_internal</em>). Protocol adapters consume commands by subscribing to this topic.
 */
public class PubSubBasedInternalCommandSender extends AbstractPubSubBasedMessageSender implements
        InternalCommandSender {

    /**
     * Creates a new Pub/Sub based internal command sender.
     *
     * @param publisherFactory The factory to use for creating Pub/Sub publishers.
     * @param projectId The identifier of the Google Cloud Project to connect to.
     * @param tracer The OpenTracing tracer.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public PubSubBasedInternalCommandSender(
            final PubSubPublisherFactory publisherFactory,
            final String projectId,
            final Tracer tracer) {
        super(publisherFactory, CommandConstants.INTERNAL_COMMAND_ENDPOINT, projectId, tracer);
    }

    @Override
    public Future<Void> sendCommand(
            final CommandContext commandContext,
            final String adapterInstanceId) {

        Objects.requireNonNull(commandContext);
        Objects.requireNonNull(adapterInstanceId);

        final Command command = commandContext.getCommand();
        if (!(command instanceof PubSubBasedCommand)) {
            commandContext.release();
            log.error("command is not an instance of PubSubBasedCommand");
            throw new IllegalArgumentException("command is not an instance of PubSubBasedCommand");
        }

        final Span span = startSpan(
                CommandConstants.INTERNAL_COMMAND_SPAN_OPERATION_NAME,
                command.getTenant(),
                command.getDeviceId(),
                References.CHILD_OF,
                commandContext.getTracingContext());
        final String topic = PubSubMessageHelper.getTopicName(CommandConstants.INTERNAL_COMMAND_ENDPOINT,
                adapterInstanceId);

        return sendAndWaitForOutcome(
                topic,
                command.getTenant(),
                command.getDeviceId(),
                command.getPayload(),
                getAttributes((PubSubBasedCommand) command),
                span)
                        .onSuccess(v -> commandContext.accept())
                        .onFailure(thr -> commandContext.release(new ServerErrorException(
                                command.getTenant(),
                                HttpURLConnection.HTTP_UNAVAILABLE,
                                "failed to publish command message on internal command topic",
                                thr)))
                        .onComplete(ar -> span.finish());
    }

    private Map<String, Object> getAttributes(final PubSubBasedCommand command) {
        final Map<String, Object> attributes = new HashMap<>(command.getPubsubMessage().getAttributesMap());
        attributes.put(MessageHelper.APP_PROPERTY_TENANT_ID, command.getTenant());
        attributes.put(MessageHelper.APP_PROPERTY_DEVICE_ID, command.getDeviceId());
        attributes.put(PubSubMessageHelper.PUBSUB_PROPERTY_PROJECT_ID, projectId);
        attributes.put(PubSubMessageHelper.PUBSUB_PROPERTY_RESPONSE_REQUIRED, !command.isOneWay());
        Optional.ofNullable(command.getGatewayId()).ifPresent(
                id -> attributes.put(MessageHelper.APP_PROPERTY_GATEWAY_ID, id));
        return attributes;
    }
}
