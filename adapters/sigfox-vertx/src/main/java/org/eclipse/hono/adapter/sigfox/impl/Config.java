/*******************************************************************************
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter.sigfox.impl;

import javax.annotation.PostConstruct;

import org.eclipse.hono.adapter.http.HttpProtocolAdapterProperties;
import org.eclipse.hono.client.RequestResponseClientConfigProperties;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.service.AbstractAdapterConfig;
import org.eclipse.hono.service.metric.MetricsTags;
import org.springframework.beans.factory.config.ObjectFactoryCreatingFactoryBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.spring.autoconfigure.MeterRegistryCustomizer;

/**
 * Spring Boot configuration for the Sigfox adapter.
 */
@Configuration
public class Config extends AbstractAdapterConfig {

    private static final String CONTAINER_ID_HONO_SIGFOX_ADAPTER = "Hono Sigfox Adapter";
    private static final String BEAN_NAME_SIGFOX_PROTOCOL_ADAPTER = "sigfoxProtocolAdapter";
    private static final String METRICS_PROTOCOL_TAG = "sigfox";

    @PostConstruct
    void validateConfiguration() {
        final HttpProtocolAdapterProperties httpProtocolAdapterProperties = adapterProperties();
        if (!httpProtocolAdapterProperties.isAuthenticationRequired()) {
            throw new IllegalStateException(
                    "SigFox Protocol Adapter does not support unauthenticated mode. Please change your configuration accordingly.");
        }
    }

    /**
     * Creates a new SigFox adapter instance.
     * 
     * @return The new instance.
     */
    @Bean(name = BEAN_NAME_SIGFOX_PROTOCOL_ADAPTER)
    @Scope("prototype")
    public SigfoxProtocolAdapter sigfoxProtocolAdapter() {
        return new SigfoxProtocolAdapter();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void customizeDownstreamSenderFactoryConfig(final ClientConfigProperties props) {
        if (props.getName() == null) {
            props.setName(CONTAINER_ID_HONO_SIGFOX_ADAPTER);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void customizeRegistrationClientFactoryConfig(final RequestResponseClientConfigProperties props) {
        if (props.getName() == null) {
            props.setName(CONTAINER_ID_HONO_SIGFOX_ADAPTER);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void customizeCredentialsClientFactoryConfig(final RequestResponseClientConfigProperties props) {
        if (props.getName() == null) {
            props.setName(CONTAINER_ID_HONO_SIGFOX_ADAPTER);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void customizeTenantClientFactoryConfig(final RequestResponseClientConfigProperties props) {
        if (props.getName() == null) {
            props.setName(CONTAINER_ID_HONO_SIGFOX_ADAPTER);
        }
    }

    /**
     * Exposes the SigFox adapter's configuration properties as a Spring bean.
     *
     * @return The configuration properties.
     */
    @Bean
    @ConfigurationProperties(prefix = "hono.http")
    public HttpProtocolAdapterProperties adapterProperties() {
        return new HttpProtocolAdapterProperties();
    }

    /**
     * Exposes a factory for creating SigFox adapter instances.
     *
     * @return The factory bean.
     */
    @Bean
    public ObjectFactoryCreatingFactoryBean serviceFactory() {
        final ObjectFactoryCreatingFactoryBean factory = new ObjectFactoryCreatingFactoryBean();
        factory.setTargetBeanName(BEAN_NAME_SIGFOX_PROTOCOL_ADAPTER);
        return factory;
    }

    /**
     * Customizer for meter registry.
     *
     * @return The new meter registry customizer.
     */
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> commonTags() {
        return r -> r.config().commonTags(
                MetricsTags.forProtocolAdapter(METRICS_PROTOCOL_TAG));
    }

}
