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

import java.util.Optional;

import org.eclipse.hono.adapter.http.HttpAdapterMetrics;
import org.eclipse.hono.adapter.http.HttpProtocolAdapterProperties;
import org.eclipse.hono.client.SendMessageSampler;
import org.eclipse.hono.service.AbstractAdapterConfig;
import org.eclipse.hono.service.metric.MetricsTags;
import org.eclipse.hono.service.resourcelimits.ResourceLimitChecks;
import org.eclipse.hono.util.Constants;
import org.springframework.beans.factory.config.ObjectFactoryCreatingFactoryBean;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import io.micrometer.core.instrument.MeterRegistry;

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
     * @param samplerFactory The sampler factory to use.
     * @param metrics The component to use for reporting metrics.
     * @param resourceLimitChecks The component to use for checking if the adapter's
     *                            resource limits are exceeded.
     * @return The new instance.
     */
    @Bean(name = BEAN_NAME_VERTX_BASED_HTTP_PROTOCOL_ADAPTER)
    @Scope("prototype")
    public VertxBasedHttpProtocolAdapter vertxBasedHttpProtocolAdapter(
            final SendMessageSampler.Factory samplerFactory,
            final HttpAdapterMetrics metrics,
            final Optional<ResourceLimitChecks> resourceLimitChecks) {

        final VertxBasedHttpProtocolAdapter adapter = new VertxBasedHttpProtocolAdapter();
        setCollaborators(adapter, adapterProperties(), samplerFactory, resourceLimitChecks);
        adapter.setConfig(adapterProperties());
        adapter.setMetrics(metrics);
        return adapter;
    }

    @Override
    protected String getAdapterName() {
        return CONTAINER_ID_HONO_HTTP_ADAPTER;
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
