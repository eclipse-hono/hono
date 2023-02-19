/**
 * Copyright (c) 2020, 2023 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.hono.adapter;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.eclipse.hono.service.auth.DeviceUser;
import org.eclipse.hono.util.ExecutionContext;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.QoS;

/**
 * A container for information relevant for processing a message sent by a device
 * which contains telemetry data or an event.
 */
public interface TelemetryExecutionContext extends ExecutionContext {

    /**
     * Gets the verified identity of the device that the message has been
     * received from which is processed in this context.
     *
     * @return The device or {@code null} if the device has not been authenticated.
     */
    DeviceUser getAuthenticatedDevice();

    /**
     * Determines if the message that is processed in this context has been received from
     * a device whose identity has been verified.
     *
     * @return {@code true} if the device has been authenticated or {@code false} otherwise.
     */
    default boolean isDeviceAuthenticated() {
        return getAuthenticatedDevice() != null;
    }

    /**
     * Gets the QoS level as set in the request by the device.
     *
     * @return The QoS level requested by the device or {@code null} if the level could not be determined.
     */
    QoS getRequestedQos();

    /**
     * Gets the <em>time-to-live</em> set by the device for an event.
     *
     * @return An optional containing the <em>time-to-live</em> duration or an empty optional if
     *         the device did not specify a ttl or if the message is not an event.
     */
    Optional<Duration> getTimeToLive();

    /**
     * Gets the transport protocol specific address of the message.
     *
     * @return The address.
     */
    String getOrigAddress();

    /**
     * Gets the properties that need to be included in the message being sent
     * downstream.
     * <p>
     * This default implementation puts the following properties to the returned map:
     * <ul>
     * <li>the value returned by {@link #getOrigAddress()} under key {@value MessageHelper#APP_PROPERTY_ORIG_ADDRESS},
     * if not {@code null}</li>
     * <li>the value returned by {@link #getTimeToLive()} under key {@value MessageHelper#SYS_HEADER_PROPERTY_TTL},
     * if not {@code null}</li>
     * </ul>
     *
     * @return The properties.
     */
    default Map<String, Object> getDownstreamMessageProperties() {
        final Map<String, Object> props = new HashMap<>();
        Optional.ofNullable(getOrigAddress())
            .ifPresent(address -> props.put(MessageHelper.APP_PROPERTY_ORIG_ADDRESS, address));
        getTimeToLive().ifPresent(ttl -> props.put(MessageHelper.SYS_HEADER_PROPERTY_TTL, ttl.getSeconds()));
        return props;
    }
}
