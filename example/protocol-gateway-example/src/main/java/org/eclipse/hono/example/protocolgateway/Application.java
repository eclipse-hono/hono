/*******************************************************************************
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
 *******************************************************************************/

package org.eclipse.hono.example.protocolgateway;

import java.util.Optional;

import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.device.amqp.AmqpAdapterClientFactory;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.config.ServerConfig;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.MessageHelper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;

/**
 * Example TCP server to send event and telemetry messages to Hono AMQP adapter and receive commands.
 */
@SpringBootApplication
public class Application {

    Application() {
        // empty
    }

    public static void main(final String[] args) {
        SpringApplication.run(Application.class, args);
    }

    /**
     * Exposes a Vert.x instance as a Spring bean.
     *
     * @return The Vert.x instance.
     */
    @Bean
    public Vertx vertx() {
        final VertxOptions options = new VertxOptions().setWarningExceptionTime(1500000000);
        return Vertx.vertx(options);
    }

    /**
     * Exposes client configuration for the TCP server of the example gateway as a Spring bean.
     * 
     * @return The configuration.
     */
    @ConfigurationProperties(prefix = "gateway.tcp")
    @Bean
    public ServerConfig tcpServerConfig() {
        return new ServerConfig();
    }


    /**
     * Exposes client configuration for the gateway's connection to Hono's AMQP adapter as a Spring bean.
     * 
     * @return The configuration.
     */
    @ConfigurationProperties(prefix = "gateway.amqp")
    @Bean
    public ClientConfigProperties amqpAdapterConnectionConfig() {
        return new ClientConfigProperties();
    }

    @Bean
    @Qualifier(MessageHelper.APP_PROPERTY_TENANT_ID)
    String amqpAdapterTenant() {
        return Optional.of(amqpAdapterConnectionConfig().getUsername())
                .map(username -> username.substring(username.indexOf("@") + 1))
                .orElse(Constants.DEFAULT_TENANT);
    }

    /**
     * Exposes a connection to the AMQP adapter as a Spring bean.
     * 
     * @return The connection.
     */
    @Bean
    @Scope("prototype")
    public HonoConnection amqpAdapterConnection() {
        return HonoConnection.newConnection(vertx(), amqpAdapterConnectionConfig());
    }

    /**
     * Exposes a factory for clients for the AMQP adapter as a Spring bean.
     * 
     * @return The factory.
     */
    @Bean
    @Scope("prototype")
    public AmqpAdapterClientFactory amqpAdapterClientFactory() {
        return AmqpAdapterClientFactory.create(amqpAdapterConnection(), amqpAdapterTenant());
    }
}
