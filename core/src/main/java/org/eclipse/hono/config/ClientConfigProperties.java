/*******************************************************************************
 * Copyright (c) 2016, 2020 Contributors to the Eclipse Foundation
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

import org.eclipse.hono.util.Constants;

/**
 * Common configuration properties required for accessing an AMQP 1.0 container.
 */
public class ClientConfigProperties extends AuthenticatingClientConfigProperties {

    /**
     * The default amount of time (milliseconds) to wait for an AMQP connection to
     * be opened.
     */
    public static final int DEFAULT_CONNECT_TIMEOUT = 5000; // ms
    /**
     * The default amount of time (milliseconds) to wait for credits after link creation.
     */
    public static final long DEFAULT_FLOW_LATENCY = 20L; //ms
    /**
     * The default amount of time (milliseconds) after which a connection will be closed
     * when no frames have been received from the remote peer.
     */
    public static final int DEFAULT_IDLE_TIMEOUT = 16000; //ms
    /**
     * The default number of credits issued by the receiver side of a link.
     */
    public static final int  DEFAULT_INITIAL_CREDITS = 200;
    /**
     * The default amount of time (milliseconds) to wait for the remote peer's <em>attach</em>
     * frame during link establishment.
     */
    public static final long DEFAULT_LINK_ESTABLISHMENT_TIMEOUT = 1000L; //ms
    /**
     * The default minimum amount of time (milliseconds) to wait before trying to re-establish an
     * AMQP connection with the peer.
     */
    public static final int DEFAULT_RECONNECT_MIN_DELAY = 0; // ms
    /**
     * The default maximum amount of time (milliseconds) to wait before trying to re-establish an
     * AMQP connection with the peer.
     */
    public static final int DEFAULT_RECONNECT_MAX_DELAY = 7000; // ms
    /**
     * The default amount of time (milliseconds) that the delay before trying to re-establish an
     * AMQP connection with the peer will be increased by with each successive attempt.
     */
    public static final int DEFAULT_RECONNECT_DELAY_INCREMENT = 100; // ms

    /**
     * The default amount of time (milliseconds) to wait for a response before a request times out.
     */
    public static final long DEFAULT_REQUEST_TIMEOUT = 200L; // ms
    /**
     * The default amount of time (milliseconds) to wait for a delivery update after a message was sent.
     */
    public static final long DEFAULT_SEND_MESSAGE_TIMEOUT = 1000L; // ms

    private String amqpHostname;
    private int connectTimeoutMillis = DEFAULT_CONNECT_TIMEOUT;
    private long flowLatency = DEFAULT_FLOW_LATENCY;
    private int idleTimeoutMillis = DEFAULT_IDLE_TIMEOUT;
    private int initialCredits = DEFAULT_INITIAL_CREDITS;
    private long linkEstablishmentTimeout = DEFAULT_LINK_ESTABLISHMENT_TIMEOUT;
    private String name;
    private int reconnectAttempts = -1;
    private long reconnectMinDelayMillis = DEFAULT_RECONNECT_MIN_DELAY;
    private long reconnectMaxDelayMillis = DEFAULT_RECONNECT_MAX_DELAY;
    private long reconnectDelayIncrementMillis = DEFAULT_RECONNECT_DELAY_INCREMENT;
    private long requestTimeoutMillis = DEFAULT_REQUEST_TIMEOUT;
    private long sendMessageTimeoutMillis = DEFAULT_SEND_MESSAGE_TIMEOUT;

    /**
     * Creates new properties with default values.
     */
    public ClientConfigProperties() {
        super();
        setPort(Constants.PORT_AMQPS);
    }

    /**
     * Creates properties based on other properties.
     * 
     * @param otherProperties The properties to copy.
     */
    public ClientConfigProperties(final ClientConfigProperties otherProperties) {
        super(otherProperties);
        this.amqpHostname = otherProperties.amqpHostname;
        this.connectTimeoutMillis = otherProperties.connectTimeoutMillis;
        this.flowLatency = otherProperties.flowLatency;
        this.idleTimeoutMillis = otherProperties.idleTimeoutMillis;
        this.initialCredits = otherProperties.initialCredits;
        this.linkEstablishmentTimeout = otherProperties.linkEstablishmentTimeout;
        this.name = otherProperties.name;
        this.reconnectAttempts = otherProperties.reconnectAttempts;
        this.reconnectMinDelayMillis = otherProperties.reconnectMinDelayMillis;
        this.reconnectMaxDelayMillis = otherProperties.reconnectMaxDelayMillis;
        this.reconnectDelayIncrementMillis = otherProperties.reconnectDelayIncrementMillis;
        this.requestTimeoutMillis = otherProperties.requestTimeoutMillis;
        this.sendMessageTimeoutMillis = otherProperties.sendMessageTimeoutMillis;
    }

    /**
     * Gets the name being indicated as the <em>container-id</em> in the client's AMQP <em>Open</em> frame.
     * 
     * @return The name or {@code null} if no name has been set.
     */
    public final String getName() {
        return name;
    }

    /**
     * Sets the name to indicate as the <em>container-id</em> in the client's AMQP <em>Open</em> frame.
     * 
     * @param name The name to set.
     */
    public final void setName(final String name) {
        this.name = name;
    }

    /**
     * Gets the name being indicated as the <em>hostname</em> in the client's AMQP <em>Open</em> frame.
     * 
     * @return The host name or {@code null} if no host name has been set.
     */
    public final String getAmqpHostname() {
        return amqpHostname;
    }

    /**
     * Sets the name to indicate as the <em>hostname</em> in the client's AMQP <em>Open</em> frame.
     * 
     * @param amqpHostname The host name to set.
     */
    public final void setAmqpHostname(final String amqpHostname) {
        this.amqpHostname = amqpHostname;
    }

    /**
     * Gets the maximum amount of time that a client should wait for credits after <em>sender link</em>
     * creation.
     * <p>
     * The AMQP 1.0 protocol requires the receiver side of a <em>link</em> to explicitly send a <em>flow</em>
     * frame containing credits granted to the sender after the link has been established.
     * <p>
     * This property can be used to <em>tune</em> the time period to wait according to the network
     * latency involved with the communication link between the client and the service.
     * <p>
     * The default value of this property is {@link #DEFAULT_FLOW_LATENCY}.
     *
     * @return The number of milliseconds to wait.
     */
    public final long getFlowLatency() {
        return flowLatency;
    }

    /**
     * Sets the maximum amount of time that a client should wait for credits after <em>sender link</em>
     * creation.
     * <p>
     * The AMQP 1.0 protocol requires the receiver side of a <em>link</em> to explicitly send a <em>flow</em>
     * frame containing credits granted to the sender after the link has been established.
     * <p>
     * This property can be used to <em>tune</em> the time period to wait according to the network
     * latency involved with the communication link between the client and the service.
     * <p>
     * The default value of this property is {@link #DEFAULT_FLOW_LATENCY}.
     * 
     * @param latency The number of milliseconds to wait.
     * @throws IllegalArgumentException if latency is negative.
     */
    public final void setFlowLatency(final long latency) {
        if (latency < 0) {
            throw new IllegalArgumentException("latency must not be negative");
        } else {
            this.flowLatency = latency;
        }
    }

    /**
     * Gets the maximum amount of time that a client waits for the establishment of a link
     * with a peer.
     * <p>
     * The AMQP 1.0 protocol defines that a link is established once both peers have exchanged their
     * <em>attach</em> frames. The value of this property defines how long the client should wait for
     * the peer's attach frame before considering the attempt to establish the link failed.
     * <p>
     * This property can be used to <em>tune</em> the time period to wait according to the network
     * latency involved with the communication link between the client and the service.
     * <p>
     * The default value of this property is {@link #DEFAULT_LINK_ESTABLISHMENT_TIMEOUT}.
     *
     * @return The number of milliseconds to wait.
     */
    public final long getLinkEstablishmentTimeout() {
        return linkEstablishmentTimeout;
    }

    /**
     * Sets the maximum amount of time that a client should wait for the establishment of a link
     * with a peer.
     * <p>
     * The AMQP 1.0 protocol defines that a link is established once both peers have exchanged their
     * <em>attach</em> frames. The value of this property defines how long the client should wait for
     * the peer's attach frame before considering the attempt to establish the link failed.
     * <p>
     * This property can be used to <em>tune</em> the time period to wait according to the network
     * latency involved with the communication link between the client and the service.
     * <p>
     * The default value of this property is {@link #DEFAULT_FLOW_LATENCY}.
     * 
     * @param latency The number of milliseconds to wait.
     * @throws IllegalArgumentException if latency is negative.
     */
    public final void setLinkEstablishmentTimeout(final long latency) {
        if (latency < 0) {
            throw new IllegalArgumentException("latency must not be negative");
        } else {
            this.linkEstablishmentTimeout = latency;
        }
    }

    /**
     * Gets the number of initial credits, that will be given from a receiver to a sender at link creation.
     * <p>
     * The default value of this property is {@link #DEFAULT_INITIAL_CREDITS}.
     *
     * @return The number of initial credits.
     */
    public final int getInitialCredits() {
        return initialCredits;
    }

    /**
     * Sets the number of initial credits, that will be given from a receiver to a sender at link creation.
     * <p>
     * The default value of this property is {@link #DEFAULT_INITIAL_CREDITS}.
     *
     * @param initialCredits The initial credits to set.
     * @throws IllegalArgumentException if the number is negative.
     */
    public final void setInitialCredits(final int initialCredits) {
        if (initialCredits < 0) {
            throw new IllegalArgumentException("initial credits must not be negative");
        } else {
            this.initialCredits = initialCredits;
        }
    }

    /**
     * Gets the maximum amount of time a client should wait for a delivery update after sending an event or command message.
     * If no delivery update is received in that time, the future with the outcome of the send operation will be failed.
     * <p>
     * The default value of this property is {@link #DEFAULT_SEND_MESSAGE_TIMEOUT}.
     *
     * @return The maximum number of milliseconds to wait.
     */
    public final long getSendMessageTimeout() {
        return sendMessageTimeoutMillis;
    }

    /**
     * Sets the maximum amount of time a client should wait for a delivery update after sending an event or command message.
     * If no delivery update is received in that time, the future with the outcome of the send operation will be failed.
     * <p>
     * The default value of this property is {@link #DEFAULT_SEND_MESSAGE_TIMEOUT}.
     *
     * @param sendMessageTimeoutMillis The maximum number of milliseconds to wait.
     * @throws IllegalArgumentException if timeout is negative.
     */
    public final void setSendMessageTimeout(final long sendMessageTimeoutMillis) {
        if (sendMessageTimeoutMillis < 0) {
            throw new IllegalArgumentException("sendMessageTimeout must not be negative");
        } else {
            this.sendMessageTimeoutMillis = sendMessageTimeoutMillis;
        }
    }

    /**
     * Gets the maximum amount of time a client should wait for a response to a request before the request
     * is failed.
     * <p>
     * The default value of this property is {@link #DEFAULT_REQUEST_TIMEOUT}.
     *
     * @return The maximum number of milliseconds to wait.
     */
    public final long getRequestTimeout() {
        return requestTimeoutMillis;
    }

    /**
     * Sets the maximum amount of time a client should wait for a response to a request before the request
     * is failed.
     * <p>
     * The default value of this property is {@link #DEFAULT_REQUEST_TIMEOUT}.
     *
     * @param requestTimeoutMillis The maximum number of milliseconds to wait.
     * @throws IllegalArgumentException if request timeout is negative.
     */
    public final void setRequestTimeout(final long requestTimeoutMillis) {
        if (requestTimeoutMillis < 0) {
            throw new IllegalArgumentException("request timeout must not be negative");
        } else {
            this.requestTimeoutMillis = requestTimeoutMillis;
        }
    }

    /**
     * Gets the number of attempts (in addition to the original connection attempt)
     * that the client should make in order to establish an AMQP connection with
     * the peer before giving up.
     * <p>
     * The default value of this property is -1 which means that the client
     * will try forever.
     * 
     * @return The number of attempts.
     */
    public int getReconnectAttempts() {
        return reconnectAttempts;
    }

    /**
     * Sets the number of attempts (in addition to the original connection attempt)
     * that the client should make in order to establish an AMQP connection with
     * the peer before giving up.
     * <p>
     * The default value of this property is -1 which means that the client
     * will try forever.
     * 
     * @param attempts The number of attempts to make.
     * @throws IllegalArgumentException if attempts is &lt; -1.
     */
    public void setReconnectAttempts(final int attempts) {
        if (attempts < -1) {
            throw new IllegalArgumentException("attempts must be >= -1");
        } else {
            this.reconnectAttempts = attempts;
        }
    }

    /**
     * Gets the minimum amount of time to wait before trying to re-establish an
     * AMQP connection with the peer.
     * <p>
     * The default value of this property is 0.
     *
     * @return The minimum delay in milliseconds.
     */
    public long getReconnectMinDelay() {
        return reconnectMinDelayMillis;
    }

    /**
     * Sets the minimum amount of time to wait before trying to re-establish an
     * AMQP connection with the peer.
     * <p>
     * The default value of this property is 0.
     *
     * @param reconnectMinDelay The minimum delay in milliseconds.
     * @throws IllegalArgumentException if reconnectMinDelay is &lt; 0.
     */
    public void setReconnectMinDelay(final long reconnectMinDelay) {
        if (reconnectMinDelay < 0) {
            throw new IllegalArgumentException("minimum delay must be >= 0");
        } else {
            this.reconnectMinDelayMillis = reconnectMinDelay;
        }
    }

    /**
     * Gets the maximum amount of time to wait before trying to re-establish an
     * AMQP connection with the peer.
     * <p>
     * The default value of this property is 7000ms.
     *
     * @return The maximum delay in milliseconds.
     */
    public long getReconnectMaxDelay() {
        return reconnectMaxDelayMillis;
    }

    /**
     * Sets the maximum amount of time to wait before trying to re-establish an
     * AMQP connection with the peer.
     * <p>
     * The default value of this property is 7000ms.
     *
     * @param reconnectMaxDelay The maximum delay in milliseconds.
     * @throws IllegalArgumentException if reconnectMaxDelay is &lt; 0.
     */
    public void setReconnectMaxDelay(final long reconnectMaxDelay) {
        if (reconnectMaxDelay < 0) {
            throw new IllegalArgumentException("maximum delay must be >= 0");
        } else {
            this.reconnectMaxDelayMillis = reconnectMaxDelay;
        }
    }

    /**
     * Gets the factor used in the exponential backoff algorithm for determining the delay
     * before trying to re-establish an AMQP connection with the peer.
     * <p>
     * The default value of this property is 100ms.
     *
     * @return The value to exponentially increase the delay by in milliseconds.
     */
    public long getReconnectDelayIncrement() {
        return reconnectDelayIncrementMillis;
    }

    /**
     * Sets the factor used in the exponential backoff algorithm for determining the delay
     * before trying to re-establish an AMQP connection with the peer.
     * <p>
     * The default value of this property is 100ms.
     *
     * @param reconnectDelayIncrement The value to exponentially increase the delay by in milliseconds.
     * @throws IllegalArgumentException if reconnectDelayIncrement is &lt; 0.
     */
    public void setReconnectDelayIncrement(final long reconnectDelayIncrement) {
        if (reconnectDelayIncrement < 0) {
            throw new IllegalArgumentException("value must be >= 0");
        } else {
            this.reconnectDelayIncrementMillis = reconnectDelayIncrement;
        }
    }

    /**
     * Gets the maximum amount of time a client should wait for an AMQP connection
     * with a peer to be opened.
     * <p>
     * This includes the time for TCP/TLS connection establishment, SASL handshake
     * and exchange of the AMQP <em>open</em> frame.
     * <p>
     * The default value of this property is {@link #DEFAULT_CONNECT_TIMEOUT}.
     *
     * @return The maximum number of milliseconds to wait.
     */
    public final int getConnectTimeout() {
        return connectTimeoutMillis;
    }

    /**
     * Sets the maximum amount of time a client should wait for an AMQP connection
     * with a peer to be opened.
     * <p>
     * This includes the time for TCP/TLS connection establishment, SASL handshake
     * and exchange of the AMQP <em>open</em> frame.
     * <p>
     * The default value of this property is {@link #DEFAULT_CONNECT_TIMEOUT}.
     *
     * @param connectTimeoutMillis The maximum number of milliseconds to wait.
     * @throws IllegalArgumentException if connect timeout is negative.
     */
    public final void setConnectTimeout(final int connectTimeoutMillis) {
        if (connectTimeoutMillis < 0) {
            throw new IllegalArgumentException("connect timeout must not be negative");
        } else {
            this.connectTimeoutMillis = connectTimeoutMillis;
        }
    }

    /**
     * Gets the interval in milliseconds in which frames are sent to the remote peer to check 
     * that the connection is still alive.
     * <p>
     * This value is set to be half of {@link #getIdleTimeout()}.
     *
     * @return The heartbeatInterval in milliseconds.
     */
    public final int getHeartbeatInterval() {
        return idleTimeoutMillis / 2;
    }

    /**
     * Gets the amount of time in milliseconds after which a connection will be closed
     * when no frames have been received from the remote peer.
     * <p>
     * This property is also used to configure a heartbeat mechanism, checking that the connection is still alive.
     * The corresponding heartbeat interval will be set to <em>idleTimeout/2</em> ms.
     * <p>
     * The default value of this property is {@link #DEFAULT_IDLE_TIMEOUT}.
     *
     * @return The idleTimeout in milliseconds.
     */
    public final int getIdleTimeout() {
        return idleTimeoutMillis;
    }

    /**
     * Sets the amount of time in milliseconds after which a connection will be closed
     * when no frames have been received from the remote peer.
     * <p>
     * This property is also used to configure a heartbeat mechanism, checking that the connection is still alive.
     * The corresponding heartbeat interval will be set to <em>idleTimeout/2</em> ms.
     * <p>
     * The default value of this property is {@link #DEFAULT_IDLE_TIMEOUT}.
     *
     * @param idleTimeoutMillis The idleTimeout in milliseconds.
     * @throws IllegalArgumentException if idleTimeout is negative.
     */
    public final void setIdleTimeout(final int idleTimeoutMillis) {
        if (idleTimeoutMillis < 0) {
            throw new IllegalArgumentException("idleTimeout must not be negative");
        } else {
            this.idleTimeoutMillis = idleTimeoutMillis;
        }
    }
}
