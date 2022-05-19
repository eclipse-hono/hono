/*******************************************************************************
 * Copyright (c) 2016, 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter.mqtt.impl;

import java.net.HttpURLConnection;
import java.util.Map;
import java.util.Objects;

import org.eclipse.hono.adapter.mqtt.AbstractVertxBasedMqttProtocolAdapter;
import org.eclipse.hono.adapter.mqtt.MappedMessage;
import org.eclipse.hono.adapter.mqtt.MessageMapping;
import org.eclipse.hono.adapter.mqtt.MqttContext;
import org.eclipse.hono.adapter.mqtt.MqttProtocolAdapterProperties;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.command.Command;
import org.eclipse.hono.client.command.CommandContext;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.ResourceIdentifier;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.Json;


/**
 * A Vert.x based Hono protocol adapter for publishing messages to Hono's Telemetry and Event APIs using MQTT.
 */
public final class VertxBasedMqttProtocolAdapter extends AbstractVertxBasedMqttProtocolAdapter<MqttProtocolAdapterProperties> {

    private static final String MAPPER_DATA = "mapper_data";

    private MessageMapping<MqttContext> messageMapping;

    /**
     * {@inheritDoc}
     *
     * @return {@link Constants#PROTOCOL_ADAPTER_TYPE_MQTT}
     */
    @Override
    public String getTypeName() {
        return Constants.PROTOCOL_ADAPTER_TYPE_MQTT;
    }

    /**
     * Sets a service to call out to for published MQTT messages.
     * <p>
     * The service will be invoked after the client device has been authenticated
     * and before the downstream AMQP message is being created.
     *
     * @param messageMappingService The service to use for messageMapping messages.
     * @throws NullPointerException if messageMapping is {@code null}.
     */
    public void setMessageMapping(final MessageMapping<MqttContext> messageMappingService) {
        Objects.requireNonNull(messageMappingService);
        this.messageMapping = messageMappingService;
    }

    @Override
    protected Future<Void> onPublishedMessage(final MqttContext ctx) {

        return checkQosAndMapTopic(ctx)
                .compose(address -> validateAddress(address, ctx.authenticatedDevice()))
                .compose(targetAddress -> mapMessageAndUpdateContext(ctx, targetAddress))
                .compose(mappedMessage -> uploadMessage(ctx))
                .recover(t -> {
                    log.debug("discarding message [topic: {}, authenticated device: {}]",
                            ctx.getOrigAddress(), ctx.authenticatedDevice(), t);
                    return Future.failedFuture(t);
                });
    }

    private Future<MappedMessage> mapMessageAndUpdateContext(
            final MqttContext ctx,
            final ResourceIdentifier targetAddress) {

        return getRegistrationAssertion(
                targetAddress.getTenantId(),
                targetAddress.getResourceId(),
                ctx.authenticatedDevice(),
                ctx.getTracingContext())
                .compose(registrationInfo -> messageMapping.mapDownstreamMessage(
                        ctx,
                        targetAddress.getTenantId(), 
                        registrationInfo))
                .map(mappedMessage -> {
                    ctx.put(MAPPER_DATA, mappedMessage.getAdditionalProperties());
                    ctx.applyMappedTargetDeviceId(mappedMessage.getTargetDeviceId());
                    ctx.applyMappedPayload(mappedMessage.getPayload());
                    return mappedMessage;
                });
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void customizeDownstreamMessageProperties(final Map<String, Object> props, final MqttContext ctx) {

        final Object additionalProperties = ctx.get(MAPPER_DATA);
        if (additionalProperties instanceof Map) {
            ((Map<Object, Object>) additionalProperties).entrySet().stream()
                .filter(entry -> entry.getKey() instanceof String)
                .forEach(entry -> {
                    final String key = (String) entry.getKey();
                    final Object value = entry.getValue();
                    if (value instanceof String) {
                        // prevent quotes around strings
                        props.put(key, value);
                    } else {
                        props.put(key, Json.encode(value));
                    }
                });
        }
    }

    Future<ResourceIdentifier> checkQosAndMapTopic(final MqttContext context) {

        final Promise<ResourceIdentifier> result = Promise.promise();
        final MqttQoS qos = context.qosLevel();

        switch (context.endpoint()) {
            case TELEMETRY:
                if (MqttQoS.EXACTLY_ONCE.equals(qos)) {
                    // client tries to send telemetry message using QoS 2
                    result.fail(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST, "QoS 2 not supported for telemetry messages"));
                } else {
                    result.complete(context.topic());
                }
                break;
            case EVENT:
                if (MqttQoS.AT_LEAST_ONCE.equals(qos)) {
                    result.complete(context.topic());
                } else {
                    // client tries to send event message using QoS 0 or 2
                    result.fail(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST, "Only QoS 1 supported for event messages"));
                }
                break;
            case COMMAND:
                if (MqttQoS.EXACTLY_ONCE.equals(qos)) {
                    // client tries to send control message using QoS 2
                    result.fail(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST, "QoS 2 not supported for command response messages"));
                } else {
                    result.complete(context.topic());
                }
                break;
            default:
                // MQTT client is trying to publish on a not supported endpoint
                log.debug("no such endpoint [{}]", context.endpoint());
                result.fail(new ClientErrorException(HttpURLConnection.HTTP_NOT_FOUND, "no such endpoint"));
        }
        return result.future();
    }

    @Override
    protected Future<Buffer> getCommandPayload(final CommandContext ctx) {
        final Command command = ctx.getCommand();
        return getRegistrationClient().assertRegistration(command.getTenant(), command.getGatewayOrDeviceId(), null, ctx.getTracingContext())
            .compose(registrationInfo -> messageMapping.mapUpstreamMessage(registrationInfo, command));
    }
}
