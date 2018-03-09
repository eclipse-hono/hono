/**
 * Copyright (c) 2017, 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 1.0 which is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * SPDX-License-Identifier: EPL-1.0
 */

package org.eclipse.hono.adapter.kura;

import java.net.HttpURLConnection;
import java.util.Arrays;

import org.eclipse.hono.adapter.mqtt.AbstractVertxBasedMqttProtocolAdapter;
import org.eclipse.hono.adapter.mqtt.MqttContext;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.ResourceIdentifier;
import org.eclipse.hono.util.TelemetryConstants;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.Future;

/**
 * A Vert.x based Hono protocol adapter for publishing messages to Hono's Telemetry and Event APIs from
 * <a href="https://www.eclipse.org/kura">Eclipse Kura</a> gateways.
 */
public final class KuraProtocolAdapter extends AbstractVertxBasedMqttProtocolAdapter<KuraAdapterProperties> {

    /**
     * Gets this adapter's type name.
     * 
     * @return {@link Constants#PROTOCOL_ADAPTER_TYPE_KURA}
     */
    @Override
    protected String getTypeName() {
        return Constants.PROTOCOL_ADAPTER_TYPE_KURA;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Future<Void> onPublishedMessage(final MqttContext ctx) {

        return mapTopic(ctx)
                .recover(t -> {
                    LOG.debug("discarding message [topic: {}] from device: {}", ctx.message().topicName(), t.getMessage());
                    return Future.failedFuture(t);
                }).compose(address -> uploadMessage(ctx, address, ctx.message().payload()));
    }

    Future<ResourceIdentifier> mapTopic(final MqttContext ctx) {

        final Future<ResourceIdentifier> result = Future.future();
        try {
            final ResourceIdentifier topic = ResourceIdentifier.fromString(ctx.message().topicName());
            ResourceIdentifier mappedTopic = null;

            if (getConfig().getControlPrefix().equals(topic.getEndpoint())) {

                // this is a "control" message
                ctx.setContentType(getConfig().getCtrlMsgContentType());
                final String[] mappedPath = Arrays.copyOf(topic.getResourcePath(), topic.getResourcePath().length);
                mappedPath[0] = getEndpoint(ctx.message().qosLevel());
                mappedTopic = ResourceIdentifier.fromPath(mappedPath);

            } else {

                // map "data" messages based on QoS
                ctx.setContentType(getConfig().getDataMsgContentType());
                final String[] mappedPath = new String[topic.getResourcePath().length + 1];
                System.arraycopy(topic.getResourcePath(), 0, mappedPath, 1, topic.getResourcePath().length);
                mappedPath[0] = getEndpoint(ctx.message().qosLevel());
                mappedTopic = ResourceIdentifier.fromPath(mappedPath);
            }

            if (mappedTopic.getResourcePath().length < 3) {
                // topic does not contain account_name and client_id
                result.fail(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST, "topic does not comply with Kura format"));
            } else {
                LOG.debug("mapped Kura message [topic: {}, QoS: {}] to Hono message [to: {}, device_id: {}, content-type: {}]",
                        topic, ctx.message().qosLevel(), mappedTopic.getBasePath(), mappedTopic.getResourceId(), ctx.contentType());
                result.complete(mappedTopic);
            }
        } catch (final IllegalArgumentException e) {
            result.fail(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST, "malformed topic name"));
        }
        return result;
    }

    private static String getEndpoint(final MqttQoS level) {

        switch(level) {
        case AT_MOST_ONCE:
            return TelemetryConstants.TELEMETRY_ENDPOINT;
        default:
            return EventConstants.EVENT_ENDPOINT;
        }
    }
}
