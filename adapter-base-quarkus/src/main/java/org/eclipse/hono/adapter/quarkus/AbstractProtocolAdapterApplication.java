/**
 * Copyright (c) 2020, 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.adapter.quarkus;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import javax.enterprise.event.Observes;
import javax.inject.Inject;

import org.eclipse.hono.adapter.AbstractProtocolAdapterBase;
import org.eclipse.hono.adapter.AdapterConfigurationSupport;
import org.eclipse.hono.adapter.MessagingClients;
import org.eclipse.hono.adapter.client.command.CommandConsumerFactory;
import org.eclipse.hono.adapter.client.command.CommandResponseSender;
import org.eclipse.hono.adapter.client.command.CommandRouterClient;
import org.eclipse.hono.adapter.client.command.CommandRouterCommandConsumerFactory;
import org.eclipse.hono.adapter.client.command.DeviceConnectionClient;
import org.eclipse.hono.adapter.client.command.amqp.ProtonBasedCommandResponseSender;
import org.eclipse.hono.adapter.client.command.amqp.ProtonBasedCommandRouterClient;
import org.eclipse.hono.adapter.client.command.amqp.ProtonBasedDelegatingCommandConsumerFactory;
import org.eclipse.hono.adapter.client.command.amqp.ProtonBasedInternalCommandConsumer;
import org.eclipse.hono.adapter.client.command.kafka.KafkaBasedCommandResponseSender;
import org.eclipse.hono.adapter.client.command.kafka.KafkaBasedInternalCommandConsumer;
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
import org.eclipse.hono.adapter.monitoring.ConnectionEventProducer;
import org.eclipse.hono.adapter.monitoring.HonoEventConnectionEventProducer;
import org.eclipse.hono.adapter.monitoring.LoggingConnectionEventProducer;
import org.eclipse.hono.adapter.monitoring.quarkus.ConnectionEventProducerConfig;
import org.eclipse.hono.adapter.resourcelimits.ResourceLimitChecks;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.SendMessageSampler;
import org.eclipse.hono.client.kafka.KafkaAdminClientConfigProperties;
import org.eclipse.hono.client.kafka.KafkaProducerConfigProperties;
import org.eclipse.hono.client.kafka.KafkaProducerFactory;
import org.eclipse.hono.client.kafka.consumer.KafkaConsumerConfigProperties;
import org.eclipse.hono.client.quarkus.RequestResponseClientConfigProperties;
import org.eclipse.hono.client.util.MessagingClient;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.config.ProtocolAdapterProperties;
import org.eclipse.hono.config.quarkus.ApplicationConfigProperties;
import org.eclipse.hono.service.HealthCheckServer;
import org.eclipse.hono.service.cache.Caches;
import org.eclipse.hono.util.CredentialsObject;
import org.eclipse.hono.util.CredentialsResult;
import org.eclipse.hono.util.MessagingType;
import org.eclipse.hono.util.RegistrationResult;
import org.eclipse.hono.util.TenantObject;
import org.eclipse.hono.util.TenantResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.benmanes.caffeine.cache.Cache;

import io.opentracing.Tracer;
import io.quarkus.arc.config.ConfigPrefix;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.impl.cpu.CpuCoreSensor;

/**
 * A Quarkus main application base class for Hono protocol adapters.
 * <p>
 * This class provides helper methods for creating clients for Hono's service APIs
 * which to be used with protocol adapter instances.
 *
 * @param <C> The type of configuration properties the adapter uses.
 */
public abstract class AbstractProtocolAdapterApplication<C extends ProtocolAdapterProperties> extends AdapterConfigurationSupport {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractProtocolAdapterApplication.class);

    @Inject
    protected Vertx vertx;

    @Inject
    protected Tracer tracer;

    @Inject
    protected SendMessageSampler.Factory messageSamplerFactory;

    @Inject
    protected HealthCheckServer healthCheckServer;

    @Inject
    protected ResourceLimitChecks resourceLimitChecks;

    @Inject
    protected ConnectionEventProducerConfig connectionEventsConfig;

    @Inject
    protected C protocolAdapterProperties;

    @Inject
    protected ApplicationConfigProperties appConfig;

    @Inject
    protected KafkaProducerConfigProperties kafkaProducerConfig;

    @Inject
    protected KafkaConsumerConfigProperties kafkaConsumerConfig;

    @Inject
    protected KafkaAdminClientConfigProperties kafkaAdminClientConfig;

    @ConfigPrefix("hono.messaging")
    protected RequestResponseClientConfigProperties downstreamSenderConfig;

    @ConfigPrefix("hono.command")
    protected RequestResponseClientConfigProperties commandConfig;

    @ConfigPrefix("hono.tenant")
    protected RequestResponseClientConfigProperties tenantClientConfig;

    @ConfigPrefix("hono.registration")
    protected RequestResponseClientConfigProperties deviceRegistrationClientConfig;

    @ConfigPrefix("hono.credentials")
    protected RequestResponseClientConfigProperties credentialsClientConfig;

    @ConfigPrefix("hono.commandRouter")
    protected RequestResponseClientConfigProperties commandRouterConfig;

    private Cache<Object, TenantResult<TenantObject>> tenantResponseCache;
    private Cache<Object, RegistrationResult> registrationResponseCache;
    private Cache<Object, CredentialsResult<CredentialsObject>> credentialsResponseCache;

    /**
     * Creates an instance of the protocol adapter.
     *
     * @return The adapter instance.
     */
    protected abstract AbstractProtocolAdapterBase<C> adapter();

    void onStart(final @Observes StartupEvent ev) {
        logJvmDetails();
        LOG.info("deploying {} {} instances ...", appConfig.getMaxInstances(), getAdapterName());

        final CompletableFuture<Void> startup = new CompletableFuture<>();
        final Promise<String> deploymentTracker = Promise.promise();
        vertx.deployVerticle(
                () -> adapter(),
                new DeploymentOptions().setInstances(appConfig.getMaxInstances()),
                deploymentTracker);
        deploymentTracker.future()
            .compose(s -> healthCheckServer.start())
            .onSuccess(ok -> startup.complete(null))
            .onFailure(t -> startup.completeExceptionally(t));
        startup.join();

    }

    void onStop(final @Observes ShutdownEvent ev) {
        LOG.info("shutting down {}", getAdapterName());
        final CompletableFuture<Void> shutdown = new CompletableFuture<>();
        healthCheckServer.stop()
            .onComplete(ok -> {
                vertx.close(attempt -> {
                    if (attempt.succeeded()) {
                        shutdown.complete(null);
                    } else {
                        shutdown.completeExceptionally(attempt.cause());
                    }
                });
            });
        shutdown.join();
    }

    /**
     * Logs information about the JVM.
     */
    private void logJvmDetails() {
        if (LOG.isInfoEnabled()) {
            LOG.info("running on Java VM [version: {}, name: {}, vendor: {}, max memory: {}MB, processors: {}]",
                    System.getProperty("java.version"),
                    System.getProperty("java.vm.name"),
                    System.getProperty("java.vm.vendor"),
                    Runtime.getRuntime().maxMemory() >> 20,
                    CpuCoreSensor.availableProcessors());
        }
    }

    /**
     * Sets collaborators required by all protocol adapters.
     *
     * @param adapter The adapter to set the collaborators on.
     * @throws NullPointerException if adapter is {@code null}
     */
    protected void setCollaborators(final AbstractProtocolAdapterBase<?> adapter) {

        Objects.requireNonNull(adapter);

        final DeviceRegistrationClient registrationClient = registrationClient();

        if (commandRouterConfig.isHostConfigured()) {
            final CommandRouterClient commandRouterClient = commandRouterClient();
            adapter.setCommandRouterClient(commandRouterClient);
            final CommandRouterCommandConsumerFactory commandConsumerFactory = commandConsumerFactory(commandRouterClient);
            commandConsumerFactory.registerInternalCommandConsumer(
                    (id, handlers) -> new ProtonBasedInternalCommandConsumer(commandConsumerConnection(), id, handlers));

            if (kafkaAdminClientConfig.isConfigured() && kafkaConsumerConfig.isConfigured()) {
                commandConsumerFactory.registerInternalCommandConsumer(
                        (id, handlers) -> new KafkaBasedInternalCommandConsumer(vertx, kafkaAdminClientConfig,
                                kafkaConsumerConfig, id, handlers, tracer));
            }

            adapter.setCommandConsumerFactory(commandConsumerFactory);
        } else {
            LOG.warn("Quarkus based protocol adapters do not support the Device Connection service to be used."
                    + " Make sure to configure a connection to the Command Router service instead.");
            throw new IllegalStateException("No Command Router connection configured");
        }

        final MessagingClient<TelemetrySender> telemetrySenders = new MessagingClient<>();
        final MessagingClient<EventSender> eventSenders = new MessagingClient<>();
        final MessagingClient<CommandResponseSender> commandResponseSenders = new MessagingClient<>();

        if (kafkaProducerConfig.isConfigured()) {
            LOG.info("Kafka Producer is configured, adding Kafka messaging clients");

            Optional.ofNullable(getAdapterName()).ifPresent(kafkaProducerConfig::setDefaultClientIdPrefix);
            LOG.debug("KafkaProducerConfig: " + kafkaProducerConfig.getProducerConfig("log"));

            final KafkaProducerFactory<String, Buffer> factory = KafkaProducerFactory.sharedProducerFactory(vertx);
            telemetrySenders.setClient(
                    MessagingType.kafka,
                    new KafkaBasedTelemetrySender(factory, kafkaProducerConfig,
                            protocolAdapterProperties.isDefaultsEnabled(), tracer));
            eventSenders.setClient(
                    MessagingType.kafka,
                    new KafkaBasedEventSender(factory, kafkaProducerConfig,
                            protocolAdapterProperties.isDefaultsEnabled(), tracer));
            commandResponseSenders.setClient(
                    MessagingType.kafka,
                    new KafkaBasedCommandResponseSender(factory, kafkaProducerConfig, tracer));
        }
        if (downstreamSenderConfig().isHostConfigured()) {
            telemetrySenders.setClient(MessagingType.amqp, downstreamSender());
            eventSenders.setClient(MessagingType.amqp, downstreamSender());
            commandResponseSenders.setClient(
                    MessagingType.amqp,
                    new ProtonBasedCommandResponseSender(
                            HonoConnection.newConnection(vertx, commandResponseSenderConfig(), tracer),
                            messageSamplerFactory,
                            protocolAdapterProperties));
        }

        adapter.setMessagingClients(new MessagingClients(telemetrySenders, eventSenders, commandResponseSenders));
        Optional.ofNullable(connectionEventProducer())
            .ifPresent(adapter::setConnectionEventProducer);
        adapter.setCredentialsClient(credentialsClient());
        adapter.setHealthCheckServer(healthCheckServer);
        adapter.setRegistrationClient(registrationClient);
        adapter.setResourceLimitChecks(resourceLimitChecks);
        adapter.setTenantClient(tenantClient());
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
        case logging:
            return new LoggingConnectionEventProducer(connectionEventsConfig);
        case events:
            return new HonoEventConnectionEventProducer();
        default:
            return null;
        }
    }

    private RequestResponseClientConfigProperties tenantServiceClientConfig() {
        tenantClientConfig.setServerRoleIfUnknown("Tenant");
        tenantClientConfig.setNameIfNotSet(getAdapterName());
        return tenantClientConfig;
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
                HonoConnection.newConnection(vertx, tenantServiceClientConfig(), tracer),
                messageSamplerFactory,
                tenantResponseCache());
    }

    private RequestResponseClientConfigProperties registrationServiceClientConfig() {
        deviceRegistrationClientConfig.setServerRoleIfUnknown("Device Registration");
        deviceRegistrationClientConfig.setNameIfNotSet(getAdapterName());
        return deviceRegistrationClientConfig;
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
                HonoConnection.newConnection(vertx, registrationServiceClientConfig(), tracer),
                messageSamplerFactory,
                registrationResponseCache());
    }

    private RequestResponseClientConfigProperties credentialsServiceClientConfig() {
        credentialsClientConfig.setServerRoleIfUnknown("Credentials");
        credentialsClientConfig.setNameIfNotSet(getAdapterName());
        return credentialsClientConfig;
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
                HonoConnection.newConnection(vertx, credentialsServiceClientConfig(), tracer),
                messageSamplerFactory,
                credentialsResponseCache());
    }

    private RequestResponseClientConfigProperties commandRouterServiceClientConfig() {
        commandRouterConfig.setServerRoleIfUnknown("Command Router");
        commandRouterConfig.setNameIfNotSet(getAdapterName());
        return commandRouterConfig;
    }

    /**
     * Creates a new client for Hono's Command Router service.
     *
     * @return The client.
     */
    protected CommandRouterClient commandRouterClient() {
        return new ProtonBasedCommandRouterClient(
                HonoConnection.newConnection(vertx, commandRouterServiceClientConfig(), tracer),
                messageSamplerFactory);
    }

    private ClientConfigProperties downstreamSenderConfig() {
        downstreamSenderConfig.setServerRoleIfUnknown("Downstream");
        downstreamSenderConfig.setNameIfNotSet(getAdapterName());
        return downstreamSenderConfig;
    }

    /**
     * Creates a new downstream sender for telemetry and event messages.
     *
     * @return The sender.
     */
    private ProtonBasedDownstreamSender downstreamSender() {
        return new ProtonBasedDownstreamSender(
                HonoConnection.newConnection(vertx, downstreamSenderConfig(), tracer),
                messageSamplerFactory,
                protocolAdapterProperties.isDefaultsEnabled(),
                protocolAdapterProperties.isJmsVendorPropsEnabled());
    }

    private ClientConfigProperties commandConsumerFactoryConfig() {
        final ClientConfigProperties props = new ClientConfigProperties(commandConfig);
        props.setServerRoleIfUnknown("Command & Control");
        props.setNameIfNotSet(getAdapterName());
        return props;
    }

    /**
     * Creates a new connection to the AMQP Messaging Network's Command &amp; Control endpoint.
     *
     * @return The connection.
     */
    protected HonoConnection commandConsumerConnection() {
        return HonoConnection.newConnection(vertx, commandConsumerFactoryConfig(), tracer);
    }

    /**
     * Creates a new factory for creating command consumers.
     * <p>
     * The returned factory will also take care of routing commands to target adapter instances.
     *
     * @param deviceConnectionClient The client to use for accessing the Device Connection service.
     * @param deviceRegistrationClient The client to use for accessing the Device Registration service.
     * @return The factory.
     */
    protected CommandConsumerFactory commandConsumerFactory(
            final DeviceConnectionClient deviceConnectionClient,
            final DeviceRegistrationClient deviceRegistrationClient) {

        LOG.debug("using Device Connection service client, configuring CommandConsumerFactory [{}]",
                ProtonBasedDelegatingCommandConsumerFactory.class.getName());
        return new ProtonBasedDelegatingCommandConsumerFactory(
                commandConsumerConnection(),
                messageSamplerFactory,
                deviceConnectionClient,
                deviceRegistrationClient,
                tracer);
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

        LOG.debug("using Command Router service client, configuring CommandConsumerFactory [{}}]",
                CommandRouterCommandConsumerFactory.class.getName());
        return new CommandRouterCommandConsumerFactory(commandRouterClient, getAdapterName());
    }

    private ClientConfigProperties commandResponseSenderConfig() {
        final ClientConfigProperties props = new ClientConfigProperties(downstreamSenderConfig);
        props.setServerRoleIfUnknown("Command Response");
        props.setNameIfNotSet(getAdapterName());
        return props;
    }

}
