/*******************************************************************************
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.registry.infinispan;

import org.eclipse.hono.deviceregistry.Application;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * A Spring Boot application exposing an AMQP based endpoint that implements Hono's device registry.
 * <p>
 * The application implements Hono's <a href="https://www.eclipse.org/hono/api/Device-Registration-API/">Device Registration API</a>
 * and <a href="https://www.eclipse.org/hono/api/Credentials-API/">Credentials API</a>.
 * </p>
 */
@ComponentScan(basePackages = {"org.eclipse.hono.service.auth", "org.eclipse.hono.registry.infinispan"})
@Configuration
@EnableAutoConfiguration
public class InfinispanRegistry extends Application {

    /**
     * Starts the Device Registry Server.
     *
     * @param args command line arguments to pass to the server.
     */
    public static void main(final String[] args) {
        SpringApplication.run(InfinispanRegistry.class, args);
    }
}
