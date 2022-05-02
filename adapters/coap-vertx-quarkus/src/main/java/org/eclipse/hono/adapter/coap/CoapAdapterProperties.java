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

import org.eclipse.hono.adapter.ProtocolAdapterProperties;

/**
 * Properties for configuring the CoAP protocol adapter.
 */
public class CoapAdapterProperties extends ProtocolAdapterProperties {

    /**
     * The default regular expression to split the identity into authority and tenant.
     */
    public static final String DEFAULT_ID_SPLIT_REGEX = "@";
    /**
     * The default number of threads for the connector.
     */
    public static final int DEFAULT_CONNECTOR_THREADS;
    /**
     * The default number of threads for the coap protocol stack.
     */
    public static final int DEFAULT_COAP_THREADS;
    /**
     * The default number of threads for the dtls connector.
     */
    public static final int DEFAULT_DTLS_THREADS;
    /**
     * The default timeout in milliseconds for DTLS retransmissions.
     */
    public static final int DEFAULT_DTLS_RETRANSMISSION_TIMEOUT = 2000;
    /**
     * The default exchange lifetime in milliseconds.
     *
     * According <a href= "https://tools.ietf.org/html/rfc7252#page-30"> RFC 7252 - 4.8. Transmission Parameters</a> the
     * default value is 247 seconds. Such a large time requires also a huge amount of heap. That time includes a
     * processing time of 100s and retransmissions of CON messages. Therefore a practical value could be much smaller.
     */
    public static final int DEFAULT_EXCHANGE_LIFETIME = 247000;
    /**
     * The default for message offloading. When messages are kept for deduplication, some parts of the messages are not
     * longer required and could be release earlier. That helps to reduce the heap consumption.
     */
    public static final boolean DEFAULT_MESSAGE_OFFLOADING = true;
    /**
     * The default timeout in milliseconds to send a separate ACK.
     */
    public static final int DEFAULT_TIMEOUT_TO_ACK = 500;
    /**
     * The default blockwise status lifetime in milliseconds.
     *
     * Time the blockwise status is kept with further message exchanges. If a blockwise transfer is not continued for
     * that time, the status gets removed and a new request will fail.
     */
    public static final int DEFAULT_BLOCKWISE_STATUS_LIFETIME = 300000;

    static {
        DEFAULT_CONNECTOR_THREADS = 2;
        final int cpu = Runtime.getRuntime().availableProcessors();
        if (cpu < 4) {
            DEFAULT_COAP_THREADS = 4;
            DEFAULT_DTLS_THREADS = 4;
        } else {
            DEFAULT_COAP_THREADS = cpu;
            DEFAULT_DTLS_THREADS = cpu;
        }
    }

    private String idSplitRegex = DEFAULT_ID_SPLIT_REGEX;
    private String networkConfig = null;
    private String secureNetworkConfig = null;
    private String insecureNetworkConfig = null;
    private int connectorThreads = DEFAULT_CONNECTOR_THREADS;
    private int coapThreads = DEFAULT_COAP_THREADS;
    private int dtlsThreads = DEFAULT_DTLS_THREADS;
    private int dtlsRetransmissionTimeout = DEFAULT_DTLS_RETRANSMISSION_TIMEOUT;
    private int exchangeLifetime = DEFAULT_EXCHANGE_LIFETIME;
    private int blockwiseStatusLifetime = DEFAULT_BLOCKWISE_STATUS_LIFETIME;
    private boolean messageOffloadingEnabled = DEFAULT_MESSAGE_OFFLOADING;
    private int timeoutToAck = DEFAULT_TIMEOUT_TO_ACK;

    /**
     * Creates properties using default values.
     */
    public CoapAdapterProperties() {
        super();
    }

    /**
     * Creates properties using existing options.
     *
     * @param options The options to copy.
     */
    public CoapAdapterProperties(final CoapAdapterOptions options) {
        super(options.adapterOptions());
        setCoapThreads(options.coapThreads());
        setConnectorThreads(options.connectorThreads());
        setDtlsRetransmissionTimeout(options.dtlsRetransmissionTimeout());
        setDtlsThreads(options.dtlsThreads());
        setExchangeLifetime(options.exchangeLifetime());
        setIdSplitRegex(options.idSplitRegex());
        this.insecureNetworkConfig = options.insecureNetworkConfig().orElse(null);
        this.messageOffloadingEnabled = options.messageOffloadingEnabled();
        this.networkConfig = options.networkConfig().orElse(null);
        this.secureNetworkConfig = options.secureNetworkConfig().orElse(null);
        setTimeoutToAck(options.timeoutToAck());
    }

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
     * The default value of this property is {@link #DEFAULT_CONNECTOR_THREADS}.
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
     * The default value of this property is {@link #DEFAULT_CONNECTOR_THREADS}.
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
     * The default value of this property is {@link #DEFAULT_COAP_THREADS}.
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
     * The default value of this property is {@link #DEFAULT_COAP_THREADS}.
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

    /**
     * Gets the number of threads used for processing DTLS message exchanges at the
     * connection layer.
     * <p>
     * The default value of this property is {@link #DEFAULT_DTLS_THREADS}.
     *
     * @return The number of threads.
     */
    public final int getDtlsThreads() {
        return dtlsThreads;
    }

    /**
     * Sets the number of threads to be used for processing DTLS message exchanges at the
     * connection layer.
     * <p>
     * The default value of this property is {@link #DEFAULT_DTLS_THREADS}.
     *
     * @param threads The number of threads.
     * @throws IllegalArgumentException if threads is &lt; 1.
     */
    public final void setDtlsThreads(final int threads) {
        if (threads < 1) {
            throw new IllegalArgumentException("dtls thread count must be at least 1");
        }
        this.dtlsThreads = threads;
    }

    /**
     * Gets the DTLS retransmission timeout.
     * <p>
     * Timeout to wait before retransmit a flight, if no response is received.
     * <p>
     * The default value of this property is {@value #DEFAULT_DTLS_RETRANSMISSION_TIMEOUT} milliseconds.
     *
     * @return The timeout in milliseconds.
     */
    public final int getDtlsRetransmissionTimeout() {
        return dtlsRetransmissionTimeout;
    }

    /**
     * Sets the DTLS retransmission timeout.
     * <p>
     * Timeout to wait before retransmit a flight, if no response is received.
     * <p>
     * The default value of this property is {@value #DEFAULT_DTLS_RETRANSMISSION_TIMEOUT} milliseconds.
     *
     * @param dtlsRetransmissionTimeout timeout in milliseconds to retransmit a flight.
     * @throws IllegalArgumentException if dtlsRetransmissionTimeout is &lt; 1.
     */
    public final void setDtlsRetransmissionTimeout(final int dtlsRetransmissionTimeout) {
        if (dtlsRetransmissionTimeout < 1) {
            throw new IllegalArgumentException("dtls retransmission timeout must be at least 1");
        }
        this.dtlsRetransmissionTimeout = dtlsRetransmissionTimeout;
    }

    /**
     * Gets the exchange lifetime.
     * <p>
     * Time to keep coap request for deduplication.
     * <p>
     * The default value of this property is {@value #DEFAULT_EXCHANGE_LIFETIME} milliseconds.
     *
     * @return The exchange lifetime in milliseconds.
     */
    public final int getExchangeLifetime() {
        return exchangeLifetime;
    }

    /**
     * Gets the exchange lifetime.
     * <p>
     * Time to keep coap request for deduplication.
     * <p>
     * The default value of this property is {@value #DEFAULT_EXCHANGE_LIFETIME} milliseconds.
     *
     * @param exchangeLifetime the exchange lifetime in milliseconds to keep the request for deduplication.
     * @throws IllegalArgumentException if exchangeLifetime is &lt; 1.
     */
    public final void setExchangeLifetime(final int exchangeLifetime) {
        if (exchangeLifetime < 1) {
            throw new IllegalArgumentException("exchange lifetime must be at least 1");
        }
        this.exchangeLifetime = exchangeLifetime;
    }

    /**
     * Gets the blockwise status lifetime.
     * <p>
     * Time to keep a blockwise status for follow up block requests.
     * <p>
     * The default value of this property is {@value #DEFAULT_BLOCKWISE_STATUS_LIFETIME} milliseconds.
     *
     * @return The blockwise status lifetime in milliseconds.
     */
    public final int getBlockwiseStatusLifetime() {
        return blockwiseStatusLifetime;
    }

    /**
     * Gets the blockwise status lifetime.
     * <p>
     * Time to keep blockwise status for follow up block requests.
     * <p>
     * The default value of this property is {@value #DEFAULT_BLOCKWISE_STATUS_LIFETIME} milliseconds.
     *
     * @param blockwiseStatusLifetime the blockwise status lifetime in milliseconds to keep the blockwise status for
     *            follow up block requests.
     * @throws IllegalArgumentException if blockwise status lifetime is &lt; 1.
     */
    public final void setBlockwiseStatusLifetime(final int blockwiseStatusLifetime) {
        if (blockwiseStatusLifetime < 1) {
            throw new IllegalArgumentException("blockwise status lifetime must be at least 1");
        }
        this.blockwiseStatusLifetime = blockwiseStatusLifetime;
    }

    /**
     * Checks, if message offloading is enabled.
     * <p>
     * When messages are kept for deduplication, parts of the message could be offloaded to reduce the heap consumption.
     * <p>
     * The default value of this property {@value #DEFAULT_MESSAGE_OFFLOADING}.
     *
     * @return {@code true} enable message offloading, {@code false} disable message offloading.
     */
    public final boolean isMessageOffloadingEnabled() {
        return messageOffloadingEnabled;
    }

    /**
     * Sets the message offloading mode.
     * <p>
     * When messages are kept for deduplication, parts of the message could be offloaded to reduce the heap consumption.
     * <p>
     * The default value of this property is {@value #DEFAULT_MESSAGE_OFFLOADING}.
     *
     * @param messageOffloading {@code true} enable message offloading, {@code false} disable message offloading.
     */
    public final void setMessageOffloadingEnabled(final boolean messageOffloading) {
        this.messageOffloadingEnabled = messageOffloading;
    }

    /**
     * Gets the timeout to ACK a CoAP CON request.
     * <p>
     * If the response is available before that timeout, a more efficient piggybacked response is used. If the timeout
     * is reached without response, a separate ACK is sent to prevent the client from retransmitting the CON request.
     * <p>
     * The default value of this property is {@value #DEFAULT_TIMEOUT_TO_ACK} milliseconds.
     *
     * @return The timeout in milliseconds. A value of {@code -1} means to always piggyback the response in an ACK and
     *         never send a separate CON; a value of {@code 0} means to always send an ACK immediately and include the
     *         response in a separate CON.
     */
    public final int getTimeoutToAck() {
        return timeoutToAck;
    }

    /**
     * Sets the timeout to ACK a CoAP CON request.
     * <p>
     * If the response is available before that timeout, a more efficient piggybacked response is used. If the timeout
     * is reached without response, a separate ACK is sent to prevent the client form retransmitting the CON request.
     * <p>
     * The default value of this property is {@value #DEFAULT_TIMEOUT_TO_ACK} milliseconds.
     *
     * @param timeoutToAck timeout in milliseconds to send a separate ACK. A value of {@code -1} means to always
     *            piggyback the response in an ACK and never send a separate CON; a value of {@code 0} means to always
     *            send an ACK immediately and include the response in a separate CON.
     * @throws IllegalArgumentException if timeoutToAck is &lt; -1.
     */
    public final void setTimeoutToAck(final int timeoutToAck) {
        if (timeoutToAck < -1) {
            throw new IllegalArgumentException("timeout to ack must be at least -1");
        }
        this.timeoutToAck = timeoutToAck;
    }
}
