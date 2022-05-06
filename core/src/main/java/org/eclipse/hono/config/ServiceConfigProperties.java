/*******************************************************************************
 * Copyright (c) 2016, 2022 Contributors to the Eclipse Foundation
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

import java.util.Objects;
import java.util.regex.Pattern;

import org.eclipse.hono.util.RegistryManagementConstants;

/**
 * A POJO for configuring common properties of server components.
 *
 */
public class ServiceConfigProperties extends ServerConfig {

    /**
     * The default maximum payload size for messages uploaded by devices.
     */
    public static final int DEFAULT_MAX_PAYLOAD_SIZE = 2048;

    /**
     * The default number of credits to flow to a client.
     */
    public static final int DEFAULT_RECEIVER_LINK_CREDITS = 100;

    /**
     * The default send timeout value in milliseconds, which is 
     * to be used when sending a message on the vert.x event bus.
     */
    public  static final long DEFAULT_SEND_TIMEOUT_IN_MS = 3000;

    /**
     * The minimum send timeout value in milliseconds is 500 ms
     * and any value less than this minimum value is not accepted.
     */
    public static final long MIN_SEND_TIMEOUT_IN_MS = 500;

    private static final int MIN_PAYLOAD_SIZE  = 128; // bytes

    private long eventLoopBlockedCheckTimeout = 5000L;
    private boolean networkDebugLoggingEnabled = false;
    private boolean waitForDownstreamConnectionEnabled = false;
    private int maxPayloadSize = DEFAULT_MAX_PAYLOAD_SIZE;
    private int receiverLinkCredit = DEFAULT_RECEIVER_LINK_CREDITS;
    private String corsAllowedOrigin = "*";
    private long sendTimeOut = DEFAULT_SEND_TIMEOUT_IN_MS;
    private Pattern tenantIdPattern = Pattern.compile(RegistryManagementConstants.DEFAULT_REGEX_TENANT_ID);
    private Pattern deviceIdPattern = Pattern.compile(RegistryManagementConstants.DEFAULT_REGEX_DEVICE_ID);

    /**
     * Creates new properties using default values.
     */
    public ServiceConfigProperties() {
        super();
    }

    /**
     * Creates a new instance from existing options.
     *
     * @param options The options to copy.
     * @throws NullPointerException if options are {@code null}.
     */
    public ServiceConfigProperties(final ServiceOptions options) {
        super(options.serverOptions());
        setCorsAllowedOrigin(options.corsAllowedOrigin());
        setDeviceIdPattern(options.deviceIdPattern());
        setEventLoopBlockedCheckTimeout(options.eventLoopBlockedCheckTimeout());
        setMaxPayloadSize(options.maxPayloadSize());
        setNetworkDebugLoggingEnabled(options.networkDebugLoggingEnabled());
        setReceiverLinkCredit(options.receiverLinkCredit());
        setSendTimeOut(options.sendTimeOut());
        setTenantIdPattern(options.tenantIdPattern());
        setWaitForDownstreamConnectionEnabled(options.waitForDownstreamConnectionEnabled());
    }

    /**
     * Sets the maximum size of a message payload this server accepts from clients.
     * <p>
     * The default value of this property is {@value #DEFAULT_MAX_PAYLOAD_SIZE} bytes.
     *
     * @param bytes The maximum number of bytes.
     * @throws IllegalArgumentException if bytes is &lt; 128.
     */
    public final void setMaxPayloadSize(final int bytes) {
        if (bytes <= MIN_PAYLOAD_SIZE) {
            throw new IllegalArgumentException("minimum message payload size is 128 bytes");
        }
        this.maxPayloadSize = bytes;
    }

    /**
     * Gets the maximum size of a message payload this server accepts from clients.
     * <p>
     * The default value of this property is {@value #DEFAULT_MAX_PAYLOAD_SIZE} bytes.
     *
     * @return The maximum number of bytes.
     */
    public final int getMaxPayloadSize() {
        return maxPayloadSize;
    }

    /**
     * Checks whether the server is configured to log TCP traffic.
     *
     * @return {@code true} if TCP traffic gets logged.
     */
    public final boolean isNetworkDebugLoggingEnabled() {
        return networkDebugLoggingEnabled;
    }

    /**
     * Sets whether the server should log TCP traffic.
     * <p>
     * The default value of this property is {@code false}.
     *
     * @param networkDebugLoggingEnabled {@code true} if TCP traffic should be logged.
     * @return This instance for setter chaining.
     */
    public final ServiceConfigProperties setNetworkDebugLoggingEnabled(final boolean networkDebugLoggingEnabled) {
        this.networkDebugLoggingEnabled = networkDebugLoggingEnabled;
        return this;
    }

    /**
     * Checks whether the server waits for downstream connections to be established
     * during startup.
     * <p>
     * If this property is set to {@code true} then startup may take some time or even
     * time out if the downstream container to connect to is not (yet) available.
     * <p>
     * The default value of this property is {@code false}.
     *
     * @return {@code true} if the server will wait for downstream connections to be established during startup.
     */
    public final boolean isWaitForDownstreamConnectionEnabled() {
        return waitForDownstreamConnectionEnabled;
    }

    /**
     * Sets whether the server should wait for downstream connections to be established
     * during startup.
     * <p>
     * If this property is set to {@code true} then startup may take some time or even
     * time out if the downstream container to connect to is not (yet) available.
     * <p>
     * The default value of this property is {@code false}.
     *
     * @param waitForConnection {@code true} if the server should wait for downstream connections to be established during startup.
     * @return This instance for setter chaining.
     */
    public final ServiceConfigProperties setWaitForDownstreamConnectionEnabled(final boolean waitForConnection) {
        this.waitForDownstreamConnectionEnabled = waitForConnection;
        return this;
    }

    /**
     * Gets the number of AMQP message credits this service flows to a client
     * when the client opens a sender link to this service.
     * <p>
     * The default value of this property is {@link #DEFAULT_RECEIVER_LINK_CREDITS}.
     *
     * @return The number of credits.
     */
    public final int getReceiverLinkCredit() {
        return receiverLinkCredit;
    }

    /**
     * Sets the number of AMQP message credits this service flows to a client
     * when the client opens a sender link to this service.
     * <p>
     * The credits are replenished automatically with each message being processed
     * successfully by this service.
     * <p>
     * The default value of this property is {@link #DEFAULT_RECEIVER_LINK_CREDITS}.

     * @param receiverLinkCredit The number of credits.
     * @throws IllegalArgumentException if the credit is &lt;= 0.
     */
    public final void setReceiverLinkCredit(final int receiverLinkCredit) {
        if (receiverLinkCredit <= 0) {
            throw new IllegalArgumentException("receiver link credit must be at least 1");
        }
        this.receiverLinkCredit = receiverLinkCredit;
    }

    /**
     * Gets the allowed origin pattern for CORS handler.
     * <p>
     * The allowed origin pattern for CORS is returned to clients via the <em>Access-Control-Allow-Origin</em> header.
     * It can be used by Web Applications to make sure that requests go only to trusted backend entities.
     * <p>
     * The default value is '*'.
     *
     * @return The allowed origin pattern for CORS handler.
     */
    public final String getCorsAllowedOrigin() {
        return corsAllowedOrigin;
    }

    /**
     * Sets the allowed origin pattern for CORS handler.
     * <p>
     * The allowed origin pattern for CORS is returned to clients via the <em>Access-Control-Allow-Origin</em> header.
     * It can be used by Web Applications to make sure that requests go only to trusted backend entities.
     * <p>
     * The default value is '*'.
     *
     * @param corsAllowedOrigin The allowed origin pattern for CORS handler.
     * @throws NullPointerException if the allowed origin pattern is {@code null}.
     */
    public final void setCorsAllowedOrigin(final String corsAllowedOrigin) {
        this.corsAllowedOrigin = Objects.requireNonNull(corsAllowedOrigin);
    }

    /**
     * Gets the send timeout value in milliseconds, which is to be used when sending a 
     * message on the vert.x event bus.
     * <p>
     * The default value of this property is {@link #DEFAULT_SEND_TIMEOUT_IN_MS}.
     *
     * @return The send timeout value in milliseconds.
     */
    public final long getSendTimeOut() {
        return sendTimeOut;
    }

    /**
     * Sets the send timeout value in milliseconds, which is to be used when sending a 
     * message on the vert.x event bus.
     * <p>
     * The default value of this property is {@link #DEFAULT_SEND_TIMEOUT_IN_MS}.
     *
     * @param sendTimeOutInMs The send timeout value in milliseconds.
     * @throws IllegalArgumentException if the timeout value is less than {@link #MIN_SEND_TIMEOUT_IN_MS}.
     */
    public final void setSendTimeOut(final long sendTimeOutInMs) {
        if (sendTimeOutInMs < MIN_SEND_TIMEOUT_IN_MS) {
            throw new IllegalArgumentException(
                    String.format("send time out value must be >= %sms", MIN_SEND_TIMEOUT_IN_MS));
        }
        this.sendTimeOut = sendTimeOutInMs;
    }

    /**
     * Gets the pattern defining valid device identifiers.
     * <p>
     * The default value of this property is {@value org.eclipse.hono.util.RegistryManagementConstants#DEFAULT_REGEX_DEVICE_ID}.
     *
     * @return The pattern.
     */
    public final Pattern getDeviceIdPattern() {
        return deviceIdPattern;
    }

    /**
     * Sets the regular expression defining valid device identifiers.
     * <p>
     * The default value of this property is {@value org.eclipse.hono.util.RegistryManagementConstants#DEFAULT_REGEX_DEVICE_ID}
     *
     * @param regex The regular expression.
     * @throws NullPointerException if regex is {@code null}.
     * @throws java.util.regex.PatternSyntaxException if regex is not a valid regular expression.
     */
    public final void setDeviceIdPattern(final String regex) {
        this.deviceIdPattern = Pattern.compile(Objects.requireNonNull(regex));
    }

    /**
     * Gets the pattern defining valid tenant identifiers.
     * <p>
     * The default value of this property is {@value org.eclipse.hono.util.RegistryManagementConstants#DEFAULT_REGEX_TENANT_ID}.
     *
     * @return The pattern.
     */
    public final Pattern getTenantIdPattern() {
        return tenantIdPattern;
    }

    /**
     * Sets the regular expression defining valid tenant identifiers.
     * <p>
     * The default value of this property is {@link org.eclipse.hono.util.RegistryManagementConstants#DEFAULT_REGEX_TENANT_ID}
     *
     * @param regex The regular expression.
     * @throws NullPointerException if regex is {@code null}.
     * @throws java.util.regex.PatternSyntaxException if regex is not a valid regular expression.
     */
    public final void setTenantIdPattern(final String regex) {
        this.tenantIdPattern = Pattern.compile(Objects.requireNonNull(regex));
    }

    /**
     * Gets the time to wait for completion after which a handler is considered to
     * be blocking the vert.x event loop.
     * <p>
     * Default value is 5000 milliseconds.
     *
     * @return The timeout value in milliseconds.
     */
    public final long getEventLoopBlockedCheckTimeout() {
        return eventLoopBlockedCheckTimeout;
    }

    /**
     * Sets the time to wait for completion after which a handler is to be considered to
     * be blocking the vert.x event loop.
     * <p>
     * Default value is 5000 milliseconds.
     *
     * @param eventLoopBlockedCheckTimeout The timeout value in milliseconds.
     */
    public final void setEventLoopBlockedCheckTimeout(final long eventLoopBlockedCheckTimeout) {
        this.eventLoopBlockedCheckTimeout = eventLoopBlockedCheckTimeout;
    }
}
