/**
 * Copyright (c) 2016, 2018 Red Hat and others.
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
import org.eclipse.hono.client.RequestResponseClientConfigProperties;
import org.eclipse.hono.client.impl.HonoClientImpl;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.connection.ConnectionFactory;
import org.eclipse.hono.connection.ConnectionFactoryImpl;
import org.eclipse.hono.service.metric.MetricConfig;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.RegistrationConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.guava.GuavaCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;

import com.google.common.cache.CacheBuilder;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.dns.AddressResolverOptions;
import io.vertx.core.metrics.MetricsOptions;

/**
 * Minimum Spring Boot configuration class defining beans required by protocol adapters.
 */
public abstract class AbstractAdapterConfig {

    private MetricsOptions metricsOptions;

    /**
     * Vert.x metrics options, if configured
     *
     * @param metricsOptions Vert.x metrics options
     * @see MetricConfig
     */
    @Autowired(required = false)
    public void setMetricsOptions(final MetricsOptions metricsOptions) {
        this.metricsOptions = metricsOptions;
    }

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
        if (metricsOptions != null) {
            options.setMetricsOptions(metricsOptions);
        }
        return Vertx.vertx(options);
    }

    /**
     * Exposes configuration properties for accessing a Hono Messaging service as a Spring bean.
     * <p>
     * The properties can be customized in subclasses by means of overriding the
     * {@link #customizeMessagingClientConfig(ClientConfigProperties)} method.
     *
     * @return The properties.
     */
    @Qualifier(Constants.QUALIFIER_MESSAGING)
    @ConfigurationProperties(prefix = "hono.messaging")
    @Bean
    public ClientConfigProperties messagingClientConfig() {
        ClientConfigProperties config = new ClientConfigProperties();
        customizeMessagingClientConfig(config);
        return config;
    }

    /**
     * Further customizes the client properties provided by the {@link #messagingClientConfig()}
     * method.
     * <p>
     * This method does nothing by default. Subclasses may override this method to set additional
     * properties programmatically.
     *
     * @param config The client configuration to customize.
     */
    protected void customizeMessagingClientConfig(final ClientConfigProperties config) {
        // empty by default
    }

    /**
     * Exposes a factory for connections to the Hono Messaging component
     * as a Spring bean.
     *
     * @return The connection factory.
     */
    @Qualifier(Constants.QUALIFIER_MESSAGING)
    @Bean
    public ConnectionFactory messagingConnectionFactory() {
        return new ConnectionFactoryImpl(vertx(), messagingClientConfig());
    }

    /**
     * Exposes a client for the <em>Hono Messaging</em> component as a Spring bean.
     * <p>
     * The client is configured with the properties provided by {@link #messagingClientConfig()}.
     *
     * @return The client.
     */
    @Qualifier(Constants.QUALIFIER_MESSAGING)
    @Bean
    @Scope("prototype")
    public HonoClient messagingClient() {
        return new HonoClientImpl(vertx(), messagingConnectionFactory(), messagingClientConfig());
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
    public RequestResponseClientConfigProperties registrationServiceClientConfig() {
        RequestResponseClientConfigProperties config = new RequestResponseClientConfigProperties();
        customizeRegistrationServiceClientConfig(config);
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
    protected void customizeRegistrationServiceClientConfig(final RequestResponseClientConfigProperties config) {
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
        final HonoClientImpl result = 
                new HonoClientImpl(vertx(), registrationServiceConnectionFactory(), registrationServiceClientConfig());
        final int minCacheSize = registrationServiceClientConfig().getResponseCacheMinSize();
        final long maxCacheSize = registrationServiceClientConfig().getResponseCacheMaxSize();
        if (maxCacheSize > 0) {
            result.setCacheManager(newCacheManager(minCacheSize, Math.max(minCacheSize, maxCacheSize)));
        }
        return result;
    }

    private CacheManager newCacheManager(final int initialCapacity, final long maxCapacity) {

        final CacheBuilder<Object, Object> builder = CacheBuilder.newBuilder()
                .concurrencyLevel(1)
                .initialCapacity(initialCapacity)
                .maximumSize(maxCapacity);

        final GuavaCacheManager manager = new GuavaCacheManager();
        manager.setAllowNullValues(false);
        manager.setCacheBuilder(builder);
        return manager;
    }

    /**
     * Exposes configuration properties for accessing the credentials service as a Spring bean.
     * <p>
     * Sets the <em>amqpHostname</em> to {@code hono-device-registry} if not set explicitly (reflecting that Hono is
     * providing the credentials API as part of the device registry component).
     *
     * @return The properties.
     */
    @Qualifier(CredentialsConstants.CREDENTIALS_ENDPOINT)
    @ConfigurationProperties(prefix = "hono.credentials")
    @Bean
    public ClientConfigProperties credentialsServiceClientConfig() {
        RequestResponseClientConfigProperties config = new RequestResponseClientConfigProperties();
        customizeCredentialsServiceClientConfig(config);
        return config;
    }

    /**
     * Further customizes the properties provided by the {@link #credentialsServiceClientConfig()}
     * method.
     * <p>
     * This method does nothing by default. Subclasses may override this method to set additional
     * properties programmatically.
     *
     * @param config The configuration to customize.
     */
    protected void customizeCredentialsServiceClientConfig(final RequestResponseClientConfigProperties config) {
        // empty by default
    }

    /**
     * Exposes a factory for connections to the credentials service
     * as a Spring bean.
     *
     * @return The connection factory.
     */
    @Qualifier(CredentialsConstants.CREDENTIALS_ENDPOINT)
    @Bean
    public ConnectionFactory credentialsServiceConnectionFactory() {
        return new ConnectionFactoryImpl(vertx(), credentialsServiceClientConfig());
    }

    /**
     * Exposes a client for the <em>Credentials</em> API as a Spring bean.
     * If no property {@code hono.credentials} is configured, no bean will be created
     * (and internally the registration client bean is used instead).
     *
     * @return The client.
     */
    @Bean
    @Qualifier(CredentialsConstants.CREDENTIALS_ENDPOINT)
    @Scope("prototype")
    public HonoClient credentialsServiceClient() {
        return new HonoClientImpl(vertx(), credentialsServiceConnectionFactory(), credentialsServiceClientConfig());
    }
}
