/**
 * Copyright (c) 2021, 2023 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.commandrouter.app;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.eclipse.hono.client.amqp.config.ClientConfigProperties;
import org.eclipse.hono.client.amqp.config.ClientOptions;
import org.eclipse.hono.client.amqp.config.RequestResponseClientConfigProperties;
import org.eclipse.hono.client.amqp.config.RequestResponseClientOptions;
import org.eclipse.hono.client.amqp.connection.HonoConnection;
import org.eclipse.hono.client.amqp.connection.SendMessageSampler;
import org.eclipse.hono.client.kafka.CommonKafkaClientOptions;
import org.eclipse.hono.client.kafka.KafkaAdminClientConfigProperties;
import org.eclipse.hono.client.kafka.KafkaAdminClientOptions;
import org.eclipse.hono.client.kafka.consumer.KafkaConsumerOptions;
import org.eclipse.hono.client.kafka.consumer.MessagingKafkaConsumerConfigProperties;
import org.eclipse.hono.client.kafka.metrics.KafkaClientMetricsSupport;
import org.eclipse.hono.client.kafka.producer.CachingKafkaProducerFactory;
import org.eclipse.hono.client.kafka.producer.KafkaProducerOptions;
import org.eclipse.hono.client.kafka.producer.MessagingKafkaProducerConfigProperties;
import org.eclipse.hono.client.notification.kafka.NotificationKafkaConsumerConfigProperties;
import org.eclipse.hono.client.pubsub.PubSubConfigProperties;
import org.eclipse.hono.client.pubsub.PubSubMessageHelper;
import org.eclipse.hono.client.pubsub.PubSubPublisherOptions;
import org.eclipse.hono.client.pubsub.publisher.CachingPubSubPublisherFactory;
import org.eclipse.hono.client.pubsub.subscriber.CachingPubSubSubscriberFactory;
import org.eclipse.hono.client.registry.DeviceRegistrationClient;
import org.eclipse.hono.client.registry.TenantClient;
import org.eclipse.hono.client.registry.amqp.ProtonBasedDeviceRegistrationClient;
import org.eclipse.hono.client.registry.amqp.ProtonBasedTenantClient;
import org.eclipse.hono.client.telemetry.EventSender;
import org.eclipse.hono.client.telemetry.amqp.ProtonBasedDownstreamSender;
import org.eclipse.hono.client.telemetry.kafka.KafkaBasedEventSender;
import org.eclipse.hono.client.telemetry.pubsub.PubSubBasedDownstreamSender;
import org.eclipse.hono.client.util.MessagingClientProvider;
import org.eclipse.hono.commandrouter.AdapterInstanceStatusService;
import org.eclipse.hono.commandrouter.CommandConsumerFactory;
import org.eclipse.hono.commandrouter.CommandRouterAmqpServer;
import org.eclipse.hono.commandrouter.CommandRouterMetrics;
import org.eclipse.hono.commandrouter.CommandRouterService;
import org.eclipse.hono.commandrouter.CommandTargetMapper;
import org.eclipse.hono.commandrouter.impl.CommandRouterServiceImpl;
import org.eclipse.hono.commandrouter.impl.DelegatingCommandRouterAmqpEndpoint;
import org.eclipse.hono.commandrouter.impl.UnknownStatusProvidingService;
import org.eclipse.hono.commandrouter.impl.amqp.ProtonBasedCommandConsumerFactoryImpl;
import org.eclipse.hono.commandrouter.impl.kafka.InternalKafkaTopicCleanupService;
import org.eclipse.hono.commandrouter.impl.kafka.KafkaBasedCommandConsumerFactoryImpl;
import org.eclipse.hono.commandrouter.impl.pubsub.PubSubBasedCommandConsumerFactoryImpl;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.config.ServiceOptions;
import org.eclipse.hono.deviceconnection.infinispan.client.DeviceConnectionInfo;
import org.eclipse.hono.service.HealthCheckProvider;
import org.eclipse.hono.service.NotificationSupportingServiceApplication;
import org.eclipse.hono.service.amqp.AmqpEndpoint;
import org.eclipse.hono.service.auth.AuthenticationService;
import org.eclipse.hono.service.cache.Caches;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.RegistrationResult;
import org.eclipse.hono.util.TenantObject;
import org.eclipse.hono.util.TenantResult;
import org.eclipse.hono.util.WrappedLifecycleComponentVerticle;
import org.eclipse.microprofile.health.Readiness;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.benmanes.caffeine.cache.Cache;

import io.smallrye.config.ConfigMapping;
import io.smallrye.health.api.HealthRegistry;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Verticle;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.proton.sasl.ProtonSaslAuthenticatorFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * The Quarkus based Command Router main application class.
 */
@ApplicationScoped
public class Application extends NotificationSupportingServiceApplication {

    private static final String COMPONENT_NAME = "Hono Command Router";
    private static final Logger LOG = LoggerFactory.getLogger(Application.class);

    @Inject
    DeviceConnectionInfo deviceConnectionInfo;

    @Inject
    ProtonSaslAuthenticatorFactory saslAuthenticatorFactory;

    @Inject
    AuthenticationService authenticationService;

    @Inject
    AdapterInstanceStatusService adapterInstanceStatusService;

    @Inject
    CommandRouterMetrics metrics;

    @Inject
    KafkaClientMetricsSupport kafkaClientMetricsSupport;

    @Readiness
    @Inject
    HealthRegistry readinessChecks;

    private ServiceConfigProperties amqpServerProperties;
    private ClientConfigProperties commandConsumerConnectionConfig;
    private RequestResponseClientConfigProperties deviceRegistrationClientConfig;
    private ClientConfigProperties downstreamSenderConfig;
    private RequestResponseClientConfigProperties tenantClientConfig;
    private MessagingKafkaProducerConfigProperties commandInternalKafkaProducerConfig;
    private MessagingKafkaProducerConfigProperties commandResponseKafkaProducerConfig;
    private MessagingKafkaConsumerConfigProperties kafkaConsumerConfig;
    private MessagingKafkaProducerConfigProperties kafkaEventConfig;
    private KafkaAdminClientConfigProperties kafkaAdminClientConfig;
    private NotificationKafkaConsumerConfigProperties kafkaNotificationConfig;

    private Cache<Object, RegistrationResult> registrationResponseCache;
    private Cache<Object, TenantResult<TenantObject>> tenantResponseCache;

    private PubSubConfigProperties pubSubConfigProperties;

    @Inject
    void setPubSubClientOptions(final PubSubPublisherOptions options) {
        this.pubSubConfigProperties = new PubSubConfigProperties(options);
    }

    @Inject
    void setAmqpServerOptions(
            @ConfigMapping(prefix = "hono.commandRouter.amqp")
            final ServiceOptions options) {
        this.amqpServerProperties = new ServiceConfigProperties(options);
    }

    @Inject
    void setCommandClientOptions(
            @ConfigMapping(prefix = "hono.command")
            final ClientOptions options) {

        final var props = new ClientConfigProperties(options);
        props.setServerRoleIfUnknown("Command & Control");
        props.setNameIfNotSet(getComponentName());
        this.commandConsumerConnectionConfig = props;
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
    void setCommandInternalKafkaClientOptions(
            @ConfigMapping(prefix = "hono.kafka")
            final CommonKafkaClientOptions commonOptions,
            @ConfigMapping(prefix = "hono.kafka.commandInternal")
            final KafkaProducerOptions commandInternalProducerOptions) {

        this.commandInternalKafkaProducerConfig = new MessagingKafkaProducerConfigProperties(
                commonOptions,
                commandInternalProducerOptions);
    }

    @Inject
    void setCommandResponseKafkaClientOptions(
            @ConfigMapping(prefix = "hono.kafka")
            final CommonKafkaClientOptions commonOptions,
            @ConfigMapping(prefix = "hono.kafka.commandResponse")
            final KafkaProducerOptions commandResponseProducerOptions) {

        this.commandResponseKafkaProducerConfig = new MessagingKafkaProducerConfigProperties(
                commonOptions,
                commandResponseProducerOptions);
    }

    @Inject
    void setCommandConsumerKafkaClientOptions(
            @ConfigMapping(prefix = "hono.kafka")
            final CommonKafkaClientOptions commonOptions,
            @ConfigMapping(prefix = "hono.kafka.command")
            final KafkaConsumerOptions consumerOptions) {

        this.kafkaConsumerConfig = new MessagingKafkaConsumerConfigProperties(commonOptions, consumerOptions);
    }

    @Inject
    void setEventKafkaClientOptions(
            @ConfigMapping(prefix = "hono.kafka")
            final CommonKafkaClientOptions commonOptions,
            @ConfigMapping(prefix = "hono.kafka.event")
            final KafkaProducerOptions eventOptions) {

        this.kafkaEventConfig = new MessagingKafkaProducerConfigProperties(commonOptions, eventOptions);
    }

    @Inject
    void setAdminKafkaClientOptions(
            @ConfigMapping(prefix = "hono.kafka") final CommonKafkaClientOptions commonOptions,
            @ConfigMapping(prefix = "hono.kafka.cleanup") final KafkaAdminClientOptions adminClientOptions) {

        this.kafkaAdminClientConfig = new KafkaAdminClientConfigProperties(commonOptions, adminClientOptions);
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
    public String getComponentName() {
        return COMPONENT_NAME;
    }

    @Override
    protected void doStart() {

        if (!(authenticationService instanceof Verticle)) {
            throw new IllegalStateException("Authentication service must be a vert.x Verticle");
        }

        final var instancesToDeploy = appConfig.getMaxInstances();
        LOG.info("deploying {} {} instances ...", instancesToDeploy, getComponentName());
        final Map<String, String> deploymentResult = new HashMap<>();

        // deploy authentication service (once only)
        final Future<String> authServiceDeploymentTracker = vertx.deployVerticle((Verticle) authenticationService)
                .onSuccess(ok -> {
                    LOG.info("successfully deployed authentication service verticle");
                    deploymentResult.put("authentication service verticle", "successfully deployed");
                    registerHealthCheckProvider(authenticationService);
                });

        // deploy AMQP 1.0 server
        final Future<String> amqpServerDeploymentTracker = vertx.deployVerticle(
                this::amqpServer,
                new DeploymentOptions().setInstances(instancesToDeploy))
            .onSuccess(ok -> {
                LOG.info("successfully deployed AMQP server verticle(s)");
                deploymentResult.put("AMQP server verticle(s)", "successfully deployed");
            });

        // deploy notification receiver
        final var notificationReceiver = notificationReceiver(kafkaNotificationConfig, commandConsumerConnectionConfig, pubSubConfigProperties);
        final Future<String> notificationReceiverTracker = vertx.deployVerticle(
                new WrappedLifecycleComponentVerticle(notificationReceiver))
            .onSuccess(ok -> {
                LOG.info("successfully deployed notification receiver verticle(s)");
                deploymentResult.put("notification receiver verticle", "successfully deployed");
            });

        // deploy Kafka topic clean-up service (once only)
        final Future<String> topicCleanUpServiceDeploymentTracker = createKafkaTopicCleanUpService()
                .map(service -> vertx.deployVerticle(service)
                        .onSuccess(ok -> {
                            LOG.info("successfully deployed Kafka topic clean-up service verticle");
                            deploymentResult.put("Kafka topic clean-up service verticle", "successfully deployed");
                            readinessChecks.register(service::checkReadiness);
                        }))
                .orElse(Future.succeededFuture());

        Future.all(
                authServiceDeploymentTracker,
                amqpServerDeploymentTracker,
                notificationReceiverTracker,
                topicCleanUpServiceDeploymentTracker)
            .map(deploymentResult)
            .onComplete(deploymentCheck);
    }

    private CommandRouterAmqpServer amqpServer() {
        final var server = new CommandRouterAmqpServer();
        server.setConfig(amqpServerProperties);
        server.setHealthCheckServer(healthCheckServer);
        server.setSaslAuthenticatorFactory(saslAuthenticatorFactory);
        server.setTracer(tracer);
        server.addEndpoint(commandRouterAmqpEndpoint());

        return server;
    }

    private AmqpEndpoint commandRouterAmqpEndpoint() {

        final var service = commandRouterService();
        final var endpoint = new DelegatingCommandRouterAmqpEndpoint<>(vertx, service) {

            @Override
            public void registerLivenessChecks(final HealthCheckHandler handler) {
                if (service instanceof HealthCheckProvider provider) {
                    provider.registerLivenessChecks(handler);
                }
            }

            @Override
            public void registerReadinessChecks(final HealthCheckHandler handler) {
                if (service instanceof HealthCheckProvider provider) {
                    provider.registerReadinessChecks(handler);
                }
            }
        };
        endpoint.setConfiguration(amqpServerProperties);
        endpoint.setTracer(tracer);
        return endpoint;
    }

    private CommandRouterService commandRouterService() {
        final DeviceRegistrationClient registrationClient = registrationClient();
        final TenantClient tenantClient = tenantClient();

        final var commandTargetMapper = CommandTargetMapper.create(registrationClient, deviceConnectionInfo, tracer);
        return new CommandRouterServiceImpl(
                amqpServerProperties,
                registrationClient,
                tenantClient,
                deviceConnectionInfo,
                commandConsumerFactoryProvider(tenantClient, commandTargetMapper),
                eventSenderProvider(),
                adapterInstanceStatusService,
                tracer);
    }

    private Optional<InternalKafkaTopicCleanupService> createKafkaTopicCleanUpService() {
        if (!appConfig.isKafkaMessagingDisabled() && kafkaAdminClientConfig.isConfigured()
                && !(adapterInstanceStatusService instanceof UnknownStatusProvidingService)) {
            return Optional.of(new InternalKafkaTopicCleanupService(
                    adapterInstanceStatusService,
                    kafkaAdminClientConfig));
        } else {
            return Optional.empty();
        }
    }

    private MessagingClientProvider<CommandConsumerFactory> commandConsumerFactoryProvider(
            final TenantClient tenantClient,
            final CommandTargetMapper commandTargetMapper) {

        final var commandConsumerFactoryProvider = new MessagingClientProvider<CommandConsumerFactory>();

        if (!appConfig.isKafkaMessagingDisabled() && kafkaConsumerConfig.isConfigured()
                && commandResponseKafkaProducerConfig.isConfigured()
                && commandInternalKafkaProducerConfig.isConfigured()) {

            final var kafkaProducerFactory = CachingKafkaProducerFactory.<String, Buffer>sharedFactory(vertx);
            kafkaProducerFactory.setMetricsSupport(kafkaClientMetricsSupport);
            commandConsumerFactoryProvider.setClient(new KafkaBasedCommandConsumerFactoryImpl(
                    vertx,
                    tenantClient,
                    commandTargetMapper,
                    kafkaProducerFactory,
                    commandInternalKafkaProducerConfig,
                    commandResponseKafkaProducerConfig,
                    kafkaConsumerConfig,
                    metrics,
                    kafkaClientMetricsSupport,
                    tracer));
        }
        if (!appConfig.isAmqpMessagingDisabled() && commandConsumerConnectionConfig.isHostConfigured()) {
            commandConsumerFactoryProvider.setClient(new ProtonBasedCommandConsumerFactoryImpl(
                    HonoConnection.newConnection(vertx, commandConsumerConnectionConfig, tracer),
                    tenantClient,
                    commandTargetMapper,
                    metrics,
                    SendMessageSampler.Factory.noop()));
        }
        if (!appConfig.isPubSubMessagingDisabled() && pubSubConfigProperties.isProjectIdConfigured()) {
            PubSubMessageHelper.getCredentialsProvider()
                    .ifPresentOrElse(provider -> {
                        LOG.debug("Configuring Pub/Sub based command consumer factory");

                        final var publisherFactory = new CachingPubSubPublisherFactory(
                                vertx,
                                pubSubConfigProperties.getProjectId(),
                                provider);
                        final var subscriberFactory = new CachingPubSubSubscriberFactory(
                                vertx,
                                pubSubConfigProperties.getProjectId(),
                                provider);
                        commandConsumerFactoryProvider.setClient(new PubSubBasedCommandConsumerFactoryImpl(
                                vertx,
                                tenantClient,
                                tracer,
                                publisherFactory,
                                pubSubConfigProperties.getProjectId(),
                                commandTargetMapper,
                                metrics,
                                subscriberFactory));
                    }, () -> LOG.error("Could not initialize Pub/Sub based command consumer factory, no Credentials Provider present."));
        }
        return commandConsumerFactoryProvider;
    }

    private MessagingClientProvider<EventSender> eventSenderProvider() {
        final var eventSenderProvider = new MessagingClientProvider<EventSender>();

        if (!appConfig.isKafkaMessagingDisabled() && kafkaEventConfig.isConfigured()) {

            final var kafkaProducerFactory = CachingKafkaProducerFactory.<String, Buffer> sharedFactory(vertx);
            kafkaProducerFactory.setMetricsSupport(kafkaClientMetricsSupport);
            eventSenderProvider.setClient(new KafkaBasedEventSender(
                    vertx,
                    kafkaProducerFactory,
                    kafkaEventConfig,
                    true,
                    tracer));
        }

        if (!appConfig.isAmqpMessagingDisabled() && downstreamSenderConfig.isHostConfigured()) {
            eventSenderProvider.setClient(new ProtonBasedDownstreamSender(
                    HonoConnection.newConnection(vertx, downstreamSenderConfig, tracer),
                    SendMessageSampler.Factory.noop(),
                    true,
                    false));
        }
        if (!appConfig.isPubSubMessagingDisabled() && pubSubConfigProperties.isProjectIdConfigured()) {
            PubSubMessageHelper.getCredentialsProvider()
                    .ifPresentOrElse(provider -> {
                        final var pubSubFactory = new CachingPubSubPublisherFactory(
                                vertx,
                                pubSubConfigProperties.getProjectId(),
                                provider);

                        eventSenderProvider.setClient(new PubSubBasedDownstreamSender(
                                vertx,
                                pubSubFactory,
                                EventConstants.EVENT_ENDPOINT,
                                pubSubConfigProperties.getProjectId(),
                                true,
                                tracer));
                    }, () -> LOG.error("Could not initialize Pub/Sub based downstream sender, no Credentials Provider present."));
        }
        return eventSenderProvider;
    }

    private Cache<Object, RegistrationResult> registrationResponseCache() {
        if (registrationResponseCache == null) {
            registrationResponseCache = Caches.newCaffeineCache(deviceRegistrationClientConfig);
        }
        return registrationResponseCache;
    }

    private Cache<Object, TenantResult<TenantObject>> tenantResponseCache() {
        if (tenantResponseCache == null) {
            tenantResponseCache = Caches.newCaffeineCache(tenantClientConfig);
        }
        return tenantResponseCache;
    }

    /**
     * Creates a new client for Hono's Device Registration service.
     *
     * @return The client.
     */
    protected DeviceRegistrationClient registrationClient() {
        return new ProtonBasedDeviceRegistrationClient(
                HonoConnection.newConnection(vertx, deviceRegistrationClientConfig, tracer),
                SendMessageSampler.Factory.noop(),
                registrationResponseCache());
    }

    /**
     * Creates a new client for Hono's Tenant service.
     *
     * @return The client.
     */
    protected TenantClient tenantClient() {
        return new ProtonBasedTenantClient(
                HonoConnection.newConnection(vertx, tenantClientConfig, tracer),
                SendMessageSampler.Factory.noop(),
                tenantResponseCache());
    }
}
