/*******************************************************************************
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
 *******************************************************************************/

package org.eclipse.hono.deviceregistry.mongodb;

import java.io.FileInputStream;
import java.util.Base64;
import java.util.Optional;

import org.eclipse.hono.auth.HonoPasswordEncoder;
import org.eclipse.hono.auth.SpringBasedHonoPasswordEncoder;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.SendMessageSampler;
import org.eclipse.hono.client.kafka.CachingKafkaProducerFactory;
import org.eclipse.hono.client.kafka.KafkaProducerConfigProperties;
import org.eclipse.hono.client.kafka.KafkaProducerFactory;
import org.eclipse.hono.client.telemetry.EventSender;
import org.eclipse.hono.client.telemetry.amqp.ProtonBasedDownstreamSender;
import org.eclipse.hono.client.telemetry.kafka.KafkaBasedEventSender;
import org.eclipse.hono.client.util.MessagingClientProvider;
import org.eclipse.hono.config.ApplicationConfigProperties;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.config.ServerConfig;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.config.VertxProperties;
import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbBasedCredentialsConfigProperties;
import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbBasedHttpServiceConfigProperties;
import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbBasedRegistrationConfigProperties;
import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbBasedTenantsConfigProperties;
import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbConfigProperties;
import org.eclipse.hono.deviceregistry.mongodb.model.CredentialsDao;
import org.eclipse.hono.deviceregistry.mongodb.model.DeviceDao;
import org.eclipse.hono.deviceregistry.mongodb.model.MongoDbBasedCredentialsDao;
import org.eclipse.hono.deviceregistry.mongodb.model.MongoDbBasedDeviceDao;
import org.eclipse.hono.deviceregistry.mongodb.model.MongoDbBasedTenantDao;
import org.eclipse.hono.deviceregistry.mongodb.model.TenantDao;
import org.eclipse.hono.deviceregistry.mongodb.service.DaoBasedTenantInformationService;
import org.eclipse.hono.deviceregistry.mongodb.service.MongoDbBasedCredentialsManagementService;
import org.eclipse.hono.deviceregistry.mongodb.service.MongoDbBasedCredentialsService;
import org.eclipse.hono.deviceregistry.mongodb.service.MongoDbBasedDeviceManagementService;
import org.eclipse.hono.deviceregistry.mongodb.service.MongoDbBasedRegistrationService;
import org.eclipse.hono.deviceregistry.mongodb.service.MongoDbBasedTenantManagementService;
import org.eclipse.hono.deviceregistry.mongodb.service.MongoDbBasedTenantService;
import org.eclipse.hono.deviceregistry.server.DeviceRegistryAmqpServer;
import org.eclipse.hono.deviceregistry.server.DeviceRegistryHttpServer;
import org.eclipse.hono.deviceregistry.service.device.AutoProvisionerConfigProperties;
import org.eclipse.hono.deviceregistry.service.device.EdgeDeviceAutoProvisioner;
import org.eclipse.hono.deviceregistry.service.tenant.TenantInformationService;
import org.eclipse.hono.deviceregistry.util.CryptVaultBasedFieldLevelEncryption;
import org.eclipse.hono.deviceregistry.util.FieldLevelEncryption;
import org.eclipse.hono.deviceregistry.util.ServiceClientAdapter;
import org.eclipse.hono.service.HealthCheckServer;
import org.eclipse.hono.service.VertxBasedHealthCheckServer;
import org.eclipse.hono.service.amqp.AmqpEndpoint;
import org.eclipse.hono.service.credentials.CredentialsService;
import org.eclipse.hono.service.credentials.DelegatingCredentialsAmqpEndpoint;
import org.eclipse.hono.service.http.HttpEndpoint;
import org.eclipse.hono.service.management.credentials.CredentialsManagementService;
import org.eclipse.hono.service.management.credentials.DelegatingCredentialsManagementHttpEndpoint;
import org.eclipse.hono.service.management.device.DelegatingDeviceManagementHttpEndpoint;
import org.eclipse.hono.service.management.device.DeviceAndGatewayAutoProvisioner;
import org.eclipse.hono.service.management.device.DeviceManagementService;
import org.eclipse.hono.service.management.tenant.DelegatingTenantManagementHttpEndpoint;
import org.eclipse.hono.service.management.tenant.TenantManagementService;
import org.eclipse.hono.service.metric.MetricsTags;
import org.eclipse.hono.service.metric.spring.PrometheusSupport;
import org.eclipse.hono.service.registration.DelegatingRegistrationAmqpEndpoint;
import org.eclipse.hono.service.registration.RegistrationService;
import org.eclipse.hono.service.tenant.DelegatingTenantAmqpEndpoint;
import org.eclipse.hono.service.tenant.TenantService;
import org.eclipse.hono.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ObjectFactoryCreatingFactoryBean;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Scope;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import com.bol.config.CryptVaultAutoConfiguration.CryptVaultConfigurationProperties;
import com.bol.config.CryptVaultAutoConfiguration.Key;
import com.bol.crypt.CryptVault;

import io.micrometer.core.instrument.MeterRegistry;
import io.opentracing.Tracer;
import io.opentracing.contrib.tracerresolver.TracerResolver;
import io.opentracing.noop.NoopTracerFactory;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.auth.mongo.MongoAuthenticationOptions;
import io.vertx.ext.auth.mongo.impl.DefaultHashStrategy;
import io.vertx.ext.auth.mongo.impl.MongoAuthenticationImpl;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.handler.AuthenticationHandler;
import io.vertx.ext.web.handler.BasicAuthHandler;

/**
 * Spring Boot configuration for the mongodb based device registry application.
 */
@Configuration
@Import(PrometheusSupport.class)
public class ApplicationConfig {

    private static final Logger LOG = LoggerFactory.getLogger(ApplicationConfig.class);
    private static final String BEAN_NAME_AMQP_SERVER = "amqpServer";
    private static final String BEAN_NAME_HTTP_SERVER = "httpServer";

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
     * Exposes configuration properties for Vert.x.
     *
     * @return The properties.
     */
    @ConfigurationProperties("hono.vertx")
    @Bean
    public VertxProperties vertxProperties() {
        return new VertxProperties();
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
    public Tracer tracer() {

        return Optional.ofNullable(TracerResolver.resolveTracer())
                .orElse(NoopTracerFactory.create());
    }

    /**
     * Customizer for meter registry.
     *
     * @return The new meter registry customizer.
     */
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> commonTags() {

        return r -> r.config().commonTags(MetricsTags.forService(Constants.SERVICE_NAME_DEVICE_REGISTRY));

    }

    /**
     * Gets general properties for configuring the Device Registry Spring Boot application.
     *
     * @return The properties.
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
     * Gets the mongodb config properties.
     *
     * @return The mongodb config properties.
     */
    @Bean
    @ConfigurationProperties(prefix = "hono.mongodb")
    public MongoDbConfigProperties mongoDbConfigProperties() {
        return new MongoDbConfigProperties();
    }

    /**
     * Gets a {@link MongoClient} instance for MongoDB interaction.
     *
     * @return An instance of the {@link MongoClient}.
     */
    @Bean
    @Scope("prototype")
    public MongoClient mongoClient() {
        return MongoClient.createShared(vertx(), mongoDbConfigProperties().getMongoClientConfig());
    }


    /**
     * Exposes a password encoder to use for encoding clear text passwords
     * and for matching password hashes.
     *
     * @return The encoder.
     */
    @Bean
    public HonoPasswordEncoder passwordEncoder() {
        return new SpringBasedHonoPasswordEncoder(credentialsServiceProperties().getMaxBcryptCostFactor());
    }

    //
    //
    // Service properties
    //
    //

    /**
     * Gets properties for configuring
     * {@link org.eclipse.hono.deviceregistry.mongodb.service.MongoDbBasedRegistrationService} which 
     * implements the <em>Device Registration</em> API.
     *
     * @return The properties.
     */
    @Bean
    @ConfigurationProperties(prefix = "hono.registry.svc")
    public MongoDbBasedRegistrationConfigProperties registrationServiceProperties() {
        return new MongoDbBasedRegistrationConfigProperties();
    }

    /**
     * Gets properties for configuring
     * {@link org.eclipse.hono.deviceregistry.mongodb.service.MongoDbBasedTenantService} which
     * implements the <em>Tenants Service</em> API.
     *
     * @return The properties.
     */
    @Bean
    @ConfigurationProperties(prefix = "hono.tenant.svc")
    public MongoDbBasedTenantsConfigProperties tenantServiceProperties() {
        return new MongoDbBasedTenantsConfigProperties();
    }

    /**
     * Gets properties for configuring
     * {@link org.eclipse.hono.deviceregistry.mongodb.service.MongoDbBasedCredentialsService} which
     * implements the <em>Credentials</em> API.
     *
     * @return The properties.
     */
    @Bean
    @ConfigurationProperties(prefix = "hono.credentials.svc")
    public MongoDbBasedCredentialsConfigProperties credentialsServiceProperties() {
        return new MongoDbBasedCredentialsConfigProperties();
    }

    private FieldLevelEncryption fieldLevelEncryption(final String path) {
        try (FileInputStream in = new FileInputStream(path)) {
            final Yaml yaml = new Yaml(new Constructor(CryptVaultConfigurationProperties.class));
            final CryptVaultConfigurationProperties config = yaml.load(in);
            final CryptVault cryptVault = new CryptVault();
            for (Key key : config.getKeys()) {
                final byte[] secretKeyBytes = Base64.getDecoder().decode(key.getKey());
                cryptVault.with256BitAesCbcPkcs5PaddingAnd128BitSaltKey(key.getVersion(), secretKeyBytes);
            }

            Optional.ofNullable(config.getDefaultKey()).ifPresent(cryptVault::withDefaultKeyVersion);
            return new CryptVaultBasedFieldLevelEncryption(cryptVault);
        } catch (final Exception e) {
            throw new IllegalArgumentException(
                    String.format("error reading CryptVault configuration from file [%s]", path),
                    e);
        }
    }

    //
    //
    // AMQP endpoints
    //
    //

    /**
     * Gets properties for configuring the Device Registry's AMQP 1.0 endpoint.
     *
     * @return The properties.
     */
    @Qualifier(Constants.QUALIFIER_AMQP)
    @Bean
    @ConfigurationProperties(prefix = "hono.registry.amqp")
    public ServiceConfigProperties amqpServerProperties() {
        return new ServiceConfigProperties();
    }

    /**
     * Creates a new server for exposing the device registry's AMQP 1.0 based
     * endpoints.
     *
     * @return The server.
     */
    @Bean(name = BEAN_NAME_AMQP_SERVER)
    @Scope("prototype")
    public DeviceRegistryAmqpServer amqpServer() {
        return new DeviceRegistryAmqpServer();
    }

    /**
     * Exposes a factory for creating Device Connection service instances.
     *
     * @return The factory bean.
     */
    @Bean
    public ObjectFactoryCreatingFactoryBean amqpServerFactory() {
        final ObjectFactoryCreatingFactoryBean factory = new ObjectFactoryCreatingFactoryBean();
        factory.setTargetBeanName(BEAN_NAME_AMQP_SERVER);
        return factory;
    }

    /**
     * Gets properties for configuring gateway based auto-provisioning.
     *
     * @return The properties.
     */
    @Bean
    @ConfigurationProperties(prefix = "hono.autoprovisioning")
    public AutoProvisionerConfigProperties autoProvisionerConfigProperties() {
        return new AutoProvisionerConfigProperties();
    }

    /**
     * Creates a client for publishing events via the configured messaging systems.
     *
     * @return The client.
     */
    @Bean
    @Scope("prototype")
    public MessagingClientProvider<EventSender> eventSenderProvider() {

        final MessagingClientProvider<EventSender> result = new MessagingClientProvider<>();

        if (downstreamSenderConfig().isHostConfigured()) {
            result.setClient(new ProtonBasedDownstreamSender(
                    HonoConnection.newConnection(vertx(), downstreamSenderConfig(), tracer()),
                    SendMessageSampler.Factory.noop(),
                    true,
                    true));
        }

        if (kafkaProducerConfig().isConfigured()) {
            final KafkaProducerFactory<String, Buffer> factory = CachingKafkaProducerFactory.sharedFactory(vertx());
            result.setClient(new KafkaBasedEventSender(factory, kafkaProducerConfig(), true, tracer()));
        }

        healthCheckServer().registerHealthCheckResources(ServiceClientAdapter.forClient(result));
        return result;
    }

    /**
     * Exposes configuration properties for accessing the AMQP Messaging Network as a Spring bean.
     *
     * @return The properties.
     */
    @ConfigurationProperties(prefix = "hono.messaging")
    @Bean
    public ClientConfigProperties downstreamSenderConfig() {
        final ClientConfigProperties config = new ClientConfigProperties();
        config.setNameIfNotSet("Device Registry");
        config.setServerRoleIfUnknown("AMQP Messaging Network");
        return config;
    }

    /**
     * Exposes configuration properties for a producer accessing the Kafka cluster as a Spring bean.
     *
     * @return The properties.
     */
    @ConfigurationProperties(prefix = "hono.kafka")
    @Bean
    public KafkaProducerConfigProperties kafkaProducerConfig() {
        final KafkaProducerConfigProperties configProperties = new KafkaProducerConfigProperties();
        configProperties.setDefaultClientIdPrefix("device-registry");
        return configProperties;
    }

    /**
     * Creates a Data Access Object for device data.
     *
     * @return The DAO.
     */
    @Bean
    public DeviceDao deviceDao() {
        final var dao =  new MongoDbBasedDeviceDao(
                mongoClient(),
                registrationServiceProperties().getCollectionName(),
                tracer());
        healthCheckServer().registerHealthCheckResources(dao);
        return dao;
    }

    /**
     * Exposes the MongoDB registration service as a Spring bean.
     * <p>
     * This bean is defined as a prototype in order to make sure that each set of event senders
     * is used by a single dedicated registration service instance only. This is necessary because during start up,
     * the registration service will implicitly invoke {@link MessagingClientProvider#start()} in order
     * to establish the senders' connection to the messaging infrastructure. For the AMQP 1.0 based senders,
     * this connection needs to be established on the verticle's event loop thread in order to work properly.
     *
     * @return The MongoDB registration service.
     */
    @Bean
    @Scope("prototype")
    public RegistrationService registrationService() {

        final EdgeDeviceAutoProvisioner edgeDeviceAutoProvisioner = new EdgeDeviceAutoProvisioner(
                vertx(),
                deviceManagementService(),
                eventSenderProvider(),
                autoProvisionerConfigProperties(),
                tracer());

        final var service = new MongoDbBasedRegistrationService(deviceDao());
        service.setEdgeDeviceAutoProvisioner(edgeDeviceAutoProvisioner);
        return service;
    }

    /**
     * Creates a Mongo DB based device management service.
     *
     * @return The service.
     */
    @Bean
    public DeviceManagementService deviceManagementService() {
        return new MongoDbBasedDeviceManagementService(deviceDao(), credentialsDao(), registrationServiceProperties());
    }

    /**
     * Creates a Data Access Object for credentials data.
     *
     * @return The DAO.
     */
    @Bean
    public CredentialsDao credentialsDao() {

        final var properties = credentialsServiceProperties();
        final var encryptionHelper = Optional.ofNullable(properties.getEncryptionKeyFile())
                .map(this::fieldLevelEncryption)
                .orElse( FieldLevelEncryption.NOOP_ENCRYPTION);

        final var dao =  new MongoDbBasedCredentialsDao(
                mongoClient(),
                credentialsServiceProperties().getCollectionName(),
                tracer(),
                encryptionHelper);
        healthCheckServer().registerHealthCheckResources(dao);
        return dao;
    }

    /**
     * Exposes the MongoDB credentials service as a Spring bean.
     * <p>
     * This bean is defined as a prototype in order to make sure that each set of event senders
     * is used by a single dedicated registration service instance only. This is necessary because during start up,
     * the registration service will implicitly invoke {@link MessagingClientProvider#start()} in order
     * to establish the senders' connection to the messaging infrastructure. For the AMQP 1.0 based senders,
     * this connection needs to be established on the verticle's event loop thread in order to work properly.
     *
     * @return The service instance.
     */
    @Bean
    @Scope("prototype")
    public MongoDbBasedCredentialsService credentialsService() {

        final var provisioner = new DeviceAndGatewayAutoProvisioner(
                vertx(),
                deviceManagementService(),
                credentialsManagementService(),
                eventSenderProvider());

        final var service = new MongoDbBasedCredentialsService(
                credentialsDao(),
                credentialsServiceProperties());
        service.setTenantInformationService(tenantInformationService());
        service.setDeviceAndGatewayAutoProvisioner(provisioner);
        return service;
    }
    /**
     * Creates a Mongo DB based credentials management service.
     *
     * @return The service.
     */
    @Bean
    public CredentialsManagementService credentialsManagementService() {
        return new MongoDbBasedCredentialsManagementService(
                vertx(),
                credentialsDao(),
                credentialsServiceProperties(),
                passwordEncoder());
    }

    /**
     * Creates a Data Access Object for tenant data.
     *
     * @return The DAO.
     */
    @Bean
    public TenantDao tenantDao() {
        final var dao =  new MongoDbBasedTenantDao(
                mongoClient(),
                tenantServiceProperties().getCollectionName(),
                tracer());
        healthCheckServer().registerHealthCheckResources(dao);
        return dao;
    }

    /**
     * Exposes the MongoDB tenant service as a Spring bean.
     *
     * @return The service.
     */
    @Bean
    public TenantService tenantService() {
        return new MongoDbBasedTenantService(tenantDao(), tenantServiceProperties()
        );
    }

    /**
     * Creates a Tenant management service instance.
     *
     * @return The service.
     */
    @Bean
    public TenantManagementService tenantManagementService() {
        return new MongoDbBasedTenantManagementService(
                tenantDao(),
                tenantServiceProperties());
    }

    /**
     * Exposes a tenant information service instance as a Spring Bean.
     *
     * @return The service instance.
     */
    @Bean
    public TenantInformationService tenantInformationService() {
        return new DaoBasedTenantInformationService(tenantDao());
    }

    /**
     * Creates a new instance of an AMQP 1.0 protocol handler for Hono's <em>Device Registration</em> API.
     *
     * @return The handler.
     */
    @Bean
    @Scope("prototype")
    public AmqpEndpoint registrationAmqpEndpoint() {
        return new DelegatingRegistrationAmqpEndpoint<RegistrationService>(vertx(), registrationService());
    }

    /**
     * Creates a new instance of an AMQP 1.0 protocol handler for Hono's <em>Credentials</em> API.
     *
     * @return The handler.
     */
    @Bean
    @Scope("prototype")
    public AmqpEndpoint credentialsAmqpEndpoint() {
        return new DelegatingCredentialsAmqpEndpoint<CredentialsService>(vertx(), credentialsService());
    }

    /**
     * Creates a new instance of an AMQP 1.0 protocol handler for Hono's <em>Tenant</em> API.
     *
     * @return The handler.
     */
    @Bean
    @Scope("prototype")
    public AmqpEndpoint tenantAmqpEndpoint() {
        return new DelegatingTenantAmqpEndpoint<TenantService>(vertx(), tenantService());
    }

    //
    //
    // Management endpoints
    //
    //

    /**
     * Gets properties for configuring the HTTP based Device Registry Management endpoint.
     *
     * @return The properties.
     */
    @Qualifier(Constants.QUALIFIER_HTTP)
    @Bean
    @ConfigurationProperties(prefix = "hono.registry.http")
    public MongoDbBasedHttpServiceConfigProperties httpServerProperties() {
        return new MongoDbBasedHttpServiceConfigProperties();
    }

    /**
     * Creates a new server for exposing the device registry's AMQP 1.0 based
     * endpoints.
     *
     * @return The server.
     */
    @Bean(name = BEAN_NAME_HTTP_SERVER)
    @Scope("prototype")
    public DeviceRegistryHttpServer httpServer() {
        return new DeviceRegistryHttpServer();
    }

    /**
     * Exposes a factory for creating Device Connection service instances.
     *
     * @return The factory bean.
     */
    @Bean
    public ObjectFactoryCreatingFactoryBean httpServerFactory() {
        final ObjectFactoryCreatingFactoryBean factory = new ObjectFactoryCreatingFactoryBean();
        factory.setTargetBeanName(BEAN_NAME_HTTP_SERVER);
        return factory;
    }

    /**
     * Creates an authentication handler supporting the Basic authentication scheme for the HTTP based
     * Device Registry Management endpoint.
     *
     * @param httpServiceConfigProperties The properties for configuring the HTTP based device registry
     *                                    management endpoint.
     * @return The created handler if the {@link MongoDbBasedHttpServiceConfigProperties#isAuthenticationRequired()} 
     *         is {@code true} or {@code null} otherwise.
     * @see <a href="https://vertx.io/docs/vertx-auth-mongo/java/">Mongo auth provider docs</a>
     */
    @SuppressWarnings("deprecation")
    @Bean
    @Scope("prototype")
    public AuthenticationHandler createAuthHandler(final MongoDbBasedHttpServiceConfigProperties httpServiceConfigProperties) {
        if (httpServiceConfigProperties.isAuthenticationRequired()) {
            final var authConfig = httpServiceConfigProperties.getAuth();
            LOG.debug("creating AuthenticationHandler guarding access to registry's HTTP endpoint using configuration:{}{}",
                    System.lineSeparator(), authConfig);
            final var mongoAuthOptions = new MongoAuthenticationOptions();
            mongoAuthOptions.setCollectionName(authConfig.getCollectionName());
            mongoAuthOptions.setUsernameField(authConfig.getUsernameField());
            // this is a fix for what I believe is a bug in the MongoAuthenticationImpl class
            // where the usernameField and usernameCredentialField properties are used
            // inconsistently (and interchangeably)
            mongoAuthOptions.setUsernameCredentialField(authConfig.getUsernameField());
            mongoAuthOptions.setPasswordField(authConfig.getPasswordField());
            // this is a fix for what I believe is a bug in the MongoAuthenticationImpl class
            // where the passwordField and passwordCredentialField properties are used
            // inconsistently (and interchangeably)
            mongoAuthOptions.setPasswordCredentialField(authConfig.getPasswordField());
            final var hashStrategy = new DefaultHashStrategy();
            Optional.ofNullable(authConfig.getHashAlgorithm())
                .ifPresent(hashStrategy::setAlgorithm);
            Optional.ofNullable(authConfig.getSaltStyle())
                .ifPresent(hashStrategy::setSaltStyle);
            final var mongoAuth = new MongoAuthenticationImpl(
                    mongoClient(),
                    hashStrategy,
                    authConfig.getSaltField(),
                    mongoAuthOptions);
            return BasicAuthHandler.create(
                    mongoAuth,
                    httpServerProperties().getRealm());
        }
        return null;
    }

    /**
     * Creates a new instance of an HTTP protocol handler for the <em>devices</em> resources
     * of Hono's Device Registry Management API's.
     *
     * @return The handler.
     */
    @Bean
    @Scope("prototype")
    public HttpEndpoint deviceHttpEndpoint() {
        return new DelegatingDeviceManagementHttpEndpoint<DeviceManagementService>(vertx(), deviceManagementService());
    }

    /**
     * Creates a new instance of an HTTP protocol handler for the <em>credentials</em> resources
     * of Hono's Device Registry Management API's.
     *
     * @return The handler.
     */
    @Bean
    @Scope("prototype")
    public HttpEndpoint credentialsHttpEndpoint() {
        return new DelegatingCredentialsManagementHttpEndpoint<CredentialsManagementService>(
                vertx(),
                credentialsManagementService());
    }

    /**
     * Creates a new instance of an HTTP protocol handler for the <em>tenants</em> resources
     * of Hono's Device Registry Management API's.
     *
     * @return The handler.
     */
    @Bean
    public HttpEndpoint tenantHttpEndpoint() {
        return new DelegatingTenantManagementHttpEndpoint<TenantManagementService>(
                vertx(),
                tenantManagementService());
    }
}
