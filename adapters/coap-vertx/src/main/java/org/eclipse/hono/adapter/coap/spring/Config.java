/**
 * Copyright (c) 2018, 2021 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.hono.adapter.coap.spring;

import java.util.Optional;
import java.util.Set;

import org.eclipse.hono.adapter.coap.CoapAdapterMetrics;
import org.eclipse.hono.adapter.coap.CoapAdapterProperties;
import org.eclipse.hono.adapter.coap.CommandResponseResource;
import org.eclipse.hono.adapter.coap.EventResource;
import org.eclipse.hono.adapter.coap.MicrometerBasedCoapAdapterMetrics;
import org.eclipse.hono.adapter.coap.TelemetryResource;
import org.eclipse.hono.adapter.coap.impl.VertxBasedCoapAdapter;
import org.eclipse.hono.adapter.coap.lwm2m.LeshanBasedLwM2MRegistrationStore;
import org.eclipse.hono.adapter.coap.lwm2m.LwM2MResourceDirectory;
import org.eclipse.hono.adapter.resourcelimits.ResourceLimitChecks;
import org.eclipse.hono.adapter.spring.AbstractAdapterConfig;
import org.eclipse.hono.client.SendMessageSampler;
import org.eclipse.hono.service.metric.MetricsTags;
import org.eclipse.hono.util.Constants;
import org.eclipse.leshan.server.californium.registration.InMemoryRegistrationStore;
import org.springframework.beans.factory.config.ObjectFactoryCreatingFactoryBean;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import io.micrometer.core.instrument.MeterRegistry;
import io.vertx.core.Vertx;

/**
 * Spring Boot configuration for the COAP adapter.
 */
@Configuration
public class Config extends AbstractAdapterConfig {

    private static final String CONTAINER_ID_HONO_COAP_ADAPTER = "Hono CoAP Adapter";
    private static final String BEAN_NAME_VERTX_BASED_COAP_ADAPTER = "vertxBasedCoapAdapter";

    /**
     * Exposes the CoAP adapter's configuration properties as a Spring bean.
     *
     * @return The configuration properties.
     */
    @Bean
    @ConfigurationProperties(prefix = "hono.coap")
    public CoapAdapterProperties adapterProperties() {
        return new CoapAdapterProperties();
    }

    @Bean
    CoapAdapterMetrics metrics(final MeterRegistry registry, final Vertx vertx) {
        return new MicrometerBasedCoapAdapterMetrics(registry, vertx);
    }

    /**
     * Creates a new COAP adapter instance.
     *
     * @param samplerFactory The sampler factory to use.
     * @param metrics The component to use for reporting metrics.
     * @param resourceLimitChecks The component to use for checking if the adapter's
     *                            resource limits are exceeded.
     * @return The new instance.
     */
    @Bean(name = BEAN_NAME_VERTX_BASED_COAP_ADAPTER)
    @Scope("prototype")
    public VertxBasedCoapAdapter vertxBasedCoapAdapter(
            final SendMessageSampler.Factory samplerFactory,
            final CoapAdapterMetrics metrics,
            final Optional<ResourceLimitChecks> resourceLimitChecks) {

        final VertxBasedCoapAdapter adapter = new VertxBasedCoapAdapter();
        setCollaborators(adapter, adapterProperties(), samplerFactory, resourceLimitChecks);
        adapter.setConfig(adapterProperties());
        adapter.setMetrics(metrics);
        adapter.addResources(Set.of(
                new TelemetryResource(adapter, getTracer(), vertx()),
                new EventResource(adapter, getTracer(), vertx()),
                new CommandResponseResource(adapter, getTracer(), vertx())));
        if (adapterProperties().isLwm2mEnabled()) {
            final var observationStore = new InMemoryRegistrationStore();
            adapter.setObservationStore(observationStore);
            final var store = new LeshanBasedLwM2MRegistrationStore(
                    observationStore,
                    adapter,
                    getTracer());
            adapter.addResources(Set.of(new LwM2MResourceDirectory(adapter, store, getTracer())));
        }
        return adapter;
    }

    @Override
    protected String getAdapterName() {
        return CONTAINER_ID_HONO_COAP_ADAPTER;
    }

    /**
     * Customizer for meter registry.
     *
     * @return The new meter registry customizer.
     */
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> commonTags() {
        return r -> r.config().commonTags(
                MetricsTags.forProtocolAdapter(Constants.PROTOCOL_ADAPTER_TYPE_COAP));
    }

    /**
     * Exposes a factory for creating COAP adapter instances.
     *
     * @return The factory bean.
     */
    @Bean
    public ObjectFactoryCreatingFactoryBean serviceFactory() {
        final ObjectFactoryCreatingFactoryBean factory = new ObjectFactoryCreatingFactoryBean();
        factory.setTargetBeanName(BEAN_NAME_VERTX_BASED_COAP_ADAPTER);
        return factory;
    }
}
