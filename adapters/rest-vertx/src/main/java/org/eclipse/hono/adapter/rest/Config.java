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

package org.eclipse.hono.adapter.rest;

import org.eclipse.hono.adapter.AdapterConfig;
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.config.HonoClientConfigProperties;
import org.eclipse.hono.config.HonoConfigProperties;
import org.springframework.beans.factory.config.ServiceLocatorFactoryBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Beans used by the Hono REST protocol adapter.
 */
@Configuration
public class Config extends AdapterConfig {

    @Override
    protected void customizeClientConfigProperties(HonoClientConfigProperties props) {
        if (props.getName() == null) {
            props.setName("Hono REST Adapter");
        }
        if (props.getAmqpHostname() == null) {
            props.setAmqpHostname("hono");
        }
    }

    /**
     * Exposes a Hono client as a Spring bean.
     * <p>
     * The client is configured with the properties provided by {@link #honoClientConfig()}.
     * 
     * @return The client.
     */
    @Bean
    public HonoClient honoClient() {
        return new HonoClient(getVertx(), honoConnectionFactory());
    }

    /**
     * Exposes the REST adapter's configuration properties as a Spring bean.
     * 
     * @return The configuration properties.
     */
    @Bean
    @ConfigurationProperties(prefix = "hono.http")
    public HonoConfigProperties honoServerProperties() {
        HonoConfigProperties props = new HonoConfigProperties();
        if (props.getPort() == 0) {
            props.setPort(8080); // set default port
        }
        return props;
    }

    @Bean
    public ServiceLocatorFactoryBean serviceLocator() {
        ServiceLocatorFactoryBean bean = new ServiceLocatorFactoryBean();
        bean.setServiceLocatorInterface(RestAdapterFactory.class);
        return bean;
    }
}
