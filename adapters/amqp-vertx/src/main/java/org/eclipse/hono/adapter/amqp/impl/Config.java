/*******************************************************************************
 * Copyright (c) 2018, 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.adapter.amqp.impl;

import java.util.Optional;

import org.eclipse.hono.adapter.amqp.AmqpAdapterMetrics;
import org.eclipse.hono.adapter.amqp.AmqpAdapterProperties;
import org.eclipse.hono.adapter.amqp.MicrometerBasedAmqpAdapterMetrics;
import org.eclipse.hono.adapter.amqp.VertxBasedAmqpProtocolAdapter;
import org.eclipse.hono.adapter.spring.AbstractAdapterConfig;
import org.eclipse.hono.client.SendMessageSampler;
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
import io.vertx.core.Vertx;

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
     * @param samplerFactory The sampler factory to use.
     * @param metrics The component to use for reporting metrics.
     * @param resourceLimitChecks The component to use for checking if the adapter's
     *                            resource limits are exceeded.
     * @return The new instance.
     */
    @Bean(name = BEAN_NAME_VERTX_BASED_AMQP_PROTOCOL_ADAPTER)
    @Scope("prototype")
    public VertxBasedAmqpProtocolAdapter vertxBasedAmqpProtocolAdapter(
            final SendMessageSampler.Factory samplerFactory,
            final AmqpAdapterMetrics metrics,
            final Optional<ResourceLimitChecks> resourceLimitChecks) {

        final VertxBasedAmqpProtocolAdapter adapter = new VertxBasedAmqpProtocolAdapter();
        setCollaborators(adapter, adapterProperties(), samplerFactory, resourceLimitChecks);
        adapter.setConfig(adapterProperties());
        adapter.setMetrics(metrics);
        return adapter;
    }

    @Override
    protected String getAdapterName() {
        return CONTAINER_ID_HONO_AMQP_ADAPTER;
    }

    @Bean
    AmqpAdapterMetrics metrics(final MeterRegistry meterRegistry, final Vertx vertx) {
        return new MicrometerBasedAmqpAdapterMetrics(meterRegistry, vertx);
    }

    /**
     * Exposes the configuration properties of the AMQP adapter as a Spring bean.
     *
     * @return The configuration properties.
     */
    @Bean
    @ConfigurationProperties(prefix = "hono.amqp")
    public AmqpAdapterProperties adapterProperties() {
        final AmqpAdapterProperties config = new AmqpAdapterProperties();
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
     * Customizer for meter registry.
     *
     * @return The new meter registry customizer.
     */
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> commonTags() {
        return r -> r.config().commonTags(
                MetricsTags.forProtocolAdapter(Constants.PROTOCOL_ADAPTER_TYPE_AMQP));
    }
}
