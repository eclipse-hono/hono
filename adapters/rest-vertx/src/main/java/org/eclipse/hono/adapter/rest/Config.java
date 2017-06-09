/**
 * Copyright (c) 2016, 2017 Bosch Software Innovations GmbH.
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
import org.eclipse.hono.client.impl.HonoClientImpl;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.connection.ConnectionFactory;
import org.eclipse.hono.util.RegistrationConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ObjectFactoryCreatingFactoryBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

/**
 * Spring Beans used by the Hono REST protocol adapter.
 */
@Configuration
public class Config extends AdapterConfig {

    @Autowired(required = false)
    @Qualifier(RegistrationConstants.REGISTRATION_ENDPOINT)
    private ConnectionFactory registrationServiceConnectionFactory;

    @Override
    protected void customizeClientConfigProperties(final ClientConfigProperties props) {
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
    @Scope("prototype")
    public HonoClient honoClient() {
        return new HonoClientImpl(getVertx(), honoConnectionFactory());
    }

    @Override
    protected void customizeRegistrationServiceClientConfigProperties(final ClientConfigProperties props) {
        if (props.getName() == null) {
            props.setName("Hono REST Adapter");
        }
        if (props.getAmqpHostname() == null) {
            props.setAmqpHostname("hono-device-registry");
        }
    }

    /**
     * Exposes a registration service client as a Spring bean.
     * <p>
     * The client is configured with the properties provided by {@link #registrationServiceClientConfig()}.
     * If no such properties are set, null is returned here.
     *
     * @return The client or null.
     */
    @Bean
    @Qualifier(RegistrationConstants.REGISTRATION_ENDPOINT)
    @Scope("prototype")
    public HonoClient registrationServiceClient() {
        if (registrationServiceConnectionFactory == null) {
            return null;
        }
        return new HonoClientImpl(getVertx(), registrationServiceConnectionFactory);
    }

    /**
     * Exposes the REST adapter's configuration properties as a Spring bean.
     * 
     * @return The configuration properties.
     */
    @Bean
    @ConfigurationProperties(prefix = "hono.http")
    public ServiceConfigProperties honoServerProperties() {
        return new ServiceConfigProperties();
    }

    /**
     * Exposes a factory for creating REST adapter instances.
     * 
     * @return The factory bean.
     */
    @Bean
    public ObjectFactoryCreatingFactoryBean serviceFactory() {
        ObjectFactoryCreatingFactoryBean factory = new ObjectFactoryCreatingFactoryBean();
        factory.setTargetBeanName(VertxBasedRestProtocolAdapter.class.getName());
        return factory;
    }
}
