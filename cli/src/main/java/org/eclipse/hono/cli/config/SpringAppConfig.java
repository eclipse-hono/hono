/*******************************************************************************
 * Copyright (c) 2016, 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.cli.config;

import org.eclipse.hono.config.ClientConfigProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.dns.AddressResolverOptions;

/**
 * Configuration for CLI application.
 */
@Configuration
public class SpringAppConfig {

    /**
     * Exposes a Vert.x instance as a Spring bean.
     *
     * @return The Vert.x instance.
     */
    @Bean
    public Vertx vertx() {
        final VertxOptions options = new VertxOptions()
                .setWarningExceptionTime(1500000000)
                .setAddressResolverOptions(addressResolverOptions());
        return Vertx.vertx(options);
    }

    /**
     * Exposes address resolver option properties as a Spring bean.
     *
     * @return The properties.
     */
    @ConfigurationProperties(prefix = "address.resolver")
    @Bean
    public AddressResolverOptions addressResolverOptions() {
        final AddressResolverOptions addressResolverOptions = new AddressResolverOptions();
        return addressResolverOptions;
    }

    /**
     * Exposes connection configuration properties as a Spring bean.
     *
     * @return The properties.
     */
    @ConfigurationProperties(prefix = "hono.client")
    @Bean
    public ClientConfigProperties honoClientConfig() {
        final ClientConfigProperties config = new ClientConfigProperties();
        return config;
    }

}
