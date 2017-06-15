/**
 * Copyright (c) 2016, 2017 Red Hat and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Red Hat - initial creation
 *    Bosch Software Innovations GmbH
 */

package org.eclipse.hono.service;

import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.impl.HonoClientImpl;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.connection.ConnectionFactory;
import org.eclipse.hono.connection.ConnectionFactoryImpl;
import org.eclipse.hono.util.RegistrationConstants;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.dns.AddressResolverOptions;

/**
 * Minimum Spring Boot configuration class defining beans required by protocol adapters.
 */
public abstract class AbstractAdapterConfig {

    /**
     * Exposes a Vert.x instance as a Spring bean.
     * 
     * @return The Vert.x instance.
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
     * Exposes client configuration properties as a Spring bean.
     * <p>
     * Sets the <em>amqpHostname</em> to {@code hono} if not set explicitly.
     * 
     * @return The properties.
     */
    @ConfigurationProperties(prefix = "hono.client")
    @Bean
    public ClientConfigProperties honoClientConfig() {
        ClientConfigProperties config = new ClientConfigProperties();
        if (config.getAmqpHostname() == null) {
            config.setAmqpHostname("hono");
        }
        customizeClientConfigProperties(config);
        return config;
    }

    /**
     * Further customizes the client properties provided by the {@link #honoClientConfig()}
     * method.
     * <p>
     * This method does nothing by default. Subclasses may override this method to set additional
     * properties programmatically.
     * 
     * @param config The client configuration to customize.
     */
    protected void customizeClientConfigProperties(final ClientConfigProperties config) {
        // empty by default
    }

    /**
     * Exposes a factory for connections to the Hono server
     * as a Spring bean.
     * 
     * @return The connection factory.
     */
    @Bean
    public ConnectionFactory honoConnectionFactory() {
        return new ConnectionFactoryImpl(vertx(), honoClientConfig());
    }

    /**
     * Exposes a Hono client as a Spring bean.
     * <p>
     * The client is configured with the properties provided by {@link #honoClientConfig()}.
     * 
     * @return The client.
     */
    @Bean
    @Scope("prototype")
    public HonoClient honoClient() {
        return new HonoClientImpl(vertx(), honoConnectionFactory());
    }

    /**
     * Exposes configuration properties for accessing the registration service as a Spring bean.
     * <p>
     * Sets the <em>amqpHostname</em> to {@code hono-device-registry} if not set explicitly.
     *
     * @return The properties.
     */
    @Qualifier(RegistrationConstants.REGISTRATION_ENDPOINT)
    @ConfigurationProperties(prefix = "hono.registration")
    @Bean
    public ClientConfigProperties registrationServiceClientConfig() {
        ClientConfigProperties config = new ClientConfigProperties();
        if (config.getAmqpHostname() == null) {
            config.setAmqpHostname("hono-device-registry");
        }
        customizeRegistrationServiceClientConfigProperties(config);
        return config;
    }

    /**
     * Further customizes the properties provided by the {@link #registrationServiceClientConfig()}
     * method.
     * <p>
     * This method does nothing by default. Subclasses may override this method to set additional
     * properties programmatically.
     *
     * @param config The configuration to customize.
     */
    protected void customizeRegistrationServiceClientConfigProperties(final ClientConfigProperties config) {
        // empty by default
    }

    /**
     * Exposes a factory for connections to the registration service 
     * as a Spring bean.
     *
     * @return The connection factory.
     */
    @Qualifier(RegistrationConstants.REGISTRATION_ENDPOINT)
    @Bean
    public ConnectionFactory registrationServiceConnectionFactory() {
        return new ConnectionFactoryImpl(vertx(), registrationServiceClientConfig());
    }

    /**
     * Exposes a client for the <em>Device Registration</em> API as a Spring bean.
     *
     * @return The client.
     */
    @Bean
    @Qualifier(RegistrationConstants.REGISTRATION_ENDPOINT)
    @Scope("prototype")
    public HonoClient registrationServiceClient() {
        return new HonoClientImpl(vertx(), registrationServiceConnectionFactory());
    }
}
