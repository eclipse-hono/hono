/*******************************************************************************
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter.mqtt;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.opentracing.Span;

/**
 * MQTT subscription of a device.
 */
public interface Subscription {

    /**
     * Gets the key to identify a subscription in the list of (same-type) subscriptions of an MQTT Endpoint.
     *
     * @return The key.
     */
    Key getKey();

    /**
     * Gets the tenant from topic or authentication.
     *
     * @return The tenant (never {@code null}).
     */
    String getTenant();

    /**
     * Gets the device id from topic or authentication.
     *
     * @return The device id (never {@code null}).
     */
    String getDeviceId();

    /**
     * Gets the device id from authentication.
     *
     * @return The device id or {@code null}.
     */
    String getAuthenticatedDeviceId();

    /**
     * Gets the endpoint of the subscription.
     *
     * @return The endpoint.
     */
    String getEndpoint();

    /**
     * Gets the QoS of the subscription.
     *
     * @return The QoS value.
     */
    MqttQoS getQos();

    /**
     * Gets the subscription topic.
     *
     * @return The topic.
     */
    String getTopic();

    /**
     * Gets the authentication status, which indicates the need to publish on tenant/device-id for unauthenticated
     * devices.
     *
     * @return {@code true} if created with an authenticated device.
     */
    boolean isAuthenticated();

    /**
     * Checks whether this subscription represents the case of a gateway subscribing for a specific device that
     * it acts on behalf of.
     *
     * @return {@code true} if a gateway is subscribing for a specific device.
     */
    boolean isGatewaySubscriptionForSpecificDevice();

    /**
     * Checks whether this subscription represents the case of a gateway subscribing for all devices that
     * it acts on behalf of.
     *
     * @return {@code true} if a gateway is subscribing for all devices.
     */
    boolean isGatewaySubscriptionForAllDevices();

    /**
     * Checks if the topic contains a tenant identifier.
     *
     * @return {@code true} if the topic contains a tenant id.
     */
    boolean containsTenantId();

    /**
     * Checks if the topic contains a device identifier.
     *
     * @return {@code true} if the topic contains a device id.
     */
    boolean containsDeviceId();

    /**
     * Logs a successful subscription attempt to the given span.
     *
     * @param span The span to log to.
     * @throws NullPointerException if span is {@code null}.
     */
    void logSubscribeSuccess(Span span);

    /**
     * Logs a failed subscription attempt to the given span.
     *
     * @param span The span to log to.
     * @param error The error to log.
     * @throws NullPointerException if span or error is {@code null}.
     */
    void logSubscribeFailure(Span span, Throwable error);

    /**
     * Logs the unsubscription to the given span.
     *
     * @param span The span to log to.
     * @throws NullPointerException if span is {@code null}.
     */
    void logUnsubscribe(Span span);

    /**
     * The key to identify a subscription.
     */
    interface Key {
        /**
         * Gets the tenant from topic or authentication.
         *
         * @return The tenant (never {@code null}).
         */
        String getTenant();
        /**
         * Gets the device id from topic or authentication.
         *
         * @return The device id (never {@code null}).
         */
        String getDeviceId();
    }

}
