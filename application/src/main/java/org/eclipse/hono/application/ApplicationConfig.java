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
     * Gets the singleton Vert.x instance to be used by Hono.
     * 
     * @return the instance.
     */
    @Bean
    public static Vertx getInstance() {
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

    @Bean
    @ConfigurationProperties(prefix = "hono.downstream")
    public HonoClientConfigProperties downstreamConnectionProperties() {
        return new HonoClientConfigProperties();
    }

    @Bean
    public ConnectionFactory downstreamConnectionFactory() {
        ConnectionFactoryImpl factory = new ConnectionFactoryImpl();
        factory.setHostname("hono-internal");
        return factory;
    }
}
