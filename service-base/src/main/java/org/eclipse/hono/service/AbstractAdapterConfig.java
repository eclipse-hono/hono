/*******************************************************************************
 * Copyright (c) 2016, 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.service;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.adapter.client.telemetry.EventSender;
import org.eclipse.hono.adapter.client.telemetry.TelemetrySender;
import org.eclipse.hono.adapter.client.telemetry.amqp.ProtonBasedDownstreamSender;
import org.eclipse.hono.cache.CacheProvider;
import org.eclipse.hono.client.BasicDeviceConnectionClientFactory;
import org.eclipse.hono.client.CommandTargetMapper;
import org.eclipse.hono.client.CredentialsClientFactory;
import org.eclipse.hono.client.DeviceConnectionClientFactory;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.ProtocolAdapterCommandConsumerFactory;
import org.eclipse.hono.client.RegistrationClientFactory;
import org.eclipse.hono.client.RequestResponseClientConfigProperties;
import org.eclipse.hono.client.SendMessageSampler;
import org.eclipse.hono.client.TenantClientFactory;
import org.eclipse.hono.config.ApplicationConfigProperties;
import org.eclipse.hono.config.AuthenticatingClientConfigProperties;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.config.ProtocolAdapterProperties;
import org.eclipse.hono.config.ServerConfig;
import org.eclipse.hono.config.VertxProperties;
import org.eclipse.hono.service.cache.SpringCacheProvider;
import org.eclipse.hono.service.monitoring.ConnectionEventProducer;
import org.eclipse.hono.service.monitoring.ConnectionEventProducerConfig;
import org.eclipse.hono.service.monitoring.HonoEventConnectionEventProducer;
import org.eclipse.hono.service.monitoring.LoggingConnectionEventProducer;
import org.eclipse.hono.service.resourcelimits.PrometheusBasedResourceLimitChecks;
import org.eclipse.hono.service.resourcelimits.PrometheusBasedResourceLimitChecksConfig;
import org.eclipse.hono.service.resourcelimits.ResourceLimitChecks;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.DeviceConnectionConstants;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.TelemetryConstants;
import org.eclipse.hono.util.TenantConstants;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;

import com.github.benmanes.caffeine.cache.Caffeine;

import io.opentracing.Tracer;
import io.opentracing.contrib.tracerresolver.TracerResolver;
import io.opentracing.noop.NoopTracerFactory;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

/**
 * Minimum Spring Boot configuration class defining beans required by protocol adapters.
 */
public abstract class AbstractAdapterConfig {

    /**
     * Exposes an OpenTracing {@code Tracer} as a Spring Bean.
     * <p>
     * The Tracer will be resolved by means of a Java service lookup.
     * If no tracer can be resolved this way, the {@code NoopTracer} is
     * returned.
     *
     * @return The tracer.
     */
    @Bean
    public Tracer getTracer() {
        return Optional.ofNullable(TracerResolver.resolveTracer())
                .orElse(NoopTracerFactory.create());
    }

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
     * Gets the name of this protocol adapter.
     * <p>
     * This name will be used as part of the <em>container-id</em> in the AMQP <em>Open</em> frame sent by the
     * clients defined in this adapter config.
     * <p>
     *
     * @return The protocol adapter name.
     */
    protected abstract String getAdapterName();

    /**
     * Creates properties for configuring the Connection Event producer.
     *
     * @return The properties.
     */
    @Bean
    @ConfigurationProperties("hono.connection-events")
    public ConnectionEventProducerConfig connectionEventProducerConfig() {
        return new ConnectionEventProducerConfig();
    }

    /**
     * Configures the Connection Event producer that the adapter should use for reporting
     * devices connecting/disconnecting to/from the adapter.
     *
     * @param config The configuration properties for the producer.
     * @return The producer or {@code null} if the configured producer type is none or unsupported.
     */
    @Bean
    public ConnectionEventProducer connectionEventProducer(final ConnectionEventProducerConfig config) {
        switch (config.getType()) {
        case logging:
            return new LoggingConnectionEventProducer(config);
        case events:
            return new HonoEventConnectionEventProducer();
        default:
            return null;
        }
    }

    /**
     * Exposes configuration properties for accessing the AMQP Messaging Network as a Spring bean.
     * <p>
     * A default set of properties, on top of which the configured properties will by loaded, can be set in subclasses
     * by means of overriding the {@link #getDownstreamSenderFactoryConfigDefaults()} method.
     *
     * @return The properties.
     */
    @Qualifier(Constants.QUALIFIER_MESSAGING)
    @ConfigurationProperties(prefix = "hono.messaging")
    @Bean
    public ClientConfigProperties downstreamSenderFactoryConfig() {
        final ClientConfigProperties config = Optional.ofNullable(getDownstreamSenderFactoryConfigDefaults())
                .orElseGet(ClientConfigProperties::new);
        setConfigServerRoleIfUnknown(config, "AMQP Messaging Network");
        setDefaultConfigNameIfNotSet(config);
        return config;
    }

    /**
     * Gets the default client properties, on top of which the configured properties will be loaded, to be then provided
     * via {@link #downstreamSenderFactoryConfig()}.
     * <p>
     * This method returns an empty set of properties by default. Subclasses may override this method to set specific
     * properties.
     *
     * @return The properties.
     */
    protected ClientConfigProperties getDownstreamSenderFactoryConfigDefaults() {
        return new ClientConfigProperties();
    }

    /**
     * Exposes the connection to the <em>AMQP Messaging Network</em> as a Spring bean.
     * <p>
     * The connection is configured with the properties provided by {@link #downstreamSenderFactoryConfig()}
     * and is already trying to establish the connection to the configured peer.
     *
     * @return The connection.
     */
    @Qualifier(Constants.QUALIFIER_MESSAGING)
    @Bean
    @Scope("prototype")
    public HonoConnection downstreamConnection() {
        return HonoConnection.newConnection(vertx(), downstreamSenderFactoryConfig());
    }

    /**
     * Exposes a client for sending telemetry messages via the <em>AMQP Messaging Network</em> as a Spring bean.
     * <p>
     * The client is initialized with the connection provided by {@link #downstreamConnection()}.
     *
     * @param samplerFactory The sampler factory to use. Can re-use adapter metrics, based on
     *                       {@link org.eclipse.hono.service.metric.MicrometerBasedMetrics}
     *                       out of the box.
     * @param adapterConfig The protocol adapter's configuration properties.
     * @return The factory.
     */
    @Qualifier(TelemetryConstants.TELEMETRY_ENDPOINT)
    @Bean
    @Scope("prototype")
    public TelemetrySender downstreamTelemetrySender(
            final SendMessageSampler.Factory samplerFactory,
            final ProtocolAdapterProperties adapterConfig) {
        return new ProtonBasedDownstreamSender(downstreamConnection(), samplerFactory, adapterConfig);
    }

    /**
     * Exposes a client for sending events via the <em>AMQP Messaging Network</em> as a Spring bean.
     * <p>
     * The client is initialized with the connection provided by {@link #downstreamConnection()}.
     *
     * @param samplerFactory The sampler factory to use. Can re-use adapter metrics, based on
     *                       {@link org.eclipse.hono.service.metric.MicrometerBasedMetrics}
     *                       out of the box.
     * @param adapterConfig The protocol adapter's configuration properties.
     * @return The factory.
     */
    @Qualifier(EventConstants.EVENT_ENDPOINT)
    @Bean
    @Scope("prototype")
    public EventSender downstreamEventSender(
            final SendMessageSampler.Factory samplerFactory,
            final ProtocolAdapterProperties adapterConfig) {
        return new ProtonBasedDownstreamSender(downstreamConnection(), samplerFactory, adapterConfig);
    }

    /**
     * Exposes configuration properties for accessing the registration service as a Spring bean.
     *
     * @return The properties.
     */
    @Qualifier(RegistrationConstants.REGISTRATION_ENDPOINT)
    @ConfigurationProperties(prefix = "hono.registration")
    @Bean
    public RequestResponseClientConfigProperties registrationClientFactoryConfig() {
        final RequestResponseClientConfigProperties config = Optional.ofNullable(getRegistrationClientFactoryConfigDefaults())
                .orElseGet(RequestResponseClientConfigProperties::new);
        setConfigServerRoleIfUnknown(config, "Device Registration");
        setDefaultConfigNameIfNotSet(config);
        return config;
    }

    /**
     * Gets the default client properties, on top of which the configured properties will be loaded, to be then provided
     * via {@link #registrationClientFactoryConfig()}.
     * <p>
     * This method returns an empty set of properties by default. Subclasses may override this method to set specific
     * properties.
     *
     * @return The properties.
     */
    protected RequestResponseClientConfigProperties getRegistrationClientFactoryConfigDefaults() {
        return new RequestResponseClientConfigProperties();
    }

    /**
     * Exposes a factory for creating clients for the <em>Device Registration</em> API as a Spring bean.
     *
     * @param samplerFactory The sampler factory to use.
     * @return The factory.
     */
    @Bean
    @Qualifier(RegistrationConstants.REGISTRATION_ENDPOINT)
    @Scope("prototype")
    public RegistrationClientFactory registrationClientFactory(final SendMessageSampler.Factory samplerFactory) {
        return RegistrationClientFactory.create(registrationServiceConnection(), registrationCacheProvider(), samplerFactory);
    }

    /**
     * Exposes the connection used for accessing the registration service as a Spring bean.
     *
     * @return The connection.
     */
    @Bean
    @Qualifier(RegistrationConstants.REGISTRATION_ENDPOINT)
    @Scope("prototype")
    public HonoConnection registrationServiceConnection() {
        return HonoConnection.newConnection(vertx(), registrationClientFactoryConfig());
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
        return newCaffeineCache(registrationClientFactoryConfig());
    }

    /**
     * Exposes configuration properties for accessing the credentials service as a Spring bean.
     *
     * @return The properties.
     */
    @Qualifier(CredentialsConstants.CREDENTIALS_ENDPOINT)
    @ConfigurationProperties(prefix = "hono.credentials")
    @Bean
    public RequestResponseClientConfigProperties credentialsClientFactoryConfig() {
        final RequestResponseClientConfigProperties config = Optional.ofNullable(getCredentialsClientFactoryConfigDefaults())
                .orElseGet(RequestResponseClientConfigProperties::new);
        setConfigServerRoleIfUnknown(config, "Credentials");
        setDefaultConfigNameIfNotSet(config);
        return config;
    }

    /**
     * Gets the default client properties, on top of which the configured properties will be loaded, to be then provided
     * via {@link #credentialsClientFactoryConfig()}.
     * <p>
     * This method returns an empty set of properties by default. Subclasses may override this method to set specific
     * properties.
     *
     * @return The properties.
     */
    protected RequestResponseClientConfigProperties getCredentialsClientFactoryConfigDefaults() {
        return new RequestResponseClientConfigProperties();
    }

    /**
     * Exposes a factory for creating clients for the <em>Credentials</em> API as a Spring bean.
     *
     * @param samplerFactory The sampler factory to use.
     * @return The factory.
     */
    @Bean
    @Qualifier(CredentialsConstants.CREDENTIALS_ENDPOINT)
    @Scope("prototype")
    public CredentialsClientFactory credentialsClientFactory(final SendMessageSampler.Factory samplerFactory) {
        return CredentialsClientFactory.create(credentialsServiceConnection(), credentialsCacheProvider(), samplerFactory);
    }

    /**
     * Exposes the connection used for accessing the credentials service as a Spring bean.
     *
     * @return The connection.
     */
    @Bean
    @Qualifier(CredentialsConstants.CREDENTIALS_ENDPOINT)
    @Scope("prototype")
    public HonoConnection credentialsServiceConnection() {
        return HonoConnection.newConnection(vertx(), credentialsClientFactoryConfig());
    }

    /**
     * Exposes the provider for caches as a Spring bean.
     *
     * @return The provider instance.
     */
    @Bean
    @Qualifier(CredentialsConstants.CREDENTIALS_ENDPOINT)
    @Scope("prototype")
    public CacheProvider credentialsCacheProvider() {
        return newCaffeineCache(credentialsClientFactoryConfig());
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
        final RequestResponseClientConfigProperties config = Optional.ofNullable(getTenantClientFactoryConfigDefaults())
                .orElseGet(RequestResponseClientConfigProperties::new);
        setConfigServerRoleIfUnknown(config, "Tenant");
        setDefaultConfigNameIfNotSet(config);
        return config;
    }

    /**
     * Gets the default client properties, on top of which the configured properties will be loaded, to be then provided
     * via {@link #tenantServiceClientConfig()}.
     * <p>
     * This method returns an empty set of properties by default. Subclasses may override this method to set specific
     * properties.
     *
     * @return The properties.
     */
    protected RequestResponseClientConfigProperties getTenantClientFactoryConfigDefaults() {
        return new RequestResponseClientConfigProperties();
    }

    /**
     * Exposes a factory for creating clients for the <em>Tenant</em> API as a Spring bean.
     *
     * @param samplerFactory The sampler factory to use.
     * @return The factory.
     */
    @Bean
    @Qualifier(TenantConstants.TENANT_ENDPOINT)
    @Scope("prototype")
    public TenantClientFactory tenantClientFactory(final SendMessageSampler.Factory samplerFactory) {
        return TenantClientFactory.create(tenantServiceConnection(), tenantCacheProvider(), samplerFactory);
    }

    /**
     * Exposes the connection used for accessing the tenant service as a Spring bean.
     *
     * @return The connection.
     */
    @Bean
    @Qualifier(TenantConstants.TENANT_ENDPOINT)
    @Scope("prototype")
    public HonoConnection tenantServiceConnection() {
        return HonoConnection.newConnection(vertx(), tenantServiceClientConfig());
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
        return newCaffeineCache(tenantServiceClientConfig());
    }

    /**
     * Exposes configuration properties for accessing the device connection service as a Spring bean.
     *
     * @return The properties.
     */
    @Bean
    @Qualifier(DeviceConnectionConstants.DEVICE_CONNECTION_ENDPOINT)
    @ConfigurationProperties(prefix = "hono.device-connection")
    @ConditionalOnProperty(prefix = "hono.device-connection", name = "host")
    public RequestResponseClientConfigProperties deviceConnectionServiceClientConfig() {
        final RequestResponseClientConfigProperties config = Optional.ofNullable(getDeviceConnectionClientFactoryConfigDefaults())
                .orElseGet(RequestResponseClientConfigProperties::new);
        setConfigServerRoleIfUnknown(config, "Device Connection");
        setDefaultConfigNameIfNotSet(config);
        return config;
    }

    /**
     * Gets the default client properties, on top of which the configured properties will be loaded, to be then provided
     * via {@link #deviceConnectionServiceClientConfig()}.
     * <p>
     * This method returns an empty set of properties by default. Subclasses may override this method to set specific
     * properties.
     *
     * @return The properties.
     */
    protected RequestResponseClientConfigProperties getDeviceConnectionClientFactoryConfigDefaults() {
        return new RequestResponseClientConfigProperties();
    }

    /**
     * Exposes the connection used for accessing the device connection service as a Spring bean.
     *
     * @return The connection.
     */
    @Bean
    @Qualifier(DeviceConnectionConstants.DEVICE_CONNECTION_ENDPOINT)
    @Scope("prototype")
    @ConditionalOnProperty(prefix = "hono.device-connection", name = "host")
    public HonoConnection deviceConnectionServiceConnection() {
        return HonoConnection.newConnection(vertx(), deviceConnectionServiceClientConfig());
    }

    /**
     * Exposes a factory for creating clients for the <em>Device Connection</em> API as a Spring bean.
     *
     * @param samplerFactory The sampler factory to use.
     * @return The factory.
     */
    @Bean
    @Qualifier(DeviceConnectionConstants.DEVICE_CONNECTION_ENDPOINT)
    @Scope("prototype")
    @ConditionalOnProperty(prefix = "hono.device-connection", name = "host")
    public BasicDeviceConnectionClientFactory deviceConnectionClientFactory(final SendMessageSampler.Factory samplerFactory) {
        return DeviceConnectionClientFactory.create(deviceConnectionServiceConnection(), samplerFactory);
    }

    /**
     * Exposes configuration properties for Command and Control.
     *
     * @return The Properties.
     */
    @Qualifier(CommandConstants.COMMAND_ENDPOINT)
    @ConfigurationProperties(prefix = "hono.command")
    @Bean
    public ClientConfigProperties commandConsumerFactoryConfig() {
        final ClientConfigProperties config = Optional.ofNullable(getCommandConsumerFactoryConfigDefaults())
                .orElseGet(ClientConfigProperties::new);
        setConfigServerRoleIfUnknown(config, "Command & Control");
        setDefaultConfigNameIfNotSet(config);
        return config;
    }

    /**
     * Gets the default client properties, on top of which the configured properties will be loaded, to be then provided
     * via {@link #commandConsumerFactoryConfig()}.
     * <p>
     * This method returns an empty set of properties by default. Subclasses may override this method to set specific
     * properties.
     *
     * @return The properties.
     */
    protected ClientConfigProperties getCommandConsumerFactoryConfigDefaults() {
        return new ClientConfigProperties();
    }

    /**
     * Exposes the connection used for receiving upstream commands as a Spring bean.
     *
     * @return The connection.
     */
    @Bean
    @Scope("prototype")
    public HonoConnection commandConsumerConnection() {
        return HonoConnection.newConnection(vertx(), commandConsumerFactoryConfig());
    }

    /**
     * Exposes a factory for creating clients for receiving upstream commands
     * via the AMQP Messaging Network.
     *
     * @return The factory.
     */
    @Bean
    @Scope("prototype")
    public ProtocolAdapterCommandConsumerFactory commandConsumerFactory() {
        return ProtocolAdapterCommandConsumerFactory.create(commandConsumerConnection());
    }

    /**
     * Exposes the component for mapping an incoming command to the gateway (if applicable)
     * and protocol adapter instance that can handle it.
     *
     * @return The newly created mapper instance.
     */
    @Bean
    @Scope("prototype")
    public CommandTargetMapper commandTargetMapper() {
        return CommandTargetMapper.create(getTracer());
    }

    /**
     * Exposes configuration properties for vert.x.
     *
     * @return The properties.
     */
    @ConfigurationProperties("hono.vertx")
    @Bean
    public VertxProperties vertxProperties() {
        return new VertxProperties();
    }

    private static void setConfigServerRoleIfUnknown(final AuthenticatingClientConfigProperties config,
            final String serverRole) {
        if (config.getServerRole().equals(AuthenticatingClientConfigProperties.SERVER_ROLE_UNKNOWN)) {
            config.setServerRole(serverRole);
        }
    }

    private void setDefaultConfigNameIfNotSet(final ClientConfigProperties config) {
        if (config.getName() == null && getAdapterName() != null) {
            config.setName(getAdapterName());
        }
    }

    /**
     * Create a new cache provider based on Caffeine and Spring Cache.
     *
     * @param config The configuration to use as base for this cache.
     * @return A new cache provider or {@code null} if no cache should be used.
     */
    private static CacheProvider newCaffeineCache(final RequestResponseClientConfigProperties config) {
        return newCaffeineCache(config.getResponseCacheMinSize(), config.getResponseCacheMaxSize());
    }

    /**
     * Create a new cache provider based on Caffeine and Spring Cache.
     *
     * @param minCacheSize The minimum size of the cache.
     * @param maxCacheSize the maximum size of the cache.
     * @return A new cache provider or {@code null} if no cache should be used.
     */
    private static CacheProvider newCaffeineCache(final int minCacheSize, final long maxCacheSize) {

        if (maxCacheSize <= 0) {
            return null;
        }

        final Caffeine<Object, Object> caffeine = Caffeine.newBuilder()
                .initialCapacity(minCacheSize)
                .maximumSize(Math.max(minCacheSize, maxCacheSize));

        final CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setAllowNullValues(false);
        manager.setCaffeine(caffeine);

        return new SpringCacheProvider(manager);
    }

    /**
     * Exposes properties for configuring the application properties as a Spring bean.
     *
     * @return The application configuration properties.
     */
    @Bean
    @ConfigurationProperties(prefix = "hono.app")
    public ApplicationConfigProperties applicationConfigProperties() {
        return new ApplicationConfigProperties();
    }

    /**
     * Exposes properties for configuring the health check as a Spring bean.
     *
     * @return The health check configuration properties.
     */
    @Bean
    @ConfigurationProperties(prefix = "hono.health-check")
    public ServerConfig healthCheckConfigProperties() {
        return new ServerConfig();
    }

    /**
     * Exposes the health check server as a Spring bean.
     *
     * @return The health check server.
     */
    @Bean
    public HealthCheckServer healthCheckServer() {
        return new VertxBasedHealthCheckServer(vertx(), healthCheckConfigProperties());
    }

    /**
     * Exposes configuration properties for ResourceLimitChecks as a Spring bean.
     *
     * @return The properties.
     */
    @Bean
    @ConfigurationProperties(prefix = "hono.resource-limits.prometheus-based")
    @ConditionalOnClass(name = "io.micrometer.prometheus.PrometheusMeterRegistry")
    @ConditionalOnProperty(name = "hono.resource-limits.prometheus-based.host")
    public PrometheusBasedResourceLimitChecksConfig resourceLimitChecksConfig() {
        return new PrometheusBasedResourceLimitChecksConfig();
    }

    /**
     * Creates resource limit checks based on data retrieved from a Prometheus server.
     *
     * @param config The configuration properties for the resource limit checks.
     * @return The resource limit checks.
     * @throws NullPointerException if config is {@code null}.
     */
    @Bean
    @ConditionalOnBean(PrometheusBasedResourceLimitChecksConfig.class)
    public ResourceLimitChecks resourceLimitChecks(final PrometheusBasedResourceLimitChecksConfig config) {

        Objects.requireNonNull(config);
        final Caffeine<Object, Object> builder = Caffeine.newBuilder()
                .initialCapacity(config.getCacheMinSize())
                .maximumSize(config.getCacheMaxSize())
                .expireAfterWrite(Duration.ofSeconds(config.getCacheTimeout()));
        final WebClientOptions webClientOptions = new WebClientOptions();
        webClientOptions.setDefaultHost(config.getHost());
        webClientOptions.setDefaultPort(config.getPort());
        webClientOptions.setTrustOptions(config.getTrustOptions());
        webClientOptions.setKeyCertOptions(config.getKeyCertOptions());
        webClientOptions.setSsl(config.isTlsEnabled());
        return new PrometheusBasedResourceLimitChecks(
                WebClient.create(vertx(), webClientOptions),
                config,
                builder.buildAsync(),
                builder.buildAsync(),
                builder.buildAsync(),
                getTracer());
    }
}
