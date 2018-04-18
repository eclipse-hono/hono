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

    private String networkConfig = null;
    private String insecureNetworkConfig = null;
    private int connectorThreads = 1;
    private int coapThreads = 2;


    public final String getNetworkConfig() {
        return networkConfig;
    }

    public final void setNetworkConfig(final String networkConfig) {
        this.networkConfig = Objects.requireNonNull(networkConfig);
    }

    public final String getInsecureNetworkConfig() {
        return insecureNetworkConfig;
    }

    public final void setInsecureNetworkConfig(final String insecureNetworkConfig) {
        this.insecureNetworkConfig = Objects.requireNonNull(insecureNetworkConfig);
    }

    /**
     * Gets the number of connector threads.
     * 
     * The connector will start sender and receiver threads, so the resulting number will be doubled!
     * 
     * @return The number of threads per connector.
     */
    public final int getConnectorThreads() {
        return connectorThreads;
    }

    /**
     * Sets the number of connector threads.
     * 
     * @param threads The number of sender and receiver threads per connector.
     * @throws IllegalArgumentException if threads is &lt; 1.
     */
    public final void setConnectorThreads(final int threads) {
        if (threads < 1) {
            throw new IllegalArgumentException("connector threads must not be less than one");
        }
        this.connectorThreads = threads;
    }

    /**
     * Gets the number of coap threads.
     * 
     * @return The number of threads for coap stack.
     */
    public final int getCoapThreads() {
        return coapThreads;
    }

    /**
     * Sets the number of coap protocol threads.
     * 
     * @param threads The number of threads for the coap protocol stack.
     * @throws IllegalArgumentException if threads is &lt; 1.
     */
    public final void setCoapThreads(final int threads) {
        if (threads < 1) {
            throw new IllegalArgumentException("protocol threads must not be less than one");
        }
        this.coapThreads = threads;
    }

}
