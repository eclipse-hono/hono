/**
 * Copyright (c) 2016, 2017 Bosch Software Innovations GmbH.
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

import java.util.Objects;

import org.eclipse.hono.util.Constants;

/**
 * Common configuration properties required for accessing an AMQP 1.0 container.
 */
public class ClientConfigProperties extends AbstractConfig {

    /**
     * The default amount of time to wait for credits after link creation.
     */
    public static final long DEFAULT_WAIT_MILLIS_FOR_CREDIT = 20L;
    /**
     * The default number of credits issued by the receiver side of a link.
     */
    public static final int  DEFAULT_INITIAL_CREDITS = 200;
    /**
     * The default amount of time to wait for a response before a request times out.
     */
    public static final long DEFAULT_REQUEST_TIMEOUT_MILLIS = 200L;

    private String name;
    private String host = "localhost";
    private int port = Constants.PORT_AMQPS;
    private String username;
    private char[] password;
    private String amqpHostname;
    private long waitMillisForCredits = DEFAULT_WAIT_MILLIS_FOR_CREDIT;
    private int initialCredits = DEFAULT_INITIAL_CREDITS;
    private long requestTimeoutMillis = DEFAULT_REQUEST_TIMEOUT_MILLIS;

    /**
     * Gets the name or literal IP address of the host that the client is configured to connect to.
     * <p>
     * The default value of this property is "localhost".
     *
     * @return The host name.
     */
    public final String getHost() {
        return host;
    }

    /**
     * Sets the name or literal IP address of the host that the client should connect to.
     * 
     * @param host The host name or IP address.
     * @throws NullPointerException if host is {@code null}.
     */
    public final void setHost(final String host) {
        this.host = Objects.requireNonNull(host);
    }

    /**
     * Gets the TCP port of the server that this client is configured to connect to.
     * <p>
     * The default value of this property is {@link Constants#PORT_AMQPS}.
     * 
     * @return The port number.
     */
    public final int getPort() {
        return port;
    }

    /**
     * Sets the TCP port of the server that this client should connect to.
     * <p>
     * The default value of this property is {@link Constants#PORT_AMQPS}.
     * 
     * @param port The port number.
     * @throws IllegalArgumentException if port &lt; 1000 or port &gt; 65535.
     */
    public final void setPort(final int port) {
        if (isValidPort(port)) {
            this.port = port;
        } else {
            throw new IllegalArgumentException("invalid port number");
        }
    }

    /**
     * Gets the user name that is used when authenticating to the Hono server.
     * 
     * @return The user name or {@code null} if not set.
     */
    public final String getUsername() {
        return username;
    }

    /**
     * Sets the user name to use when authenticating to the Hono server.
     * <p>
     * If not set then this client will not try to authenticate to the server.
     * 
     * @param username The user name.
     */
    public final void setUsername(final String username) {
        this.username = username;
    }

    /**
     * Gets the password that is used when authenticating to the Hono server.
     * 
     * @return The password or {@code null} if not set.
     */
    public final String getPassword() {
        if (password == null) {
            return null;
        } else {
            return String.valueOf(password);
        }
    }

    /**
     * Sets the password to use in conjunction with the user name when authenticating to the Hono server.
     * <p>
     * If not set then this client will not try to authenticate to the server.
     * 
     * @param password The password.
     */
    public final void setPassword(final String password) {
        if (password != null) {
            this.password = password.toCharArray();
        } else {
            this.password = null;
        }
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
    public final void setAmqpHostname(String amqpHostname) {
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
     * The default value of this property is {@link #DEFAULT_WAIT_MILLIS_FOR_CREDIT}.
     *
     * @return The number of milliseconds to wait.
     */
    public final long getWaitMillisForInitialCredits() {
        return waitMillisForCredits;
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
     * The default value of this property is {@link #DEFAULT_WAIT_MILLIS_FOR_CREDIT}.
     * 
     * @param waitMillisForCredits The number of milliseconds to wait.
     * @throws IllegalArgumentException if the number is negative.
     */
    public final void setWaitMillisForInitialCredits(final long waitMillisForCredits) {
        if (waitMillisForCredits < 0) {
            throw new IllegalArgumentException("time to wait must not be negative");
        } else {
            this.waitMillisForCredits = waitMillisForCredits;
        }
    }

    /**
     * Gets the number of initial credits, that will be given from a receiver to a sender at link creation.
     * <p>
     * The default value of this property is {@link #DEFAULT_INITIAL_CREDITS}.
     *
     * @return The number of inital credits.
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
     * Gets the maximum amount of time a client should wait for a response to a request before the request
     * is failed.
     * <p>
     * The default value of this property is {@link #DEFAULT_REQUEST_TIMEOUT_MILLIS}.
     *
     * @return The maximum number of milliseconds to wait.
     */
    public final long getRequestTimeoutMillis() {
        return requestTimeoutMillis;
    }

    /**
     * Sets the maximum amount of time a client should wait for a response to a request before the request
     * is failed.
     * <p>
     * The default value of this property is {@link #DEFAULT_REQUEST_TIMEOUT_MILLIS}.
     *
     * @param requestTimeoutMillis The maximum number of milliseconds to wait.
     * @throws IllegalArgumentException if the number is negative.
     */
    public final void setRequestTimeoutMillis(final long requestTimeoutMillis) {
        if (requestTimeoutMillis < 0) {
            throw new IllegalArgumentException("request timeout must not be negative");
        } else {
            this.requestTimeoutMillis = requestTimeoutMillis;
        }
    }
}
