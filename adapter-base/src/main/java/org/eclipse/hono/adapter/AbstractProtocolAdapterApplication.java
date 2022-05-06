/**
 * Copyright (c) 2020, 2022 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hono.adapter;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;

import javax.inject.Inject;

import org.eclipse.hono.adapter.monitoring.ConnectionEventProducer;
import org.eclipse.hono.adapter.monitoring.ConnectionEventProducerConfig;
import org.eclipse.hono.adapter.monitoring.ConnectionEventProducerOptions;
import org.eclipse.hono.adapter.monitoring.HonoEventConnectionEventProducer;
import org.eclipse.hono.adapter.monitoring.LoggingConnectionEventProducer;
import org.eclipse.hono.adapter.resourcelimits.ConnectedDevicesAsyncCacheLoader;
import org.eclipse.hono.adapter.resourcelimits.ConnectionDurationAsyncCacheLoader;
import org.eclipse.hono.adapter.resourcelimits.DataVolumeAsyncCacheLoader;
import org.eclipse.hono.adapter.resourcelimits.NoopResourceLimitChecks;
import org.eclipse.hono.adapter.resourcelimits.PrometheusBasedResourceLimitCheckOptions;
import org.eclipse.hono.adapter.resourcelimits.PrometheusBasedResourceLimitChecks;
import org.eclipse.hono.adapter.resourcelimits.PrometheusBasedResourceLimitChecksConfig;
import org.eclipse.hono.adapter.resourcelimits.ResourceLimitChecks;
import org.eclipse.hono.client.amqp.config.ClientConfigProperties;
import org.eclipse.hono.client.amqp.config.ClientOptions;
import org.eclipse.hono.client.amqp.config.RequestResponseClientConfigProperties;
import org.eclipse.hono.client.amqp.config.RequestResponseClientOptions;
import org.eclipse.hono.client.amqp.connection.HonoConnection;
import org.eclipse.hono.client.amqp.connection.SendMessageSampler;
import org.eclipse.hono.client.command.CommandResponseSender;
import org.eclipse.hono.client.command.CommandRouterClient;
import org.eclipse.hono.client.command.CommandRouterCommandConsumerFactory;
import org.eclipse.hono.client.command.amqp.ProtonBasedCommandResponseSender;
import org.eclipse.hono.client.command.amqp.ProtonBasedCommandRouterClient;
import org.eclipse.hono.client.command.amqp.ProtonBasedInternalCommandConsumer;
import org.eclipse.hono.client.command.kafka.KafkaBasedCommandResponseSender;
import org.eclipse.hono.client.command.kafka.KafkaBasedInternalCommandConsumer;
import org.eclipse.hono.client.kafka.CommonKafkaClientOptions;
import org.eclipse.hono.client.kafka.KafkaAdminClientConfigProperties;
import org.eclipse.hono.client.kafka.KafkaAdminClientOptions;
import org.eclipse.hono.client.kafka.consumer.KafkaConsumerOptions;
import org.eclipse.hono.client.kafka.consumer.MessagingKafkaConsumerConfigProperties;
import org.eclipse.hono.client.kafka.metrics.KafkaClientMetricsSupport;
import org.eclipse.hono.client.kafka.metrics.KafkaMetricsOptions;
import org.eclipse.hono.client.kafka.metrics.MicrometerKafkaClientMetricsSupport;
import org.eclipse.hono.client.kafka.metrics.NoopKafkaClientMetricsSupport;
import org.eclipse.hono.client.kafka.producer.CachingKafkaProducerFactory;
import org.eclipse.hono.client.kafka.producer.KafkaProducerFactory;
import org.eclipse.hono.client.kafka.producer.KafkaProducerOptions;
import org.eclipse.hono.client.kafka.producer.MessagingKafkaProducerConfigProperties;
import org.eclipse.hono.client.notification.amqp.ProtonBasedNotificationReceiver;
import org.eclipse.hono.client.notification.kafka.KafkaBasedNotificationReceiver;
import org.eclipse.hono.client.notification.kafka.NotificationKafkaConsumerConfigProperties;
import org.eclipse.hono.client.registry.CredentialsClient;
import org.eclipse.hono.client.registry.DeviceRegistrationClient;
import org.eclipse.hono.client.registry.TenantClient;
import org.eclipse.hono.client.registry.amqp.ProtonBasedCredentialsClient;
import org.eclipse.hono.client.registry.amqp.ProtonBasedDeviceRegistrationClient;
import org.eclipse.hono.client.registry.amqp.ProtonBasedTenantClient;
import org.eclipse.hono.client.telemetry.EventSender;
import org.eclipse.hono.client.telemetry.TelemetrySender;
import org.eclipse.hono.client.telemetry.amqp.ProtonBasedDownstreamSender;
import org.eclipse.hono.client.telemetry.kafka.KafkaBasedEventSender;
import org.eclipse.hono.client.telemetry.kafka.KafkaBasedTelemetrySender;
import org.eclipse.hono.client.util.MessagingClientProvider;
import org.eclipse.hono.client.util.ServiceClient;
import org.eclipse.hono.notification.NotificationConstants;
import org.eclipse.hono.notification.NotificationEventBusSupport;
import org.eclipse.hono.notification.NotificationReceiver;
import org.eclipse.hono.service.AbstractServiceApplication;
import org.eclipse.hono.service.cache.Caches;
import org.eclipse.hono.service.util.ServiceClientAdapter;
import org.eclipse.hono.util.CredentialsObject;
import org.eclipse.hono.util.CredentialsResult;
import org.eclipse.hono.util.MessagingType;
import org.eclipse.hono.util.RegistrationResult;
import org.eclipse.hono.util.TenantObject;
import org.eclipse.hono.util.TenantResult;
import org.eclipse.hono.util.WrappedLifecycleComponentVerticle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.opentracing.Tracer;
import io.smallrye.config.ConfigMapping;
import io.vertx.core.CompositeFuture;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

/**
 * A Quarkus main application base class for Hono protocol adapters.
 * <p>
 * This class provides helper methods for creating clients for Hono's service APIs
 * which to be used with protocol adapter instances.
 *
 * @param <C> The type of configuration properties the adapter uses.
 */
public abstract class AbstractProtocolAdapterApplication<C extends ProtocolAdapterProperties> extends AbstractServiceApplication {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractProtocolAdapterApplication.class);

    /**
     * The OpenTracing tracer to use.
     */
    @Inject
    protected Tracer tracer;

    /**
     * The factory for creating samplers for tracking the sending of AMQP messages.
     */
    @Inject
    protected SendMessageSampler.Factory messageSamplerFactory;

    /**
     * The adapter's configuration properties.
     */
    @Inject
    protected C protocolAdapterProperties;

    /**
     * The configuration to use for Kafka client metrics.
     */
    @Inject
    protected KafkaMetricsOptions kafkaMetricsOptions;

    private ClientConfigProperties commandConsumerConfig;
    private ClientConfigProperties downstreamSenderConfig;
    private RequestResponseClientConfigProperties tenantClientConfig;
    private RequestResponseClientConfigProperties deviceRegistrationClientConfig;
    private RequestResponseClientConfigProperties credentialsClientConfig;
    private RequestResponseClientConfigProperties commandRouterConfig;
    private PrometheusBasedResourceLimitChecksConfig resourceLimitChecksConfig;
    private ConnectionEventProducerConfig connectionEventsConfig;

    private MessagingKafkaProducerConfigProperties kafkaTelemetryConfig;
    private MessagingKafkaProducerConfigProperties kafkaEventConfig;
    private MessagingKafkaProducerConfigProperties kafkaCommandResponseConfig;
    private MessagingKafkaConsumerConfigProperties kafkaCommandConfig;
    private KafkaAdminClientConfigProperties kafkaCommandInternalConfig;
    private NotificationKafkaConsumerConfigProperties kafkaNotificationConfig;

    private Cache<Object, TenantResult<TenantObject>> tenantResponseCache;
    private Cache<Object, RegistrationResult> registrationResponseCache;
    private Cache<Object, CredentialsResult<CredentialsObject>> credentialsResponseCache;

    /**
     * Creates an instance of the protocol adapter.
     *
     * @return The adapter instance.
     */
    protected abstract AbstractProtocolAdapterBase<C> adapter();

    @Inject
    void setCommandConsumerClientOptions(
            @ConfigMapping(prefix = "hono.command")
            final ClientOptions options) {

        final var props = new ClientConfigProperties(options);
        props.setServerRoleIfUnknown("Command & Control");
        props.setNameIfNotSet(getComponentName());
        this.commandConsumerConfig = props;
    }

    @Inject
    void setDownstreamSenderOptions(
            @ConfigMapping(prefix = "hono.messaging")
            final ClientOptions options) {

        final var props = new ClientConfigProperties(options);
        props.setServerRoleIfUnknown("Downstream");
        props.setNameIfNotSet(getComponentName());
        this.downstreamSenderConfig = props;
    }

    @Inject
    void setTenantServiceClientConfig(
            @ConfigMapping(prefix = "hono.tenant")
            final RequestResponseClientOptions options) {
        final var props = new RequestResponseClientConfigProperties(options);
        props.setServerRoleIfUnknown("Tenant");
        props.setNameIfNotSet(getComponentName());
        this.tenantClientConfig = props;
    }

    @Inject
    void setDeviceRegistrationClientConfig(
            @ConfigMapping(prefix = "hono.registration")
            final RequestResponseClientOptions options) {
        final var props = new RequestResponseClientConfigProperties(options);
        props.setServerRoleIfUnknown("Device Registration");
        props.setNameIfNotSet(getComponentName());
        this.deviceRegistrationClientConfig = props;
    }

    @Inject
    void setCredentialsServiceClientConfig(
            @ConfigMapping(prefix = "hono.credentials")
            final RequestResponseClientOptions options) {
        final var props = new RequestResponseClientConfigProperties(options);
        props.setServerRoleIfUnknown("Credentials");
        props.setNameIfNotSet(getComponentName());
        this.credentialsClientConfig = props;
    }

    @Inject
    void setCommandRouterClientConfig(
            @ConfigMapping(prefix = "hono.commandRouter")
            final RequestResponseClientOptions options) {
        final var props = new RequestResponseClientConfigProperties(options);
        props.setServerRoleIfUnknown("Command Router");
        props.setNameIfNotSet(getComponentName());
        this.commandRouterConfig = props;
    }

    @Inject
    void setResourceLimitCheckConfig(final PrometheusBasedResourceLimitCheckOptions options) {
        final var props = new PrometheusBasedResourceLimitChecksConfig(options);
        props.setServerRoleIfUnknown("Prometheus");
        this.resourceLimitChecksConfig = props;
    }

    @Inject
    void setConnectionEventProducerConfig(final ConnectionEventProducerOptions options) {
        this.connectionEventsConfig = new ConnectionEventProducerConfig(options);
    }

    @Inject
    void setTelemetryKafkaClientOptions(
            @ConfigMapping(prefix = "hono.kafka") final CommonKafkaClientOptions commonOptions,
            @ConfigMapping(prefix = "hono.kafka.telemetry") final KafkaProducerOptions telemetryOptions) {

        kafkaTelemetryConfig = new MessagingKafkaProducerConfigProperties(commonOptions, telemetryOptions);
    }

    @Inject
    void setEventKafkaClientOptions(
            @ConfigMapping(prefix = "hono.kafka") final CommonKafkaClientOptions commonOptions,
            @ConfigMapping(prefix = "hono.kafka.event") final KafkaProducerOptions eventOptions) {

        kafkaEventConfig = new MessagingKafkaProducerConfigProperties(commonOptions, eventOptions);
    }

    @Inject
    void setCommandResponseKafkaClientOptions(
            @ConfigMapping(prefix = "hono.kafka") final CommonKafkaClientOptions commonOptions,
            @ConfigMapping(prefix = "hono.kafka.commandResponse") final KafkaProducerOptions commandResponseOptions) {

        kafkaCommandResponseConfig = new MessagingKafkaProducerConfigProperties(commonOptions, commandResponseOptions);
    }

    @Inject
    void setCommandConsumerKafkaClientOptions(
            @ConfigMapping(prefix = "hono.kafka") final CommonKafkaClientOptions commonOptions,
            @ConfigMapping(prefix = "hono.kafka.command") final KafkaConsumerOptions commandOptions) {

        kafkaCommandConfig = new MessagingKafkaConsumerConfigProperties(commonOptions, commandOptions);
    }

    @Inject
    void setCommandInternalKafkaClientOptions(
            @ConfigMapping(prefix = "hono.kafka") final CommonKafkaClientOptions commonOptions,
            @ConfigMapping(prefix = "hono.kafka.commandInternal") final KafkaAdminClientOptions commandInternalOptions) {

        kafkaCommandInternalConfig = new KafkaAdminClientConfigProperties(commonOptions, commandInternalOptions);
    }

    @Inject
    void setNotificationKafkaClientOptions(
            @ConfigMapping(prefix = "hono.kafka") final CommonKafkaClientOptions commonOptions,
            @ConfigMapping(prefix = "hono.kafka.notification") final KafkaConsumerOptions notificationOptions) {

        this.kafkaNotificationConfig = new NotificationKafkaConsumerConfigProperties(
                commonOptions,
                notificationOptions);
    }

    @Override
    protected void doStart() {

        LOG.info("deploying {} {} instances ...", appConfig.getMaxInstances(), getComponentName());

        final Future<String> adapterTracker = vertx.deployVerticle(
                this::adapter,
                new DeploymentOptions().setInstances(appConfig.getMaxInstances()));

        final Future<String> notificationReceiverTracker = vertx.deployVerticle(
                new WrappedLifecycleComponentVerticle(notificationReceiver()));

        CompositeFuture.all(adapterTracker, notificationReceiverTracker)
            .mapEmpty()
            .onComplete(deploymentCheck);
    }

    /**
     * Sets collaborators required by all protocol adapters.
     *
     * @param adapter The adapter to set the collaborators on.
     * @throws NullPointerException if adapter is {@code null}
     * @throws IllegalStateException if no connection to the Command Router service has been configured.
     */
    protected void setCollaborators(final AbstractProtocolAdapterBase<?> adapter) {

        Objects.requireNonNull(adapter);

        final DeviceRegistrationClient registrationClient = registrationClient();

        final var telemetrySenderProvider = new MessagingClientProvider<TelemetrySender>();
        final var eventSenderProvider = new MessagingClientProvider<EventSender>();
        final var commandResponseSenderProvider = new MessagingClientProvider<CommandResponseSender>();
        final var kafkaClientMetricsSupport = kafkaClientMetricsSupport(kafkaMetricsOptions);
        final var tenantClient = tenantClient();

        if (kafkaEventConfig.isConfigured()) {
            LOG.info("Kafka client configuration present, adding Kafka messaging clients");

            final KafkaProducerFactory<String, Buffer> factory = CachingKafkaProducerFactory.sharedFactory(vertx);
            factory.setMetricsSupport(kafkaClientMetricsSupport);

            telemetrySenderProvider.setClient(new KafkaBasedTelemetrySender(
                    vertx,
                    factory,
                    kafkaTelemetryConfig,
                    protocolAdapterProperties.isDefaultsEnabled(),
                    tracer));
            eventSenderProvider.setClient(new KafkaBasedEventSender(
                    vertx,
                    factory,
                    kafkaEventConfig,
                    protocolAdapterProperties.isDefaultsEnabled(),
                    tracer));
            commandResponseSenderProvider.setClient(new KafkaBasedCommandResponseSender(
                    vertx,
                    factory,
                    kafkaCommandResponseConfig,
                    tracer));
        }
        if (downstreamSenderConfig.isHostConfigured()) {
            telemetrySenderProvider.setClient(downstreamSender());
            eventSenderProvider.setClient(downstreamSender());
            commandResponseSenderProvider.setClient(
                    new ProtonBasedCommandResponseSender(
                            HonoConnection.newConnection(vertx, commandResponseSenderConfig(), tracer),
                            messageSamplerFactory,
                            protocolAdapterProperties.isJmsVendorPropsEnabled()));
        }

        final MessagingClientProviders messagingClientProviders = new MessagingClientProviders(
                telemetrySenderProvider,
                eventSenderProvider,
                commandResponseSenderProvider);

        if (commandRouterConfig.isHostConfigured()) {
            final CommandRouterClient commandRouterClient = commandRouterClient();
            adapter.setCommandRouterClient(commandRouterClient);
            final CommandRouterCommandConsumerFactory commandConsumerFactory = commandConsumerFactory(commandRouterClient);
            if (commandConsumerConfig.isHostConfigured()) {
                commandConsumerFactory.registerInternalCommandConsumer(
                        (id, handlers) -> new ProtonBasedInternalCommandConsumer(commandConsumerConnection(), id, handlers));
            }

            final CommandResponseSender kafkaCommandResponseSender = messagingClientProviders
                    .getCommandResponseSenderProvider().getClient(MessagingType.kafka);
            if (kafkaCommandInternalConfig.isConfigured() && kafkaCommandConfig.isConfigured()
                    && kafkaCommandResponseSender != null) {
                commandConsumerFactory.registerInternalCommandConsumer(
                        (id, handlers) -> new KafkaBasedInternalCommandConsumer(
                                vertx,
                                kafkaCommandInternalConfig,
                                kafkaCommandConfig,
                                tenantClient,
                                kafkaCommandResponseSender,
                                id,
                                handlers,
                                tracer)
                            .setMetricsSupport(kafkaClientMetricsSupport));
            }

            adapter.setCommandConsumerFactory(commandConsumerFactory);
        } else {
            throw new IllegalStateException("No Command Router connection configured");
        }

        adapter.setMessagingClientProviders(messagingClientProviders);
        Optional.ofNullable(connectionEventProducer())
            .ifPresent(adapter::setConnectionEventProducer);
        adapter.setCredentialsClient(credentialsClient());
        adapter.setHealthCheckServer(healthCheckServer);
        adapter.setRegistrationClient(registrationClient);
        adapter.setResourceLimitChecks(prometheusResourceLimitChecks(resourceLimitChecksConfig, tenantClient));
        adapter.setTenantClient(tenantClient);
        adapter.setTracer(tracer);
    }

    /**
     * Creates a component that the adapter should use for reporting
     * devices connecting/disconnecting to/from the adapter.
     *
     * @return The component or {@code null} if the configured producer type is <em>none</em> or unsupported.
     */
    protected ConnectionEventProducer connectionEventProducer() {
        switch (connectionEventsConfig.getType()) {
        case LOGGING:
            return new LoggingConnectionEventProducer(connectionEventsConfig);
        case EVENTS:
            return new HonoEventConnectionEventProducer();
        default:
            return null;
        }
    }

    private Cache<Object, TenantResult<TenantObject>> tenantResponseCache() {
        if (tenantResponseCache == null) {
            tenantResponseCache = Caches.newCaffeineCache(tenantClientConfig);
        }
        return tenantResponseCache;
    }

    /**
     * Creates a new client for Hono's Tenant service.
     *
     * @return The client.
     */
    protected TenantClient tenantClient() {
        return new ProtonBasedTenantClient(
                HonoConnection.newConnection(vertx, tenantClientConfig, tracer),
                messageSamplerFactory,
                tenantResponseCache());
    }

    private Cache<Object, RegistrationResult> registrationResponseCache() {
        if (registrationResponseCache == null) {
            registrationResponseCache = Caches.newCaffeineCache(deviceRegistrationClientConfig);
        }
        return registrationResponseCache;
    }

    /**
     * Creates a new client for Hono's Device Registration service.
     *
     * @return The client.
     */
    protected DeviceRegistrationClient registrationClient() {
        return new ProtonBasedDeviceRegistrationClient(
                HonoConnection.newConnection(vertx, deviceRegistrationClientConfig, tracer),
                messageSamplerFactory,
                registrationResponseCache());
    }

    private Cache<Object, CredentialsResult<CredentialsObject>> credentialsResponseCache() {
        if (credentialsResponseCache == null) {
            credentialsResponseCache = Caches.newCaffeineCache(credentialsClientConfig);
        }
        return credentialsResponseCache;
    }

    /**
     * Creates a new client for Hono's Credentials service.
     *
     * @return The client.
     */
    protected CredentialsClient credentialsClient() {
        return new ProtonBasedCredentialsClient(
                HonoConnection.newConnection(vertx, credentialsClientConfig, tracer),
                messageSamplerFactory,
                credentialsResponseCache());
    }

    /**
     * Creates a new client for Hono's Command Router service.
     *
     * @return The client.
     */
    protected CommandRouterClient commandRouterClient() {
        return new ProtonBasedCommandRouterClient(
                HonoConnection.newConnection(vertx, commandRouterConfig, tracer),
                messageSamplerFactory);
    }

    /**
     * Creates a new downstream sender for telemetry and event messages.
     *
     * @return The sender.
     */
    private ProtonBasedDownstreamSender downstreamSender() {
        return new ProtonBasedDownstreamSender(
                HonoConnection.newConnection(vertx, downstreamSenderConfig, tracer),
                messageSamplerFactory,
                protocolAdapterProperties.isDefaultsEnabled(),
                protocolAdapterProperties.isJmsVendorPropsEnabled());
    }

    /**
     * Creates a new connection to the AMQP Messaging Network's Command &amp; Control endpoint.
     *
     * @return The connection.
     */
    protected HonoConnection commandConsumerConnection() {
        return HonoConnection.newConnection(vertx, commandConsumerConfig, tracer);
    }

    /**
     * Creates a new factory for creating command consumers.
     * <p>
     * The returned factory creates consumers that receive commands forwarded by the Command Router.
     * The factory does not support routing commands to target adapter instances.
     *
     * @param commandRouterClient The client for accessing the Command Router service.
     * @return The factory.
     */
    protected CommandRouterCommandConsumerFactory commandConsumerFactory(final CommandRouterClient commandRouterClient) {

        LOG.debug("using Command Router service client, configuring CommandConsumerFactory [{}]",
                CommandRouterCommandConsumerFactory.class.getName());
        return new CommandRouterCommandConsumerFactory(commandRouterClient, getComponentName());
    }

    private ClientConfigProperties commandResponseSenderConfig() {
        final ClientConfigProperties props = new ClientConfigProperties(downstreamSenderConfig);
        props.setServerRole("Command Response");
        return props;
    }

    /**
     * Creates resource limit checks based on data retrieved from a Prometheus server
     * via its HTTP API.
     *
     * @param config The configuration properties.
     * @param tenantClient The client to use for retrieving tenant configuration data.
     * @throws NullPointerException if any of the parameters are {@code null}-
     * @return The checks.
     */
    protected ResourceLimitChecks prometheusResourceLimitChecks(
            final PrometheusBasedResourceLimitChecksConfig config,
            final TenantClient tenantClient) {

        Objects.requireNonNull(config);
        Objects.requireNonNull(tenantClient);

        if (config.isHostConfigured()) {
            final WebClientOptions webClientOptions = new WebClientOptions();
            webClientOptions.setConnectTimeout(config.getConnectTimeout());
            webClientOptions.setDefaultHost(config.getHost());
            webClientOptions.setDefaultPort(config.getPort());
            webClientOptions.setTrustOptions(config.getTrustOptions());
            webClientOptions.setKeyCertOptions(config.getKeyCertOptions());
            webClientOptions.setSsl(config.isTlsEnabled());

            final var webClient = WebClient.create(vertx, webClientOptions);
            final var cacheTimeout = Duration.ofSeconds(config.getCacheTimeout());
            final Caffeine<Object, Object> builder = Caffeine.newBuilder()
                    // make sure we run one Prometheus query at a time
                    .executor(Executors.newSingleThreadExecutor(r -> {
                        final var t = new Thread(r);
                        t.setDaemon(true);
                        return t;
                    }))
                    .initialCapacity(config.getCacheMinSize())
                    .maximumSize(config.getCacheMaxSize())
                    .expireAfterWrite(cacheTimeout)
                    .refreshAfterWrite(cacheTimeout.dividedBy(2));

            return new PrometheusBasedResourceLimitChecks(
                    builder.buildAsync(new ConnectedDevicesAsyncCacheLoader(webClient, config, tracer)),
                    builder.buildAsync(new ConnectionDurationAsyncCacheLoader(webClient, config, tracer)),
                    builder.buildAsync(new DataVolumeAsyncCacheLoader(webClient, config, tracer)),
                    tenantClient,
                    tracer);
        } else {
            return new NoopResourceLimitChecks();
        }
    }

    /**
     * Creates the Kafka metrics support.
     *
     * @param options The Kafka metrics options.
     * @return The Kafka metrics support.
     */
    public KafkaClientMetricsSupport kafkaClientMetricsSupport(final KafkaMetricsOptions options) {
        if (options.enabled()) {
            return new MicrometerKafkaClientMetricsSupport(
                    meterRegistry,
                    options.useDefaultMetrics(),
                    options.metricsPrefixes().orElse(List.of()));
        } else {
            return NoopKafkaClientMetricsSupport.INSTANCE;
        }
    }

    /**
     * Creates the notification receiver.
     *
     * @return The bean instance.
     */
    public NotificationReceiver notificationReceiver() {
        final NotificationReceiver notificationReceiver;
        if (kafkaNotificationConfig.isConfigured()) {
            notificationReceiver = new KafkaBasedNotificationReceiver(vertx, kafkaNotificationConfig);
        } else {
            final ClientConfigProperties notificationConfig = new ClientConfigProperties(downstreamSenderConfig);
            notificationConfig.setServerRole("Notification");
            notificationReceiver = new ProtonBasedNotificationReceiver(HonoConnection.newConnection(vertx, notificationConfig, tracer));
        }
        if (notificationReceiver instanceof ServiceClient serviceClient) {
            healthCheckServer.registerHealthCheckResources(ServiceClientAdapter.forClient(serviceClient));
        }
        final var notificationSender = NotificationEventBusSupport.getNotificationSender(vertx);
        NotificationConstants.DEVICE_REGISTRY_NOTIFICATION_TYPES.forEach(notificationType ->
            notificationReceiver.registerConsumer(notificationType, notificationSender::handle));
        return notificationReceiver;
    }

}
