/**
 * Copyright (c) 2016 Bosch Software Innovations GmbH.
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

/**
 * Common configuration properties required for accessing a Hono server.
 */
public final class HonoClientConfigProperties extends AbstractHonoConfig {

    private String name;
    private String host = "localhost";
    private int port = 5672;
    private String username;
    private char[] password;
    private String amqpHostname;

    /**
     * @return the host
     */
    public String getHost() {
        return host;
    }

    /**
     * @param host the host to set
     */
    public void setHost(final String host) {
        this.host = host;
    }

    /**
     * @return the port
     */
    public int getPort() {
        return port;
    }

    /**
     * @param port the port to set
     */
    public void setPort(final int port) {
        this.port = port;
    }

    /**
     * @return the username
     */
    public String getUsername() {
        return username;
    }

    /**
     * @param username the username to set
     */
    public void setUsername(final String username) {
        this.username = username;
    }

    /**
     * @return the password
     */
    public String getPassword() {
        if (password == null) {
            return null;
        } else {
            return String.valueOf(password);
        }
    }

    /**
     * @param password the password to set
     */
    public void setPassword(final String password) {
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
    public String getName() {
        return name;
    }

    /**
     * Sets the name to indicate as the <em>container-id</em> in the client's AMQP <em>Open</em> frame.
     * 
     * @param name The name to set.
     */
    public void setName(final String name) {
        this.name = name;
    }

    /**
     * Gets the name being indicated as the <em>hostname</em> in the client's AMQP <em>Open</em> frame.
     * 
     * @return The host name or {@code null} if no host name has been set.
     */
    public String getAmqpHostname() {
        return amqpHostname;
    }

    /**
     * Sets the name to indicate as the <em>hostname</em> in the client's AMQP <em>Open</em> frame.
     * 
     * @param amqpHostname The host name to set.
     */
    public void setAmqpHostname(String amqpHostname) {
        this.amqpHostname = amqpHostname;
    }
}
