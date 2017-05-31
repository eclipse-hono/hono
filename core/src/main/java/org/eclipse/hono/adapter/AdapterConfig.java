/**
 * Copyright (c) 2016 Red Hat
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Red Hat - initial creation
 */

package org.eclipse.hono.adapter;

import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.connection.ConnectionFactory;
import org.eclipse.hono.connection.ConnectionFactoryImpl;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;

import io.vertx.core.Vertx;

/**
 * Minimum configuration for protocol adapters
 */
public abstract class AdapterConfig {

    private final Vertx vertx = Vertx.vertx();

    /**
     * Exposes a Vert.x instance as a Spring bean.
     * 
     * @return The Vert.x instance.
     */
    @Bean
    public Vertx getVertx() {
        return vertx;
    }

    /**
     * Exposes client configuration properties as a Spring bean.
     * 
     * @return The properties.
     */
    @ConfigurationProperties(prefix = "hono.client")
    @Bean
    public ClientConfigProperties honoClientConfig() {
        ClientConfigProperties config = new ClientConfigProperties();
        customizeClientConfigProperties(config);
        return config;
    }

    /**
     * Further customizes the client properties provided by the {@link #honoClientConfig()}
     * method.
     * <p>
     * This method does nothing by default. Subclasses may override this method to set additional
     * properties programmatically.
     * 
     * @param config The client configuration to customize.
     */
    protected void customizeClientConfigProperties(final ClientConfigProperties config) {
        // empty by default
    }

    /**
     * Exposes a factory for connections to the Hono server
     * as a Spring bean.
     * 
     * @return The connection factory.
     */
    @Bean
    public ConnectionFactory honoConnectionFactory() {
        return new ConnectionFactoryImpl();
    }
}
