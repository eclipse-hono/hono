/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
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
 * A POJO for configuring common properties of server components.
 *
 */
public class ServiceConfigProperties extends ServerConfig {


    private static final int MIN_PAYLOAD_SIZE  = 128; // bytes
    private static final int DEFAULT_RECEIVER_LINK_CREDITS = 100;

    private boolean singleTenant = false;
    private boolean networkDebugLogging = false;
    private boolean waitForDownstreamConnection = false;
    private int maxPayloadSize = 2048;
    private int receiverLinkCredit = DEFAULT_RECEIVER_LINK_CREDITS;

    /**
     * Sets the maximum size of a message payload this server accepts from clients.
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
     *
     * @return The maximum number of bytes.
     */
    public final int getMaxPayloadSize() {
        return maxPayloadSize;
    }

    /**
     * Checks whether the server is configured to run in single-tenant mode.
     * <p>
     * In this mode clients do not need to specify a <em>tenant</em>
     * component in resource addresses. The server will use the
     * {@link Constants#DEFAULT_TENANT} instead.
     *
     * @return {@code true} if the server is configured to run in single-tenant mode.
     */
    public final boolean isSingleTenant() {
        return singleTenant;
    }

    /**
     * Sets whether the server should support a single tenant only.
     * <p>
     * In this mode clients do not need to specify a <em>tenant</em>
     * component in resource addresses. The server will use the
     * {@link Constants#DEFAULT_TENANT} instead.
     * <p>
     * The default value of this property is {@code false}.
     *
     * @param singleTenant {@code true} if the server should support a single tenant only.
     * @return This instance for setter chaining.
     */
    public final ServiceConfigProperties setSingleTenant(final boolean singleTenant) {
        this.singleTenant = singleTenant;
        return this;
    }

    /**
     * Checks whether the server is configured to log TCP traffic.
     *
     * @return {@code true} if TCP traffic gets logged.
     */
    public final boolean isNetworkDebugLoggingEnabled() {
        return networkDebugLogging;
    }

    /**
     * Sets whether the server should log TCP traffic.
     * <p>
     * The default value of this property is {@code false}.
     *
     * @param networkDebugLogging {@code true} if TCP traffic should be logged.
     * @return This instance for setter chaining.
     */
    public final ServiceConfigProperties setNetworkDebugLoggingEnabled(final boolean networkDebugLogging) {
        this.networkDebugLogging = networkDebugLogging;
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
        return waitForDownstreamConnection;
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
        this.waitForDownstreamConnection = waitForConnection;
        return this;
    }

    /**
     * Gets the number of AMQP message credits this service flows to a client
     * when the client opens a sender link to this service.
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

     * @param receiverLinkCredit The number of credits.
     * @throws IllegalArgumentException if the credit is &lt;= 0.
     */
    public final void setReceiverLinkCredit(final int receiverLinkCredit) {
        if (receiverLinkCredit <= 0) {
            throw new IllegalArgumentException("receiver link credit must be at least 1");
        }
        this.receiverLinkCredit = receiverLinkCredit;
    }
}
