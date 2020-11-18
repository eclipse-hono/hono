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

import org.eclipse.hono.adapter.client.command.CommandConsumerFactory;
import org.eclipse.hono.adapter.client.command.CommandResponseSender;
import org.eclipse.hono.adapter.client.command.CommandRouterClient;
import org.eclipse.hono.adapter.client.command.DeviceConnectionClient;
import org.eclipse.hono.adapter.client.command.DeviceConnectionClientAdapter;
import org.eclipse.hono.adapter.client.command.amqp.ProtonBasedCommandConsumerFactory;
import org.eclipse.hono.adapter.client.command.amqp.ProtonBasedCommandResponseSender;
import org.eclipse.hono.adapter.client.command.amqp.ProtonBasedDeviceConnectionClient;
import org.eclipse.hono.adapter.client.registry.CredentialsClient;
import org.eclipse.hono.adapter.client.registry.DeviceRegistrationClient;
import org.eclipse.hono.adapter.client.registry.TenantClient;
import org.eclipse.hono.adapter.client.registry.amqp.ProtonBasedCredentialsClient;
import org.eclipse.hono.adapter.client.registry.amqp.ProtonBasedDeviceRegistrationClient;
import org.eclipse.hono.adapter.client.registry.amqp.ProtonBasedTenantClient;
import org.eclipse.hono.adapter.client.telemetry.EventSender;
import org.eclipse.hono.adapter.client.telemetry.TelemetrySender;
import org.eclipse.hono.adapter.client.telemetry.amqp.ProtonBasedDownstreamSender;
import org.eclipse.hono.adapter.client.telemetry.kafka.KafkaBasedEventSender;
import org.eclipse.hono.adapter.client.telemetry.kafka.KafkaBasedTelemetrySender;
import org.eclipse.hono.cache.CacheProvider;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.RequestResponseClientConfigProperties;
import org.eclipse.hono.client.SendMessageSampler;
import org.eclipse.hono.config.ApplicationConfigProperties;
import org.eclipse.hono.config.AuthenticatingClientConfigProperties;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.config.KafkaProducerConfigProperties;
import org.eclipse.hono.config.ProtocolAdapterProperties;
import org.eclipse.hono.config.ServerConfig;
import org.eclipse.hono.config.VertxProperties;
import org.eclipse.hono.kafka.client.CachingKafkaProducerFactory;
import org.eclipse.hono.service.cache.SpringCacheProvider;
import org.eclipse.hono.service.conditions.ConditionalOnAmqpMessaging;
import org.eclipse.hono.service.conditions.ConditionalOnKafkaMessaging;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;

import com.github.benmanes.caffeine.cache.Caffeine;

import io.opentracing.Tracer;
import io.opentracing.contrib.tracerresolver.TracerResolver;
import io.opentracing.noop.NoopTracerFactory;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

/**
 * Minimum Spring Boot configuration class defining beans required by protocol adapters.
 */
public abstract class AbstractAdapterConfig {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractAdapterConfig.class);

    @Autowired
    private ApplicationContext context;

    /**
     * Sets collaborators required by all protocol adapters.
     *
     * @param adapter The adapter to set the collaborators on.
     * @param adapterProperties The adapter's configuration properties.
     * @param samplerFactory The sampler factory to use.
     * @param resourceLimitChecks The component to use for checking if the adapter's
     *                            resource limits are exceeded.
     * @throws NullPointerException if any of the parameters other than resource limit checks are {@code null}
     */
    protected void setCollaborators(
            final AbstractProtocolAdapterBase<?> adapter,
            final ProtocolAdapterProperties adapterProperties,
            final SendMessageSampler.Factory samplerFactory,
            final Optional<ResourceLimitChecks> resourceLimitChecks) {

        Objects.requireNonNull(adapter);
        Objects.requireNonNull(adapterProperties);
        Objects.requireNonNull(samplerFactory);

        final DeviceRegistrationClient registrationClient = registrationClient(samplerFactory, adapterProperties);
        try {
            // look up client via bean factory in order to take advantage of conditional bean instantiation based
            // on config properties
            final DeviceConnectionClient deviceConnectionClient = context.getBean(DeviceConnectionClient.class);
            adapter.setCommandRouterClient(new DeviceConnectionClientAdapter(deviceConnectionClient));
            adapter.setCommandConsumerFactory(commandConsumerFactory(
                    adapterProperties,
                    samplerFactory,
                    deviceConnectionClient,
                    registrationClient));
        } catch (final BeansException e) {
            // try to look up a Command Router client instead
            // no need to catch BeansException here because startup should fail if neither
            // Device Connection nor Command Router client have been configured anyway
            final CommandRouterClient commandRouterClient = context.getBean(CommandRouterClient.class);
            adapter.setCommandRouterClient(commandRouterClient);
            adapter.setCommandConsumerFactory(commandConsumerFactory(adapterProperties, samplerFactory, commandRouterClient));
        }

        final KafkaProducerConfigProperties kafkaProducerConfig = kafkaProducerConfig();
        if (kafkaProducerConfig.getAtLeastOnceConfig() == null) {
            // look up via bean factory is not possible because EventSender and TelemetrySender are implemented by
            // ProtonBasedDownstreamSender
            adapter.setEventSender(downstreamEventSender(samplerFactory, adapterProperties));
            adapter.setTelemetrySender(downstreamTelemetrySender(samplerFactory, adapterProperties));
        } else {
            final CachingKafkaProducerFactory<String, Buffer> kafkaProducerFactory = kafkaProducerFactory();
            adapter.setEventSender(downstreamEventKafkaSender(kafkaProducerFactory, kafkaProducerConfig));
            adapter.setTelemetrySender(downstreamTelemetryKafkaSender(kafkaProducerFactory, kafkaProducerConfig));
        }

        adapter.setCommandResponseSender(commandResponseSender(samplerFactory, adapterProperties));
        Optional.ofNullable(connectionEventProducer())
            .ifPresent(adapter::setConnectionEventProducer);
        adapter.setCredentialsClient(credentialsClient(samplerFactory, adapterProperties));
        adapter.setHealthCheckServer(healthCheckServer());
        adapter.setRegistrationClient(registrationClient);
        adapter.setTenantClient(tenantClient(samplerFactory, adapterProperties));
        adapter.setTracer(getTracer());
        resourceLimitChecks.ifPresent(adapter::setResourceLimitChecks);
    }

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
     * @return The producer or {@code null} if the configured producer type is none or unsupported.
     */
    @Bean
    public ConnectionEventProducer connectionEventProducer() {
        final ConnectionEventProducerConfig config = connectionEventProducerConfig();
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
    @ConditionalOnAmqpMessaging
    public ClientConfigProperties downstreamSenderFactoryConfig() {
        final ClientConfigProperties config = Optional.ofNullable(getDownstreamSenderFactoryConfigDefaults())
                .orElseGet(ClientConfigProperties::new);
        setConfigServerRoleIfUnknown(config, "AMQP Messaging Network");
        setDefaultConfigNameIfNotSet(config);
        return config;
    }

    /**
     * Exposes configuration properties for accessing the Kafka cluster as a Spring bean.
     *
     * @return The properties.
     */
    @ConfigurationProperties(prefix = "hono.kafka")
    @Bean
    public KafkaProducerConfigProperties kafkaProducerConfig() {
        final KafkaProducerConfigProperties configProperties = new KafkaProducerConfigProperties();
        if (getAdapterName() != null) {
            configProperties.setClientId(getAdapterName());
        }
        return configProperties;
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
    @ConditionalOnAmqpMessaging
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
     * @return The client.
     */
    @Qualifier(TelemetryConstants.TELEMETRY_ENDPOINT)
    @Bean
    @ConditionalOnAmqpMessaging
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
     * @return The client.
     */
    @Qualifier(EventConstants.EVENT_ENDPOINT)
    @Bean
    @ConditionalOnAmqpMessaging
    @Scope("prototype")
    public EventSender downstreamEventSender(
            final SendMessageSampler.Factory samplerFactory,
            final ProtocolAdapterProperties adapterConfig) {
        return new ProtonBasedDownstreamSender(downstreamConnection(), samplerFactory, adapterConfig);
    }

    /**
     * Exposes a factory for creating producers for sending downstream messages via the Kafka cluster.
     *
     * @return The factory.
     */
    @Bean
    @Scope("prototype")
    @ConditionalOnKafkaMessaging
    public CachingKafkaProducerFactory<String, Buffer> kafkaProducerFactory() {
        return CachingKafkaProducerFactory.sharedProducerFactory(vertx());
    }

    /**
     * Exposes a client for sending telemetry messages via <em>Kafka</em> as a Spring bean.
     *
     * @return The client.
     * @param kafkaProducerFactory The producer factory to use.
     * @param kafkaProducerConfig The producer configuration to use.
     */
    @Bean
    @ConditionalOnKafkaMessaging
    @Scope("prototype")
    public TelemetrySender downstreamTelemetryKafkaSender(
            final CachingKafkaProducerFactory<String, Buffer> kafkaProducerFactory,
            final KafkaProducerConfigProperties kafkaProducerConfig) {
        return new KafkaBasedTelemetrySender(kafkaProducerFactory, kafkaProducerConfig, getTracer());
    }

    /**
     * Exposes a client for sending events via <em>Kafka</em> as a Spring bean.
     *
     * @return The client.
     * @param kafkaProducerFactory The producer factory to use.
     * @param kafkaProducerConfig The producer configuration to use.
     */
    @Bean
    @ConditionalOnKafkaMessaging
    @Scope("prototype")
    public EventSender downstreamEventKafkaSender(
            final CachingKafkaProducerFactory<String, Buffer> kafkaProducerFactory,
            final KafkaProducerConfigProperties kafkaProducerConfig) {
        return new KafkaBasedEventSender(kafkaProducerFactory, kafkaProducerConfig, getTracer());
    }

    /**
     * Exposes configuration properties for accessing the registration service as a Spring bean.
     *
     * @return The properties.
     */
    @Qualifier(RegistrationConstants.REGISTRATION_ENDPOINT)
    @ConfigurationProperties(prefix = "hono.registration")
    @Bean
    public RequestResponseClientConfigProperties registrationClientConfig() {
        final RequestResponseClientConfigProperties config = Optional.ofNullable(getRegistrationClientConfigDefaults())
                .orElseGet(RequestResponseClientConfigProperties::new);
        setConfigServerRoleIfUnknown(config, "Device Registration");
        setDefaultConfigNameIfNotSet(config);
        return config;
    }

    /**
     * Gets the default client properties, on top of which the configured properties will be loaded, to be then provided
     * via {@link #registrationClientConfig()}.
     * <p>
     * This method returns an empty set of properties by default. Subclasses may override this method to set specific
     * properties.
     *
     * @return The properties.
     */
    protected RequestResponseClientConfigProperties getRegistrationClientConfigDefaults() {
        return new RequestResponseClientConfigProperties();
    }

    /**
     * Exposes a client for accessing the <em>Device Registration</em> API as a Spring bean.
     *
     * @param samplerFactory The sampler factory to use.
     * @param adapterConfig The protocol adapter's configuration properties.
     * @return The client.
     */
    @Bean
    @Qualifier(RegistrationConstants.REGISTRATION_ENDPOINT)
    @Scope("prototype")
    public DeviceRegistrationClient registrationClient(
            final SendMessageSampler.Factory samplerFactory,
            final ProtocolAdapterProperties adapterConfig) {

        return new ProtonBasedDeviceRegistrationClient(
                registrationServiceConnection(),
                samplerFactory,
                adapterConfig,
                registrationCacheProvider());
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
        return HonoConnection.newConnection(vertx(), registrationClientConfig());
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
        return newCaffeineCache(registrationClientConfig());
    }

    /**
     * Exposes configuration properties for accessing the credentials service as a Spring bean.
     *
     * @return The properties.
     */
    @Qualifier(CredentialsConstants.CREDENTIALS_ENDPOINT)
    @ConfigurationProperties(prefix = "hono.credentials")
    @Bean
    public RequestResponseClientConfigProperties credentialsClientConfig() {
        final RequestResponseClientConfigProperties config = Optional.ofNullable(getCredentialsClientConfigDefaults())
                .orElseGet(RequestResponseClientConfigProperties::new);
        setConfigServerRoleIfUnknown(config, "Credentials");
        setDefaultConfigNameIfNotSet(config);
        return config;
    }

    /**
     * Gets the default client properties, on top of which the configured properties will be loaded, to be then provided
     * via {@link #credentialsClientConfig()}.
     * <p>
     * This method returns an empty set of properties by default. Subclasses may override this method to set specific
     * properties.
     *
     * @return The properties.
     */
    protected RequestResponseClientConfigProperties getCredentialsClientConfigDefaults() {
        return new RequestResponseClientConfigProperties();
    }

    /**
     * Exposes a client for accessing the <em>Credentials</em> API as a Spring bean.
     *
     * @param samplerFactory The sampler factory to use.
     * @param adapterConfig The protocol adapter's configuration properties.
     * @return The client.
     */
    @Bean
    @Qualifier(CredentialsConstants.CREDENTIALS_ENDPOINT)
    @Scope("prototype")
    public CredentialsClient credentialsClient(
            final SendMessageSampler.Factory samplerFactory,
            final ProtocolAdapterProperties adapterConfig) {

        return new ProtonBasedCredentialsClient(
                credentialsServiceConnection(),
                samplerFactory,
                adapterConfig,
                credentialsCacheProvider());
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
        return HonoConnection.newConnection(vertx(), credentialsClientConfig());
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
        return newCaffeineCache(credentialsClientConfig());
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
        final RequestResponseClientConfigProperties config = Optional.ofNullable(getTenantClientConfigDefaults())
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
    protected RequestResponseClientConfigProperties getTenantClientConfigDefaults() {
        return new RequestResponseClientConfigProperties();
    }

    /**
     * Exposes a client for accessing the <em>Tenant</em> API as a Spring bean.
     *
     * @param samplerFactory The sampler factory to use.
     * @param adapterConfig The protocol adapter's configuration properties.
     * @return The client.
     */
    @Bean
    @Qualifier(TenantConstants.TENANT_ENDPOINT)
    @Scope("prototype")
    public TenantClient tenantClient(
            final SendMessageSampler.Factory samplerFactory,
            final ProtocolAdapterProperties adapterConfig) {

        return new ProtonBasedTenantClient(
                tenantServiceConnection(),
                samplerFactory,
                adapterConfig,
                tenantCacheProvider());
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
     * Exposes a client for accessing the <em>Device Connection</em> API as a Spring bean.
     *
     * @param samplerFactory The sampler factory to use.
     * @param adapterConfig The protocol adapter's configuration properties.
     * @return The client.
     */
    @Bean
    @Qualifier(DeviceConnectionConstants.DEVICE_CONNECTION_ENDPOINT)
    @Scope("prototype")
    @ConditionalOnProperty(prefix = "hono.device-connection", name = "host")
    public DeviceConnectionClient deviceConnectionClient(
            final SendMessageSampler.Factory samplerFactory, 
            final ProtocolAdapterProperties adapterConfig) {

        return new ProtonBasedDeviceConnectionClient(
                deviceConnectionServiceConnection(),
                samplerFactory,
                adapterConfig);
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

    CommandConsumerFactory commandConsumerFactory(
            final ProtocolAdapterProperties adapterProperties,
            final SendMessageSampler.Factory samplerFactory,
            final DeviceConnectionClient deviceConnectionClient,
            final DeviceRegistrationClient registrationClient) {

        LOG.debug("using Device Connection service client, configuring CommandConsumerFactory [{}]",
                ProtonBasedCommandConsumerFactory.class.getName());
        return new ProtonBasedCommandConsumerFactory(
                commandConsumerConnection(),
                samplerFactory,
                adapterProperties,
                deviceConnectionClient,
                registrationClient,
                getTracer());
    }

    CommandConsumerFactory commandConsumerFactory(
            final ProtocolAdapterProperties adapterProperties,
            final SendMessageSampler.Factory samplerFactory,
            final CommandRouterClient commandRouterClient) {

        LOG.debug("using Command Router service client, configuring CommandConsumerFactory [unknown]");
        throw new UnsupportedOperationException("not implemented yet");
    }

    /**
     * Exposes a client for sending command response messages downstream.
     *
     * @param samplerFactory The sampler factory to use.
     * @param adapterConfig The protocol adapter's configuration properties.
     * @return The client.
     */
    @Bean
    @Scope("prototype")
    public CommandResponseSender commandResponseSender(
            final SendMessageSampler.Factory samplerFactory,
            final ProtocolAdapterProperties adapterConfig) {

        return new ProtonBasedCommandResponseSender(
                commandConsumerConnection(),
                samplerFactory,
                adapterConfig);
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
     * @return The resource limit checks.
     * @throws NullPointerException if config is {@code null}.
     */
    @Bean
    @ConditionalOnBean(PrometheusBasedResourceLimitChecksConfig.class)
    public ResourceLimitChecks resourceLimitChecks() {

        final PrometheusBasedResourceLimitChecksConfig config = resourceLimitChecksConfig();
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
