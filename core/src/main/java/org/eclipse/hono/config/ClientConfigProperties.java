/**
 * Copyright (c) 2016, 2018 Bosch Software Innovations GmbH.
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

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

import org.eclipse.hono.util.Constants;

/**
 * Common configuration properties required for accessing an AMQP 1.0 container.
 */
public class ClientConfigProperties extends AbstractConfig {

    /**
     * The default amount of time to wait for credits after link creation.
     */
    public static final long DEFAULT_FLOW_LATENCY = 20L; //ms
    /**
     * The default number of credits issued by the receiver side of a link.
     */
    public static final int  DEFAULT_INITIAL_CREDITS = 200;
    /**
     * The default amount of time to wait for a response before a request times out.
     */
    public static final long DEFAULT_REQUEST_TIMEOUT = 200L; // ms

    private String name;
    private String host = "localhost";
    private int port = Constants.PORT_AMQPS;
    private String username;
    private char[] password;
    private String credentialsPath;
    private String amqpHostname;
    private long flowLatency = DEFAULT_FLOW_LATENCY;
    private int initialCredits = DEFAULT_INITIAL_CREDITS;
    private long requestTimeoutMillis = DEFAULT_REQUEST_TIMEOUT;
    private boolean hostnameVerificationRequired = true;
    private boolean tlsEnabled = false;

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
     * <p>
     * This method returns the value set using the <em>setUsername</em> method
     * if the value is not {@code null}. Otherwise, the user name is read from the
     * properties file indicated by the <em>credentialsPath</em> property (if not
     * {@code null}).
     * 
     * @return The user name or {@code null} if not set.
     */
    public final String getUsername() {
        if (username == null) {
            loadCredentials();
        }
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
     * <p>
     * This method returns the value set using the <em>setPassword</em> method
     * if the value is not {@code null}. Otherwise, the password is read from the
     * properties file indicated by the <em>credentialsPath</em> property (if not
     * {@code null}).
     * 
     * @return The password or {@code null} if not set.
     */
    public final String getPassword() {
        if (password == null) {
            loadCredentials();
        }
        return password == null ? null : String.valueOf(password);
    }

    /**
     * Sets the password to use in conjunction with the user name when authenticating
     * to the Hono server.
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
     * Gets the file system path to a properties file containing the
     * credentials for authenticating to the server.
     * <p>
     * The file is expected to contain a <em>username</em> and a
     * <em>password</em> property, e.g.
     * <pre>
     * username=foo
     * password=bar
     * </pre>
     * 
     * @return The path or {@code null} if not set.
     */
    public final String getCredentialsPath() {
        return credentialsPath;
    }

    /**
     * Sets the file system path to a properties file containing the
     * credentials for authenticating to the server.
     * <p>
     * The file is expected to contain a <em>username</em> and a
     * <em>password</em> property, e.g.
     * <pre>
     * username=foo
     * password=bar
     * </pre>
     * 
     * @param path The path to the properties file.
     */
    public final void setCredentialsPath(final String path) {
        this.credentialsPath = path;
    }

    private void loadCredentials() {

        if (username == null && password == null && credentialsPath != null) {
            try (FileInputStream fis = new FileInputStream(credentialsPath)) {
                LOG.info("loading credentials for [{}] from [{}]", host, credentialsPath);
                final Properties props = new Properties();
                props.load(fis);
                this.username = props.getProperty("username");
                this.password = Optional.ofNullable(props.getProperty("password"))
                        .map(pwd -> pwd.toCharArray()).orElse(null);
            } catch (IOException e) {
                LOG.warn("could not load client credentials for [{}] from file [{}]",
                        host, credentialsPath, e);
            }
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
     * Checks if the <em>host</em> property must match the distinguished or
     * any of the alternative names asserted by the server's certificate when
     * connecting using TLS.
     * 
     * @return {@code true} if the host name will be matched against the
     *         asserted names.
     */
    public final boolean isHostnameVerificationRequired() {
        return hostnameVerificationRequired;
    }

    /**
     * Sets whether the <em>host</em> property must match the distinguished or
     * any of the alternative names asserted by the server's certificate when
     * connecting using TLS.
     * <p>
     * Verification is enabled by default, i.e. the connection will be established
     * only if the server presents a certificate that has been signed by one of the
     * client's trusted CAs and one of the asserted names matches the host name that
     * the client used to connect to the server.
     * 
     * @param hostnameVerificationRequired {@code true} if the host name should be matched.
     */
    public final void setHostnameVerificationRequired(final boolean hostnameVerificationRequired) {
        this.hostnameVerificationRequired = hostnameVerificationRequired;
    }

    /**
     * Checks if the client should use TLS to verify the server's identity
     * and encrypt the connection.
     * <p>
     * Verification is disabled by default. Setting the <em>trustStorePath</em>
     * property enables verification of the server identity implicitly and the
     * value of this property is ignored.
     * 
     * @return {@code true} if the server identity should be verified.
     */
    public final boolean isTlsEnabled() {
        return tlsEnabled;
    }

    /**
     * Sets whether the client should use TLS to verify the server's identity
     * and encrypt the connection.
     * <p>
     * Verification is disabled by default. Setting the <em>trustStorePath</em>
     * property enables verification of the server identity implicitly and the
     * value of this property is ignored.
     * <p>
     * This property should be set to {@code true} if the server uses a certificate
     * that has been signed by a CA that is contained in the standard trust store
     * that the JVM is configured to use. In this case the <em>trustStorePath</em>
     * does not need to be set.
     * 
     * @param enabled {@code true} if the server identity should be verified.
     */
    public final void setTlsEnabled(final boolean enabled) {
        this.tlsEnabled = enabled;
    }
}
