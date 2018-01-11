/**
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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

import java.util.Arrays;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.adapter.mqtt.AbstractVertxBasedMqttProtocolAdapter;
import org.eclipse.hono.service.auth.device.Device;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.ResourceIdentifier;
import org.eclipse.hono.util.TelemetryConstants;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.Future;
import io.vertx.mqtt.messages.MqttPublishMessage;

/**
 * A Vert.x based Hono protocol adapter for publishing messages to Hono's Telemetry and Event APIs from
 * <a href="https://www.eclipse.org/kura">Eclipse Kura</a> gateways.
 */
public final class KuraProtocolAdapter extends AbstractVertxBasedMqttProtocolAdapter<KuraAdapterProperties> {

    @Override
    protected Future<Message> getDownstreamMessage(final MqttPublishMessage messageFromDevice) {

        return getDownstreamMessage(messageFromDevice, null);
    }

    @Override
    protected Future<Message> getDownstreamMessage(final MqttPublishMessage messageFromDevice, final Device deviceIdentity) {

        final Future<Message> result = Future.future();
        try {
            final ResourceIdentifier topic = ResourceIdentifier.fromString(messageFromDevice.topicName());
            ResourceIdentifier mappedTopic = null;
            String contentType = null;

            if (getConfig().getControlPrefix().equals(topic.getEndpoint())) {

                // this is a "control" message
                contentType = getConfig().getCtrlMsgContentType();
                final String[] mappedPath = Arrays.copyOf(topic.getResourcePath(), topic.getResourcePath().length);
                mappedPath[0] = getEndpoint(messageFromDevice.qosLevel());
                mappedTopic = ResourceIdentifier.fromPath(mappedPath);

            } else {

                // map "data" messages based on QoS
                contentType = getConfig().getDataMsgContentType();
                final String[] mappedPath = new String[topic.getResourcePath().length + 1];
                System.arraycopy(topic.getResourcePath(), 0, mappedPath, 1, topic.getResourcePath().length);
                mappedPath[0] = getEndpoint(messageFromDevice.qosLevel());
                mappedTopic = ResourceIdentifier.fromPath(mappedPath);
            }

            if (mappedTopic.getResourcePath().length < 3) {
                // topic does not contain account_name and client_id
                result.fail(new IllegalArgumentException("topic does not comply with Kura format"));
            } else {
                LOG.debug("mapped Kura message [topic: {}, QoS: {}] to Hono message [to: {}, device_id: {}, content-type: {}]",
                        topic, messageFromDevice.qosLevel(), mappedTopic.getBasePath(), mappedTopic.getResourceId(), contentType);
                result.complete(newMessage(mappedTopic.getBasePath(), mappedTopic.getResourceId(), messageFromDevice.topicName(), contentType));
            }
        } catch (IllegalArgumentException e) {
            result.fail(e);
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
