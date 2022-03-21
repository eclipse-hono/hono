/**
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.deviceregistry.jdbc.quarkus;

import java.util.Objects;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.inject.Named;

import org.eclipse.hono.client.amqp.config.ClientConfigProperties;
import org.eclipse.hono.client.amqp.connection.HonoConnection;
import org.eclipse.hono.client.amqp.connection.SendMessageSampler;
import org.eclipse.hono.client.kafka.metrics.KafkaClientMetricsSupport;
import org.eclipse.hono.client.kafka.producer.CachingKafkaProducerFactory;
import org.eclipse.hono.client.kafka.producer.KafkaProducerFactory;
import org.eclipse.hono.client.kafka.producer.MessagingKafkaProducerConfigProperties;
import org.eclipse.hono.client.telemetry.EventSender;
import org.eclipse.hono.client.telemetry.amqp.ProtonBasedDownstreamSender;
import org.eclipse.hono.client.telemetry.kafka.KafkaBasedEventSender;
import org.eclipse.hono.client.util.MessagingClientProvider;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.config.quarkus.ServiceOptions;
import org.eclipse.hono.deviceregistry.jdbc.config.DeviceServiceOptions;
import org.eclipse.hono.deviceregistry.jdbc.config.SchemaCreator;
import org.eclipse.hono.deviceregistry.jdbc.config.TenantServiceOptions;
import org.eclipse.hono.deviceregistry.jdbc.impl.CredentialsServiceImpl;
import org.eclipse.hono.deviceregistry.jdbc.impl.RegistrationServiceImpl;
import org.eclipse.hono.deviceregistry.jdbc.impl.TenantServiceImpl;
import org.eclipse.hono.deviceregistry.server.DeviceRegistryAmqpServer;
import org.eclipse.hono.deviceregistry.service.device.AutoProvisionerConfigProperties;
import org.eclipse.hono.deviceregistry.service.device.EdgeDeviceAutoProvisioner;
import org.eclipse.hono.deviceregistry.service.tenant.TenantInformationService;
import org.eclipse.hono.deviceregistry.util.ServiceClientAdapter;
import org.eclipse.hono.service.HealthCheckServer;
import org.eclipse.hono.service.amqp.AmqpEndpoint;
import org.eclipse.hono.service.base.jdbc.store.device.TableAdapterStore;
import org.eclipse.hono.service.base.jdbc.store.tenant.AdapterStore;
import org.eclipse.hono.service.credentials.CredentialsService;
import org.eclipse.hono.service.credentials.DelegatingCredentialsAmqpEndpoint;
import org.eclipse.hono.service.management.credentials.CredentialsManagementService;
import org.eclipse.hono.service.management.device.DeviceAndGatewayAutoProvisioner;
import org.eclipse.hono.service.management.device.DeviceManagementService;
import org.eclipse.hono.service.registration.DelegatingRegistrationAmqpEndpoint;
import org.eclipse.hono.service.registration.RegistrationService;
import org.eclipse.hono.service.tenant.DelegatingTenantAmqpEndpoint;
import org.eclipse.hono.service.tenant.TenantService;

import io.opentracing.Tracer;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.ConfigMapping.NamingStrategy;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.proton.sasl.ProtonSaslAuthenticatorFactory;

/**
 * A factory for creating AMQP 1.0 based endpoints of Hono's south bound APIs.
 *
 */
@ApplicationScoped
public class AmqpServerFactory {

    @Inject
    Vertx vertx;

    @Inject
    Tracer tracer;

    @Inject
    SchemaCreator schemaCreator;

    @Inject
    TableAdapterStore devicesAdapterStore;

    @Inject
    DeviceServiceOptions deviceServiceOptions;

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

    private final ServiceConfigProperties amqpServerProperties;
    private final TenantService tenantService;

    /**
     * Creates a factory.
     *
     * @param endpointOptions The AMQP endpoint configuration.
     * @param tenantAdapterStore The store that contains the tenant information.
     * @param tenantServiceOptions The configuration properties for the Tenant service.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public AmqpServerFactory(
            @ConfigMapping(prefix = "hono.registry.amqp", namingStrategy = NamingStrategy.VERBATIM)
            final ServiceOptions endpointOptions,
            final AdapterStore tenantAdapterStore,
            final TenantServiceOptions tenantServiceOptions) {
        Objects.requireNonNull(endpointOptions);
        this.amqpServerProperties = new ServiceConfigProperties(endpointOptions);
        this.tenantService = new TenantServiceImpl(tenantAdapterStore, tenantServiceOptions);
    }

    /**
     * Creates a server with AMQP 1.0 endpoints exposing Hono's south bound APIs.
     *
     * @return The server.
     */
    public DeviceRegistryAmqpServer newServer() {
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
     * Creates an AMQP 1.0 based endpoint for the Tenant service.
     *
     * @return The endpoint.
     */
    private AmqpEndpoint tenantAmqpEndpoint() {

        final var endpoint = new DelegatingTenantAmqpEndpoint<TenantService>(vertx, tenantService);
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
        final var endpoint = new DelegatingRegistrationAmqpEndpoint<RegistrationService>(vertx, registrationService());
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
        final var endpoint = new DelegatingCredentialsAmqpEndpoint<CredentialsService>(vertx, credentialsService());
        endpoint.setConfiguration(amqpServerProperties);
        endpoint.setTracer(tracer);
        return endpoint;
    }

    /**
     * Creates a Device Registration service instance.
     * <p>
     * This factory method makes sure that each set of event senders
     * is used by a single dedicated service instance only. This is necessary because during start up,
     * the service will implicitly invoke {@link MessagingClientProvider#start()} in order
     * to establish the senders' connection to the messaging infrastructure. For the AMQP 1.0 based senders,
     * this connection needs to be established on the verticle's event loop thread in order to work properly.
     *
     * @return The MongoDB registration service.
     */
    private RegistrationService registrationService() {

        final EdgeDeviceAutoProvisioner edgeDeviceAutoProvisioner = new EdgeDeviceAutoProvisioner(
                vertx,
                deviceManagementService,
                eventSenderProvider(),
                autoProvisionerConfigProperties,
                tracer);

        final var service = new RegistrationServiceImpl(devicesAdapterStore, schemaCreator);
        service.setEdgeDeviceAutoProvisioner(edgeDeviceAutoProvisioner);
        service.setTenantInformationService(tenantInformationService);
        return service;
    }

    /**
     * Creates a Credentials service instance.
     * <p>
     * This factory method makes sure that each set of event senders
     * is used by a single dedicated service instance only. This is necessary because during start up,
     * the service will implicitly invoke {@link MessagingClientProvider#start()} in order
     * to establish the senders' connection to the messaging infrastructure. For the AMQP 1.0 based senders,
     * this connection needs to be established on the verticle's event loop thread in order to work properly.
     *
     * @return The service instance.
     */
    private CredentialsService credentialsService() {

        final var provisioner = new DeviceAndGatewayAutoProvisioner(
                vertx,
                deviceManagementService,
                credentialsManagementService,
                eventSenderProvider());

        final var service = new CredentialsServiceImpl(devicesAdapterStore, deviceServiceOptions);
        service.setTenantInformationService(tenantInformationService);
        service.setDeviceAndGatewayAutoProvisioner(provisioner);
        return service;
    }

    /**
     * Creates a client for publishing events via the configured messaging systems.
     *
     * @return The client.
     */
    private MessagingClientProvider<EventSender> eventSenderProvider() {

        final MessagingClientProvider<EventSender> result = new MessagingClientProvider<>();

        if (downstreamSenderConfig.isHostConfigured()) {
            result.setClient(new ProtonBasedDownstreamSender(
                    HonoConnection.newConnection(vertx, downstreamSenderConfig, tracer),
                    SendMessageSampler.Factory.noop(),
                    true,
                    true));
        }

        if (eventKafkaProducerConfig.isConfigured()) {
            final KafkaProducerFactory<String, Buffer> factory = CachingKafkaProducerFactory.sharedFactory(vertx);
            factory.setMetricsSupport(kafkaClientMetricsSupport);
            result.setClient(new KafkaBasedEventSender(vertx, factory, eventKafkaProducerConfig, true, tracer));
        }

        healthCheckServer.registerHealthCheckResources(ServiceClientAdapter.forClient(result));
        return result;
    }
}
