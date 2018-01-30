/**
 * Copyright (c) 2016, 2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */
package org.eclipse.hono.example;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * An example of using HonoClient for uploading and retrieving telemetry data and event messages to/from Hono.
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
