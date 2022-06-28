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
     * @param qos The quality-of-service level for the subscription.
     * @param authenticatedDevice The authenticated device or {@code null}.
     * @throws NullPointerException if topicResource or qos is {@code null}.
     * @throws IllegalArgumentException if the topic does not match the rules.
     */
    protected AbstractSubscription(
            final ResourceIdentifier topicResource,
            final MqttQoS qos,
            final Device authenticatedDevice) {

        Objects.requireNonNull(topicResource);
        Objects.requireNonNull(qos);
        this.topic = topicResource.toString();
        this.qos = qos;

        this.endpoint = topicResource.getEndpoint();
        final String resourceTenant = "+".equals(topicResource.getTenantId()) ? null : topicResource.getTenantId();
        this.containsTenantId = !Strings.isNullOrEmpty(resourceTenant);
        final String resourceDeviceId = "+".equals(topicResource.getResourceId()) ? null : topicResource.getResourceId();
        this.containsDeviceId = !Strings.isNullOrEmpty(resourceDeviceId);

        this.authenticated = authenticatedDevice != null;

        if (authenticatedDevice != null) {
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

    @Override
    public final void logUnsubscribe(final Span span) {
        Objects.requireNonNull(span);
        final Map<String, Object> items = new HashMap<>(2);
        items.put(Fields.EVENT, "removing subscription");
        items.put(LOG_FIELD_TOPIC_FILTER, getTopic());
        span.log(items);
    }

    /**
     * The key to identify a subscription.
     * To be used for a kind of subscription where there can only be one subscription for a given device.
     */
    static final class DefaultKey implements Key {

        enum Type {
          COMMAND,
          ERROR
        }

        private final String tenant;
        private final String deviceId;
        private final Type type;

        /**
         * Creates a new Key.
         *
         * @param tenant The tenant identifier.
         * @param deviceId The device identifier.
         * @param type The type of the subscription.
         * @throws NullPointerException If any of the parameters is {@code null}.
         */
        DefaultKey(final String tenant, final String deviceId, final Type type) {
            this.tenant = Objects.requireNonNull(tenant);
            this.deviceId = Objects.requireNonNull(deviceId);
            this.type = Objects.requireNonNull(type);
        }

        @Override
        public String getTenant() {
            return tenant;
        }

        @Override
        public String getDeviceId() {
            return deviceId;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final DefaultKey that = (DefaultKey) o;
            return tenant.equals(that.tenant) && deviceId.equals(that.deviceId) && type == that.type;
        }

        @Override
        public int hashCode() {
            return Objects.hash(tenant, deviceId, type);
        }
    }
}
