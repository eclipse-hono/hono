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

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.adapter.mqtt.AbstractVertxBasedMqttProtocolAdapter;
import org.eclipse.hono.config.ProtocolAdapterProperties;
import org.eclipse.hono.service.auth.device.Device;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.ResourceIdentifier;
import org.eclipse.hono.util.TelemetryConstants;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.Future;
import io.vertx.mqtt.messages.MqttPublishMessage;

/**
 * A Vert.x based Hono protocol adapter for publishing messages to Hono's Telemetry and Event APIs using MQTT.
 */
public final class VertxBasedMqttProtocolAdapter extends AbstractVertxBasedMqttProtocolAdapter<ProtocolAdapterProperties> {

    @Override
    protected Future<Message> getDownstreamMessage(final MqttPublishMessage messageFromDevice) {

        return mapTopic(messageFromDevice).map(address -> {
            if (address.getTenantId() == null || address.getResourceId() == null) {
                throw new IllegalArgumentException("topic of unauthenticated message must contain tenant and device IDs");
            } else {
                return newMessage(address.getBasePath(), address.getResourceId(), messageFromDevice.topicName());
            }
        });
    }

    @Override
    protected Future<Message> getDownstreamMessage(final MqttPublishMessage messageFromDevice, final Device deviceIdentity) {

        return mapTopic(messageFromDevice).map(address -> {
            if (address.getTenantId() != null && address.getResourceId() == null) {
                throw new IllegalArgumentException("topic of authenticated message must not contain tenant ID only");
            } else if (address.getTenantId() == null && address.getResourceId() == null) {
                // use authenticated device's tenant to fill in missing information
                final ResourceIdentifier downstreamAddress = ResourceIdentifier.from(address.getEndpoint(),
                        deviceIdentity.getTenantId(), deviceIdentity.getDeviceId());
                return newMessage(downstreamAddress.getBasePath(), downstreamAddress.getResourceId(), messageFromDevice.topicName());
            } else {
                return newMessage(address.getBasePath(), address.getResourceId(), messageFromDevice.topicName());
            }
        });
    }

    private Future<ResourceIdentifier> mapTopic(final MqttPublishMessage message) {

        try {
            final ResourceIdentifier topic = ResourceIdentifier.fromString(message.topicName());
            if (TelemetryConstants.TELEMETRY_ENDPOINT.equals(topic.getEndpoint())) {
                if (!MqttQoS.AT_MOST_ONCE.equals(message.qosLevel())) {
                    // client tries to send telemetry message using QoS 1 or 2
                    return Future.failedFuture("Only QoS 0 supported for telemetry messages");
                } else {
                    return Future.succeededFuture(topic);
                }
            } else if (EventConstants.EVENT_ENDPOINT.equals(topic.getEndpoint())) {
                if (!MqttQoS.AT_LEAST_ONCE.equals(message.qosLevel())) {
                    // client tries to send event message using QoS 0 or 2
                    return Future.failedFuture("Only QoS 1 supported for event messages");
                } else {
                    return Future.succeededFuture(topic);
                }
            } else {
                // MQTT client is trying to publish on a not supported endpoint
                LOG.debug("no such endpoint [{}]", topic.getEndpoint());
                return Future.failedFuture("no such endpoint");
            }
        } catch (IllegalArgumentException e) {
            return Future.failedFuture(e);
        }
    }
}
