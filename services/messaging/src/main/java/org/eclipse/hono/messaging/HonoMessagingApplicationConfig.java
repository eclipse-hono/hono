/**
 * Copyright (c) 2016, 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.messaging;

import org.eclipse.hono.config.ApplicationConfigProperties;
import org.eclipse.hono.connection.ConnectionFactory;
import org.eclipse.hono.connection.ConnectionFactoryImpl;
import org.eclipse.hono.service.metric.MetricConfig;
import org.eclipse.hono.service.registration.RegistrationAssertionHelper;
import org.eclipse.hono.service.registration.RegistrationAssertionHelperImpl;
import org.eclipse.hono.util.Constants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ObjectFactoryCreatingFactoryBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.dns.AddressResolverOptions;
import io.vertx.core.metrics.MetricsOptions;
import org.springframework.context.annotation.Scope;

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
     * @see MetricConfig
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
        VertxOptions options = new VertxOptions()
                .setWarningExceptionTime(1500000000)
                .setAddressResolverOptions(new AddressResolverOptions()
                        .setCacheNegativeTimeToLive(0) // discard failed DNS lookup results immediately
                        .setCacheMaxTimeToLive(0) // support DNS based service resolution
                        .setQueryTimeout(1000));
        if (metricsOptions != null) {
            options.setMetricsOptions(metricsOptions);
        }
        return Vertx.vertx(options);
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
        ObjectFactoryCreatingFactoryBean factory = new ObjectFactoryCreatingFactoryBean();
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
    @ConfigurationProperties(prefix = "hono.downstream")
    public DownstreamClientConfigProperties downstreamConnectionProperties() {
        DownstreamClientConfigProperties props = new DownstreamClientConfigProperties();
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
        HonoMessagingConfigProperties serviceProps = honoMessagingProperties();
        if (!serviceProps.getValidation().isAppropriateForValidating() && serviceProps.getCertPath() != null) {
            // fall back to TLS configuration
            serviceProps.getValidation().setCertPath(serviceProps.getCertPath());
        }
        return RegistrationAssertionHelperImpl.forValidating(vertx(), serviceProps.getValidation());
    }
}
