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

package org.eclipse.hono.service.auth.impl;

import org.eclipse.hono.config.ApplicationConfigProperties;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.config.VertxProperties;
import org.eclipse.hono.service.HealthCheckServer;
import org.eclipse.hono.service.VertxBasedHealthCheckServer;
import org.eclipse.hono.service.auth.AuthTokenHelper;
import org.eclipse.hono.service.auth.AuthTokenHelperImpl;
import org.eclipse.hono.service.metric.MetricsTags;
import org.eclipse.hono.util.AuthenticationConstants;
import org.eclipse.hono.util.Constants;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ObjectFactoryCreatingFactoryBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.spring.autoconfigure.MeterRegistryCustomizer;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;

/**
 * Spring Boot configuration for the simple authentication server application.
 *
 */
@Configuration
public class ApplicationConfig {

    private static final String BEAN_NAME_SIMPLE_AUTHENTICATION_SERVER = "simpleAuthenticationServer";

    /**
     * Exposes a Vert.x instance as a Spring bean.
     * <p>
     * This method creates new Vert.x default options and invokes
     * {@link VertxProperties#configureVertx(VertxOptions)} on the object returned
     * by {@link #vertxProperties()}.
     * 
     * @return The Vert.x instance.
     */
    @Bean
    public Vertx vertx() {
        return Vertx.vertx(vertxProperties().configureVertx(new VertxOptions()));
    }

    /**
     * Exposes configuration properties for Vert.x.
     * 
     * @return The properties.
     */
    @ConfigurationProperties("hono.vertx")
    @Bean
    public VertxProperties vertxProperties() {
        return new VertxProperties();
    }

    /**
     * Creates a new Authentication Server instance and exposes it as a Spring Bean.
     * 
     * @return The new instance.
     */
    @Bean(name = BEAN_NAME_SIMPLE_AUTHENTICATION_SERVER)
    @Scope("prototype")
    public SimpleAuthenticationServer simpleAuthenticationServer(){
        return new SimpleAuthenticationServer();
    }

    /**
     * Exposes a factory for Authentication Server instances as a Spring bean.
     * 
     * @return The factory.
     */
    @Bean
    public ObjectFactoryCreatingFactoryBean authServerFactory() {
        final ObjectFactoryCreatingFactoryBean factory = new ObjectFactoryCreatingFactoryBean();
        factory.setTargetBeanName(BEAN_NAME_SIMPLE_AUTHENTICATION_SERVER);
        return factory;
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
     * Exposes this service's AMQP endpoint configuration properties as a Spring bean.
     * 
     * @return The properties.
     */
    @Bean
    @ConfigurationProperties(prefix = "hono.auth.amqp")
    public ServiceConfigProperties amqpProperties() {
        final ServiceConfigProperties props = new ServiceConfigProperties();
        return props;
    }

    /**
     * Exposes this service's AMQP endpoint configuration properties as a Spring bean.
     * 
     * @return The properties.
     */
    @Bean
    @ConfigurationProperties(prefix = "hono.auth.svc")
    public AuthenticationServerConfigProperties serviceProperties() {
        return new AuthenticationServerConfigProperties();
    }

    /**
     * Exposes a factory for JWTs asserting a client's identity as a Spring bean.
     * 
     * @return The bean.
     */
    @Bean
    @Qualifier("signing")
    public AuthTokenHelper authTokenFactory() {
        final ServiceConfigProperties amqpProps = amqpProperties();
        final AuthenticationServerConfigProperties serviceProps = serviceProperties();
        if (!serviceProps.getSigning().isAppropriateForCreating() && amqpProps.getKeyPath() != null) {
            // fall back to TLS configuration
            serviceProps.getSigning().setKeyPath(amqpProps.getKeyPath());
        }
        return AuthTokenHelperImpl.forSigning(vertx(), serviceProps.getSigning());
    }

    /**
     * Creates a helper for validating JWTs asserting a client's identity and authorities.
     * <p>
     * An instance of this bean is required for the {@code HonoSaslAuthenticationFactory}.
     * 
     * @return The bean.
     */
    @Bean
    @Qualifier(AuthenticationConstants.QUALIFIER_AUTHENTICATION)
    public AuthTokenHelper tokenValidator() {
        final ServiceConfigProperties amqpProps = amqpProperties();
        final AuthenticationServerConfigProperties serviceProps = serviceProperties();
        if (!serviceProps.getValidation().isAppropriateForValidating() && amqpProps.getCertPath() != null) {
            // fall back to TLS configuration
            serviceProps.getValidation().setCertPath(amqpProps.getCertPath());
        }
        return AuthTokenHelperImpl.forValidating(vertx(), serviceProps.getValidation());
    }

    /**
     * Customizer for meter registry.
     * 
     * @return The new meter registry customizer.
     */
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> commonTags() {

        return r -> r.config().commonTags(MetricsTags.forService(Constants.SERVICE_NAME_AUTH));
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
