/*******************************************************************************
 * Copyright (c) 2016, 2021 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter.kura.impl;

import org.eclipse.hono.adapter.mqtt.MicrometerBasedMqttAdapterMetrics;
import org.eclipse.hono.adapter.mqtt.MqttAdapterMetrics;
import org.eclipse.hono.adapter.spring.AbstractAdapterConfig;
import org.eclipse.hono.client.SendMessageSampler;
import org.eclipse.hono.service.metric.MetricsTags;
import org.eclipse.hono.util.Constants;
import org.springframework.beans.factory.config.ObjectFactoryCreatingFactoryBean;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Spring Boot configuration for the Eclipse Kura protocol adapter.
 */
@Configuration
public class Config extends AbstractAdapterConfig {

    private static final String CONTAINER_ID_KURA_ADAPTER = "Kura Adapter";
    private static final String BEAN_NAME_KURA_PROTOCOL_ADAPTER = "kuraProtocolAdapter";

    /**
     * Creates a new Kura protocol adapter instance.
     *
     * @param samplerFactory The sampler factory to use.
     * @param metrics The component to use for reporting metrics.
     * @return The new instance.
     */
    @Bean(name = BEAN_NAME_KURA_PROTOCOL_ADAPTER)
    @Scope("prototype")
    public KuraProtocolAdapter kuraProtocolAdapter(
            final SendMessageSampler.Factory samplerFactory,
            final MqttAdapterMetrics metrics) {

        final KuraProtocolAdapter adapter = new KuraProtocolAdapter();
        setCollaborators(adapter, adapterProperties(), samplerFactory);
        adapter.setConfig(adapterProperties());
        adapter.setMetrics(metrics);
        return adapter;
    }

    @Override
    protected String getAdapterName() {
        return CONTAINER_ID_KURA_ADAPTER;
    }

    /**
     * Exposes the Kura adapter's configuration properties as a Spring bean.
     *
     * @return The configuration properties.
     */
    @Bean
    @ConfigurationProperties(prefix = "hono.kura")
    public KuraAdapterProperties adapterProperties() {
        return new KuraAdapterProperties();
    }

    /**
     * Customizer for meter registry.
     *
     * @return The new meter registry customizer.
     */
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> commonTags() {
        return r -> r.config().commonTags(
                MetricsTags.forProtocolAdapter(Constants.PROTOCOL_ADAPTER_TYPE_KURA));
    }

    /**
     * Provides the adapter metrics instance to use.
     *
     * @param registry The meter registry to use.
     * @return A new adapter metrics instance.
     */
    @Bean
    public MqttAdapterMetrics adapterMetrics(final MeterRegistry registry) {
        return new MicrometerBasedMqttAdapterMetrics(registry, vertx());
    }

    /**
     * Exposes a factory for creating Kura adapter instances.
     *
     * @return The factory bean.
     */
    @Bean
    public ObjectFactoryCreatingFactoryBean serviceFactory() {
        final ObjectFactoryCreatingFactoryBean factory = new ObjectFactoryCreatingFactoryBean();
        factory.setTargetBeanName(BEAN_NAME_KURA_PROTOCOL_ADAPTER);
        return factory;
    }
}
