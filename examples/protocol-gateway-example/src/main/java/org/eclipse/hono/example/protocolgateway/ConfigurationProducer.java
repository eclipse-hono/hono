/**
 * Copyright (c) 2022, 2023 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.example.protocolgateway;

import org.eclipse.hono.client.amqp.config.ClientConfigProperties;
import org.eclipse.hono.client.amqp.config.ClientOptions;
import org.eclipse.hono.client.amqp.connection.HonoConnection;
import org.eclipse.hono.client.device.amqp.AmqpAdapterClient;
import org.eclipse.hono.config.ServerOptions;
import org.eclipse.hono.util.Constants;

import io.smallrye.config.ConfigMapping;
import io.vertx.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

/**
 * Example TCP server to send event and telemetry messages to Hono AMQP adapter and receive commands.
 */
@ApplicationScoped
public class ConfigurationProducer {

    @Inject
    Vertx vertx;

    @Inject
    @ConfigMapping(prefix = "gateway.tcp")
    ServerOptions serverOptions;

    @Inject
    @ConfigMapping(prefix = "gateway.amqp")
    ClientOptions amqpAdapterClientOptions;

    @Produces
    @Singleton
    @Named(value = "TENANT_ID")
    String tenantId() {
        return amqpAdapterClientOptions.authenticatingClientOptions().username()
                .map(username -> username.substring(username.indexOf("@") + 1))
                .orElse(Constants.DEFAULT_TENANT);
    }

    @Produces
    @Dependent
    HonoConnection amqpAdapterConnection() {
        final var props = new ClientConfigProperties(amqpAdapterClientOptions);
        return HonoConnection.newConnection(vertx, props);
    }

    @Produces
    @Dependent
    AmqpAdapterClient amqpAdapterClient(
            final HonoConnection amqpAdapterConnection) {

        return AmqpAdapterClient.create(amqpAdapterConnection);
    }
}
