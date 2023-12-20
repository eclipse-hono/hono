/**
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client.amqp.config;

import java.util.Optional;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithParentName;

/**
 * Common options for configuring a client for accessing an AMQP 1.0 container.
 *
 */
@ConfigMapping(prefix = "hono.client", namingStrategy = ConfigMapping.NamingStrategy.VERBATIM)
public interface ClientOptions {

    /**
     * Gets the authenticating client options.
     *
     * @return The options.
     */
    @WithParentName
    AuthenticatingClientOptions authenticatingClientOptions();

    /**
     * Gets the name being indicated as part of the <em>container-id</em> in the client's AMQP <em>Open</em> frame.
     *
     * @return The name.
     */
    Optional<String> name();

    /**
     * Gets the name being indicated as the <em>hostname</em> in the client's AMQP <em>Open</em> frame.
     *
     * @return The host name.
     */
    Optional<String> amqpHostname();

    /**
     * Gets the maximum amount of time that a client should wait for credits after <em>sender link</em>
     * creation.
     * <p>
     * The AMQP 1.0 protocol requires the receiver side of a <em>link</em> to explicitly send a <em>flow</em>
     * frame containing credits granted to the sender after the link has been established.
     * <p>
     * This property can be used to <em>tune</em> the time period to wait according to the network
     * latency involved with the communication link between the client and the service.
     *
     * @return The number of milliseconds to wait.
     */
    @WithDefault("20")
    long flowLatency();

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
     *
     * @return The number of milliseconds to wait.
     */
    @WithDefault("1000")
    long linkEstablishmentTimeout();

    /**
     * Gets the number of initial credits, that will be given from a receiver to a sender at link creation.
     * <p>
     * Note that having the credits set to {@code 0} will require the receiver to manually flow credit to
     * the sender after receiving messages.
     *
     * @return The number of initial credits.
     */
    @WithDefault("200")
    int initialCredits();

    /**
     * Gets the maximum amount of time a client should wait for a delivery update after sending an event or command message.
     * If no delivery update is received in that time, the future with the outcome of the send operation will be failed.
     *
     * @return The maximum number of milliseconds to wait.
     */
    @WithDefault("1000")
    long sendMessageTimeout();

    /**
     * Gets the maximum amount of time a client should wait for a response to a request before the request
     * is failed.
     *
     * @return The maximum number of milliseconds to wait.
     */
    @WithDefault("200")
    long requestTimeout();

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
    @WithDefault("-1")
    int reconnectAttempts();

    /**
     * Gets the minimum amount of time to wait before trying to re-establish an
     * AMQP connection with the peer.
     *
     * @return The minimum delay in milliseconds.
     */
    @WithDefault("0")
    long reconnectMinDelay();

    /**
     * Gets the maximum amount of time to wait before trying to re-establish an
     * AMQP connection with the peer.
     *
     * @return The maximum delay in milliseconds.
     */
    @WithDefault("7000")
    long reconnectMaxDelay();

    /**
     * Gets the factor used in the exponential backoff algorithm for determining the delay
     * before trying to re-establish an AMQP connection with the peer.
     *
     * @return The value to exponentially increase the delay by in milliseconds.
     */
    @WithDefault("100")
    long reconnectDelayIncrement();

    /**
     * Gets the maximum amount of time a client should wait for an AMQP connection
     * with a peer to be opened.
     * <p>
     * This includes the time for TCP/TLS connection establishment, SASL handshake
     * and exchange of the AMQP <em>open</em> frame.
     *
     * @return The maximum number of milliseconds to wait.
     */
    @WithDefault("5000")
    int connectTimeout();

    /**
     * Gets the amount of time in milliseconds after which a connection will be closed
     * when no frames have been received from the remote peer.
     * <p>
     * This property is also used to configure a heartbeat mechanism, checking that the connection is still alive.
     * The corresponding heartbeat interval will be set to <em>idleTimeout/2</em> ms.
     *
     * @return The idleTimeout in milliseconds.
     */
    @WithDefault("16000")
    int idleTimeout();

    /**
     * Gets the rewrite rule for downstream addresses.
     *
     * @return The rewrite rule to be applied to the address.
     * @see org.eclipse.hono.client.amqp.config.AddressHelper#rewrite(String, ClientConfigProperties)
     */
    Optional<String> addressRewriteRule();

    /**
     * Gets the minimum size of AMQP 1.0 messages that this client requires a peer to accept.
     * <p>
     * The value of this property will be used by the client during sender link establishment.
     * Link establishment will fail, if the peer indicates a max-message-size in its <em>attach</em>
     * frame that is smaller than the configured minimum message size.
     *
     * @return The message size in bytes or 0, indicating no minimum size at all.
     */
    @WithDefault("0")
    long minMaxMessageSize();

    /**
     * Gets the maximum size of an AMQP 1.0 message that this client accepts from a peer.
     *
     * @return The message size in bytes or -1, indicating messages of any size.
     */
    @WithDefault("-1")
    long maxMessageSize();

    /**
     * Gets the maximum number of bytes that can be contained in a single AMQP <em>transfer</em> frame.
     *
     * @return The frame size in bytes or -1, indicating that the frame size is not limited.
     */
    @WithDefault("-1")
    int maxFrameSize();

    /**
     * Gets the maximum number of AMQP transfer frames for sessions created on this connection.
     * This is the number of transfer frames that may simultaneously be in flight for all links
     * in the session.
     *
     * @return The number of frames or -1, indicating that the number of frames is unlimited.
     */
    @WithDefault("-1")
    int maxSessionFrames();

    /**
     * Checks whether the legacy trace context format shall be used, writing to the message annotations instead of the
     * application properties.
     *
     * @return {@code true} if the legacy format shall be used.
     */
    @WithDefault("true")
    boolean useLegacyTraceContextFormat();
}
