/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.config;

import java.time.Duration;
import java.util.Objects;

/**
 * Common configuration properties for protocol adapters of Hono.
 *
 */
public class ProtocolAdapterProperties extends ServiceConfigProperties {

    /**
     * The default duration after which a tenant times out when no message has been sent for it. The default value
     * disables automatic tenant timeout.
     */
    public static final Duration DEFAULT_TENANT_IDLE_TIMEOUT = Duration.ZERO;

    private boolean authenticationRequired = true;
    private boolean jmsVendorPropsEnabled = false;
    private boolean defaultsEnabled = true;
    private long eventLoopBlockedCheckTimeout = 5000L;
    private int maxConnections = 0;
    private Duration tenantIdleTimeout = DEFAULT_TENANT_IDLE_TIMEOUT;

    /**
     * Checks whether the protocol adapter always authenticates devices using their provided credentials as defined
     * in the <a href="https://www.eclipse.org/hono/docs/api/credentials-api/">Credentials API</a>.
     * <p>
     * If this property is {@code false} then devices are always allowed to publish data without providing
     * credentials. This should only be set to false in test setups.
     * <p>
     * The default value of this property is {@code true}.
     *
     * @return {@code true} if the protocol adapter demands the authentication of devices to allow the publishing of data.
     */
    public final boolean isAuthenticationRequired() {
        return authenticationRequired;
    }

    /**
     * Sets whether the protocol adapter always authenticates devices using their provided credentials as defined
     * in the <a href="https://www.eclipse.org/hono/docs/api/credentials-api/">Credentials API</a>.
     * <p>
     * If this property is set to {@code false} then devices are always allowed to publish data without providing
     * credentials. This should only be set to false in test setups.
     * <p>
     * The default value of this property is {@code true}.
     *
     * @param authenticationRequired {@code true} if the server should wait for downstream connections to be established during startup.
     */
    public final void setAuthenticationRequired(final boolean authenticationRequired) {
        this.authenticationRequired = authenticationRequired;
    }

    /**
     * Checks if adapter should include <em>Vendor Properties</em> as defined by <a
     * href="https://www.oasis-open.org/committees/download.php/60574/amqp-bindmap-jms-v1.0-wd09.pdf">
     * Advanced Message Queuing Protocol (AMQP) JMS Mapping Version 1.0, Chapter 4</a>
     * in downstream messages.
     * <p>
     * The default value of this property is {@code false}.
     * 
     * @return {@code true} if the properties should be included.
     */
    public final boolean isJmsVendorPropsEnabled() {
        return jmsVendorPropsEnabled;
    }

    /**
     * Sets if the adapter should include <em>Vendor Properties</em> as defined by <a
     * href="https://www.oasis-open.org/committees/download.php/60574/amqp-bindmap-jms-v1.0-wd09.pdf">
     * Advanced Message Queuing Protocol (AMQP) JMS Mapping Version 1.0, Chapter 4</a>
     * in downstream messages.
     * <p>
     * Setting this property to {@code true} can be helpful if downstream consumers
     * receive messages from Hono using a JMS provider which doesn't support the vendor
     * properties but provides access to all application properties of received
     * messages.
     * <p>
     * If set to {@code} the adapter will add the following vendor properties to a message's
     * application properties:
     * <ul>
     * <li>{@code JMS_AMQP_CONTENT_TYPE} - with the value of the standard AMQP 1.0
     * <em>content-type</em> property (if set)</li>
     * <li>{@code JMS_AMQP_CONTENT_ENCODING} - with the value of the standard AMQP 1.0
     * <em>content-encoding</em> property (if set)</li>
     * </ul>
     * <p>
     * The default value of this property is {@code false}.
     * 
     * @param flag {@code true} if the properties should be included.
     */
    public final void setJmsVendorPropsEnabled(final boolean flag) {
        this.jmsVendorPropsEnabled = flag;
    }

    /**
     * Checks if the adapter should use <em>default</em> values registered for a device
     * to augment messages published by the device.
     * <p>
     * Default values that can be registered for devices include:
     * <ul>
     * <li><em>content-type</em> - the default content type to set on a downstream message
     * if the device did not specify a content type when it published the message. This
     * is particularly useful for defining a content type for devices connected via MQTT
     * which does not provide a standard way of setting a content type.</li>
     * </ul>
     * 
     * @return {@code true} if the adapter should use default values.
     */
    public final boolean isDefaultsEnabled() {
        return defaultsEnabled;
    }

    /**
     * Sets if the adapter should use <em>default</em> values registered for a device
     * to augment messages published by the device.
     * <p>
     * Default values that can be registered for devices include:
     * <ul>
     * <li><em>content-type</em> - the default content type to set on a downstream message
     * if the device did not specify a content type when it published the message. This
     * is particularly useful for defining a content type for devices connected via MQTT
     * which does not provide a standard way of setting a content type.</li>
     * </ul>
     * 
     * @param flag {@code true} if the adapter should use default values.
     */
    public final void setDefaultsEnabled(final boolean flag) {
        this.defaultsEnabled = flag;
    }

    /**
     * Gets the timeout value used by protocol adapter liveness check,
     * which determines if protocol adapter vert.x event loop is blocked.
     * <p>
     * Default value of the timeout is 5000 milliseconds.
     *
     * @return The timeout value in milliseconds.
     */
    public final long getEventLoopBlockedCheckTimeout() {
        return eventLoopBlockedCheckTimeout;
    }

    /**
     * Sets the timeout value used by protocol adapter liveness check,
     * which determines if protocol adapter vert.x event loop is blocked.
     * <p>
     *
     * @param eventLoopBlockedCheckTimeout Liveness check timeout value in milliseconds.
     */
    public final void setEventLoopBlockedCheckTimeout(final long eventLoopBlockedCheckTimeout) {
        this.eventLoopBlockedCheckTimeout = eventLoopBlockedCheckTimeout;
    }

    /**
     * Gets the maximum number of concurrent connections that the protocol adapter
     * accepts.
     * <p>
     * The default value of this property is 0 which lets the protocol adapter
     * determine an appropriate value based on e.g. available memory and CPU resources.
     * 
     * @return The number of connections.
     */
    public final int getMaxConnections() {
        return maxConnections;
    }

    /**
     * Sets the maximum number of concurrent connections that the protocol adapter
     * should accept.
     * <p>
     * Setting this property to 0 (the default value) will let the protocol adapter
     * determine an appropriate value based on e.g. available memory and CPU resources.
     * 
     * @param maxConnections The number of connections.
     * @throws IllegalArgumentException if the number is &lt; 0.
     */
    public final void setMaxConnections(final int maxConnections) {
        if (maxConnections < 0) {
            throw new IllegalArgumentException("connection limit must be a positive integer");
        }
        this.maxConnections = maxConnections;
    }

    /**
     * Checks if a connection limit has been configured.
     * 
     * @return {@code true} if the maximum number of connections is &gt; 0.
     */
    public final boolean isConnectionLimitConfigured() {
        return maxConnections > 0;
    }

    /**
     * Gets the duration after which a tenant times out when no messages had been sent for it.
     * <p>
     * The default value of this property is {@link #DEFAULT_TENANT_IDLE_TIMEOUT}, which disables automatic tenant
     * timeout.
     *
     * @return The duration to wait for idle tenants.
     */
    public Duration getTenantIdleTimeout() {
        return tenantIdleTimeout;
    }

    /**
     * Sets the duration after which a tenant times out when no messages had been sent for it.
     * <p>
     * The default value of this property is {@link #DEFAULT_TENANT_IDLE_TIMEOUT}, which disables automatic tenant
     * timeout.
     *
     * @param tenantIdleTimeout The duration to wait for idle tenants.
     * @throws NullPointerException if parameter is {@code null}.
     */
    public void setTenantIdleTimeout(final Duration tenantIdleTimeout) {
        this.tenantIdleTimeout = Objects.requireNonNull(tenantIdleTimeout);
    }
}
