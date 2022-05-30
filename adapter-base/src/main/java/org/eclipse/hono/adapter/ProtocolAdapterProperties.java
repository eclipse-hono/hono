/*******************************************************************************
 * Copyright (c) 2016, 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.eclipse.hono.config.ServiceConfigProperties;

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
    /**
     * The default share of heap memory that should not be used by the live-data set.
     */
    public static final int DEFAULT_GC_HEAP_PERCENTAGE = 25;

    private boolean authenticationRequired = true;
    private boolean jmsVendorPropsEnabled = false;
    private boolean defaultsEnabled = true;
    private int maxConnections = 0;
    private Duration tenantIdleTimeout = DEFAULT_TENANT_IDLE_TIMEOUT;
    private int gcHeapPercentage = DEFAULT_GC_HEAP_PERCENTAGE;
    private Map<String, MapperEndpoint> mapperEndpoints = new HashMap<>();

    /**
     * Creates properties using default values.
     */
    public ProtocolAdapterProperties() {
        super();
    }

    /**
     * Creates properties using existing options.
     *
     * @param options The options to copy.
     */
    public ProtocolAdapterProperties(final ProtocolAdapterOptions options) {
        super(options.serviceOptions());
        this.authenticationRequired = options.authenticationRequired();
        this.defaultsEnabled = options.defaultsEnabled();
        this.gcHeapPercentage = options.gcHeapPercentage();
        this.jmsVendorPropsEnabled = options.jmsVendorPropsEnabled();
        options.mapperEndpoints().entrySet()
            .forEach(entry -> mapperEndpoints.put(entry.getKey(), new MapperEndpoint(entry.getValue())));
        this.maxConnections = options.maxConnections();
        this.tenantIdleTimeout = options.tenantIdleTimeout();
    }

    /**
     * Checks whether the protocol adapter always authenticates devices using their provided credentials as defined
     * in the <a href="https://www.eclipse.org/hono/docs/api/credentials/">Credentials API</a>.
     * <p>
     * If this property is {@code false} then devices are always allowed to publish data without providing
     * credentials. This should only be set to false in test setups.
     * <p>
     * The default value of this property is {@code true}.
     *
     * @return {@code true} if the protocol adapter should require clients to authenticate.
     */
    public final boolean isAuthenticationRequired() {
        return authenticationRequired;
    }

    /**
     * Sets whether the protocol adapter always authenticates devices using their provided credentials as defined
     * in the <a href="https://www.eclipse.org/hono/docs/api/credentials/">Credentials API</a>.
     * <p>
     * If this property is set to {@code false} then devices are always allowed to publish data without providing
     * credentials. This should only be set to false in test setups.
     * <p>
     * The default value of this property is {@code true}.
     *
     * @param authenticationRequired {@code true} if the protocol adapter should require clients to authenticate.
     */
    public final void setAuthenticationRequired(final boolean authenticationRequired) {
        this.authenticationRequired = authenticationRequired;
    }

    /**
     * Checks if the adapter should include <em>Vendor Properties</em> as defined by <a
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
     * If set to {@code true} the adapter will add the following vendor properties to a message's
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
     * Gets the share of heap memory that should not be used by the live-data set but should be left
     * to be used by the garbage collector.
     * <p>
     * This value should be adapted based on the total amount of heap memory available to the JVM and
     * the type of garbage collector being used.
     * <p> The default value of this property is {@value #DEFAULT_GC_HEAP_PERCENTAGE}.
     *
     * @return The percentage of the heap memory reserved for the GC.
     */
    public final int getGcHeapPercentage() {
        return gcHeapPercentage;
    }

    /**
     * Sets the share of heap memory that should not be used by the live-data set but should be left
     * to be used by the garbage collector.
     * <p>
     * This value should be adapted based on the total amount of heap memory available to the JVM and
     * the type of garbage collector being used.
     * <p> The default value of this property is {@value #DEFAULT_GC_HEAP_PERCENTAGE}.
     *
     * @param share The percentage of the heap memory to be reserved for the GC.
     * @throws IllegalArgumentException if the share is &lt; 0 or &gt; 100.
     */
    public final void setGcHeapPercentage(final int share) {
        if (share < 0 || share > 100) {
            throw new IllegalArgumentException("percentage must be an integer in the range [0,100]");
        }
        this.gcHeapPercentage = share;
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
     * @throws NullPointerException if tenantIdleTimeout is {@code null}.
     */
    public void setTenantIdleTimeout(final Duration tenantIdleTimeout) {
        this.tenantIdleTimeout = Objects.requireNonNull(tenantIdleTimeout);
    }

    /**
     * Sets the configured mappers for this adapter
     * <p>
     * Setting this property to an empty hashMap will disable mapping for this adapter.
     *
     * @param mapperEndpoints The new hashMap with mapper endpoints
     * @throws NullPointerException if mapperEndpoints is {@code null}.
     */
    public final void setMapperEndpoints(final Map<String, MapperEndpoint> mapperEndpoints) {
        Objects.requireNonNull(mapperEndpoints);
        this.mapperEndpoints.clear();
        this.mapperEndpoints.putAll(mapperEndpoints);
    }

    /**
     * Gets the configured mapper for the given key.
     *
     * @param key The key to identify the mapper
     * @return the mapperEndpoint. If not found, will return {@code null}.
     */
    public final MapperEndpoint getMapperEndpoint(final String key) {
        return mapperEndpoints.get(key);
    }
}
