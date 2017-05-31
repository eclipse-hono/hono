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

package org.eclipse.hono.application;

import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.connection.ConnectionFactory;
import org.eclipse.hono.connection.ConnectionFactoryImpl;
import org.eclipse.hono.server.HonoServerConfigProperties;
import org.eclipse.hono.server.HonoServerFactory;
import org.eclipse.hono.service.registration.RegistrationAssertionHelper;
import org.eclipse.hono.service.registration.RegistrationAssertionHelperImpl;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ServiceLocatorFactoryBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.vertx.core.Vertx;

/**
 * Spring bean definitions required by the Hono application.
 */
@Configuration
public class ApplicationConfig {

    private static final Vertx vertx = Vertx.vertx();

    /**
     * Gets the singleton Vert.x instance to be used by Hono.
     * 
     * @return the instance.
     */
    @Bean
    public Vertx vertx() {
        return vertx;
    }

    /**
     * Exposes the {@link HonoServerFactory} as a Spring bean.
     * 
     * @return A Spring service locator for the factory.
     */
    @Bean
    public ServiceLocatorFactoryBean honoServerFactoryLocator() {
        ServiceLocatorFactoryBean bean = new ServiceLocatorFactoryBean();
        bean.setServiceLocatorInterface(HonoServerFactory.class);
        return bean;
    }

    /**
     * Exposes properties for configuring the connection to the downstream
     * AMQP container as a Spring bean.
     * 
     * @return The connection properties.
     */
    @Bean
    @ConfigurationProperties(prefix = "hono.downstream")
    public ClientConfigProperties downstreamConnectionProperties() {
        ClientConfigProperties props = new ClientConfigProperties();
        if (props.getAmqpHostname() == null) {
            props.setAmqpHostname("hono-internal");
        }
        return props;
    }

    /**
     * Exposes properties for configuring the Hono server as a Spring bean.
     * 
     * @return The configuration properties.
     */
    @Bean
    @ConfigurationProperties(prefix = "hono.server")
    public HonoServerConfigProperties honoServerProperties() {
        return new HonoServerConfigProperties();
    }

    /**
     * Exposes a factory for connections to the downstream AMQP container
     * as a Spring bean.
     * 
     * @return The connection factory.
     */
    @Bean
    public ConnectionFactory downstreamConnectionFactory() {
        return new ConnectionFactoryImpl();
    }

    /**
     * Exposes a utility object for validating the signature of JWTs asserting a device's registration status as a Spring bean.
     * 
     * @param honoProps The properties to determine the key material from.
     * @return The bean.
     */
    @Bean
    @Qualifier("validation")
    public RegistrationAssertionHelper registrationAssertionValidator(final HonoServerConfigProperties honoProps) {
        if (!honoProps.getRegistrationAssertion().isAppropriateForValidating() && honoProps.getCertPath() != null) {
            // fall back to TLS configuration
            honoProps.getRegistrationAssertion().setCertPath(honoProps.getCertPath());
        }
        return RegistrationAssertionHelperImpl.forValidating(vertx, honoProps.getRegistrationAssertion());
    }

    /**
     * Exposes a factory for JWTs asserting a device's registration status as a Spring bean.
     * 
     * @param honoProps The properties to determine the key material from.
     * @return The bean.
     */
    @Bean
    @Qualifier("signing")
    public RegistrationAssertionHelper registrationAssertionFactory(final HonoServerConfigProperties honoProps) {
        if (!honoProps.getRegistrationAssertion().isAppropriateForCreating() && honoProps.getKeyPath() != null) {
            // fall back to TLS configuration
            honoProps.getRegistrationAssertion().setKeyPath(honoProps.getKeyPath());
        }
        return RegistrationAssertionHelperImpl.forSigning(vertx, honoProps.getRegistrationAssertion());
    }
}
