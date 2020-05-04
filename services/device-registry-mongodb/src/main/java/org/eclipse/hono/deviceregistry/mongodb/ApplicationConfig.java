/*******************************************************************************
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

import java.util.Optional;

import org.eclipse.hono.config.ApplicationConfigProperties;
import org.eclipse.hono.config.ServerConfig;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.config.VertxProperties;
import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbBasedRegistrationConfigProperties;
import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbConfigProperties;
import org.eclipse.hono.deviceregistry.mongodb.service.MongoDbBasedCredentialsService;
import org.eclipse.hono.deviceregistry.mongodb.service.MongoDbBasedDeviceBackend;
import org.eclipse.hono.deviceregistry.mongodb.service.MongoDbBasedRegistrationService;
import org.eclipse.hono.deviceregistry.mongodb.utils.MongoDbCallExecutor;
import org.eclipse.hono.deviceregistry.server.DeviceRegistryAmqpServer;
import org.eclipse.hono.deviceregistry.server.DeviceRegistryHttpServer;
import org.eclipse.hono.service.HealthCheckServer;
import org.eclipse.hono.service.VertxBasedHealthCheckServer;
import org.eclipse.hono.service.amqp.AmqpEndpoint;
import org.eclipse.hono.service.credentials.CredentialsService;
import org.eclipse.hono.service.credentials.DelegatingCredentialsAmqpEndpoint;
import org.eclipse.hono.service.http.HonoBasicAuthHandler;
import org.eclipse.hono.service.http.HttpEndpoint;
import org.eclipse.hono.service.http.HttpServiceConfigProperties;
import org.eclipse.hono.service.management.credentials.CredentialsManagementService;
import org.eclipse.hono.service.management.credentials.DelegatingCredentialsManagementHttpEndpoint;
import org.eclipse.hono.service.management.device.DelegatingDeviceManagementHttpEndpoint;
import org.eclipse.hono.service.management.device.DeviceManagementService;
import org.eclipse.hono.service.metric.MetricsTags;
import org.eclipse.hono.service.registration.DelegatingRegistrationAmqpEndpoint;
import org.eclipse.hono.service.registration.RegistrationService;
import org.eclipse.hono.util.Constants;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ObjectFactoryCreatingFactoryBean;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import io.micrometer.core.instrument.MeterRegistry;
import io.opentracing.Tracer;
import io.opentracing.contrib.tracerresolver.TracerResolver;
import io.opentracing.noop.NoopTracerFactory;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.mongo.MongoAuth;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.handler.AuthHandler;

/**
 * Spring Boot configuration for the mongodb based device registry application.
 */
@Configuration
public class ApplicationConfig {

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
    public Tracer getTracer() {

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
     * Gets a {@link MongoDbCallExecutor} instance containing helper methods for mongodb interaction.
     *
     * @return An instance of the helper class {@link MongoDbCallExecutor}.
     */
    @Bean
    @Scope("prototype")
    public MongoDbCallExecutor mongoDBCallExecutor() {
        return new MongoDbCallExecutor(vertx(), mongoClient());
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
    public MongoDbBasedRegistrationConfigProperties serviceProperties() {
        return new MongoDbBasedRegistrationConfigProperties();
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
     * Exposes the MongoDB registration service as a Spring bean.
     *
     * @return The MongoDB registration service.
     */
    @Bean
    @Scope("prototype")
    public MongoDbBasedRegistrationService registrationService() {
        return new MongoDbBasedRegistrationService();
    }

    /**
     * Exposes the MongoDB credentials service as a Spring bean.
     *
     * @return The MongoDB credentials service.
     */
    @Bean
    @Scope("prototype")
    public MongoDbBasedCredentialsService credentialsService() {
        return new MongoDbBasedCredentialsService();
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
        // need to use backend service because the Credentials service's "get" operation
        // supports auto-provisioning and thus needs to be able to create a device on the fly
        final MongoDbBasedDeviceBackend service = new MongoDbBasedDeviceBackend(registrationService(),
                credentialsService());
        return new DelegatingCredentialsAmqpEndpoint<CredentialsService>(vertx(), service);
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
    @Qualifier(Constants.QUALIFIER_REST)
    @Bean
    @ConfigurationProperties(prefix = "hono.registry.http")
    public HttpServiceConfigProperties httpServerProperties() {
        return new HttpServiceConfigProperties();
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
     * Creates a new instance of an auth handler to provide basic authentication for the 
     * HTTP based Device Registry Management endpoint.
     * <p>
     * This creates an instance of the {@link HonoBasicAuthHandler} with an auth provider of type
     * {@link MongoAuth} if the property corresponding to {@link HttpServiceConfigProperties#isAuthenticationRequired()}
     * is set to {@code true}.
     *
     * @param httpServiceConfigProperties The properties for configuring the HTTP based device registry
     *                                    management endpoint.
     * @return The auth handler if the {@link HttpServiceConfigProperties#isAuthenticationRequired()} 
     *         is {@code true} or {@code null} otherwise.
     * @see <a href="https://vertx.io/docs/vertx-auth-mongo/java/">Mongo auth provider docs</a>
     */
    @Bean
    @Scope("prototype")
    public AuthHandler createAuthHandler(final HttpServiceConfigProperties httpServiceConfigProperties) {
        if (httpServiceConfigProperties != null && httpServiceConfigProperties.isAuthenticationRequired()) {
            return new HonoBasicAuthHandler(
                    MongoAuth.create(mongoClient(), new JsonObject()),
                    httpServerProperties().getRealm(),
                    getTracer());
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
        // need to use backend service because creating a device implicitly
        // creates (empty) credentials as well for the device
        final MongoDbBasedDeviceBackend service = new MongoDbBasedDeviceBackend(registrationService(),
                credentialsService());
        return new DelegatingDeviceManagementHttpEndpoint<DeviceManagementService>(vertx(), service);
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
        return new DelegatingCredentialsManagementHttpEndpoint<CredentialsManagementService>(vertx(),
                credentialsService());
    }
}
