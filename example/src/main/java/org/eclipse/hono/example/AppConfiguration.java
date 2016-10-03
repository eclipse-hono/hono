/**
 * Copyright (c) 2016 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 *
 */

package org.eclipse.hono.example;

import org.eclipse.hono.client.HonoClientConfigProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.vertx.core.Vertx;

/**
 * Configuration for Example application.
 */
@Configuration
public class AppConfiguration {

    private final Vertx vertx = Vertx.vertx();

    @ConfigurationProperties(prefix = "hono.client")
    @Bean
    public HonoClientConfigProperties honoClientConfig() {
        return new HonoClientConfigProperties();
    }

    @Bean
    public Vertx vertx() {
        return vertx;
    }
}
