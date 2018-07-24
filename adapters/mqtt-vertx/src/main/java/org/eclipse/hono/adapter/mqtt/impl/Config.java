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

package org.eclipse.hono.adapter.mqtt.impl;

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
 * Spring Boot configuration for the MQTT protocol adapter.
 */
@Configuration
public class Config extends AbstractAdapterConfig {

    private static final String CONTAINER_ID_HONO_MQTT_ADAPTER = "Hono MQTT Adapter";
    private static final String BEAN_NAME_VERTX_BASED_MQTT_PROTOCOL_ADAPTER = "vertxBasedMqttProtocolAdapter";

    /**
     * Creates a new MQTT protocol adapter instance.
     * 
     * @return The new instance.
     */
    @Bean(name = BEAN_NAME_VERTX_BASED_MQTT_PROTOCOL_ADAPTER)
    @Scope("prototype")
    public VertxBasedMqttProtocolAdapter vertxBasedMqttProtocolAdapter() {
        return new VertxBasedMqttProtocolAdapter();
    }

    @Override
    protected void customizeMessagingClientConfig(final ClientConfigProperties props) {
        if (props.getName() == null) {
            props.setName(CONTAINER_ID_HONO_MQTT_ADAPTER);
        }
    }

    @Override
    protected void customizeRegistrationServiceClientConfig(final RequestResponseClientConfigProperties props) {
        if (props.getName() == null) {
            props.setName(CONTAINER_ID_HONO_MQTT_ADAPTER);
        }
    }

    @Override
    protected void customizeCredentialsServiceClientConfig(final RequestResponseClientConfigProperties props) {
        if (props.getName() == null) {
            props.setName(CONTAINER_ID_HONO_MQTT_ADAPTER);
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
     * Exposes the MQTT adapter's configuration properties as a Spring bean.
     * 
     * @return The configuration properties.
     */
    @Bean
    @ConfigurationProperties(prefix = "hono.mqtt")
    public ProtocolAdapterProperties adapterProperties() {
        return new ProtocolAdapterProperties();
    }

    /**
     * Exposes a factory for creating MQTT adapter instances.
     * 
     * @return The factory bean.
     */
    @Bean
    public ObjectFactoryCreatingFactoryBean serviceFactory() {
        final ObjectFactoryCreatingFactoryBean factory = new ObjectFactoryCreatingFactoryBean();
        factory.setTargetBeanName(BEAN_NAME_VERTX_BASED_MQTT_PROTOCOL_ADAPTER);
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
