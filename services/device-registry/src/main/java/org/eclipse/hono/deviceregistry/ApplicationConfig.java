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

package org.eclipse.hono.deviceregistry;

import io.vertx.core.VertxOptions;
import io.vertx.core.dns.AddressResolverOptions;

import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.service.credentials.CredentialsEndpoint;
import org.eclipse.hono.service.registration.RegistrationAssertionHelper;
import org.eclipse.hono.service.registration.RegistrationAssertionHelperImpl;
import org.eclipse.hono.service.registration.RegistrationEndpoint;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ObjectFactoryCreatingFactoryBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.vertx.core.Vertx;
import org.springframework.context.annotation.Scope;

/**
 * Spring Boot configuration for the simple device registry server application.
 *
 */
@Configuration
public class ApplicationConfig {

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
     * Exposes a factory for creating service instances as a Spring bean.
     * 
     * @return The factory.
     */
    @Bean
    public ObjectFactoryCreatingFactoryBean deviceRegistryServerFactory() {
        ObjectFactoryCreatingFactoryBean factory = new ObjectFactoryCreatingFactoryBean();
        factory.setTargetBeanName(DeviceRegistryAmqpServer.class.getName());
        return factory;
    }

    /**
     * Exposes this service's AMQP endpoint configuration properties as a Spring bean.
     * 
     * @return The properties.
     */
    @Qualifier("amqp")
    @Bean
    @ConfigurationProperties(prefix = "hono.registry.amqp")
    public ServiceConfigProperties amqpProperties() {
        ServiceConfigProperties props = new ServiceConfigProperties();
        return props;
    }

    /**
     * Exposes the Device Registration service's configuration properties as a Spring bean.
     * 
     * @return The properties.
     */
    @Bean
    @ConfigurationProperties(prefix = "hono.registry.svc")
    public DeviceRegistryConfigProperties serviceProperties() {
        return new DeviceRegistryConfigProperties();
    }

    /**
     * Exposes the Credentials service's configuration properties as a Spring bean.
     * 
     * @return The properties.
     */
    @Bean
    @ConfigurationProperties(prefix = "hono.credentials.svc")
    public CredentialsConfigProperties credentialsProperties() {
        return new CredentialsConfigProperties();
    }

    /**
     * Exposes a factory for JWTs asserting a device's registration status as a Spring bean.
     *
     * @return The bean.
     */
    @Bean
    @Qualifier("signing")
    public RegistrationAssertionHelper registrationAssertionFactory() {
        ServiceConfigProperties amqpProps = amqpProperties();
        DeviceRegistryConfigProperties serviceProps = serviceProperties();
        if (!serviceProps.getSigning().isAppropriateForCreating() && amqpProps.getKeyPath() != null) {
            // fall back to TLS configuration
            serviceProps.getSigning().setKeyPath(amqpProps.getKeyPath());
        }
        return RegistrationAssertionHelperImpl.forSigning(vertx(), serviceProps.getSigning());
    }

    /**
     * Expose Hono's <a href="https://www.eclipse.org/hono/api/Device-Registration-API/">Device Registration API</a> endpoint as a Spring bean.
     * 
     * @return The endpoint.
     */
    @Bean
    @Scope("prototype")
    public RegistrationEndpoint registrationEndpoint() {
        return new RegistrationEndpoint(vertx());
    }

    /**
     * Expose Hono's <a href="https://www.eclipse.org/hono/api/Credentials-API/">Credentials API</a> endpoint as a Spring bean.
     * 
     * @return The endpoint.
     */
    @Bean
    @Scope("prototype")
    public CredentialsEndpoint credentialsEndpoint() {
        return new CredentialsEndpoint(vertx());
    }
}
