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

    private String name;
    private String host = "localhost";
    private int port = Constants.PORT_AMQPS;
    private String username;
    private char[] password;
    private String amqpHostname;
    private long waitMillisForCredits = 10L;
    private int initialCredits = 1000;
    private long requestTimeoutMillis = 200L;

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
     * Gets millis to wait for a receivers AMQP <em>flow</em> frame after sender link creation.
     * <p>
     * The default value of this property is 10.
     *
     * @return The millis to wait after sender link creation, if there are no credits directly
     */
    public final long getWaitMillisForInitialCredits() {
        return waitMillisForCredits;
    }

    /**
     * Sets millis to wait for a receivers AMQP <em>flow</em> frame after sender link creation.
     *
     * @param waitMillisForCredits The millis to set
     */
    public void setWaitMillisForInitialCredits(final long waitMillisForCredits) {
        this.waitMillisForCredits = waitMillisForCredits;
    }

    /**
     * Gets the number of initial credits, that will be given from a receiver to a sender at link creation.
     * <p>
     * The default value of this property is 1000.
     *
     * @return The number of inital credits.
     */
    public final int getInitialCredits() {
        return initialCredits;
    }

    /**
     * Sets the number of initial credits, that will be given from a receiver to a sender at link creation.
     *
     * @param initialCredits The initial credits to set.
     */
    public void setInitialCredits(final int initialCredits) {
        this.initialCredits = initialCredits;
    }

    /**
     * Gets the timeout in millis between a request and the awaited response in a request/response style communication
     * <p>
     * The default value of this property is 200.
     *
     * @return The timeout in millis
     */
    public final long getRequestTimeoutMillis() {
        return requestTimeoutMillis;
    }

    /**
     * Sets the timeout in millis between a request and the awaited response in a request/response style communication
     *
     * @param requestTimeoutMillis The timeout in millis to set.
     */
    public void setRequestTimeoutMillis(final long requestTimeoutMillis) {
        this.requestTimeoutMillis = requestTimeoutMillis;
    }
}
