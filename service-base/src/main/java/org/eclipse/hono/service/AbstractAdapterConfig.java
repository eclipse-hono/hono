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

import org.eclipse.hono.cache.CacheProvider;
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.RequestResponseClientConfigProperties;
import org.eclipse.hono.client.impl.HonoClientImpl;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.service.cache.SpringCacheProvider;
import org.eclipse.hono.service.metric.MetricConfig;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.TenantConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
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
     * Exposes a Vert.x instance as a Spring bean.
     *
     * @return The Vert.x instance.
     */
    @Bean
    public Vertx vertx() {
        final VertxOptions options = new VertxOptions()
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
        final ClientConfigProperties config = new ClientConfigProperties();
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
        return new HonoClientImpl(vertx(), messagingClientConfig());
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
        final RequestResponseClientConfigProperties config = new RequestResponseClientConfigProperties();
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
     * Exposes a client for the <em>Device Registration</em> API as a Spring bean.
     *
     * @return The client.
     */
    @Bean
    @Qualifier(RegistrationConstants.REGISTRATION_ENDPOINT)
    @Scope("prototype")
    public HonoClient registrationServiceClient() {
        final HonoClientImpl result = 
                new HonoClientImpl(vertx(), registrationServiceClientConfig());

        final CacheProvider cacheProvider = registrationCacheProvider();
        if (cacheProvider != null) {
            result.setCacheProvider(cacheProvider);
        }

        return result;
    }

    /**
     * Exposes the provider for caches as a Spring bean.
     * 
     * @return The provider instance.
     */
    @Bean
    @Qualifier(RegistrationConstants.REGISTRATION_ENDPOINT)
    @Scope("prototype")
    public CacheProvider registrationCacheProvider() {
        return newGuavaCache(registrationServiceClientConfig());
    }

    /**
     * Exposes configuration properties for accessing the credentials service as a Spring bean.
     *
     * @return The properties.
     */
    @Qualifier(CredentialsConstants.CREDENTIALS_ENDPOINT)
    @ConfigurationProperties(prefix = "hono.credentials")
    @Bean
    public ClientConfigProperties credentialsServiceClientConfig() {
        final RequestResponseClientConfigProperties config = new RequestResponseClientConfigProperties();
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
     * Exposes a client for the <em>Credentials</em> API as a Spring bean.
     *
     * @return The client.
     */
    @Bean
    @Qualifier(CredentialsConstants.CREDENTIALS_ENDPOINT)
    @Scope("prototype")
    public HonoClient credentialsServiceClient() {
        return new HonoClientImpl(vertx(), credentialsServiceClientConfig());
    }

    /**
     * Exposes configuration properties for accessing the tenant service as a Spring bean.
     *
     * @return The properties.
     */
    @Qualifier(TenantConstants.TENANT_ENDPOINT)
    @ConfigurationProperties(prefix = "hono.tenant")
    @Bean
    public RequestResponseClientConfigProperties tenantServiceClientConfig() {
        final RequestResponseClientConfigProperties config = new RequestResponseClientConfigProperties();
        customizeTenantServiceClientConfig(config);
        return config;
    }

    /**
     * Further customizes the properties provided by the {@link #tenantServiceClientConfig()}
     * method.
     * <p>
     * This method does nothing by default. Subclasses may override this method to set additional
     * properties programmatically.
     *
     * @param config The configuration to customize.
     */
    protected void customizeTenantServiceClientConfig(final RequestResponseClientConfigProperties config) {
        // empty by default
    }

    /**
     * Exposes a client for the <em>Tenant</em> API as a Spring bean.
     *
     * @return The client.
     */
    @Bean
    @Qualifier(TenantConstants.TENANT_ENDPOINT)
    @Scope("prototype")
    public HonoClient tenantServiceClient() {

        final HonoClientImpl result = new HonoClientImpl(vertx(), tenantServiceClientConfig());

        final CacheProvider cacheProvider = tenantCacheProvider();
        if (cacheProvider != null) {
            result.setCacheProvider(cacheProvider);
        }

        return result;
    }

    /**
     * Exposes the provider for caches as a Spring bean.
     * 
     * @return The provider instance.
     */
    @Bean
    @Qualifier(TenantConstants.TENANT_ENDPOINT)
    @Scope("prototype")
    public CacheProvider tenantCacheProvider() {
        return newGuavaCache(tenantServiceClientConfig());
    }

    /**
     * Create a new cache provider based on Guava and Spring Cache.
     * 
     * @param config The configuration to use as base for this cache.
     * @return A new cache provider or {@code null} if no cache should be used.
     */
    private static CacheProvider newGuavaCache(final RequestResponseClientConfigProperties config) {
        final int minCacheSize = config.getResponseCacheMinSize();
        final long maxCacheSize = config.getResponseCacheMaxSize();

        if (maxCacheSize <= 0) {
            return null;
        }

        final CacheBuilder<Object, Object> builder = CacheBuilder.newBuilder()
                .concurrencyLevel(1)
                .initialCapacity(minCacheSize)
                .maximumSize(Math.max(minCacheSize, maxCacheSize));

        final GuavaCacheManager manager = new GuavaCacheManager();
        manager.setAllowNullValues(false);
        manager.setCacheBuilder(builder);

        return new SpringCacheProvider(manager);
    }
}
