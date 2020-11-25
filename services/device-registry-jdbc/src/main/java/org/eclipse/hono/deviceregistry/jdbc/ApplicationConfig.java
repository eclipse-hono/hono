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

package org.eclipse.hono.deviceregistry.jdbc;

import java.io.IOException;
import java.util.Optional;

import org.eclipse.hono.auth.HonoPasswordEncoder;
import org.eclipse.hono.auth.SpringBasedHonoPasswordEncoder;
import org.eclipse.hono.config.ApplicationConfigProperties;
import org.eclipse.hono.config.ServerConfig;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.config.VertxProperties;
import org.eclipse.hono.deviceregistry.jdbc.config.DeviceServiceProperties;
import org.eclipse.hono.deviceregistry.jdbc.config.TenantServiceProperties;
import org.eclipse.hono.deviceregistry.jdbc.impl.CredentialsManagementServiceImpl;
import org.eclipse.hono.deviceregistry.jdbc.impl.CredentialsServiceImpl;
import org.eclipse.hono.deviceregistry.jdbc.impl.DeviceManagementServiceImpl;
import org.eclipse.hono.deviceregistry.jdbc.impl.RegistrationServiceImpl;
import org.eclipse.hono.deviceregistry.jdbc.impl.TenantManagementServiceImpl;
import org.eclipse.hono.deviceregistry.jdbc.impl.TenantServiceImpl;
import org.eclipse.hono.deviceregistry.server.DeviceRegistryAmqpServer;
import org.eclipse.hono.deviceregistry.server.DeviceRegistryHttpServer;
import org.eclipse.hono.deviceregistry.service.tenant.AutowiredTenantInformationService;
import org.eclipse.hono.deviceregistry.service.tenant.TenantInformationService;
import org.eclipse.hono.service.HealthCheckServer;
import org.eclipse.hono.service.VertxBasedHealthCheckServer;
import org.eclipse.hono.service.amqp.AmqpEndpoint;
import org.eclipse.hono.service.base.jdbc.config.JdbcDeviceStoreProperties;
import org.eclipse.hono.service.base.jdbc.config.JdbcProperties;
import org.eclipse.hono.service.base.jdbc.config.JdbcTenantStoreProperties;
import org.eclipse.hono.service.base.jdbc.store.device.DeviceStores;
import org.eclipse.hono.service.base.jdbc.store.device.TableAdapterStore;
import org.eclipse.hono.service.base.jdbc.store.device.TableManagementStore;
import org.eclipse.hono.service.base.jdbc.store.tenant.AdapterStore;
import org.eclipse.hono.service.base.jdbc.store.tenant.ManagementStore;
import org.eclipse.hono.service.base.jdbc.store.tenant.Stores;
import org.eclipse.hono.service.credentials.CredentialsService;
import org.eclipse.hono.service.credentials.DelegatingCredentialsAmqpEndpoint;
import org.eclipse.hono.service.http.HonoBasicAuthHandler;
import org.eclipse.hono.service.http.HttpEndpoint;
import org.eclipse.hono.service.http.HttpServiceConfigProperties;
import org.eclipse.hono.service.management.credentials.CredentialsManagementService;
import org.eclipse.hono.service.management.credentials.DelegatingCredentialsManagementHttpEndpoint;
import org.eclipse.hono.service.management.device.DelegatingDeviceManagementHttpEndpoint;
import org.eclipse.hono.service.management.device.DeviceManagementService;
import org.eclipse.hono.service.management.tenant.DelegatingTenantManagementHttpEndpoint;
import org.eclipse.hono.service.management.tenant.TenantManagementService;
import org.eclipse.hono.service.metric.MetricsTags;
import org.eclipse.hono.service.registration.DelegatingRegistrationAmqpEndpoint;
import org.eclipse.hono.service.registration.RegistrationService;
import org.eclipse.hono.service.tenant.DelegatingTenantAmqpEndpoint;
import org.eclipse.hono.service.tenant.TenantService;
import org.eclipse.hono.util.Constants;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ObjectFactoryCreatingFactoryBean;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Scope;

import io.micrometer.core.instrument.MeterRegistry;
import io.opentracing.Tracer;
import io.opentracing.contrib.tracerresolver.TracerResolver;
import io.opentracing.noop.NoopTracerFactory;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.auth.jdbc.JDBCAuth;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.web.handler.AuthHandler;

/**
 * Spring Boot configuration for the JDBC based device registry application.
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
     * Exposes a password encoder to use for encoding clear text passwords
     * and for matching password hashes.
     *
     * @return The encoder.
     */
    @Bean
    public HonoPasswordEncoder passwordEncoder() {
        return new SpringBasedHonoPasswordEncoder(deviceRegistryServiceProperties().getMaxBcryptCostfactor());
    }

    //
    //
    // JDBC store properties
    //
    //

    /**
     * Expose JDBC device registry service properties.
     *
     * @return The properties.
     */
    @Bean
    @ConfigurationProperties("hono.registry.jdbc")
    public JdbcDeviceStoreProperties devicesProperties() {
        return new JdbcDeviceStoreProperties();
    }

    /**
     * Expose JDBC tenant service properties.
     *
     * @return The properties.
     */
    @Bean
    @ConfigurationProperties("hono.tenant.jdbc")
    public JdbcTenantStoreProperties tenantsProperties() {
        return new JdbcTenantStoreProperties();
    }

    /**
     * Provider a new device backing store for the adapter facing service.
     *
     * @return A new store instance.
     * @throws IOException if reading the SQL configuration fails.
     */
    @Bean
    @Profile(Profiles.PROFILE_REGISTRY_ADAPTER)
    public TableAdapterStore devicesAdapterStore() throws IOException {
        return DeviceStores.store(vertx(), tracer(), devicesProperties(), JdbcDeviceStoreProperties::getAdapter, DeviceStores.adapterStoreFactory());
    }

    /**
     * Provider a new tenant backing store for the adapter facing service.
     *
     * @return A new store instance.
     * @throws IOException if reading the SQL configuration fails.
     */
    @Bean
    @Profile(Profiles.PROFILE_REGISTRY_ADAPTER + " & " + Profiles.PROFILE_TENANT_SERVICE)
    public AdapterStore tenantAdapterStore() throws IOException {
        return Stores.adapterStore(vertx(), tracer(), tenantsProperties().getAdapter());
    }

    /**
     * Provider a new device backing store for the management facing service.
     *
     * @return A new store instance.
     * @throws IOException if reading the SQL configuration fails.
     */
    @Bean
    @Profile(Profiles.PROFILE_REGISTRY_MANAGEMENT)
    public TableManagementStore devicesManagementStore() throws IOException {
        return DeviceStores.store(vertx(), tracer(), devicesProperties(), JdbcDeviceStoreProperties::getManagement, DeviceStores.managementStoreFactory());
    }

    /**
     * Provider a new tenant backing store for the management facing service.
     *
     * @return A new store instance.
     * @throws IOException if reading the SQL configuration fails.
     */
    @Bean
    @Profile(Profiles.PROFILE_REGISTRY_MANAGEMENT + " & " + Profiles.PROFILE_TENANT_SERVICE)
    public ManagementStore tenantManagementStore() throws IOException {
        return Stores.managementStore(vertx(), tracer(), tenantsProperties().getManagement());
    }

    //
    //
    // Service properties
    //
    //

    /**
     * Gets properties for configuring the device registry services.
     *
     * @return The properties.
     */
    @Bean
    @ConfigurationProperties("hono.registry.svc")
    public DeviceServiceProperties deviceRegistryServiceProperties() {
        return new DeviceServiceProperties();
    }

    /**
     * Gets properties for configuring the tenant services.
     *
     * @return The properties.
     */
    @Bean
    @ConfigurationProperties("hono.tenant.svc")
    @Profile(Profiles.PROFILE_TENANT_SERVICE)
    public TenantServiceProperties tenantServiceProperties() {
        return new TenantServiceProperties();
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
    @Profile(Profiles.PROFILE_REGISTRY_ADAPTER)
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
    @Profile(Profiles.PROFILE_REGISTRY_ADAPTER)
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
     * Provide a tenant information service, backed by the JDBC tenant service instance.
     *
     * @return The bean instance.
     */
    @Bean
    @Scope("prototype")
    @Lazy
    public TenantInformationService tenantInformationService() {
        return new AutowiredTenantInformationService();
    }

    /**
     * Provide a registration service.
     *
     * @return The bean instance.
     * @throws IOException if reading the SQL configuration fails.
     */
    @Bean
    @Scope("prototype")
    @Profile(Profiles.PROFILE_REGISTRY_ADAPTER)
    public RegistrationService registrationService() throws IOException {
        return new RegistrationServiceImpl(devicesAdapterStore());
    }

    /**
     * Provide a credentials service.
     *
     * @return The bean instance.
     * @throws IOException if reading the SQL configuration fails.
     */
    @Bean
    @Scope("prototype")
    @Profile(Profiles.PROFILE_REGISTRY_ADAPTER)
    public CredentialsService credentialsService() throws IOException {
        return new CredentialsServiceImpl(devicesAdapterStore(), deviceRegistryServiceProperties());
    }

    /**
     * Provide a tenant service.
     *
     * @return The bean instance.
     * @throws IOException if reading the SQL configuration fails.
     */
    @Bean
    @Scope("prototype")
    @Profile(Profiles.PROFILE_REGISTRY_ADAPTER + " & " + Profiles.PROFILE_TENANT_SERVICE)
    public TenantService tenantService() throws IOException {
        return new TenantServiceImpl(tenantAdapterStore(), tenantServiceProperties());
    }

    /**
     * Provide a registration management service.
     *
     * @return The bean instance.
     * @throws IOException if reading the SQL configuration fails.
     */
    @Bean
    @Scope("prototype")
    @Profile(Profiles.PROFILE_REGISTRY_MANAGEMENT)
    public DeviceManagementService registrationManagementService() throws IOException {
        return new DeviceManagementServiceImpl(devicesManagementStore(), deviceRegistryServiceProperties());
    }

    /**
     * Provide a credentials management service.
     *
     * @return The bean instance.
     * @throws IOException if reading the SQL configuration fails.
     */
    @Bean
    @Scope("prototype")
    @Profile(Profiles.PROFILE_REGISTRY_MANAGEMENT)
    public CredentialsManagementService credentialsManagementService() throws IOException {
        return new CredentialsManagementServiceImpl(vertx(), passwordEncoder(), devicesManagementStore(), deviceRegistryServiceProperties());
    }

    /**
     * Provide a tenant management service.
     *
     * @return The bean instance.
     * @throws IOException if reading the SQL configuration fails.
     */
    @Bean
    @Scope("prototype")
    @Profile(Profiles.PROFILE_REGISTRY_MANAGEMENT + " & " + Profiles.PROFILE_TENANT_SERVICE)
    public TenantManagementService tenantManagementService() throws IOException {
        return new TenantManagementServiceImpl(tenantManagementStore(), tenantServiceProperties());
    }

    /**
     * Creates a new instance of an AMQP 1.0 protocol handler for Hono's <em>Device Registration</em> API.
     *
     * @return The handler.
     * @throws IOException if reading the SQL configuration fails.
     */
    @Bean
    @Scope("prototype")
    @ConditionalOnBean(RegistrationService.class)
    public AmqpEndpoint registrationAmqpEndpoint() throws IOException {
        return new DelegatingRegistrationAmqpEndpoint<>(vertx(), registrationService());
    }

    /**
     * Creates a new instance of an AMQP 1.0 protocol handler for Hono's <em>Credentials</em> API.
     *
     * @return The handler.
     * @throws IOException if reading the SQL configuration fails.
     */
    @Bean
    @Scope("prototype")
    @ConditionalOnBean(CredentialsService.class)
    public AmqpEndpoint credentialsAmqpEndpoint() throws IOException {
        return new DelegatingCredentialsAmqpEndpoint<>(vertx(), credentialsService());
    }

    /**
     * Creates a new instance of an AMQP 1.0 protocol handler for Hono's <em>Tenant</em> API.
     *
     * @return The handler.
     * @throws IOException if reading the SQL configuration fails.
     */
    @Bean
    @Scope("prototype")
    @ConditionalOnBean(TenantService.class)
    public AmqpEndpoint tenantAmqpEndpoint() throws IOException {
        return new DelegatingTenantAmqpEndpoint<>(vertx(), tenantService());
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
    @Bean
    @ConfigurationProperties(prefix = "hono.registry.http")
    @Profile(Profiles.PROFILE_REGISTRY_MANAGEMENT)
    @Qualifier(Constants.QUALIFIER_HTTP)
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
    @Profile(Profiles.PROFILE_REGISTRY_MANAGEMENT)
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
     * Provide an auth provider, backed by vert.x {@link JDBCAuth}.
     *
     * @return The auth provider instance.
     */
    @Bean
    @Scope("prototype")
    public AuthProvider authProvider() {
        final JDBCClient client = JdbcProperties.dataSource(vertx(), devicesProperties().getManagement());
        return JDBCAuth.create(vertx(), client);
    }

    /**
     * Creates a new instance of an auth handler to provide basic authentication for the 
     * HTTP based Device Registry Management endpoint.
     * <p>
     * This creates an instance of the {@link HonoBasicAuthHandler} with the auth provider returned by
     * {@link #authProvider()} if the property corresponding to {@link HttpServiceConfigProperties#isAuthenticationRequired()}
     * is set to {@code true}.
     *
     * @param httpServiceConfigProperties The properties for configuring the HTTP based device registry
     *                                    management endpoint.
     * @return The auth handler if the {@link HttpServiceConfigProperties#isAuthenticationRequired()} 
     *         is {@code true} or {@code null} otherwise.
     * @see <a href="https://vertx.io/docs/vertx-auth-jdbc/java/">JDBC Auth Provider docs</a>
     */
    @Bean
    @Scope("prototype")
    public AuthHandler createAuthHandler(final HttpServiceConfigProperties httpServiceConfigProperties) {
        if (httpServiceConfigProperties != null && httpServiceConfigProperties.isAuthenticationRequired()) {
            return new HonoBasicAuthHandler(
                    authProvider(),
                    httpServerProperties().getRealm(),
                    tracer());
        }
        return null;
    }

    /**
     * Creates a new instance of an HTTP protocol handler for the <em>devices</em> resources
     * of Hono's Device Registry Management API's.
     *
     * @return The handler.
     * @throws IOException if reading the SQL configuration fails.
     */
    @Bean
    @Scope("prototype")
    @ConditionalOnBean(DeviceManagementService.class)
    public HttpEndpoint deviceHttpEndpoint() throws IOException {
        return new DelegatingDeviceManagementHttpEndpoint<>(vertx(), registrationManagementService());
    }

    /**
     * Creates a new instance of an HTTP protocol handler for the <em>credentials</em> resources
     * of Hono's Device Registry Management API's.
     *
     * @return The handler.
     * @throws IOException if reading the SQL configuration fails.
     */
    @Bean
    @Scope("prototype")
    @ConditionalOnBean(CredentialsManagementService.class)
    public HttpEndpoint credentialsHttpEndpoint() throws IOException {
        return new DelegatingCredentialsManagementHttpEndpoint<>(vertx(),  credentialsManagementService());
    }

    /**
     * Creates a new instance of an HTTP protocol handler for the <em>tenants</em> resources
     * of Hono's Device Registry Management API's.
     *
     * @return The handler.
     * @throws IOException if reading the SQL configuration fails.
     */
    @Bean
    @Scope("prototype")
    @ConditionalOnBean(TenantManagementService.class)
    public HttpEndpoint tenantHttpEndpoint() throws IOException {
        return new DelegatingTenantManagementHttpEndpoint<>(vertx(), tenantManagementService());
    }
}
