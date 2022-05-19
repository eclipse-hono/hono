/*******************************************************************************
 * Copyright (c) 2019, 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter.sigfox.impl;

import org.eclipse.hono.adapter.http.HttpProtocolAdapterProperties;

/**
 * Enhanced configuration properties for {@link SigfoxProtocolAdapter}.
 */
public class SigfoxProtocolAdapterProperties extends HttpProtocolAdapterProperties {

    private static final int DEFAULT_TTD_WHEN_ACK_REQUIRED = 20;

    private int ttdWhenAckRequired = DEFAULT_TTD_WHEN_ACK_REQUIRED;

    /**
     * Creates default properties.
     */
    public SigfoxProtocolAdapterProperties() {
        // nothing to do
    }

    /**
     * Creates properties for existing options.
     *
     * @param options The options.
     * @throws NullPointerException if options is {@code null}.
     */
    public SigfoxProtocolAdapterProperties(final SigfoxProtocolAdapterOptions options) {
        super(options.httpAdapterOptions());
        setTtdWhenAckRequired(options.ttdWhenAckRequired());
    }

    /**
     * Sets a custom TTD value which is being used when an ack is required.
     * <p>
     * This defaults to the Sigfox default of 20 seconds. Only change this when you know what you are doing.
     *
     * @param ttdWhenAckRequired The custom value to set the TTD to. Must not be negative. It may be zero, in which case
     *            the adapter will never wait for command.
     * @throws IllegalArgumentException If the value is negative.
     */
    public void setTtdWhenAckRequired(final int ttdWhenAckRequired) {
        if (ttdWhenAckRequired < 0) {
            throw new IllegalArgumentException("'ttdWhenAckRequired' must not be negative");
        }
        this.ttdWhenAckRequired = ttdWhenAckRequired;
    }

    /**
     * Gets the TTD value which is being used when an ack is required.
     * <p>
     * This defaults to the Sigfox default of 20 seconds. Only change this when you know what you are doing.
     *
     * @return The custom value to set the TTD to.
     */
    public int getTtdWhenAckRequired() {
        return ttdWhenAckRequired;
    }

}
