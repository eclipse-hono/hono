/**
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Dependent;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.eclipse.hono.client.amqp.connection.HonoConnection;
import org.eclipse.hono.client.device.amqp.AmqpAdapterClientFactory;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.config.quarkus.ClientOptions;
import org.eclipse.hono.config.quarkus.ServerOptions;
import org.eclipse.hono.util.Constants;

import io.smallrye.config.ConfigMapping;
import io.vertx.core.Vertx;

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
    AmqpAdapterClientFactory amqpAdapterClientFactory(
            @Named(value = "TENANT_ID")
            final String tenantId,
            final HonoConnection amqpAdapterConnection) {

        return AmqpAdapterClientFactory.create(
                amqpAdapterConnection,
                tenantId);
    }
}
