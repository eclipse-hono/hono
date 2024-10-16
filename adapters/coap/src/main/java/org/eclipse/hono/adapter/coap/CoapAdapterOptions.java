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

package org.eclipse.hono.adapter.coap;

import java.util.Optional;

import org.eclipse.hono.adapter.ProtocolAdapterOptions;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.ConfigMapping.NamingStrategy;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithParentName;

/**
 * Options for configuring the CoAP protocol adapter.
 *
 */
@ConfigMapping(prefix = "hono.coap", namingStrategy = NamingStrategy.VERBATIM)
public interface CoapAdapterOptions {

    /**
     * Gets the adapter options.
     *
     * @return The options.
     */
    @WithParentName
    ProtocolAdapterOptions adapterOptions();

    /**
     * Gets the regular expression used for splitting up
     * a username into the auth-id and tenant.
     *
     * @return The regex.
     */
    @WithDefault("@")
    String idSplitRegex();

    /**
     * Gets the absolute path to a properties file containing
     * network configuration properties that should be used for
     * all CoAP endpoints.
     *
     * @return The path.
     */
    Optional<String> networkConfig();

    /**
     * Gets the absolute path to a properties file containing
     * network configuration properties that should be used for
     * the <em>secure</em> CoAP endpoint only.
     * <p>
     * The properties contained in this file will overwrite
     * properties of the same name read from the file indicated
     * by {@link #networkConfig()}.
     *
     * @return The path.
     */
    Optional<String> secureNetworkConfig();

    /**
     * Gets the absolute path to a properties file containing
     * network configuration properties that should be used for
     * the <em>insecure</em> CoAP endpoint only.
     * <p>
     * The properties contained in this file will overwrite
     * properties of the same name read from the file indicated
     * by {@link #networkConfig()}.
     *
     * @return The path.
     */
    Optional<String> insecureNetworkConfig();

    /**
     * Gets the number of threads used for receiving/sending UDP packets.
     * <p>
     * The connector will start the given number of threads for each direction, outbound (sending)
     * as well as inbound (receiving).
     *
     * @return The number of threads.
     */
    @WithDefault("2")
    int connectorThreads();

    /**
     * Gets the number of threads used for processing CoAP message exchanges at the
     * protocol layer.
     *
     * @return The number of threads.
     */
    @WithDefault("2")
    int coapThreads();

    /**
     * Gets the number of threads used for processing DTLS message exchanges at the
     * connection layer.
     *
     * @return The number of threads.
     */
    @WithDefault("32")
    int dtlsThreads();

    /**
     * Gets the DTLS retransmission timeout.
     * <p>
     * Timeout to wait before retransmit a flight, if no response is received.
     *
     * @return The timeout in milliseconds.
     */
    @WithDefault("2000")
    int dtlsRetransmissionTimeout();

    /**
     * Gets the exchange lifetime.
     * <p>
     * Time to keep coap request for deduplication.
     *
     * @return The exchange lifetime in milliseconds.
     */
    @WithDefault("247000")
    int exchangeLifetime();

    /**
     * Checks, if message offloading is enabled.
     * <p>
     * When messages are kept for deduplication, parts of the message could be offloaded to reduce the heap consumption.
     *
     * @return {@code true} enable message offloading, {@code false} disable message offloading.
     */
    @WithDefault("true")
    boolean messageOffloadingEnabled();

    /**
     * Gets the timeout to ACK a CoAP CON request.
     * <p>
     * If the response is available before that timeout, a more efficient piggybacked response is used. If the timeout
     * is reached without response, a separate ACK is sent to prevent the client from retransmitting the CON request.
     *
     * @return The timeout in milliseconds. A value of {@code -1} means to always piggyback the response in an ACK and
     *         never send a separate CON; a value of {@code 0} means to always send an ACK immediately and include the
     *         response in a separate CON.
     */
    @WithDefault("500")
    int timeoutToAck();
}
