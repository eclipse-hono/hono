/*******************************************************************************
 * Copyright (c) 2016 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client.amqp.config;

import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.Strings;

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
    public static final int DEFAULT_INITIAL_CREDITS = 200;
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

    /**
     * The value indicating an unlimited AMQP frame size.
     */
    public static final int MAX_FRAME_SIZE_UNLIMITED = -1;
    /**
     * The value indicating that this client accepts messages of any size from a peer.
     */
    public static final long MAX_MESSAGE_SIZE_UNLIMITED = -1;
    /**
     * The value indicating an unlimited number of frames that can be in flight for an AMQP session.
     */
    public static final int MAX_SESSION_FRAMES_UNLIMITED = -1;
    /**
     * The value indicating that this client does not require a peer to accept messages of a particular
     * size.
     */
    public static final long MIN_MAX_MESSAGE_SIZE_NONE = 0;
    /**
     * The default value for deciding whether to use the legacy trace context format.
     */
    public static final boolean DEFAULT_USE_LEGACY_TRACE_CONTEXT_FORMAT = true;

    private Pattern addressRewritePattern;
    private String addressRewriteReplacement;
    private String addressRewriteRule = null;
    private String amqpHostname = null;
    private int connectTimeout = DEFAULT_CONNECT_TIMEOUT;
    private long flowLatency = DEFAULT_FLOW_LATENCY;
    private int idleTimeout = DEFAULT_IDLE_TIMEOUT;
    private int initialCredits = DEFAULT_INITIAL_CREDITS;
    private long linkEstablishmentTimeout = DEFAULT_LINK_ESTABLISHMENT_TIMEOUT;
    private int maxFrameSize = MAX_FRAME_SIZE_UNLIMITED;
    private long maxMessageSize = MAX_MESSAGE_SIZE_UNLIMITED;
    private int maxSessionFrames = MAX_SESSION_FRAMES_UNLIMITED;
    private long minMaxMessageSize = MIN_MAX_MESSAGE_SIZE_NONE;
    private String name = null;
    private int reconnectAttempts = -1;
    private long reconnectMinDelay = DEFAULT_RECONNECT_MIN_DELAY;
    private long reconnectMaxDelay = DEFAULT_RECONNECT_MAX_DELAY;
    private long reconnectDelayIncrement = DEFAULT_RECONNECT_DELAY_INCREMENT;
    private long requestTimeout = DEFAULT_REQUEST_TIMEOUT;
    private long sendMessageTimeout = DEFAULT_SEND_MESSAGE_TIMEOUT;
    private boolean useLegacyTraceContextFormat = DEFAULT_USE_LEGACY_TRACE_CONTEXT_FORMAT;

    /**
     * Creates new properties using default values.
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
        this.addressRewritePattern = otherProperties.addressRewritePattern;
        this.addressRewriteReplacement = otherProperties.addressRewriteReplacement;
        this.addressRewriteRule = otherProperties.addressRewriteRule;
        this.amqpHostname = otherProperties.amqpHostname;
        this.connectTimeout = otherProperties.connectTimeout;
        this.flowLatency = otherProperties.flowLatency;
        this.idleTimeout = otherProperties.idleTimeout;
        this.initialCredits = otherProperties.initialCredits;
        this.linkEstablishmentTimeout = otherProperties.linkEstablishmentTimeout;
        this.maxFrameSize = otherProperties.maxFrameSize;
        this.maxMessageSize = otherProperties.maxMessageSize;
        this.maxSessionFrames = otherProperties.maxSessionFrames;
        this.minMaxMessageSize = otherProperties.minMaxMessageSize;
        this.name = otherProperties.name;
        this.reconnectAttempts = otherProperties.reconnectAttempts;
        this.reconnectMinDelay = otherProperties.reconnectMinDelay;
        this.reconnectMaxDelay = otherProperties.reconnectMaxDelay;
        this.reconnectDelayIncrement = otherProperties.reconnectDelayIncrement;
        this.requestTimeout = otherProperties.requestTimeout;
        this.sendMessageTimeout = otherProperties.sendMessageTimeout;
        this.useLegacyTraceContextFormat = otherProperties.useLegacyTraceContextFormat;
    }

    /**
     * Creates properties based on existing options.
     *
     * @param options The options to copy.
     */
    public ClientConfigProperties(final ClientOptions options) {
        super(options.authenticatingClientOptions());
        setAddressRewriteRule(options.addressRewriteRule().orElse(null));
        setAmqpHostname(options.amqpHostname().orElse(null));
        setConnectTimeout(options.connectTimeout());
        setFlowLatency(options.flowLatency());
        setIdleTimeout(options.idleTimeout());
        setInitialCredits(options.initialCredits());
        setLinkEstablishmentTimeout(options.linkEstablishmentTimeout());
        setMaxFrameSize(options.maxFrameSize());
        setMaxMessageSize(options.maxMessageSize());
        setMaxSessionFrames(options.maxSessionFrames());
        setMinMaxMessageSize(options.minMaxMessageSize());
        setName(options.name().orElse(null));
        setReconnectAttempts(options.reconnectAttempts());
        setReconnectMinDelay(options.reconnectMinDelay());
        setReconnectMaxDelay(options.reconnectMaxDelay());
        setReconnectDelayIncrement(options.reconnectDelayIncrement());
        setRequestTimeout(options.requestTimeout());
        setSendMessageTimeout(options.sendMessageTimeout());
        setUseLegacyTraceContextFormat(options.useLegacyTraceContextFormat());
    }

    /**
     * Gets the name being indicated as part of the <em>container-id</em> in the client's AMQP <em>Open</em> frame.
     *
     * @return The name or {@code null} if no name has been set.
     */
    public final String getName() {
        return name;
    }

    /**
     * Sets the default name to use if the <em>name</em> property has not been set explicitly.
     *
     * @param defaultName The name to set.
     * @throws NullPointerException if default name is {@code null}.
     */
    public final void setNameIfNotSet(final String defaultName) {
        Objects.requireNonNull(defaultName);
        if (Strings.isNullOrEmpty(name)) {
            setName(defaultName);
        }
    }

    /**
     * Sets the name to indicate as part of the <em>container-id</em> in the client's AMQP <em>Open</em> frame.
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
     * <p>
     * Note that having the credits set to {@code 0} will require the receiver to manually flow credit to
     * the sender after receiving messages.
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
     * <p>
     * Note that having the credits set to {@code 0} will require the receiver to manually flow credit to
     * the sender after receiving messages.
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
        return sendMessageTimeout;
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
            this.sendMessageTimeout = sendMessageTimeoutMillis;
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
        return requestTimeout;
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
            this.requestTimeout = requestTimeoutMillis;
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
    public final int getReconnectAttempts() {
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
    public final void setReconnectAttempts(final int attempts) {
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
    public final long getReconnectMinDelay() {
        return reconnectMinDelay;
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
    public final void setReconnectMinDelay(final long reconnectMinDelay) {
        if (reconnectMinDelay < 0) {
            throw new IllegalArgumentException("minimum delay must be >= 0");
        } else {
            this.reconnectMinDelay = reconnectMinDelay;
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
    public final long getReconnectMaxDelay() {
        return reconnectMaxDelay;
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
    public final void setReconnectMaxDelay(final long reconnectMaxDelay) {
        if (reconnectMaxDelay < 0) {
            throw new IllegalArgumentException("maximum delay must be >= 0");
        } else {
            this.reconnectMaxDelay = reconnectMaxDelay;
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
    public final long getReconnectDelayIncrement() {
        return reconnectDelayIncrement;
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
    public final void setReconnectDelayIncrement(final long reconnectDelayIncrement) {
        if (reconnectDelayIncrement < 0) {
            throw new IllegalArgumentException("value must be >= 0");
        } else {
            this.reconnectDelayIncrement = reconnectDelayIncrement;
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
        return connectTimeout;
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
            this.connectTimeout = connectTimeoutMillis;
        }
    }

    /**
     * Gets the maximum amount of time a client should wait for a peer's AMQP 1.0 <em>close</em> performative
     * when closing a connection.
     *
     * @return The maximum number of milliseconds to wait.
     */
    public final int getCloseConnectionTimeout() {
        final int connectTimeoutToUse = connectTimeout > 0 ? connectTimeout : DEFAULT_CONNECT_TIMEOUT;
        return connectTimeoutToUse / 2;
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
        return idleTimeout / 2;
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
        return idleTimeout;
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
            this.idleTimeout = idleTimeoutMillis;
        }
    }

    /**
     * Gets the rewrite rule for downstream addresses.
     * <p>
     * See {@link org.eclipse.hono.client.amqp.config.AddressHelper#rewrite(String, ClientConfigProperties)} for more
     * information about syntax and behavior of this property.
     *
     * @return The rewrite rule to be applied to the address.
     */
    public final String getAddressRewriteRule() {
        return addressRewriteRule;
    }

    /**
     * Sets the rewrite rule for downstream addresses.
     * <p>
     * This method parses the rule and tries to precompile the pattern to be used.
     * The pattern and replacement can be obtained by {@link #getAddressRewritePattern()} and
     * {@link #getAddressRewriteReplacement()} methods.
     * <p>
     * For more information about syntax and behavior of this property see
     * {@link org.eclipse.hono.client.amqp.config.AddressHelper#rewrite(String, ClientConfigProperties)} method.
     *
     * @param addressRewriteRule The rewrite rule to be applied to the address.
     * @throws PatternSyntaxException if the rewrite rule contains an invalid pattern definition.
     */
    public final void setAddressRewriteRule(final String addressRewriteRule) {
        this.addressRewriteRule = addressRewriteRule;
        if (!Strings.isNullOrEmpty(addressRewriteRule)) {
            final String[] elements = addressRewriteRule.split(" ", 2);
            if (elements.length == 2) {
                addressRewritePattern = Pattern.compile(elements[0]);
                addressRewriteReplacement = elements[1];
            }
        }
    }

    /**
     * Gets precompiled address rewrite pattern.
     *
     * @return The precompiled address rewrite pattern.
     */
    public final Pattern getAddressRewritePattern() {
        return addressRewritePattern;
    }

    /**
     * Gets address rewrite replacement.
     *
     * @return The address rewrite replacement.
     */
    public final String getAddressRewriteReplacement() {
        return addressRewriteReplacement;
    }

    /**
     * Gets the minimum size of AMQP 1.0 messages that this client requires a peer to accept.
     * <p>
     * The default value of this property is {@value #MIN_MAX_MESSAGE_SIZE_NONE}.
     * <p>
     * The value of this property will be used by the client during sender link establishment.
     * Link establishment will fail, if the peer indicates a max-message-size in its <em>attach</em>
     * frame that is smaller than the configured minimum message size.
     *
     * @return The message size in bytes or {@value #MIN_MAX_MESSAGE_SIZE_NONE}, indicating no minimum size at all.
     */
    public final long getMinMaxMessageSize() {
        return minMaxMessageSize;
    }

    /**
     * Sets the minimum size of AMQP 1.0 messages that this client requires a peer to accept.
     * <p>
     * The default value of this property is {@value #MIN_MAX_MESSAGE_SIZE_NONE}.
     * <p>
     * The value of this property will be used by the client during sender link establishment.
     * Link establishment will fail, if the peer indicates a max-message-size in its <em>attach</em>
     * frame that is smaller than the configured minimum message size.
     *
     * @param size The message size in bytes or {@value #MIN_MAX_MESSAGE_SIZE_NONE}, indicating no minimum size at all.
     * @throws IllegalArgumentException if size is &lt; 0.
     */
    public final void setMinMaxMessageSize(final long size) {
        if (size < MIN_MAX_MESSAGE_SIZE_NONE) {
            throw new IllegalArgumentException("min message size must be >= 0");
        }
        this.minMaxMessageSize = size;
    }


    /**
     * Gets the maximum size of an AMQP 1.0 message that this client accepts from a peer.
     * <p>
     * The default value of this property is {@value #MAX_MESSAGE_SIZE_UNLIMITED}.
     *
     * @return The message size in bytes or {@value #MAX_MESSAGE_SIZE_UNLIMITED}, indicating messages of any size.
     */
    public final long getMaxMessageSize() {
        return maxMessageSize;
    }

    /**
     * Sets the maximum size of an AMQP 1.0 message that this client should accept from a peer.
     * <p>
     * The default value of this property is {@value #MAX_MESSAGE_SIZE_UNLIMITED}.
     *
     * @param size The message size in bytes or {@value #MAX_MESSAGE_SIZE_UNLIMITED}, indicating messages of any size.
     * @throws IllegalArgumentException if size is &lt; {@value #MAX_MESSAGE_SIZE_UNLIMITED}.
     */
    public final void setMaxMessageSize(final long size) {
        if (size < MAX_MESSAGE_SIZE_UNLIMITED) {
            throw new IllegalArgumentException("max-message-size must be >= -1");
        }
        this.maxMessageSize = size;
    }

    /**
     * Gets the maximum number of bytes that can be contained in a single AMQP <em>transfer</em> frame.
     * <p>
     * The default value of this property is {@value #MAX_FRAME_SIZE_UNLIMITED}.
     *
     * @return The frame size in bytes or {@value #MAX_FRAME_SIZE_UNLIMITED}, indicating that the frame size is not limited.
     */
    public final int getMaxFrameSize() {
        return maxFrameSize;
    }

    /**
     * Sets the maximum number of bytes that can be contained in a single AMQP <em>transfer</em> frame.
     * <p>
     * The default value of this property is {@value #MAX_FRAME_SIZE_UNLIMITED}.
     *
     * @param maxFrameSize The frame size in bytes or {@value #MAX_FRAME_SIZE_UNLIMITED}, indicating that the
     *        frame size is not limited.
     * @throws IllegalArgumentException if frame size &lt; 512 (minimum value
     *           defined by AMQP 1.0 spec) and not {@value #MAX_FRAME_SIZE_UNLIMITED}.
     */
    public final void setMaxFrameSize(final int maxFrameSize) {
        if (maxFrameSize != -1 && maxFrameSize < 512) {
            throw new IllegalArgumentException("frame size must be at least 512 bytes");
        }
        this.maxFrameSize = maxFrameSize;
    }

    /**
     * Gets the maximum number of AMQP transfer frames for sessions created on this connection.
     * This is the number of transfer frames that may simultaneously be in flight for all links
     * in the session.
     * <p>
     * The default value of this property is {@value #MAX_SESSION_FRAMES_UNLIMITED}.
     *
     * @return The number of frames or {@value #MAX_SESSION_FRAMES_UNLIMITED}, indicating that the number
     *         of frames is unlimited.
     */
    public final int getMaxSessionFrames() {
        return maxSessionFrames;
    }

    /**
     * Sets the maximum number of AMQP transfer frames for sessions created on this connection.
     * This is the number of transfer frames that may simultaneously be in flight for all links
     * in the session.
     * <p>
     * The default value of this property is {@value #MAX_SESSION_FRAMES_UNLIMITED}.
     *
     * @param maxSessionFrames The number of frames.
     * @throws IllegalArgumentException if the number is less than 1 and not {@value #MAX_SESSION_FRAMES_UNLIMITED}.
     */
    public final void setMaxSessionFrames(final int maxSessionFrames) {
        if (maxSessionFrames != MAX_SESSION_FRAMES_UNLIMITED && maxSessionFrames < 1) {
            throw new IllegalArgumentException("must support at least one frame being in flight");
        }
        this.maxSessionFrames = maxSessionFrames;
    }

    /**
     * Gets the maximum AMQP session window size.
     *
     * @return The session window size in bytes or 0, indicating that the window size is unlimited.
     *         The value returned is computed as the product of maxSessionFrames and maxFrameSize if they
     *         are different from {@link #MAX_SESSION_FRAMES_UNLIMITED} and {@link #MAX_FRAME_SIZE_UNLIMITED}
     *         respectively.
     */
    public final int getMaxSessionWindowSize() {
        if (maxSessionFrames != MAX_SESSION_FRAMES_UNLIMITED && maxFrameSize != MAX_FRAME_SIZE_UNLIMITED) {
            return maxSessionFrames * maxFrameSize;
        } else {
            return 0;
        }
    }

    /**
     * Checks whether the legacy trace context format shall be used, writing to the message annotations instead of the
     * application properties.
     *
     * @return {@code true} if the legacy format shall be used.
     */
    public final boolean isUseLegacyTraceContextFormat() {
        return useLegacyTraceContextFormat;
    }

    /**
     * Sets whether the legacy trace context format shall be used, writing to the message annotations instead of the
     * application properties.
     *
     * @param useLegacyTraceContextFormat {@code true} if the legacy format shall be used.
     */
    public final void setUseLegacyTraceContextFormat(final boolean useLegacyTraceContextFormat) {
        this.useLegacyTraceContextFormat = useLegacyTraceContextFormat;
    }
}
