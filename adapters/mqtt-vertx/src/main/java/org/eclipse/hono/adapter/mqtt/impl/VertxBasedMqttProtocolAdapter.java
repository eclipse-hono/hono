/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
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

import org.eclipse.hono.adapter.mqtt.AbstractVertxBasedMqttProtocolAdapter;
import org.eclipse.hono.adapter.mqtt.MqttContext;
import org.eclipse.hono.adapter.mqtt.MqttProtocolAdapterProperties;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.service.metric.MetricsTags;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.ResourceIdentifier;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.Future;

/**
 * A Vert.x based Hono protocol adapter for publishing messages to Hono's Telemetry and Event APIs using MQTT.
 */
public final class VertxBasedMqttProtocolAdapter extends AbstractVertxBasedMqttProtocolAdapter<MqttProtocolAdapterProperties> {

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

        return mapTopic(ctx)
        .compose(address -> checkAddress(ctx, address))
        .compose(targetAddress -> uploadMessage(ctx, targetAddress, ctx.message()))
        .recover(t -> {
            LOG.debug("discarding message [topic: {}] from device: {}", ctx.message().topicName(), t.getMessage());
            return Future.failedFuture(t);
        });
    }

    Future<ResourceIdentifier> mapTopic(final MqttContext context) {

        final Future<ResourceIdentifier> result = Future.future();
        final ResourceIdentifier topic = context.topic();
        final MqttQoS qos = context.message().qosLevel();

        switch (MetricsTags.EndpointType.fromString(topic.getEndpoint())) {
            case TELEMETRY:
                if (MqttQoS.EXACTLY_ONCE.equals(qos)) {
                    // client tries to send telemetry message using QoS 2
                    result.fail(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST, "QoS 2 not supported for telemetry messages"));
                } else {
                    result.complete(topic);
                }
                break;
            case EVENT:
                if (MqttQoS.AT_LEAST_ONCE.equals(qos)) {
                    result.complete(topic);
                } else {
                    // client tries to send event message using QoS 0 or 2
                    result.fail(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST, "Only QoS 1 supported for event messages"));
                }
                break;
            case CONTROL:
                if (MqttQoS.EXACTLY_ONCE.equals(qos)) {
                    // client tries to send control message using QoS 2
                    result.fail(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST, "QoS 2 not supported for command response messages"));
                } else {
                    result.complete(topic);
                }
                break;
            default:
                // MQTT client is trying to publish on a not supported endpoint
                LOG.debug("no such endpoint [{}]", topic.getEndpoint());
                result.fail(new ClientErrorException(HttpURLConnection.HTTP_NOT_FOUND, "no such endpoint"));
        }
        return result;
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
            } else if (address.getTenantId() != null && !address.getTenantId().equals(ctx.authenticatedDevice().getTenantId())) {
                result.fail(new ClientErrorException(HttpURLConnection.HTTP_FORBIDDEN, "can only publish for device of same tenant"));
            } else if (address.getTenantId() == null && address.getResourceId() == null) {
                // use authenticated device's tenant to fill in missing information
                final ResourceIdentifier downstreamAddress = ResourceIdentifier.from(address,
                        ctx.authenticatedDevice().getTenantId(), ctx.authenticatedDevice().getDeviceId());
                result.complete(downstreamAddress);
            } else {
                result.complete(address);
            }
        }
        return result;
    }
}
