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

package org.eclipse.hono.messaging;

import org.eclipse.hono.config.ApplicationConfigProperties;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.config.VertxProperties;
import org.eclipse.hono.connection.ConnectionFactory;
import org.eclipse.hono.connection.impl.ConnectionFactoryImpl;
import org.eclipse.hono.service.HealthCheckServer;
import org.eclipse.hono.service.VertxBasedHealthCheckServer;
import org.eclipse.hono.service.metric.MetricsTags;
import org.eclipse.hono.service.metric.MicrometerBasedMetrics;
import org.eclipse.hono.service.registration.RegistrationAssertionHelper;
import org.eclipse.hono.service.registration.RegistrationAssertionHelperImpl;
import org.eclipse.hono.util.Constants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ObjectFactoryCreatingFactoryBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.spring.autoconfigure.MeterRegistryCustomizer;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.metrics.MetricsOptions;

/**
 * Spring bean definitions required by the Hono application.
 */
@Configuration
public class HonoMessagingApplicationConfig {

    private static final String BEAN_NAME_HONO_MESSAGING = "honoMessaging";

    private MetricsOptions metricsOptions;

    /**
     * Vert.x metrics options, if configured.
     *
     * @param metricsOptions Vert.x metrics options
     */
    @Autowired(required = false)
    public void setMetricsOptions(final MetricsOptions metricsOptions) {
        this.metricsOptions = metricsOptions;
    }

    /**
     * Gets the singleton Vert.x instance to be used by Hono.
     *
     * @return the instance.
     */
    @Bean
    public Vertx vertx() {

        final VertxOptions options = vertxProperties().configureVertx(new VertxOptions());
        configureMetrics(options);
        return Vertx.vertx(options);
    }

    /**
     * Exposes configuration options for vertx.
     * 
     * @return The Properties.
     */
    @ConfigurationProperties("hono.vertx")
    @Bean
    public VertxProperties vertxProperties() {
        return new VertxProperties();
    }

    /**
     * Configure metrics system of vertx.
     * <p>
     * This method will apply the configured metrics options. If no metrics options are configured, then metrics will be
     * enabled without further configuration. When vertx-micrometer support is found, then this will trigger the use of
     * the global {@link MeterRegistry}.
     * </p>
     * 
     * @param options The options object used to configure the vertx instance.
     */
    protected void configureMetrics(final VertxOptions options) {

        if (this.metricsOptions != null) {

            options.setMetricsOptions(this.metricsOptions);

        } else {

            options.setMetricsOptions(
                    new MetricsOptions()
                            .setEnabled(true));

        }
    }

    /**
     * Creates a new Hono Messaging instance and exposes it as a Spring Bean.
     * 
     * @return The new instance.
     */
    @Bean(name = BEAN_NAME_HONO_MESSAGING)
    @Scope("prototype")
    public HonoMessaging honoMessaging() {
        return new HonoMessaging();
    }

    /**
     * Exposes a factory for Hono Messaging instances as a Spring bean.
     * 
     * @return The factory.
     */
    @Bean
    public ObjectFactoryCreatingFactoryBean honoServerFactory() {
        final ObjectFactoryCreatingFactoryBean factory = new ObjectFactoryCreatingFactoryBean();
        factory.setTargetBeanName(BEAN_NAME_HONO_MESSAGING);
        return factory;
    }

    /**
     * Exposes properties for configuring the connection to the downstream
     * AMQP container as a Spring bean.
     * 
     * @return The connection properties.
     */
    @Bean
    @Qualifier(Constants.QUALIFIER_DOWNSTREAM)
    @ConfigurationProperties(prefix = "hono.downstream")
    public ClientConfigProperties downstreamConnectionProperties() {
        final ClientConfigProperties props = new ClientConfigProperties();
        if (props.getAmqpHostname() == null) {
            props.setAmqpHostname("hono-internal");
        }
        return props;
    }

    /**
     * Exposes a factory for connections to the downstream AMQP container
     * as a Spring bean.
     * 
     * @return The connection factory.
     */
    @Bean
    @Qualifier(Constants.QUALIFIER_DOWNSTREAM)
    public ConnectionFactory downstreamConnectionFactory() {
        return new ConnectionFactoryImpl(vertx(), downstreamConnectionProperties());
    }

    /**
     * Exposes properties for configuring the application properties a Spring bean.
     *
     * @return The application configuration properties.
     */
    @Bean
    @ConfigurationProperties(prefix = "hono.app")
    public ApplicationConfigProperties applicationConfigProperties(){
        return new ApplicationConfigProperties();
    }

    /**
     * Exposes properties for configuring the Hono server as a Spring bean.
     * 
     * @return The configuration properties.
     */
    @Bean
    @Qualifier(Constants.QUALIFIER_AMQP)
    @ConfigurationProperties(prefix = "hono.messaging")
    public HonoMessagingConfigProperties honoMessagingProperties() {
        return new HonoMessagingConfigProperties();
    }

    /**
     * Exposes a utility object for validating the signature of JWTs asserting a device's registration status as a Spring bean.
     * 
     * @return The bean.
     */
    @Bean
    @Qualifier("validation")
    public RegistrationAssertionHelper registrationAssertionValidator() {
        final HonoMessagingConfigProperties serviceProps = honoMessagingProperties();
        if (!serviceProps.getValidation().isAppropriateForValidating() && serviceProps.getCertPath() != null) {
            // fall back to TLS configuration
            serviceProps.getValidation().setCertPath(serviceProps.getCertPath());
        }
        return RegistrationAssertionHelperImpl.forValidating(vertx(), serviceProps.getValidation());
    }

    /**
     * Customizer for meter registry.
     * 
     * @return The new meter registry customizer.
     */
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> commonTags() {

        return r -> r.config()
                .commonTags(MetricsTags.forService(Constants.SERVICE_NAME_MESSAGING))
                // Hono Messaging does not have any connections to devices
                .meterFilter(MeterFilter.denyNameStartsWith(MicrometerBasedMetrics.METER_CONNECTIONS_AUTHENTICATED))
                .meterFilter(MeterFilter.denyNameStartsWith(MicrometerBasedMetrics.METER_CONNECTIONS_UNAUTHENTICATED));
    }

    /**
     * Exposes the health check server as a Spring bean.
     *
     * @return The health check server.
     */
    @Bean
    public HealthCheckServer healthCheckServer() {
        return new VertxBasedHealthCheckServer(vertx(), applicationConfigProperties());
    }
}
