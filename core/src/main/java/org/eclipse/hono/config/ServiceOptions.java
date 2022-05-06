/**
 * Copyright (c) 2021, 2022 Contributors to the Eclipse Foundation
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

import org.eclipse.hono.util.RegistryManagementConstants;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithParentName;

/**
 * Options for configuring Hono service components.
 *
 */
@ConfigMapping(prefix = "hono.service", namingStrategy = ConfigMapping.NamingStrategy.VERBATIM)
public interface ServiceOptions {

    /**
     * Gets the server options.
     *
     * @return The options.
     */
    @WithParentName
    ServerOptions serverOptions();

    /**
     * Gets the maximum size of a message payload this server accepts from clients.
     *
     * @return The maximum number of bytes.
     */
    @WithDefault("2048")
    int maxPayloadSize();

    /**
     * Checks whether the server is configured to log TCP traffic.
     *
     * @return {@code true} if TCP traffic gets logged.
     */
    @WithDefault("false")
    boolean networkDebugLoggingEnabled();

    /**
     * Checks whether the server waits for downstream connections to be established
     * during startup.
     * <p>
     * If this property is set to {@code true} then startup may take some time or even
     * time out if the downstream container to connect to is not (yet) available.
     *
     * @return {@code true} if the server will wait for downstream connections to be established during startup.
     */
    @WithDefault("false")
    boolean waitForDownstreamConnectionEnabled();

    /**
     * Gets the number of AMQP message credits this service flows to a client
     * when the client opens a sender link to this service.
     *
     * @return The number of credits.
     */
    @WithDefault("100")
    int receiverLinkCredit();

    /**
     * Gets the allowed origin pattern for CORS handler.
     * <p>
     * The allowed origin pattern for CORS is returned to clients via the <em>Access-Control-Allow-Origin</em> header.
     * It can be used by Web Applications to make sure that requests go only to trusted backend entities.
     *
     * @return The allowed origin pattern for CORS handler.
     */
    @WithDefault("*")
    String corsAllowedOrigin();

    /**
     * Gets the send timeout value in milliseconds, which is to be used when sending a 
     * message on the vert.x event bus.
     *
     * @return The send timeout value in milliseconds.
     */
    @WithDefault("3000")
    long sendTimeOut();

    /**
     * Gets the pattern defining valid device identifiers.
     *
     * @return The pattern.
     */
    @WithDefault(RegistryManagementConstants.DEFAULT_REGEX_DEVICE_ID)
    String deviceIdPattern();

    /**
     * Gets the pattern defining valid tenant identifiers.
     *
     * @return The pattern.
     */
    @WithDefault(RegistryManagementConstants.DEFAULT_REGEX_TENANT_ID)
    String tenantIdPattern();

    /**
     * Gets the time to wait for completion after which a handler is considered to
     * be blocking the vert.x event loop.
     *
     * @return The timeout value in milliseconds.
     */
    @WithDefault("5000")
    long eventLoopBlockedCheckTimeout();
}
