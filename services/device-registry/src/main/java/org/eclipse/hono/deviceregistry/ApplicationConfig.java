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

import org.eclipse.hono.config.ApplicationConfigProperties;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.service.credentials.CredentialsAmqpEndpoint;
import org.eclipse.hono.service.credentials.CredentialsHttpEndpoint;
import org.eclipse.hono.service.registration.RegistrationAssertionHelper;
import org.eclipse.hono.service.registration.RegistrationAssertionHelperImpl;
import org.eclipse.hono.service.registration.RegistrationHttpEndpoint;
import org.eclipse.hono.service.registration.RegistrationAmqpEndpoint;
import org.eclipse.hono.service.tenant.TenantAmqpEndpoint;
import org.eclipse.hono.service.tenant.TenantHttpEndpoint;
import org.eclipse.hono.util.Constants;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ObjectFactoryCreatingFactoryBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.vertx.core.Vertx;
import org.springframework.context.annotation.Scope;

/**
 * Spring Boot configuration for the Device Registry application.
 *
 */
@Configuration
public class ApplicationConfig {

    private static final String BEAN_NAME_DEVICE_REGISTRY_AMQP_SERVER = "deviceRegistryAmqpServer";
    private static final String BEAN_NAME_DEVICE_REGISTRY_REST_SERVER = "deviceRegistryRestServer";

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
     * Gets general properties for configuring the Device Registry Spring Boot application.
     *
     * @return The properties.
     */
    @Bean
    @ConfigurationProperties(prefix = "hono.app")
    public ApplicationConfigProperties applicationConfigProperties(){
        return new ApplicationConfigProperties();
    }

    /**
     * Gets properties for configuring the Device Registry's AMQP 1.0 endpoint.
     * 
     * @return The properties.
     */
    @Qualifier(Constants.QUALIFIER_AMQP)
    @Bean
    @ConfigurationProperties(prefix = "hono.registry.amqp")
    public ServiceConfigProperties amqpProperties() {
        ServiceConfigProperties props = new ServiceConfigProperties();
        return props;
    }

    /**
     * Creates a new instance of an AMQP 1.0 protocol handler for Hono's <em>Device Registration</em> API.
     * 
     * @return The handler.
     */
    @Bean
    @Scope("prototype")
    public RegistrationAmqpEndpoint registrationAmqpEndpoint() {
        return new RegistrationAmqpEndpoint(vertx());
    }

    /**
     * Creates a new instance of an AMQP 1.0 protocol handler for Hono's <em>Credentials</em> API.
     * 
     * @return The handler.
     */
    @Bean
    @Scope("prototype")
    public CredentialsAmqpEndpoint credentialsAmqpEndpoint() {
        return new CredentialsAmqpEndpoint(vertx());
    }

    /**
     * Creates a new instance of an AMQP 1.0 protocol handler for Hono's <em>Tenant</em> API.
     *
     * @return The handler.
     */
    @Bean
    @Scope("prototype")
    public TenantAmqpEndpoint tenantAmqpEndpoint() {
        return new TenantAmqpEndpoint(vertx());
    }

    /**
     * Creates a new instance of the Device Registry's AMQP 1.0 endpoint.
     * <p>
     * The endpoint is used for accessing both, the <em>Device Registration</em> and the <em>Credentials</em> API.
     * 
     * @return The endpoint.
     */
    @Bean(BEAN_NAME_DEVICE_REGISTRY_AMQP_SERVER)
    @Scope("prototype")
    public DeviceRegistryAmqpServer deviceRegistryAmqpServer(){
        return new DeviceRegistryAmqpServer();
    }

    /**
     * Gets a factory for creating instances of the AMQP 1.0 based endpoint.
     * 
     * @return The factory.
     */
    @Bean
    public ObjectFactoryCreatingFactoryBean deviceRegistryAmqpServerFactory() {
        ObjectFactoryCreatingFactoryBean factory = new ObjectFactoryCreatingFactoryBean();
        factory.setTargetBeanName(BEAN_NAME_DEVICE_REGISTRY_AMQP_SERVER);
        return factory;
    }

    /**
     * Gets properties for configuring the Device Registry's REST endpoint.
     * 
     * @return The properties.
     */
    @Qualifier(Constants.QUALIFIER_REST)
    @Bean
    @ConfigurationProperties(prefix = "hono.registry.rest")
    public ServiceConfigProperties restProperties() {
        ServiceConfigProperties props = new ServiceConfigProperties();
        return props;
    }

    /**
     * Creates a new instance of an HTTP protocol handler for Hono's <em>Device Registration</em> API.
     * 
     * @return The handler.
     */
    @Bean
    @Scope("prototype")
    public RegistrationHttpEndpoint registrationHttpEndpoint() {
        return new RegistrationHttpEndpoint(vertx());
    }

    /**
     * Creates a new instance of an HTTP protocol handler for Hono's <em>Credentials</em> API.
     *
     * @return The handler.
     */
    @Bean
    @Scope("prototype")
    public CredentialsHttpEndpoint credentialsHttpEndpoint() {
        return new CredentialsHttpEndpoint(vertx());
    }

    /**
     * Creates a new instance of an HTTP protocol handler for Hono's <em>Tenant</em> API.
     *
     * @return The handler.
     */
    @Bean
    @Scope("prototype")
    public TenantHttpEndpoint tenantHttpEndpoint() {
        return new TenantHttpEndpoint(vertx());
    }

    /**
     * Creates a new instance of the Device Registry's REST endpoint.
     * <p>
     * The endpoint is used for accessing both, the <em>Device Registration</em> and the <em>Credentials</em> API.
     * 
     * @return The endpoint.
     */
    @Bean(BEAN_NAME_DEVICE_REGISTRY_REST_SERVER)
    @Scope("prototype")
    public DeviceRegistryRestServer deviceRegistryRestServer(){
        return new DeviceRegistryRestServer();
    }

    /**
     * Gets a factory for creating instances of the REST based endpoint.
     * 
     * @return The factory.
     */
    @Bean
    public ObjectFactoryCreatingFactoryBean deviceRegistryRestServerFactory() {
        ObjectFactoryCreatingFactoryBean factory = new ObjectFactoryCreatingFactoryBean();
        factory.setTargetBeanName(BEAN_NAME_DEVICE_REGISTRY_REST_SERVER);
        return factory;
    }

    /**
     * Gets properties for configuring {@code FileBasedRegistrationService} which implements
     * the <em>Device Registration</em> API.
     * 
     * @return The properties.
     */
    @Bean
    @ConfigurationProperties(prefix = "hono.registry.svc")
    public FileBasedRegistrationConfigProperties serviceProperties() {
        return new FileBasedRegistrationConfigProperties();
    }

    /**
     * Gets properties for configuring {@code FileBasedCredentialsService} which implements
     * the <em>Credentials</em> API.
     * 
     * @return The properties.
     */
    @Bean
    @ConfigurationProperties(prefix = "hono.credentials.svc")
    public FileBasedCredentialsConfigProperties credentialsProperties() {
        return new FileBasedCredentialsConfigProperties();
    }

    /**
     * Gets properties for configuring {@code FileBasedTenantsService} which implements
     * the <em>Tenants</em> API.
     *
     * @return The properties.
     */
    @Bean
    @ConfigurationProperties(prefix = "hono.tenant.svc")
    public FileBasedTenantsConfigProperties tenantsProperties() {
        return new FileBasedTenantsConfigProperties();
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
        FileBasedRegistrationConfigProperties serviceProps = serviceProperties();
        if (!serviceProps.getSigning().isAppropriateForCreating() && amqpProps.getKeyPath() != null) {
            // fall back to TLS configuration
            serviceProps.getSigning().setKeyPath(amqpProps.getKeyPath());
        }
        return RegistrationAssertionHelperImpl.forSigning(vertx(), serviceProps.getSigning());
    }
}
