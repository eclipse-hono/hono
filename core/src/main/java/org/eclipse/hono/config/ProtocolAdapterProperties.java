/**
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.config;

/**
 * Common configuration properties for protocol adapters of Hono.
 *
 */
public class ProtocolAdapterProperties extends ServiceConfigProperties {

    private boolean authenticationRequired = true;
    private boolean jmsVendorPropsEnabled = false;
    private boolean defaultsEnabled = true;

    /**
     * Checks whether the protocol adapter always authenticates devices using their provided credentials as defined
     * in the <a href="https://www.eclipse.org/hono/api/credentials-api/">Credentials API</a>.
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
     * in the <a href="https://www.eclipse.org/hono/api/credentials-api/">Credentials API</a>.
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
    public boolean isDefaultsEnabled() {
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
    public void setDefaultsEnabled(final boolean flag) {
        this.defaultsEnabled = flag;
    }
}
