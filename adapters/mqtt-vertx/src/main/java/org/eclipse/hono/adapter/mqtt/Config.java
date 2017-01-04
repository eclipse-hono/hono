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

import org.eclipse.hono.adapter.AdapterConfig;
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.config.HonoClientConfigProperties;
import org.springframework.beans.factory.config.ServiceLocatorFactoryBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class
 */
@Configuration
public class Config extends AdapterConfig {

    @Override
    protected void customizeClientConfigProperties(HonoClientConfigProperties props) {
        if (props.getName() == null) {
            props.setName("Hono MQTT Adapter");
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

    @Bean
    public ServiceLocatorFactoryBean serviceLocator() {
        ServiceLocatorFactoryBean bean = new ServiceLocatorFactoryBean();
        bean.setServiceLocatorInterface(MqttAdapterFactory.class);
        return bean;
    }
}
