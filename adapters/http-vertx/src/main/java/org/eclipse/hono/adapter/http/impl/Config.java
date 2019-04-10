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

package org.eclipse.hono.adapter.http.impl;

import org.eclipse.hono.adapter.http.HttpProtocolAdapterProperties;
import org.eclipse.hono.client.RequestResponseClientConfigProperties;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.service.AbstractAdapterConfig;
import org.eclipse.hono.service.metric.MetricsTags;
import org.eclipse.hono.util.Constants;
import org.springframework.beans.factory.config.ObjectFactoryCreatingFactoryBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.spring.autoconfigure.MeterRegistryCustomizer;

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
    public VertxBasedHttpProtocolAdapter vertxBasedHttpProtocolAdapter() {
        return new VertxBasedHttpProtocolAdapter();
    }

    @Override
    protected void customizeMessagingClientConfig(final ClientConfigProperties props) {
        if (props.getName() == null) {
            props.setName(CONTAINER_ID_HONO_HTTP_ADAPTER);
        }
    }

    @Override
    protected void customizeRegistrationClientFactoryConfig(final RequestResponseClientConfigProperties props) {
        if (props.getName() == null) {
            props.setName(CONTAINER_ID_HONO_HTTP_ADAPTER);
        }
    }

    @Override
    protected void customizeCredentialsClientFactoryConfig(final RequestResponseClientConfigProperties props) {
        if (props.getName() == null) {
            props.setName(CONTAINER_ID_HONO_HTTP_ADAPTER);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void customizeTenantClientFactoryConfig(final RequestResponseClientConfigProperties props) {
        if (props.getName() == null) {
            props.setName(CONTAINER_ID_HONO_HTTP_ADAPTER);
        }
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
     * Customizer for meter registry.
     * 
     * @return The new meter registry customizer.
     */
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> commonTags() {
        return r -> r.config().commonTags(
                MetricsTags.forProtocolAdapter(Constants.PROTOCOL_ADAPTER_TYPE_HTTP));
    }

    /**
     * Exposes a factory for creating HTTP adapter instances.
     *
     * @return The factory bean.
     */
    @Bean
    public ObjectFactoryCreatingFactoryBean serviceFactory() {
        final ObjectFactoryCreatingFactoryBean factory = new ObjectFactoryCreatingFactoryBean();
        factory.setTargetBeanName(BEAN_NAME_VERTX_BASED_HTTP_PROTOCOL_ADAPTER);
        return factory;
    }
}
