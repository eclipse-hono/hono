/**
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter.coap;

import java.util.Objects;

import org.eclipse.hono.config.ProtocolAdapterProperties;

/**
 * Properties for configuring an COAP adapter.
 */
public class CoapAdapterProperties extends ProtocolAdapterProperties {

    /**
     * The default regular expression to split the identity into authority and tenant.
     */
    public static final String DEFAULT_ID_SPLIT_REGEX = "@";

    private String idSplitRegex = DEFAULT_ID_SPLIT_REGEX;
    private String networkConfig = null;
    private String secureNetworkConfig = null;
    private String insecureNetworkConfig = null;
    private int connectorThreads = 1;
    private int coapThreads = 2;

    /**
     * Gets the regular expression used for splitting up
     * a username into the auth-id and tenant.
     * <p>
     * The default value of this property is {@link #DEFAULT_ID_SPLIT_REGEX}.
     * 
     * @return The regex.
     */
    public final String getIdSplitRegex() {
        return idSplitRegex;
    }

    /**
     * Sets the regular expression to use for splitting up
     * a username into the auth-id and tenant.
     * <p>
     * The default value of this property is {@link #DEFAULT_ID_SPLIT_REGEX}.
     * 
     * @param idSplitRegex The regex.
     * @throws NullPointerException if regex is {@code null}.
     */
    public final void setIdSplitRegex(final String idSplitRegex) {
        this.idSplitRegex = Objects.requireNonNull(idSplitRegex);
    }

    /**
     * Gets the absolute path to a properties file containing
     * network configuration properties that should be used for
     * all CoAP endpoints.
     * 
     * @return The path.
     */
    public final String getNetworkConfig() {
        return networkConfig;
    }

    /**
     * Sets the absolute path to a properties file containing
     * network configuration properties that should be used for
     * all CoAP endpoints..
     * 
     * @param path The path to the properties file.
     */
    public final void setNetworkConfig(final String path) {
        this.networkConfig = Objects.requireNonNull(path);
    }

    /**
     * Gets the absolute path to a properties file containing
     * network configuration properties that should be used for
     * the <em>secure</em> CoAP endpoint only.
     * <p>
     * The properties contained in this file will overwrite
     * properties of the same name read from the file indicated
     * by {@link #getNetworkConfig()}.
     * 
     * @return The path.
     */
    public final String getSecureNetworkConfig() {
        return secureNetworkConfig;
    }

    /**
     * Sets the absolute path to a properties file containing
     * network configuration properties that should be used for
     * the <em>secure</em> CoAP endpoint only.
     * <p>
     * The properties contained in this file will overwrite
     * properties of the same name read from the file indicated
     * by {@link #getNetworkConfig()}.
     * 
     * @param path The path.
     */
    public final void setSecureNetworkConfig(final String path) {
        this.secureNetworkConfig = Objects.requireNonNull(path);
    }

    /**
     * Gets the absolute path to a properties file containing
     * network configuration properties that should be used for
     * the <em>insecure</em> CoAP endpoint only.
     * <p>
     * The properties contained in this file will overwrite
     * properties of the same name read from the file indicated
     * by {@link #getNetworkConfig()}.
     * 
     * @return The path.
     */
    public final String getInsecureNetworkConfig() {
        return insecureNetworkConfig;
    }

    /**
     * Sets the absolute path to a properties file containing
     * network configuration properties that should be used for
     * the <em>insecure</em> CoAP endpoint only.
     * <p>
     * The properties contained in this file will overwrite
     * properties of the same name read from the file indicated
     * by {@link #getNetworkConfig()}.
     * 
     * @param path The path.
     */
    public final void setInsecureNetworkConfig(final String path) {
        this.insecureNetworkConfig = Objects.requireNonNull(path);
    }

    /**
     * Gets the number of threads used for receiving/sending UDP packets.
     * <p>
     * The connector will start the given number of threads for each direction, outbound (sending)
     * as well as inbound (receiving).
     * <p>
     * The default value of this property is 1.
     * 
     * @return The number of threads.
     */
    public final int getConnectorThreads() {
        return connectorThreads;
    }

    /**
     * Gets the number of threads to use for receiving/sending UDP packets.
     * <p>
     * The connector will start the given number of threads for each direction, outbound (sending)
     * as well as inbound (receiving).
     * <p>
     * The default value of this property is 1.
     * 
     * @param threads The number of threads.
     * @throws IllegalArgumentException if threads is &lt; 1.
     */
    public final void setConnectorThreads(final int threads) {
        if (threads < 1) {
            throw new IllegalArgumentException("connector thread count must be at least 1");
        }
        this.connectorThreads = threads;
    }

    /**
     * Gets the number of threads used for processing CoAP message exchanges at the
     * protocol layer.
     * <p>
     * The default value of this property is 2.
     * 
     * @return The number of threads.
     */
    public final int getCoapThreads() {
        return coapThreads;
    }

    /**
     * Sets the number of threads to be used for processing CoAP message exchanges at the
     * protocol layer.
     * <p>
     * The default value of this property is 2.
     * 
     * @param threads The number of threads.
     * @throws IllegalArgumentException if threads is &lt; 1.
     */
    public final void setCoapThreads(final int threads) {
        if (threads < 1) {
            throw new IllegalArgumentException("protocol thread count must be at least 1");
        }
        this.coapThreads = threads;
    }

}
