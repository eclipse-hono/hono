/**
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.service.auth.impl;

import org.eclipse.hono.config.ApplicationConfigProperties;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.service.auth.AuthTokenHelper;
import org.eclipse.hono.service.auth.AuthTokenHelperImpl;
import org.eclipse.hono.util.AuthenticationConstants;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ObjectFactoryCreatingFactoryBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.dns.AddressResolverOptions;
import org.springframework.context.annotation.Scope;

/**
 * Spring Boot configuration for the simple authentication server application.
 *
 */
@Configuration
public class ApplicationConfig {

    private static final String BEAN_NAME_SIMPLE_AUTHENTICATION_SERVER = "simpleAuthenticationServer";

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
        return Vertx.vertx(options);
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
        ObjectFactoryCreatingFactoryBean factory = new ObjectFactoryCreatingFactoryBean();
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
        ServiceConfigProperties props = new ServiceConfigProperties();
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
        ServiceConfigProperties amqpProps = amqpProperties();
        AuthenticationServerConfigProperties serviceProps = serviceProperties();
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
        ServiceConfigProperties amqpProps = amqpProperties();
        AuthenticationServerConfigProperties serviceProps = serviceProperties();
        if (!serviceProps.getValidation().isAppropriateForValidating() && amqpProps.getCertPath() != null) {
            // fall back to TLS configuration
            serviceProps.getValidation().setCertPath(amqpProps.getCertPath());
        }
        return AuthTokenHelperImpl.forValidating(vertx(), serviceProps.getValidation());
    }
}
