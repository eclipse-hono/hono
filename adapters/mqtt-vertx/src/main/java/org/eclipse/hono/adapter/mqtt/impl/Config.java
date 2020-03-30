/*******************************************************************************
 * Copyright (c) 2016, 2020 Contributors to the Eclipse Foundation
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

import org.eclipse.hono.adapter.mqtt.MqttProtocolAdapterProperties;
import org.eclipse.hono.client.RequestResponseClientConfigProperties;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.service.AbstractAdapterConfig;
import org.eclipse.hono.service.metric.MetricsTags;
import org.eclipse.hono.util.Constants;
import org.springframework.beans.factory.config.ObjectFactoryCreatingFactoryBean;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import io.micrometer.core.instrument.MeterRegistry;
import io.vertx.ext.web.client.WebClient;

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
    protected void customizeDownstreamSenderFactoryConfig(final ClientConfigProperties props) {
        if (props.getName() == null) {
            props.setName(CONTAINER_ID_HONO_MQTT_ADAPTER);
        }
    }

    @Override
    protected void customizeRegistrationClientFactoryConfig(final RequestResponseClientConfigProperties props) {
        if (props.getName() == null) {
            props.setName(CONTAINER_ID_HONO_MQTT_ADAPTER);
        }
    }

    @Override
    protected void customizeCredentialsClientFactoryConfig(final RequestResponseClientConfigProperties props) {
        if (props.getName() == null) {
            props.setName(CONTAINER_ID_HONO_MQTT_ADAPTER);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void customizeTenantClientFactoryConfig(final RequestResponseClientConfigProperties props) {
        if (props.getName() == null) {
            props.setName(CONTAINER_ID_HONO_MQTT_ADAPTER);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void customizeDeviceConnectionClientFactoryConfig(final RequestResponseClientConfigProperties props) {
        if (props.getName() == null) {
            props.setName(CONTAINER_ID_HONO_MQTT_ADAPTER);
        }
    }

    /**
     * Exposes the MQTT adapter's configuration properties as a Spring bean.
     * 
     * @return The configuration properties.
     */
    @Bean
    @ConfigurationProperties(prefix = "hono.mqtt")
    public MqttProtocolAdapterProperties adapterProperties() {
        return new MqttProtocolAdapterProperties();
    }

    /**
     * Customizer for meter registry.
     * 
     * @return The new meter registry customizer.
     */
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> commonTags() {
        return r -> r.config().commonTags(
                MetricsTags.forProtocolAdapter(Constants.PROTOCOL_ADAPTER_TYPE_MQTT));
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
     * Constructs messageMapping.
     *
     * @return Returns MessageMapping containing a webclient to perform mapper requests.
     */
    @Bean
    public MessageMapping messageMapping() {
        final WebClient webClient = WebClient.create(vertx());
        return new HttpBasedMessageMapping(webClient, adapterProperties());
    }
}
