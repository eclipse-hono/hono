/**
 * Copyright (c) 2016, 2017 Red Hat and others
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Red Hat - initial creation
 */

package org.eclipse.hono.adapter.mqtt;

import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.config.ProtocolAdapterProperties;
import org.eclipse.hono.service.auth.device.Device;
import org.eclipse.hono.util.ResourceIdentifier;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.Future;
import io.vertx.mqtt.messages.MqttPublishMessage;

/**
 * A Vert.x based Hono protocol adapter for accessing Hono's Telemetry API using MQTT.
 */
public final class VertxBasedMqttProtocolAdapter extends AbstractVertxBasedMqttProtocolAdapter<ProtocolAdapterProperties> {

    /**
     * Gets a sender for forwarding a message that has been published by an unauthenticated device.
     * <p>
     * Verifies that topic that the message has been published to has
     * contains a <em>tenantId</em> and a <em>deviceId</em>.
     * If it does, a sender is returned that corresponds to the <em>endpoint</em> of the topic.
     * Otherwise the returned future is failed.
     * 
     * @param message The message to get the sender for.
     * @param topic The topic that the message has been published to.
     * @return A future containing the sender.
     */
    protected Future<MessageSender> getSender(final MqttPublishMessage message, final ResourceIdentifier topic) {

        if (topic.getTenantId() == null || topic.getResourceId() == null) {
            LOG.debug("discarding message published to unsupported topic [{}]", message.topicName());
            return Future.failedFuture("unsupported topic name");
        } else {
            return getSenderForTopic(message.qosLevel(), topic.getEndpoint(), topic.getTenantId());
        }
    }

    /**
     * Gets a sender for forwarding a message that has been published by an authenticated device.
     * <p>
     * Verifies that the <em>tenantId</em> and <em>deviceId</em> contained
     * in the topic match the authenticated device by means of the
     * {@link #validateCredentialsWithTopicStructure(ResourceIdentifier, String, String)} method.
     * If they do, a sender is returned that corresponds to the <em>endpoint</em> of the topic.
     * Otherwise the returned future is failed.
     * 
     * @param message The message to get the sender for.
     * @param topic The topic that the message has been published to.
     * @param authenticatedDevice The authenticated device that has published the message.
     * @return A future containing the sender.
     */
    protected Future<MessageSender> getSender(final MqttPublishMessage message, final ResourceIdentifier topic,
            final Device authenticatedDevice) {

        if (validateCredentialsWithTopicStructure(topic, authenticatedDevice.getTenantId(), authenticatedDevice.getDeviceId())) {
            return getSenderForTopic(message.qosLevel(), topic.getEndpoint(), authenticatedDevice.getTenantId());
        } else {
            LOG.debug("discarding message published by device [tenant-id: {}, device-id: {}] to unauthorized topic [{}]",
                    authenticatedDevice.getTenantId(), authenticatedDevice.getDeviceId(), message.topicName());
            return Future.failedFuture("unauthorized ");
        }
    }

    private Future<MessageSender> getSenderForTopic(final MqttQoS qosLevel, final String endpoint, final String tenantId) {

        if (endpoint.equals(TELEMETRY_ENDPOINT)) {
            if (!MqttQoS.AT_MOST_ONCE.equals(qosLevel)) {
                // client tries to send telemetry message using QoS 1 or 2
                return Future.failedFuture("Only QoS 0 supported for telemetry messages");
            } else {
                return getTelemetrySender(tenantId);
            }
        } else if (endpoint.equals(EVENT_ENDPOINT)) {
            if (!MqttQoS.AT_LEAST_ONCE.equals(qosLevel)) {
                // client tries to send event message using QoS 0 or 2
                return Future.failedFuture("Only QoS 1 supported for event messages");
            } else {
                return getEventSender(tenantId);
            }
        } else {
            // MQTT client is trying to publish on a not supported endpoint
            LOG.debug("no such endpoint [{}]", endpoint);
            return Future.failedFuture("no such endpoint");
        }
    }

}
