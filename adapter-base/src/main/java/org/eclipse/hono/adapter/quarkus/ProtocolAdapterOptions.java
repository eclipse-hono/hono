/**
 * Copyright (c) 2021, 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter.quarkus;

import java.time.Duration;
import java.util.Map;

import org.eclipse.hono.adapter.MapperEndpointOptions;
import org.eclipse.hono.config.quarkus.ServiceOptions;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.ConfigMapping.NamingStrategy;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithParentName;

/**
 * Common options for configuring protocol adapters.
 *
 */
@ConfigMapping(prefix = "hono.adapter", namingStrategy = NamingStrategy.VERBATIM)
public interface ProtocolAdapterOptions {

    /**
     * Gets the service options.
     *
     * @return The options.
     */
    @WithParentName
    ServiceOptions serviceOptions();

    /**
     * Checks whether the protocol adapter always authenticates devices using their provided credentials as defined
     * in the <a href="https://www.eclipse.org/hono/docs/api/credentials/">Credentials API</a>.
     * <p>
     * If this property is {@code false} then devices are always allowed to publish data without providing
     * credentials. This should only be set to false in test setups.
     *
     * @return {@code true} if the protocol adapter should require clients to authenticate.
     */
    @WithDefault("true")
    boolean authenticationRequired();

    /**
     * Checks if the adapter should include <em>Vendor Properties</em> as defined by <a
     * href="https://www.oasis-open.org/committees/download.php/60574/amqp-bindmap-jms-v1.0-wd09.pdf">
     * Advanced Message Queuing Protocol (AMQP) JMS Mapping Version 1.0, Chapter 4</a>
     * in downstream messages.
     *
     * @return {@code true} if the properties should be included.
     */
    @WithDefault("false")
    boolean jmsVendorPropsEnabled();

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
    @WithDefault("true")
    boolean defaultsEnabled();

    /**
     * Gets the maximum number of concurrent connections that the protocol adapter
     * accepts.
     * <p>
     * The default value of this property is 0 which lets the protocol adapter
     * determine an appropriate value based on e.g. available memory and CPU resources.
     *
     * @return The number of connections.
     */
    @WithDefault("0")
    int maxConnections();

    /**
     * Gets the duration after which a tenant times out when no messages had been sent for it.
     * <p>
     * The default value of this property is {@link Duration#ZERO}, which disables automatic tenant
     * timeout.
     *
     * @return The duration to wait for idle tenants.
     */
    @WithDefault("PT0S")
    Duration tenantIdleTimeout();

    /**
     * Gets the configured mapper endpoints.
     *
     * @return The endpoints.
     */
    Map<String, MapperEndpointOptions> mapperEndpoints();
}
