/**
 * Copyright (c) 2022, 2023 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.deviceregistry.app;

import org.eclipse.hono.client.amqp.config.ClientConfigProperties;
import org.eclipse.hono.client.amqp.connection.HonoConnection;
import org.eclipse.hono.client.amqp.connection.SendMessageSampler;
import org.eclipse.hono.client.kafka.metrics.KafkaClientMetricsSupport;
import org.eclipse.hono.client.kafka.producer.CachingKafkaProducerFactory;
import org.eclipse.hono.client.kafka.producer.KafkaProducerFactory;
import org.eclipse.hono.client.kafka.producer.MessagingKafkaProducerConfigProperties;
import org.eclipse.hono.client.pubsub.PubSubConfigProperties;
import org.eclipse.hono.client.pubsub.PubSubMessageHelper;
import org.eclipse.hono.client.pubsub.publisher.CachingPubSubPublisherFactory;
import org.eclipse.hono.client.telemetry.EventSender;
import org.eclipse.hono.client.telemetry.amqp.ProtonBasedDownstreamSender;
import org.eclipse.hono.client.telemetry.kafka.KafkaBasedEventSender;
import org.eclipse.hono.client.telemetry.pubsub.PubSubBasedDownstreamSender;
import org.eclipse.hono.client.util.MessagingClientProvider;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.config.ServiceOptions;
import org.eclipse.hono.deviceregistry.server.DeviceRegistryAmqpServer;
import org.eclipse.hono.deviceregistry.service.credentials.AbstractCredentialsService;
import org.eclipse.hono.deviceregistry.service.device.AbstractRegistrationService;
import org.eclipse.hono.deviceregistry.service.device.AutoProvisionerConfigProperties;
import org.eclipse.hono.deviceregistry.service.device.EdgeDeviceAutoProvisioner;
import org.eclipse.hono.deviceregistry.service.tenant.TenantInformationService;
import org.eclipse.hono.service.ApplicationConfigProperties;
import org.eclipse.hono.service.HealthCheckServer;
import org.eclipse.hono.service.amqp.AmqpEndpoint;
import org.eclipse.hono.service.credentials.DelegatingCredentialsAmqpEndpoint;
import org.eclipse.hono.service.management.credentials.CredentialsManagementService;
import org.eclipse.hono.service.management.device.DeviceAndGatewayAutoProvisioner;
import org.eclipse.hono.service.management.device.DeviceManagementService;
import org.eclipse.hono.service.registration.DelegatingRegistrationAmqpEndpoint;
import org.eclipse.hono.service.tenant.DelegatingTenantAmqpEndpoint;
import org.eclipse.hono.service.tenant.TenantService;
import org.eclipse.hono.service.util.ServiceClientAdapter;
import org.eclipse.hono.util.EventConstants;

import io.opentracing.Tracer;
import io.smallrye.config.ConfigMapping;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.proton.sasl.ProtonSaslAuthenticatorFactory;
import jakarta.inject.Inject;
import jakarta.inject.Named;

/**
 * A factory base class for creating AMQP 1.0 based endpoints of Hono's south bound APIs.
 *
 */
public abstract class AbstractAmqpServerFactory {

    @Inject
    Vertx vertx;

    @Inject
    Tracer tracer;

    @Inject
    DeviceManagementService deviceManagementService;

    @Inject
    CredentialsManagementService credentialsManagementService;

    @Inject
    TenantInformationService tenantInformationService;

    @Inject
    HealthCheckServer healthCheckServer;

    @Inject
    ProtonSaslAuthenticatorFactory saslAuthenticatorFactory;

    @Inject
    MessagingKafkaProducerConfigProperties eventKafkaProducerConfig;

    @Inject
    AutoProvisionerConfigProperties autoProvisionerConfigProperties;

    @Inject
    @Named("amqp-messaging-network")
    ClientConfigProperties downstreamSenderConfig;

    @Inject
    KafkaClientMetricsSupport kafkaClientMetricsSupport;

    @Inject
    PubSubConfigProperties pubSubConfigProperties;

    @Inject
    ApplicationConfigProperties appConfig;

    private ServiceConfigProperties amqpServerProperties;

    @Inject
    void setAmqpServerProperties(
            @ConfigMapping(prefix = "hono.registry.amqp", namingStrategy = ConfigMapping.NamingStrategy.VERBATIM)
            final ServiceOptions endpointOptions) {
        this.amqpServerProperties = new ServiceConfigProperties(endpointOptions);
    }

    /**
     * Creates a server with AMQP 1.0 endpoints exposing Hono's south bound APIs.
     *
     * @return The server.
     */
    public final DeviceRegistryAmqpServer newServer() {
        final var server = new DeviceRegistryAmqpServer();
        server.setConfig(amqpServerProperties);
        server.setHealthCheckServer(healthCheckServer);
        server.setSaslAuthenticatorFactory(saslAuthenticatorFactory);
        server.setTracer(tracer);
        server.addEndpoint(tenantAmqpEndpoint());
        server.addEndpoint(registrationAmqpEndpoint());
        server.addEndpoint(credentialsAmqpEndpoint());

        return server;
    }

    /**
     * Creates a tenant service instance.
     *
     * @return The service instance.
     */
    protected abstract TenantService createTenantService();

    /**
     * Creates a registration service instance.
     * <p>
     * Note that an edge device auto-provisioner and a tenant information service will be
     * set on the returned instance as part of the {@link #newServer()} invocation.
     *
     * @return The service instance.
     */
    protected abstract AbstractRegistrationService createRegistrationService();

    /**
     * Creates a credentials service instance.
     * <p>
     * Note that an <em>DeviceAndGatewayAutoProvisioner</em> and a tenant information service will be
     * set on the returned instance as part of the {@link #newServer()} invocation.
     *
     * @return The service instance.
     */
    protected abstract AbstractCredentialsService createCredentialsService();

    /**
     * Creates an AMQP 1.0 based endpoint for the Tenant service.
     *
     * @return The endpoint.
     */
    private AmqpEndpoint tenantAmqpEndpoint() {
        final var endpoint = new DelegatingTenantAmqpEndpoint<>(vertx, createTenantService());
        endpoint.setConfiguration(amqpServerProperties);
        endpoint.setTracer(tracer);
        return endpoint;
    }

    /**
     * Creates an AMQP 1.0 based endpoint for the Device Registration service.
     *
     * @return The endpoint.
     */
    private AmqpEndpoint registrationAmqpEndpoint() {
        final AbstractRegistrationService service = createRegistrationService();
        prepareRegistrationService(service);
        final var endpoint = new DelegatingRegistrationAmqpEndpoint<>(vertx, service);
        endpoint.setConfiguration(amqpServerProperties);
        endpoint.setTracer(tracer);
        return endpoint;
    }

    /**
     * Creates an AMQP 1.0 based endpoint for the Credentials service.
     *
     * @return The endpoint.
     */
    private AmqpEndpoint credentialsAmqpEndpoint() {
        final AbstractCredentialsService service = createCredentialsService();
        prepareCredentialsService(service);
        final var endpoint = new DelegatingCredentialsAmqpEndpoint<>(vertx, service);
        endpoint.setConfiguration(amqpServerProperties);
        endpoint.setTracer(tracer);
        return endpoint;
    }

    /**
     * Prepares the given Device Registration service instance by setting an edge device auto-provisioner
     * and a tenant information service on it.
     * <p>
     * This factory method makes sure that each set of event senders (as created via {@link #eventSenderProvider()})
     * is used by a single dedicated service instance only. This is necessary because during start up,
     * the service will implicitly invoke {@link MessagingClientProvider#start()} in order
     * to establish the senders' connection to the messaging infrastructure. For the AMQP 1.0 based senders,
     * this connection needs to be established on the verticle's event loop thread in order to work properly.
     */
    private void prepareRegistrationService(final AbstractRegistrationService service) {

        final EdgeDeviceAutoProvisioner edgeDeviceAutoProvisioner = new EdgeDeviceAutoProvisioner(
                vertx,
                deviceManagementService,
                eventSenderProvider(),
                autoProvisionerConfigProperties,
                tracer);
        service.setEdgeDeviceAutoProvisioner(edgeDeviceAutoProvisioner);
        service.setTenantInformationService(tenantInformationService);
    }

    /**
     * Prepares the given Credentials service instance by setting an <em>DeviceAndGatewayAutoProvisioner</em>
     * and a tenant information service on it.
     * <p>
     * This factory method makes sure that each set of event senders (as created via {@link #eventSenderProvider()})
     * is used by a single dedicated service instance only. This is necessary because during start up,
     * the service will implicitly invoke {@link MessagingClientProvider#start()} in order
     * to establish the senders' connection to the messaging infrastructure. For the AMQP 1.0 based senders,
     * this connection needs to be established on the verticle's event loop thread in order to work properly.
     */
    private void prepareCredentialsService(final AbstractCredentialsService service) {

        final var provisioner = new DeviceAndGatewayAutoProvisioner(
                vertx,
                deviceManagementService,
                credentialsManagementService,
                eventSenderProvider());
        service.setDeviceAndGatewayAutoProvisioner(provisioner);
        service.setTenantInformationService(tenantInformationService);
    }

    /**
     * Creates a client for publishing events via the configured messaging systems.
     *
     * @return The client.
     */
    private MessagingClientProvider<EventSender> eventSenderProvider() {

        final MessagingClientProvider<EventSender> result = new MessagingClientProvider<>();

        if (!appConfig.isAmqpMessagingDisabled() && downstreamSenderConfig.isHostConfigured()) {
            result.setClient(new ProtonBasedDownstreamSender(
                    HonoConnection.newConnection(vertx, downstreamSenderConfig, tracer),
                    SendMessageSampler.Factory.noop(),
                    true,
                    true));
        }

        if (!appConfig.isKafkaMessagingDisabled() && eventKafkaProducerConfig.isConfigured()) {
            final KafkaProducerFactory<String, Buffer> factory = CachingKafkaProducerFactory.sharedFactory(vertx);
            factory.setMetricsSupport(kafkaClientMetricsSupport);
            result.setClient(new KafkaBasedEventSender(vertx, factory, eventKafkaProducerConfig, true, tracer));
        }
        if (!appConfig.isPubSubMessagingDisabled() && pubSubConfigProperties.isProjectIdConfigured()) {
            PubSubMessageHelper.getCredentialsProvider()
                    .ifPresent(provider -> {
                        final var factory = new CachingPubSubPublisherFactory(
                                        vertx,
                                        pubSubConfigProperties.getProjectId(),
                                        provider);
                        result.setClient(new PubSubBasedDownstreamSender(
                                vertx,
                                factory,
                                EventConstants.EVENT_ENDPOINT,
                                pubSubConfigProperties.getProjectId(),
                                true,
                                tracer));
                            });
        }

        healthCheckServer.registerHealthCheckResources(ServiceClientAdapter.forClient(result));
        return result;
    }
}
