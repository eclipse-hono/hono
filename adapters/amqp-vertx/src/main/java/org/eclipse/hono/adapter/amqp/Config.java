/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.adapter.amqp;

import org.eclipse.hono.client.RequestResponseClientConfigProperties;
import org.eclipse.hono.config.ApplicationConfigProperties;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.config.ProtocolAdapterProperties;
import org.eclipse.hono.service.AbstractAdapterConfig;
import org.eclipse.hono.service.monitoring.ConnectionEventProducer;
import org.eclipse.hono.service.monitoring.LoggingConnectionEventProducer;
import org.springframework.beans.factory.config.ObjectFactoryCreatingFactoryBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

/**
 * Spring Boot configuration for the AMQP protocol adapter.
 */
@Configuration
public class Config extends AbstractAdapterConfig {

    private static final String CONTAINER_ID_HONO_AMQP_ADAPTER = "Hono AMQP Adapter";
    private static final String BEAN_NAME_VERTX_BASED_AMQP_PROTOCOL_ADAPTER = "vertxBasedAmqpProtocolAdapter";

    /**
     * Creates an AMQP protocol adapter instance.
     * 
     * @return The new instance.
     */
    @Bean(name = BEAN_NAME_VERTX_BASED_AMQP_PROTOCOL_ADAPTER)
    @Scope("prototype")
    public VertxBasedAmqpProtocolAdapter vertxBasedAmqpProtocolAdapter() {
        return new VertxBasedAmqpProtocolAdapter();
    }

    @Override
    protected void customizeMessagingClientConfig(final ClientConfigProperties config) {
        if (config.getName() == null) {
            config.setName(CONTAINER_ID_HONO_AMQP_ADAPTER);
        }
    }

    @Override
    protected void customizeRegistrationServiceClientConfig(final RequestResponseClientConfigProperties config) {
        if (config.getName() == null) {
            config.setName(CONTAINER_ID_HONO_AMQP_ADAPTER);
        }
    }

    @Override
    protected void customizeCredentialsServiceClientConfig(final RequestResponseClientConfigProperties config) {
        if (config.getName() == null) {
            config.setName(CONTAINER_ID_HONO_AMQP_ADAPTER);
        }
    }

    @Override
    protected void customizeTenantServiceClientConfig(final RequestResponseClientConfigProperties config) {
        if (config != null) {
            config.setName(CONTAINER_ID_HONO_AMQP_ADAPTER);
        }
    }

    /**
     * Exposes properties for configuring the application properties as a Spring bean.
     * 
     * @return The application configuration properties.
     */
    @Bean
    @ConfigurationProperties(prefix = "hono.app")
    public ApplicationConfigProperties applicationConfigProperties() {
        return new ApplicationConfigProperties();
    }

    /**
     * Exposes the configuration properties of the AMQP adapter as a Spring bean.
     * 
     * @return The configuration properties.
     */
    @Bean
    @ConfigurationProperties(prefix = "hono.amqp")
    public ProtocolAdapterProperties adapterProperties() {
        final ProtocolAdapterProperties config = new ProtocolAdapterProperties();
        return config;
    }

    /**
     * Exposes a factory for creating AMQP adapter instances.
     * 
     * @return The factory bean.
     */
    @Bean
    public ObjectFactoryCreatingFactoryBean serviceFactory() {
        final ObjectFactoryCreatingFactoryBean factory = new ObjectFactoryCreatingFactoryBean();
        factory.setTargetBeanName(BEAN_NAME_VERTX_BASED_AMQP_PROTOCOL_ADAPTER);
        return factory;
    }

    /**
     * Exposes the connection event producer implementation.
     * <p>
     * This defaults to a {@link LoggingConnectionEventProducer} which logs to the default level.
     * 
     * @return The connection event producer.
     */
    @Bean
    public ConnectionEventProducer connectionEventProducer() {
        return new LoggingConnectionEventProducer();
    }

}
