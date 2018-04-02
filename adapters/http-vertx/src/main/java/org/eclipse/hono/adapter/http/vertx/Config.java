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

package org.eclipse.hono.adapter.http.vertx;

import org.eclipse.hono.adapter.http.HttpProtocolAdapterProperties;
import org.eclipse.hono.client.RequestResponseClientConfigProperties;
import org.eclipse.hono.config.ApplicationConfigProperties;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.service.AbstractAdapterConfig;
import org.springframework.beans.factory.config.ObjectFactoryCreatingFactoryBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

/**
 * Spring Boot configuration for the HTTP adapter.
 */
@Configuration
public class Config extends AbstractAdapterConfig {

    private static final String CONTAINER_ID_HONO_HTTP_ADAPTER = "Hono HTTP Adapter";
    private static final String BEAN_NAME_VERTX_BASED_HTTP_PROTOCOL_ADAPTER = "vertxBasedHttpProtocolAdapter";

    /**
     * Creates a new HTTP adapter instance.
     * 
     * @return The new instance.
     */
    @Bean(name = BEAN_NAME_VERTX_BASED_HTTP_PROTOCOL_ADAPTER)
    @Scope("prototype")
    public VertxBasedHttpProtocolAdapter vertxBasedHttpProtocolAdapter(){
        return new VertxBasedHttpProtocolAdapter();
    }

    @Override
    protected void customizeMessagingClientConfig(final ClientConfigProperties props) {
        if (props.getName() == null) {
            props.setName(CONTAINER_ID_HONO_HTTP_ADAPTER);
        }
    }

    @Override
    protected void customizeRegistrationServiceClientConfig(final RequestResponseClientConfigProperties props) {
        if (props.getName() == null) {
            props.setName(CONTAINER_ID_HONO_HTTP_ADAPTER);
        }
    }

    @Override
    protected void customizeCredentialsServiceClientConfig(final RequestResponseClientConfigProperties props) {
        if (props.getName() == null) {
            props.setName(CONTAINER_ID_HONO_HTTP_ADAPTER);
        }
    }

    /**
     * Exposes properties for configuring the application properties as a Spring bean.
     *
     * @return The application configuration properties.
     */
    @Bean
    @ConfigurationProperties(prefix = "hono.app")
    public ApplicationConfigProperties applicationConfigProperties(){
        return new ApplicationConfigProperties();
    }

    /**
     * Exposes the HTTP adapter's configuration properties as a Spring bean.
     *
     * @return The configuration properties.
     */
    @Bean
    @ConfigurationProperties(prefix = "hono.http")
    public HttpProtocolAdapterProperties adapterProperties() {
        return new HttpProtocolAdapterProperties();
    }

    /**
     * Exposes a factory for creating HTTP adapter instances.
     *
     * @return The factory bean.
     */
    @Bean
    public ObjectFactoryCreatingFactoryBean serviceFactory() {
        ObjectFactoryCreatingFactoryBean factory = new ObjectFactoryCreatingFactoryBean();
        factory.setTargetBeanName(BEAN_NAME_VERTX_BASED_HTTP_PROTOCOL_ADAPTER);
        return factory;
    }
}
