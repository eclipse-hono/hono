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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

import io.vertx.core.Vertx;

/**
 * The "Azure IoT Hub" Protocol Gateway main application class.
 */
@ComponentScan
@EnableAutoConfiguration
public class ProtocolGatewayApplication implements ApplicationRunner {

    private final Vertx vertx = Vertx.vertx();

    @Autowired
    private AzureIotHubMqttGateway azureIotHubMqttGateway;

    /**
     * Starts the "Azure IoT Hub" Protocol Gateway application.
     *
     * @param args Command line arguments passed to the application.
     */
    public static void main(final String[] args) {
        SpringApplication.run(ProtocolGatewayApplication.class, args);
    }

    @Override
    public void run(final ApplicationArguments args) {

        vertx.deployVerticle(azureIotHubMqttGateway);
    }
}
