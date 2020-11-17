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
package org.eclipse.hono.service.quarkus;

import org.eclipse.hono.client.RequestResponseClientConfigProperties;
import org.eclipse.hono.config.ApplicationConfigProperties;
import org.eclipse.hono.config.ServerConfig;
import org.eclipse.hono.service.monitoring.ConnectionEventProducerConfig;

import io.quarkus.arc.config.ConfigProperties;

/**
 * Configuration properties for a Hono protocol adapter.
 */
@ConfigProperties(prefix = "hono", namingStrategy = ConfigProperties.NamingStrategy.VERBATIM, failOnMismatchingMember = false)
public class ProtocolAdapterConfig {

    public ApplicationConfig app;

    public CommandConfig command;

    public CommandRouterConfig commandRouter;

    public QuarkusConnectionEventProducerConfig connectionEvents;

    public CredentialsClientConfig credentials;

    public DeviceConnectionConfig deviceConnection;

    public HealthCheckConfig healthCheck;

    public DownstreamSenderConfig messaging;

    public RegistrationClientConfig registration;

    public TenantClientConfig tenant;

    /**
     * Command configuration.
     */
    public static class CommandConfig extends RequestResponseClientConfigProperties {
    }

    /**
     * Application configuration.
     */
    public static class ApplicationConfig extends ApplicationConfigProperties {
    }

    /**
     * Command Router client configuration.
     */
    public static class CommandRouterConfig extends RequestResponseClientConfigProperties { }

     /**
     * Credentials client configuration.
     */
    public static class CredentialsClientConfig extends RequestResponseClientConfigProperties {
    }

    /**
     * Device connection client configuration.
     */
    public static class DeviceConnectionConfig extends RequestResponseClientConfigProperties {
    }

    /**
     * Messaging client configuration.
     */
    public static class DownstreamSenderConfig extends RequestResponseClientConfigProperties {
    }

    /**
     * Health check configuration.
     */
    public static class HealthCheckConfig extends ServerConfig {
    }

    /**
     * Connection event producer configuration.
     */
    public static class QuarkusConnectionEventProducerConfig extends ConnectionEventProducerConfig {
    }

    /**
     * Device registration client configuration.
     */
    public static class RegistrationClientConfig extends RequestResponseClientConfigProperties {
    }

    /**
     * Tenant client configuration.
     */
    public static class TenantClientConfig extends RequestResponseClientConfigProperties {
    }
}
