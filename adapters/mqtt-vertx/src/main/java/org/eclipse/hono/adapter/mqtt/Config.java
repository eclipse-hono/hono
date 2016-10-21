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

package org.eclipse.hono.adapter.mqtt;

import io.vertx.core.Vertx;
import org.eclipse.hono.client.HonoClientConfigProperties;
import org.springframework.beans.factory.config.ServiceLocatorFactoryBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class
 */
@Configuration
public class Config {

    private final Vertx vertx = Vertx.vertx();

    @Bean
    public Vertx getVertx() { return this.vertx; }

    @ConfigurationProperties(prefix = "hono.client")
    @Bean
    public HonoClientConfigProperties honoClientConfig() {
        return new HonoClientConfigProperties();
    }

    @Bean
    public ServiceLocatorFactoryBean serviceLocator() {
        ServiceLocatorFactoryBean bean = new ServiceLocatorFactoryBean();
        bean.setServiceLocatorInterface(MqttAdapterFactory.class);
        return bean;
    }
}
