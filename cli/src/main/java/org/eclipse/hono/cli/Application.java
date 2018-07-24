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
package org.eclipse.hono.cli;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * A CLI(Command Line Interface) Application using HonoClient for receiving telemetry data and event messages to/from
 * Hono.
 */
@SpringBootApplication
public class Application {

    private static final Logger LOG = LoggerFactory.getLogger(Application.class);

    @Value(value = "${spring.profiles.active}")
    private String profiles;

    @PostConstruct
    private void start() throws Exception {
        LOG.info("Starting example client in role {}", profiles);
    }

    /**
     * Starts the client.
     * 
     * @param args Command line arguments passed on to the Spring Boot application.
     */
    public static void main(final String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
