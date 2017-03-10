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
 */

package org.eclipse.hono.application;

import org.eclipse.hono.config.HonoClientConfigProperties;
import org.eclipse.hono.config.HonoConfigProperties;
import org.eclipse.hono.connection.ConnectionFactory;
import org.eclipse.hono.connection.ConnectionFactoryImpl;
import org.eclipse.hono.server.HonoServerFactory;
import org.springframework.beans.factory.config.ServiceLocatorFactoryBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.vertx.core.Vertx;

/**
 * Spring bean definitions required by the Hono application.
 */
@Configuration
public class ApplicationConfig {

    private static final Vertx vertx = Vertx.vertx();

    /**
     * Returns the HonoApplication instance responsible for initializing the
     * Hono server components.
     * 
     * @return HonoApplication instance
     */
    @Bean
    public HonoApplication honoApplication() {
        return new HonoApplication();
    }
    
    /**
     * Gets the singleton Vert.x instance to be used by Hono.
     * 
     * @return the instance.
     */
    @Bean
    public Vertx vertx() {
        return vertx;
    }

    /**
     * Exposes the {@link HonoServerFactory} as a Spring bean.
     * 
     * @return A Spring service locator for the factory.
     */
    @Bean
    public ServiceLocatorFactoryBean honoServerFactoryLocator() {
        ServiceLocatorFactoryBean bean = new ServiceLocatorFactoryBean();
        bean.setServiceLocatorInterface(HonoServerFactory.class);
        return bean;
    }

    /**
     * Exposes properties for configuring the connection to the downstream
     * AMQP container as a Spring bean.
     * 
     * @return The connection properties.
     */
    @Bean
    @ConfigurationProperties(prefix = "hono.downstream")
    public HonoClientConfigProperties downstreamConnectionProperties() {
        HonoClientConfigProperties props = new HonoClientConfigProperties();
        if (props.getAmqpHostname() == null) {
            props.setAmqpHostname("hono-internal");
        }
        return props;
    }

    /**
     * Exposes properties for configuring the Hono server as a Spring bean.
     * 
     * @return The configuration properties.
     */
    @Bean
    @ConfigurationProperties(prefix = "hono.server")
    public HonoConfigProperties honoServerProperties() {
        HonoConfigProperties props = new HonoConfigProperties();
        if (props.getPort() == 0) {
            props.setPort(5672); // set default AMQP port
        }
        return props;
    }

    /**
     * Exposes a factory for connections to the downstream AMQP container
     * as a Spring bean.
     * 
     * @return The connection factory.
     */
    @Bean
    public ConnectionFactory downstreamConnectionFactory() {
        return new ConnectionFactoryImpl();
    }
}
