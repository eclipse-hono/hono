/**
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter.mqtt;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.eclipse.hono.auth.Device;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.ResourceIdentifier;
import org.eclipse.hono.util.Strings;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.opentracing.Span;
import io.opentracing.log.Fields;
import io.opentracing.tag.Tags;

/**
 * Base class for an MQTT subscription of a device.
 */
public abstract class AbstractSubscription implements Subscription {

    private static final String LOG_FIELD_TOPIC_FILTER = "filter";

    private final String endpoint;
    private final String tenant;
    private final String deviceId;
    private final String authenticatedDeviceId;
    private final String topic;
    private final boolean authenticated;
    private final boolean containsTenantId;
    private final boolean containsDeviceId;

    private final MqttQoS qos;

    /**
     * Creates a new AbstractSubscription.
     *
     * @param topicResource The topic to subscribe for.
     * @param authenticatedDevice The authenticated device or {@code null}.
     * @param qos The quality-of-service level for the subscription.
     * @throws NullPointerException if topicResource is {@code null}.
     * @throws IllegalArgumentException if the topic does not match the rules.
     */
    protected AbstractSubscription(
            final ResourceIdentifier topicResource,
            final Device authenticatedDevice,
            final MqttQoS qos) {

        Objects.requireNonNull(topicResource);
        this.topic = topicResource.toString();
        this.qos = qos;

        this.endpoint = topicResource.getEndpoint();
        final String resourceTenant = "+".equals(topicResource.getTenantId()) ? null : topicResource.getTenantId();
        this.containsTenantId = !Strings.isNullOrEmpty(resourceTenant);
        final String resourceDeviceId = "+".equals(topicResource.getResourceId()) ? null : topicResource.getResourceId();
        this.containsDeviceId = !Strings.isNullOrEmpty(resourceDeviceId);

        this.authenticated = authenticatedDevice != null;

        if (isAuthenticated()) {
            if (resourceTenant != null && !authenticatedDevice.getTenantId().equals(resourceTenant)) {
                throw new IllegalArgumentException("tenant in topic filter does not match authenticated device");
            }
            this.tenant = authenticatedDevice.getTenantId();
            this.authenticatedDeviceId = authenticatedDevice.getDeviceId();
            this.deviceId = Strings.isNullOrEmpty(resourceDeviceId) ? authenticatedDevice.getDeviceId() : resourceDeviceId;
        } else {
            if (Strings.isNullOrEmpty(resourceTenant)) {
                throw new IllegalArgumentException(
                        "for unauthenticated devices the tenant needs to be given in the subscription");
            }
            if (Strings.isNullOrEmpty(resourceDeviceId)) {
                throw new IllegalArgumentException(
                        "for unauthenticated devices the device-id needs to be given in the subscription");
            }
            this.tenant = resourceTenant;
            this.authenticatedDeviceId = null;
            this.deviceId = resourceDeviceId;
        }
    }

    @Override
    public final String getTenant() {
        return tenant;
    }

    @Override
    public final String getDeviceId() {
        return deviceId;
    }

    @Override
    public final String getAuthenticatedDeviceId() {
        return authenticatedDeviceId;
    }

    @Override
    public final String getEndpoint() {
        return endpoint;
    }

    @Override
    public final MqttQoS getQos() {
        return qos;
    }

    @Override
    public final String getTopic() {
        return topic;
    }

    @Override
    public final boolean isAuthenticated() {
        return authenticated;
    }

    @Override
    public final boolean isGatewaySubscriptionForSpecificDevice() {
        return authenticatedDeviceId != null && !authenticatedDeviceId.equals(deviceId);
    }

    @Override
    public boolean containsTenantId() {
        return containsTenantId;
    }

    @Override
    public boolean containsDeviceId() {
        return containsDeviceId;
    }

    @Override
    public final void logSubscribeSuccess(final Span span) {
        Objects.requireNonNull(span);
        final Map<String, Object> items = new HashMap<>(4);
        items.put(Fields.EVENT, "accepting subscription");
        items.put(LOG_FIELD_TOPIC_FILTER, getTopic());
        items.put("requested QoS", getQos());
        items.put("granted QoS", getQos());
        span.log(items);
    }

    @Override
    public final void logSubscribeFailure(final Span span, final Throwable error) {
        Objects.requireNonNull(span);
        Objects.requireNonNull(error);
        final Map<String, Object> items = new HashMap<>(4);
        items.put(Fields.EVENT, Tags.ERROR.getKey());
        items.put(LOG_FIELD_TOPIC_FILTER, getTopic());
        items.put("requested QoS", getQos());
        items.put(Fields.MESSAGE, "rejecting subscription: " + error.getMessage());
        TracingHelper.logError(span, items);
    }
}
