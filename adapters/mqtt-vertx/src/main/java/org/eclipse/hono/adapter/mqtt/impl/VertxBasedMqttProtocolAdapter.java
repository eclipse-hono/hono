/**
 * Copyright (c) 2016, 2018 Red Hat and others
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Red Hat - initial creation
 *    Bosch Software Innovations GmbH - add Eclipse Kura support
 */

package org.eclipse.hono.adapter.mqtt.impl;

import java.net.HttpURLConnection;

import org.eclipse.hono.adapter.mqtt.AbstractVertxBasedMqttProtocolAdapter;
import org.eclipse.hono.adapter.mqtt.MqttContext;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.config.ProtocolAdapterProperties;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.EndpointType;
import org.eclipse.hono.util.ResourceIdentifier;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.Future;
import io.vertx.mqtt.messages.MqttPublishMessage;

/**
 * A Vert.x based Hono protocol adapter for publishing messages to Hono's Telemetry and Event APIs using MQTT.
 */
public final class VertxBasedMqttProtocolAdapter extends AbstractVertxBasedMqttProtocolAdapter<ProtocolAdapterProperties> {

    /**
     * {@inheritDoc}
     * 
     * @return {@link Constants#PROTOCOL_ADAPTER_TYPE_MQTT}
     */
    @Override
    protected String getTypeName() {
        return Constants.PROTOCOL_ADAPTER_TYPE_MQTT;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Future<Void> onPublishedMessage(final MqttContext ctx) {

        return mapTopic(ctx.message())
        .compose(address -> checkAddress(ctx, address))
        .compose(address -> uploadMessage(ctx, address, ctx.message().payload()))
        .recover(t -> {
            LOG.debug("discarding message [topic: {}] from device: {}", ctx.message().topicName(), t.getMessage());
            return Future.failedFuture(t);
        });
    }

    Future<ResourceIdentifier> mapTopic(final MqttPublishMessage message) {

        try {
            final ResourceIdentifier topic = ResourceIdentifier.fromString(message.topicName());

            switch (EndpointType.fromString(topic.getEndpoint())) {
                case TELEMETRY:
                    if (MqttQoS.EXACTLY_ONCE.equals(message.qosLevel())) {
                        // client tries to send telemetry message using QoS 2
                        return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST, "QoS 2 not supported for telemetry messages"));
                    } else {
                        return Future.succeededFuture(topic);
                    }
                case EVENT:
                    if (!MqttQoS.AT_LEAST_ONCE.equals(message.qosLevel())) {
                        // client tries to send event message using QoS 0 or 2
                        return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST, "Only QoS 1 supported for event messages"));
                    } else {
                        return Future.succeededFuture(topic);
                    }
                default:
                    // MQTT client is trying to publish on a not supported endpoint
                    LOG.debug("no such endpoint [{}]", topic.getEndpoint());
                    return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST, "no such endpoint"));
            }
        } catch (final IllegalArgumentException e) {
            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST, "malformed topic name"));
        }
    }

    Future<ResourceIdentifier> checkAddress(final MqttContext ctx, final ResourceIdentifier address) {

        final Future<ResourceIdentifier> result = Future.future();

        if (ctx.authenticatedDevice() == null) {
            if (address.getTenantId() == null || address.getResourceId() == null) {
                result.fail(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST,
                        "topic of unauthenticated message must contain tenant and device ID"));
            } else {
                result.complete(address);
            }
        } else {
            if (address.getTenantId() != null && address.getResourceId() == null) {
                result.fail(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST,
                        "topic of authenticated message must not contain tenant ID only"));
            } else if (address.getTenantId() == null && address.getResourceId() == null) {
                // use authenticated device's tenant to fill in missing information
                final ResourceIdentifier downstreamAddress = ResourceIdentifier.from(address.getEndpoint(),
                        ctx.authenticatedDevice().getTenantId(), ctx.authenticatedDevice().getDeviceId());
                result.complete(downstreamAddress);
            } else {
                result.complete(address);
            }
        }
        return result;
    }
}
