/**
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.config;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

import org.eclipse.hono.util.Constants;

/**
 * Common configuration properties required for accessing and authenticating to a remote server.
 *
 */
public class AuthenticatingClientConfigProperties extends AbstractConfig {

    private String credentialsPath;
    private String host = "localhost";
    private boolean hostnameVerificationRequired = true;
    private char[] password;
    private int port;
    private String serverRole = "unknown";
    private boolean tlsEnabled = false;
    private String username;

    /**
     * Creates new properties with default values.
     */
    public AuthenticatingClientConfigProperties() {
        super();
    }

    /**
     * Creates properties based on other properties.
     * 
     * @param otherProperties The properties to copy.
     */
    public AuthenticatingClientConfigProperties(final AuthenticatingClientConfigProperties otherProperties) {
        super(otherProperties);
        this.credentialsPath = otherProperties.credentialsPath;
        this.host = otherProperties.host;
        this.hostnameVerificationRequired = otherProperties.hostnameVerificationRequired;
        this.password = otherProperties.password;
        this.port = otherProperties.port;
        this.serverRole = otherProperties.serverRole;
        this.tlsEnabled = otherProperties.tlsEnabled;
        this.username = otherProperties.username;
    }

    /**
     * Gets the name or literal IP address of the host that the client is configured to connect to.
     * <p>
     * The default value of this property is <em>localhost</em>.
     *
     * @return The host name.
     */
    public final String getHost() {
        return host;
    }

    /**
     * Sets the name or literal IP address of the host that the client should connect to.
     * <p>
     * The default value of this property is <em>localhost</em>.
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
                LOG.info("loading credentials for [{}:{}, role: {}] from [{}]",
                        host, port, serverRole, credentialsPath);
                final Properties props = new Properties();
                props.load(fis);
                this.username = props.getProperty("username");
                this.password = Optional.ofNullable(props.getProperty("password"))
                        .map(pwd -> pwd.toCharArray()).orElse(null);
            } catch (IOException e) {
                LOG.warn("could not load client credentials for [{}:{}, role: {}] from file [{}]",
                        host, port, serverRole, credentialsPath, e);
            }
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
     * @return {@code true} if TLS should be used.
     */
    public final boolean isTlsEnabled() {
        return tlsEnabled || getTrustOptions() != null;
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

    /**
     * Sets the name of the role that the server plays from the client's perspective.
     * <p>
     * The default value of this property is <em>unknown</em>.
     * 
     * @param roleName The name.
     * @throws NullPointerException if name is {@code null}.
     */
    public final void setServerRole(final String roleName) {
        this.serverRole = Objects.requireNonNull(roleName);
    }

    /**
     * Gets the name of the role that the server plays from the client's perspective.
     * <p>
     * The default value of this property is <em>unknown</em>.
     * 
     * @return The name.
     */
    public final String getServerRole() {
        return serverRole;
    }
}
