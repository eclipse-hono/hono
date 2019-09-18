/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.tests;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.service.credentials.AbstractCredentialsServiceTest;
import org.eclipse.hono.service.management.credentials.PasswordCredential;
import org.eclipse.hono.service.management.device.Device;
import org.eclipse.hono.util.BufferResult;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.TimeUntilDisconnectNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxTestContext;

/**
 * A helper class for integration tests.
 *
 */
public final class IntegrationTestSupport {

    /**
     * The default port exposed by the AMQP adapter.
     */
    public static final int    DEFAULT_AMQP_PORT = 5672;
    /**
     * The default TLS secured port exposed by the AMQP adapter.
     */
    public static final int    DEFAULT_AMQPS_PORT = 5671;
    /**
     * The default port exposed by the CoAP adapter.
     */
    public static final int    DEFAULT_COAP_PORT = 5683;
    /**
     * The default DTLS secured port exposed by the CoAP adapter.
     */
    public static final int    DEFAULT_COAPS_PORT = 5684;
    /**
     * The default AMQP port exposed by the device registry.
     */
    public static final int    DEFAULT_DEVICEREGISTRY_AMQP_PORT = 25672;
    /**
     * The default HTTP port exposed by the device registry.
     */
    public static final int    DEFAULT_DEVICEREGISTRY_HTTP_PORT = 28080;
    /**
     * The default AMQP port exposed by the AMQP Messaging Network.
     */
    public static final int    DEFAULT_DOWNSTREAM_PORT = 15672;
    /**
     * The default IP address that services and adapters bind their endpoints to.
     */
    public static final String DEFAULT_HOST = InetAddress.getLoopbackAddress().getHostAddress();
    /**
     * The default port exposed by the HTTP adapter.
     */
    public static final int    DEFAULT_HTTP_PORT = 8080;
    /**
     * The default TLS secured port exposed by the HTTP adapter.
     */
    public static final int    DEFAULT_HTTPS_PORT = 8443;
    /**
     * The default number of iterations to use with the BCrypt hash algorithm.
     */
    public static final int    DEFAULT_MAX_BCRYPT_ITERATIONS = 10;
    /**
     * The default port exposed by the MQTT adapter.
     */
    public static final int    DEFAULT_MQTT_PORT = 1883;
    /**
     * The default TLS secured port exposed by the MQTT adapter.
     */
    public static final int    DEFAULT_MQTTS_PORT = 8883;

    /**
     * The name of the system property to use for setting the IP address of the Auth service.
     */
    public static final String PROPERTY_AUTH_HOST = "auth.host";
    /**
     * The name of the system property to use for setting the port number that the Auth service
     * should listen on.
     */
    public static final String PROPERTY_AUTH_PORT = "auth.amqp.port";
    /**
     * The name of the system property to use for setting the username that protocol adapters
     * use for authenticating to the Device Registry in a SASL handshake.
     */
    public static final String PROPERTY_HONO_USERNAME = "hono.username";
    /**
     * The name of the system property to use for setting the password that protocol adapters
     * use for authenticating to the Device Registry in a SASL handshake.
     */
    public static final String PROPERTY_HONO_PASSWORD = "hono.password";
    /**
     * The name of the system property to use for setting the username of the principal that
     * has access to all tenants.
     */
    public static final String PROPERTY_TENANT_ADMIN_USERNAME = "tenant.admin.username";
    /**
     * The name of the system property to use for setting the password of the principal that
     * has access to all tenants.
     */
    public static final String PROPERTY_TENANT_ADMIN_PASSWORD = "tenant.admin.password";
    /**
     * The name of the system property to use for setting the IP address of the Device Registry.
     */
    public static final String PROPERTY_DEVICEREGISTRY_HOST = "deviceregistry.host";
    /**
     * The name of the system property to use for setting the port number that the Device Registry
     * should listen on for AMQP connections.
     */
    public static final String PROPERTY_DEVICEREGISTRY_AMQP_PORT = "deviceregistry.amqp.port";
    /**
     * The name of the system property to use for setting the port number that the Device Registry
     * should listen on for HTTP connections.
     */
    public static final String PROPERTY_DEVICEREGISTRY_HTTP_PORT = "deviceregistry.http.port";
    /**
     * The name of the system property to use for indicating whether the Device Registry supports
     * gateway mode.
     */
    public static final String PROPERTY_DEVICEREGISTRY_SUPPORTS_GW_MODE = "deviceregistry.supportsGatewayMode";
    /**
     * The name of the system property to use for setting the IP address of the AMQP Messaging Network.
     */
    public static final String PROPERTY_DOWNSTREAM_HOST = "downstream.host";
    /**
     * The name of the system property to use for setting the port number that the AMQP Messaging
     * Network should listen on for connections.
     */
    public static final String PROPERTY_DOWNSTREAM_PORT = "downstream.amqp.port";
    /**
     * The name of the system property to use for setting the username for authenticating to
     * the AMQP Messaging Network.
     */
    public static final String PROPERTY_DOWNSTREAM_USERNAME = "downstream.username";
    /**
     * The name of the system property to use for setting the password for authenticating to
     * the AMQP Messaging Network.
     */
    public static final String PROPERTY_DOWNSTREAM_PASSWORD = "downstream.password";
    /**
     * The name of the system property to use for setting the IP address of the CoAP protocol adapter.
     */
    public static final String PROPERTY_COAP_HOST = "coap.host";
    /**
     * The name of the system property to use for setting the port number that the CoAP adapter
     * should listen on for requests.
     */
    public static final String PROPERTY_COAP_PORT = "coap.port";
    /**
     * The name of the system property to use for setting the port number that the CoAP adapter
     * should listen on for secure requests.
     */
    public static final String PROPERTY_COAPS_PORT = "coaps.port";
    /**
     * The name of the system property to use for setting the IP address of the HTTP protocol adapter.
     */
    public static final String PROPERTY_HTTP_HOST = "http.host";
    /**
     * The name of the system property to use for setting the port number that the HTTP adapter
     * should listen on for requests.
     */
    public static final String PROPERTY_HTTP_PORT = "http.port";
    /**
     * The name of the system property to use for setting the port number that the HTTP adapter
     * should listen on for secure requests.
     */
    public static final String PROPERTY_HTTPS_PORT = "https.port";
    /**
     * The name of the system property to use for setting the IP address of the MQTT protocol adapter.
     */
    public static final String PROPERTY_MQTT_HOST = "mqtt.host";
    /**
     * The name of the system property to use for setting the port number that the MQTT adapter
     * should listen on for connections.
     */
    public static final String PROPERTY_MQTT_PORT = "mqtt.port";
    /**
     * The name of the system property to use for setting the port number that the MQTT adapter
     * should listen on for secure connections.
     */
    public static final String PROPERTY_MQTTS_PORT = "mqtts.port";
    /**
     * The name of the system property to use for setting the IP address of the AMQP protocol adapter.
     */
    public static final String PROPERTY_AMQP_HOST = "adapter.amqp.host";
    /**
     * The name of the system property to use for setting the port number that the AMQP adapter
     * should listen on for connections.
     */
    public static final String PROPERTY_AMQP_PORT = "adapter.amqp.port";
    /**
     * The name of the system property to use for setting the port number that the AMQP adapter
     * should listen on for secure connections.
     */
    public static final String PROPERTY_AMQPS_PORT = "adapter.amqps.port";
    /**
     * The name of the system property to use for setting the maximum number of BCrypt iterations supported
     * by Hono.
     */
    public static final String PROPERTY_MAX_BCRYPT_ITERATIONS = "max.bcrypt.iterations";


    /**
     * The IP address of the Auth service.
     */
    public static final String AUTH_HOST = System.getProperty(PROPERTY_AUTH_HOST, DEFAULT_HOST);
    /**
     * The port number that the Auth service listens on.
     */
    public static final int    AUTH_PORT = Integer.getInteger(PROPERTY_AUTH_PORT, Constants.PORT_AMQP);

    /**
     * The username of the principal that has access to the DEFAULT_TENANT only.
     */
    public static final String HONO_USER = System.getProperty(PROPERTY_HONO_USERNAME);
    /**
     * The password of the principal that has access to the DEFAULT_TENANT only.
     */
    public static final String HONO_PWD = System.getProperty(PROPERTY_HONO_PASSWORD);
    /**
     * The username of the principal that has access to all tenants.
     */
    public static final String TENANT_ADMIN_USER = System.getProperty(PROPERTY_TENANT_ADMIN_USERNAME);
    /**
     * The password of the principal that has access to all tenants..
     */
    public static final String TENANT_ADMIN_PWD = System.getProperty(PROPERTY_TENANT_ADMIN_PASSWORD);


    /**
     * The IP address of the Device Registry.
     */
    public static final String HONO_DEVICEREGISTRY_HOST = System.getProperty(PROPERTY_DEVICEREGISTRY_HOST, DEFAULT_HOST);
    /**
     * The port number that the Device Registry listens on for AMQP connections.
     */
    public static final int    HONO_DEVICEREGISTRY_AMQP_PORT = Integer.getInteger(PROPERTY_DEVICEREGISTRY_AMQP_PORT, DEFAULT_DEVICEREGISTRY_AMQP_PORT);
    /**
     * The port number that the Device Registry listens on for HTTP requests.
     */
    public static final int    HONO_DEVICEREGISTRY_HTTP_PORT = Integer.getInteger(PROPERTY_DEVICEREGISTRY_HTTP_PORT, DEFAULT_DEVICEREGISTRY_HTTP_PORT);

    /**
     * The IP address of the AMQP Messaging Network.
     */
    public static final String DOWNSTREAM_HOST = System.getProperty(PROPERTY_DOWNSTREAM_HOST, DEFAULT_HOST);
    /**
     * The port number that the AMQP Messaging Network listens on for connections.
     */
    public static final int    DOWNSTREAM_PORT = Integer.getInteger(PROPERTY_DOWNSTREAM_PORT, DEFAULT_DOWNSTREAM_PORT);
    /**
     * The username that applications use for authenticating to the AMQP Messaging Network.
     */
    public static final String DOWNSTREAM_USER = System.getProperty(PROPERTY_DOWNSTREAM_USERNAME);
    /**
     * The password that applications use for authenticating to the AMQP Messaging Network.
     */
    public static final String DOWNSTREAM_PWD = System.getProperty(PROPERTY_DOWNSTREAM_PASSWORD);
    /**
     * The username that applications use for authenticating to the AMQP Messaging Network when
     * requiring access to the default tenant only.
     */
    public static final String RESTRICTED_CONSUMER_NAME = "user1@HONO";
    /**
     * The password that applications use for authenticating to the AMQP Messaging Network when
     * requiring access to the default tenant only.
     */
    public static final String RESTRICTED_CONSUMER_PWD = "pw";

    /**
     * The IP address of the CoAP protocol adapter.
     */
    public static final String COAP_HOST = System.getProperty(PROPERTY_COAP_HOST, DEFAULT_HOST);
    /**
     * The  port number that the CoAP adapter listens on for requests.
     */
    public static final int    COAP_PORT = Integer.getInteger(PROPERTY_COAP_PORT, DEFAULT_COAP_PORT);
    /**
     * The  port number that the CoAP adapter listens on for secure requests.
     */
    public static final int    COAPS_PORT = Integer.getInteger(PROPERTY_COAPS_PORT, DEFAULT_COAPS_PORT);
    /**
     * The IP address of the HTTP protocol adapter.
     */
    public static final String HTTP_HOST = System.getProperty(PROPERTY_HTTP_HOST, DEFAULT_HOST);
    /**
     * The  port number that the HTTP adapter listens on for requests.
     */
    public static final int    HTTP_PORT = Integer.getInteger(PROPERTY_HTTP_PORT, DEFAULT_HTTP_PORT);
    /**
     * The  port number that the HTTP adapter listens on for secure requests.
     */
    public static final int    HTTPS_PORT = Integer.getInteger(PROPERTY_HTTPS_PORT, DEFAULT_HTTPS_PORT);
    /**
     * The IP address of the MQTT protocol adapter.
     */
    public static final String MQTT_HOST = System.getProperty(PROPERTY_MQTT_HOST, DEFAULT_HOST);
    /**
     * The  port number that the MQTT adapter listens on for connections.
     */
    public static final int    MQTT_PORT = Integer.getInteger(PROPERTY_MQTT_PORT, DEFAULT_MQTT_PORT);
    /**
     * The  port number that the MQTT adapter listens on for secure connections.
     */
    public static final int    MQTTS_PORT = Integer.getInteger(PROPERTY_MQTTS_PORT, DEFAULT_MQTTS_PORT);
    /**
     * The IP address of the AMQP protocol adapter.
     */
    public static final String AMQP_HOST = System.getProperty(PROPERTY_AMQP_HOST, DEFAULT_HOST);
    /**
     * The  port number that the AMQP adapter listens on for connections.
     */
    public static final int    AMQP_PORT = Integer.getInteger(PROPERTY_AMQP_PORT, DEFAULT_AMQP_PORT);
    /**
     * The  port number that the AMQP adapter listens on for secure connections.
     */
    public static final int    AMQPS_PORT = Integer.getInteger(PROPERTY_AMQPS_PORT, DEFAULT_AMQPS_PORT);

    /**
     * The number of messages to send by default in protocol adapter tests.
     */
    public static final int    MSG_COUNT = Integer.getInteger("msg.count", 400);

    /**
     * The maximum number of BCrypt iterations supported by Hono.
     */
    public static final int    MAX_BCRYPT_ITERATIONS = Integer.getInteger(PROPERTY_MAX_BCRYPT_ITERATIONS, DEFAULT_MAX_BCRYPT_ITERATIONS);

    /**
     * The absolute path to the trust store to use for establishing secure connections with Hono.
     */
    public static final String TRUST_STORE_PATH = System.getProperty("trust-store.path");

    /**
     * Pattern used for the <em>name</em> field of the {@code @ParameterizedTest} annotation.
     */
    public static final String PARAMETERIZED_TEST_NAME_PATTERN = "{displayName} [{index}]; parameters: {arguments}";

    private static final Logger LOGGER = LoggerFactory.getLogger(IntegrationTestSupport.class);
    private static final BCryptPasswordEncoder bcryptPwdEncoder = new BCryptPasswordEncoder(4);

    /**
     * A client for managing tenants/devices/credentials.
     */
    public DeviceRegistryHttpClient registry;
    /**
     * A client for connecting to Hono's north bound APIs
     * via the AMQP Messaging Network.
     */
    public IntegrationTestApplicationClientFactory applicationClientFactory;

    private final Set<String> tenantsToDelete = new HashSet<>();
    private final Map<String, Set<String>> devicesToDelete = new HashMap<>();
    private final Vertx vertx;
    private final boolean gatewayModeSupported;

    /**
     * Creates a new helper instance.
     * 
     * @param vertx The vert.x instance.
     * @throws NullPointerException if vert.x is {@code null}.
     */
    public IntegrationTestSupport(final Vertx vertx) {
        this.vertx = Objects.requireNonNull(vertx);
        final String gatewayModeFlag = System.getProperty(PROPERTY_DEVICEREGISTRY_SUPPORTS_GW_MODE, "true");
        gatewayModeSupported = Boolean.parseBoolean(gatewayModeFlag);
    }

    private static ClientConfigProperties getClientConfigProperties(
            final String host,
            final int port,
            final String username,
            final String password) {

        final ClientConfigProperties props = new ClientConfigProperties();
        props.setHost(host);
        props.setPort(port);
        props.setUsername(username);
        props.setPassword(password);
        return props;
    }

    /**
     * Creates properties for connecting to the AMQP Messaging Network.
     * 
     * @return The properties.
     */
    public static ClientConfigProperties getMessagingNetworkProperties() {

        final ClientConfigProperties props = getClientConfigProperties(
                IntegrationTestSupport.DOWNSTREAM_HOST,
                IntegrationTestSupport.DOWNSTREAM_PORT,
                IntegrationTestSupport.DOWNSTREAM_USER,
                IntegrationTestSupport.DOWNSTREAM_PWD);
        props.setFlowLatency(200);
        return props;
    }

    /**
     * Creates properties for connecting to the AMQP protocol adapter.
     * 
     * @param username The username to use for authenticating to the adapter.
     * @param password The password to use for authenticating to the adapter.
     * @return The properties.
     */
    public static ClientConfigProperties getAmqpAdapterProperties(final String username, final String password) {

        final ClientConfigProperties props = getClientConfigProperties(
                IntegrationTestSupport.AMQP_HOST,
                IntegrationTestSupport.AMQP_PORT,
                username,
                password);
        props.setFlowLatency(200);
        return props;
    }

    /**
     * Creates properties for connecting to the device registry.
     * 
     * @param username The username to use for authenticating to the device registry.
     * @param password The password to use for authenticating to the device registry.
     * @return The properties.
     */
    public static ClientConfigProperties getDeviceRegistryProperties(final String username, final String password) {

        final ClientConfigProperties props = getClientConfigProperties(
                IntegrationTestSupport.HONO_DEVICEREGISTRY_HOST,
                IntegrationTestSupport.HONO_DEVICEREGISTRY_AMQP_PORT,
                username,
                password);
        return props;
    }


    /**
     * Connects to the AMQP 1.0 Messaging Network.
     * <p>
     * Also creates an HTTP client for accessing the Device Registry.
     * 
     * @param ctx The vert.x test context.
     */
    public void init(final TestContext ctx) {

        init(ctx, getMessagingNetworkProperties());
    }

    /**
     * Connects to the AMQP 1.0 Messaging Network.
     * <p>
     * Also creates an HTTP client for accessing the Device Registry.
     * 
     * @param ctx The vert.x test context.
     * @param downstreamProps The properties for connecting to the AMQP Messaging
     *                           Network.
     */
    public void init(final TestContext ctx, final ClientConfigProperties downstreamProps) {

        initRegistryClient();
        final Async amqpNetworkConnection = ctx.async();
        applicationClientFactory = IntegrationTestApplicationClientFactory.create(HonoConnection.newConnection(vertx, downstreamProps));
        applicationClientFactory.connect().setHandler(ctx.asyncAssertSuccess(ok -> {
            LOGGER.info("connected to AMQP Messaging Network [{}:{}]", downstreamProps.getHost(), downstreamProps.getPort());
            amqpNetworkConnection.complete();
        }));
        amqpNetworkConnection.await();
    }

    /**
     * Connects to the AMQP 1.0 Messaging Network.
     * <p>
     * Also creates an HTTP client for accessing the Device Registry.
     * 
     * @return A future indicating the outcome of the operation.
     */
    public Future<?> init() {

        return init(getMessagingNetworkProperties());
    }

    /**
     * Connects to the AMQP 1.0 Messaging Network.
     * <p>
     * Also creates an HTTP client for accessing the Device Registry.
     * 
     * @param downstreamProps The properties for connecting to the AMQP Messaging
     *                           Network.
     * @return A future indicating the outcome of the operation.
     */
    public Future<?> init(final ClientConfigProperties downstreamProps) {

        initRegistryClient();
        applicationClientFactory = IntegrationTestApplicationClientFactory.create(HonoConnection.newConnection(vertx, downstreamProps));
        return applicationClientFactory.connect()
                .map(con -> {
                    LOGGER.info("connected to AMQP Messaging Network [{}:{}]", downstreamProps.getHost(), downstreamProps.getPort());
                    return Future.succeededFuture();
                });
    }

    /**
     * Creates an HTTP client for accessing the Device Registry.
     */
    public void initRegistryClient() {

        registry = new DeviceRegistryHttpClient(
                vertx,
                IntegrationTestSupport.HONO_DEVICEREGISTRY_HOST,
                IntegrationTestSupport.HONO_DEVICEREGISTRY_HTTP_PORT);
    }

    /**
     * Checks if the Device Registry supports devices connecting via gateways.
     * 
     * @return {@code true} if the registry supports gateway mode.
     */
    public boolean isGatewayModeSupported() {
        return gatewayModeSupported;
    }

    /**
     * Deletes all temporary objects from the Device Registry which
     * have been created during the last test execution.
     * 
     * @param ctx The vert.x context.
     */
    public void deleteObjects(final TestContext ctx) {

        devicesToDelete.forEach((tenantId, devices) -> {
            devices.forEach(deviceId -> {
                final Async deletion = ctx.async();
                registry.deregisterDevice(tenantId, deviceId).setHandler(ok -> deletion.complete());
                deletion.await(1000);
            });
        });
        devicesToDelete.clear();

        tenantsToDelete.forEach(tenantId -> {
            final Async deletion = ctx.async();
            registry.removeTenant(tenantId).setHandler(ok -> deletion.complete());
            deletion.await(1000);
        });
        tenantsToDelete.clear();
    }

    /**
     * Deletes all temporary objects from the Device Registry which
     * have been created during the last test execution.
     * 
     * @param ctx The vert.x context.
     */
    public void deleteObjects(final VertxTestContext ctx) {

        if (!devicesToDelete.isEmpty()) {
            devicesToDelete.forEach((tenantId, devices) -> {
                final Checkpoint deviceDeletion = ctx.checkpoint(devices.size());
                devices.forEach(deviceId -> {
                    registry.deregisterDevice(tenantId, deviceId).setHandler(ok -> deviceDeletion.flag());
                });
                LOGGER.debug("deleted {} devices from tenant {}", devicesToDelete.size(), tenantId);
            });
            devicesToDelete.clear();
        }

        if (!tenantsToDelete.isEmpty()) {
            final Checkpoint tenantDeletion = ctx.checkpoint(tenantsToDelete.size());
            tenantsToDelete.forEach(tenantId -> {
                registry.removeTenant(tenantId).setHandler(ok -> tenantDeletion.flag());
            });
            LOGGER.debug("deleted {} tenants", tenantsToDelete.size());
            tenantsToDelete.clear();
        }
    }

    /**
     * Closes the connections to the AMQP 1.0 Messaging Network.
     * 
     * @param ctx The vert.x test context.
     */
    public void disconnect(final TestContext ctx) {

        final Async shutdown = ctx.async();
        applicationClientFactory.disconnect(ctx.asyncAssertSuccess(ok -> {
            LOGGER.info("connection to AMQP Messaging Network closed");
            shutdown.complete();
        }));
        shutdown.await();
    }

    /**
     * Closes the connections to the AMQP 1.0 Messaging Network.
     * 
     * @return A future indicating the outcome of the operation.
     */
    public Future<?> disconnect() {

        final Future<Void> result = Future.future();
        applicationClientFactory.disconnect(result);
        return result.map(ok -> {
            LOGGER.info("connection to AMQP Messaging Network closed");
            return ok;
        });
    }

    /**
     * Gets a random tenant identifier and adds it to the list
     * of tenants to be deleted after the current test has finished.
     * 
     * @return The identifier.
     * @see #deleteObjects(TestContext)
     */
    public String getRandomTenantId() {
        final String tenantId = UUID.randomUUID().toString();
        tenantsToDelete.add(tenantId);
        return tenantId;
    }

    /**
     * Gets a random device identifier and adds it to the list
     * of devices to be deleted after the current test has finished.
     * 
     * @param tenantId The tenant that he device belongs to.
     * @return The identifier.
     * @see #deleteObjects(TestContext)
     */
    public String getRandomDeviceId(final String tenantId) {
        final String deviceId = UUID.randomUUID().toString();
        final Set<String> devices = devicesToDelete.computeIfAbsent(tenantId, t -> new HashSet<>());
        devices.add(deviceId);
        return deviceId;
    }

    /**
     * Registers a new device for a tenant that is connected via a gateway.
     * 
     * @param tenantId The tenant that the gateway and device belong to.
     * @param deviceId The device identifier.
     * @param isGatewayDevice {@code true} if the given device is a gateway.
     * @param timeoutSeconds The number of seconds to wait for the setup to succeed.
     * @return The device identifier to use for sending commands.
     *         The identifier will be that of the given device if it is not a gateway,
     *         otherwise it will be the identifier of the newly registered device.
     * @throws IllegalStateException if setup failed.
     */
    public String setupGatewayDeviceBlocking(
            final String tenantId,
            final String deviceId,
            final boolean isGatewayDevice,
            final int timeoutSeconds) {

        final CompletableFuture<String> result = new CompletableFuture<>();

        setupGatewayDevice(tenantId, deviceId, isGatewayDevice)
        .setHandler(attempt -> {
            if (attempt.succeeded()) {
                result.complete(attempt.result());
            } else {
                result.completeExceptionally(attempt.cause());
            }
        });

        try {
            return result.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new IllegalStateException("could not set up gateway device", e);
        }
    }

    /**
     * Registers a new device for a tenant that is connected via a gateway.
     * 
     * @param tenantId The tenant that the gateway and device belong to.
     * @param deviceId The device identifier.
     * @param isGatewayDevice {@code true} if the given device is a gateway.
     * @return A future indicating the outcome of the operation.
     *         The future will be completed with the device identifier to use for
     *         sending commands or will be failed with a {@link ServiceInvocationException}.
     *         The identifier will be that of the given device if it is not a gateway,
     *         otherwise it will be the identifier of the newly registered device.
     */
    public Future<String> setupGatewayDevice(
            final String tenantId,
            final String deviceId,
            final boolean isGatewayDevice) {

        final Future<String> result = Future.future();
        if (isGatewayDevice) {
            final String newDeviceId = getRandomDeviceId(tenantId);
            final Device newDevice = new Device().setVia(List.of(deviceId));
            registry.addDeviceToTenant(tenantId, newDeviceId, newDevice, "pwd")
            .map(ok -> newDeviceId)
            .setHandler(result);
        } else {
            result.complete(deviceId);
        }
        return result;
    }

    /**
     * Sends a command to a device.
     * 
     * @param notification The empty notification indicating the device's readiness to receive a command.
     * @param command The name of the command to send.
     * @param contentType The type of the command's input data.
     * @param payload The command's input data to send to the device.
     * @param properties The headers to include in the command message as AMQP application properties.
     * @return A future that is either succeeded with the response payload from the device or
     *         failed with a {@link ServiceInvocationException}.
     */
    public Future<BufferResult> sendCommand(
            final TimeUntilDisconnectNotification notification,
            final String command,
            final String contentType,
            final Buffer payload,
            final Map<String, Object> properties) {

        return sendCommand(
                notification.getTenantId(),
                notification.getDeviceId(),
                command,
                contentType,
                payload,
                properties,
                notification.getMillisecondsUntilExpiry());
    }

    /**
     * Sends a command to a device.
     * 
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The identifier of the device.
     * @param command The name of the command to send.
     * @param contentType The type of the command's input data.
     * @param payload The command's input data to send to the device.
     * @param properties The headers to include in the command message as AMQP application properties.
     * @param requestTimeout The number of milliseconds to wait for a response from the device.
     * @param useLegacyEndpoint {@code true} if the legacy endpoint should be used for sending the command.
     * @return A future that is either succeeded with the response payload from the device or
     *         failed with a {@link ServiceInvocationException}.
     */
    public Future<BufferResult> sendCommand(
            final String tenantId,
            final String deviceId,
            final String command,
            final String contentType,
            final Buffer payload,
            final Map<String, Object> properties,
            final long requestTimeout,
            final boolean useLegacyEndpoint) {

        if (useLegacyEndpoint) {
            return sendLegacyCommand(tenantId, deviceId, command, contentType, payload, properties, requestTimeout);
        } else {
            return sendCommand(tenantId, deviceId, command, contentType, payload, properties, requestTimeout);
        }
    }

    /**
     * Sends a command to a device.
     * 
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The identifier of the device.
     * @param command The name of the command to send.
     * @param contentType The type of the command's input data.
     * @param payload The command's input data to send to the device.
     * @param properties The headers to include in the command message as AMQP application properties.
     * @param requestTimeout The number of milliseconds to wait for a response from the device.
     * @return A future that is either succeeded with the response payload from the device or
     *         failed with a {@link ServiceInvocationException}.
     */
    public Future<BufferResult> sendCommand(
            final String tenantId,
            final String deviceId,
            final String command,
            final String contentType,
            final Buffer payload,
            final Map<String, Object> properties,
            final long requestTimeout) {

        return applicationClientFactory.getOrCreateCommandClient(tenantId).compose(commandClient -> {

            commandClient.setRequestTimeout(requestTimeout);
            final Future<BufferResult> result = Future.future();
            final Handler<Void> send = s -> {
                // send the command upstream to the device
                LOGGER.trace("sending command [name: {}, contentType: {}, payload: {}]", command, contentType, payload);
                commandClient.sendCommand(deviceId, command, contentType, payload, properties).map(responsePayload -> {
                    LOGGER.debug("successfully sent command [name: {}, payload: {}] and received response [payload: {}]",
                            command, payload, responsePayload);
                    commandClient.close(v -> {});
                    return responsePayload;
                }).recover(t -> {
                    LOGGER.debug("could not send command or did not receive a response: {}", t.getMessage());
                    commandClient.close(v -> {});
                    return Future.failedFuture(t);
                }).setHandler(result);
            };
            if (commandClient.getCredit() == 0) {
                commandClient.sendQueueDrainHandler(send);
            } else {
                send.handle(null);
            }
            return result;
        });
    }

    /**
     * Sends a command to a device using the north bound legacy endpoint.
     * 
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The identifier of the device.
     * @param command The name of the command to send.
     * @param contentType The type of the command's input data.
     * @param payload The command's input data to send to the device.
     * @param properties The headers to include in the command message as AMQP application properties.
     * @param requestTimeout The number of milliseconds to wait for a response from the device.
     * @return A future that is either succeeded with the response payload from the device or
     *         failed with a {@link ServiceInvocationException}.
     */
    public Future<BufferResult> sendLegacyCommand(
            final String tenantId,
            final String deviceId,
            final String command,
            final String contentType,
            final Buffer payload,
            final Map<String, Object> properties,
            final long requestTimeout) {

        return applicationClientFactory.createLegacyCommandClient(tenantId, deviceId).compose(commandClient -> {

            commandClient.setRequestTimeout(requestTimeout);
            final Future<BufferResult> result = Future.future();
            final Handler<Void> send = s -> {
                // send the command upstream to the device
                LOGGER.trace("sending legacy command [name: {}, contentType: {}, payload: {}]", command, contentType, payload);
                commandClient.sendCommand(command, contentType, payload, properties).map(responsePayload -> {
                    LOGGER.debug("successfully sent legacy command [name: {}, payload: {}] and received response [payload: {}]",
                            command, payload, responsePayload);
                    commandClient.close(v -> {});
                    return responsePayload;
                }).recover(t -> {
                    LOGGER.debug("could not send legacy command or did not receive a response: {}", t.getMessage());
                    commandClient.close(v -> {});
                    return Future.failedFuture(t);
                }).setHandler(result);
            };
            if (commandClient.getCredit() == 0) {
                commandClient.sendQueueDrainHandler(send);
            } else {
                send.handle(null);
            }
            return result;
        });
    }

    /**
     * Sends a one-way command to a device.
     * 
     * @param notification The empty notification indicating the device's readiness to receive a command.
     * @param command The name of the command to send.
     * @param contentType The type of the command's input data.
     * @param payload The command's input data to send to the device.
     * @param properties The headers to include in the command message as AMQP application properties.
     * @return A future that is either succeeded if the command has been sent to the device or
     *         failed with a {@link ServiceInvocationException}.
     */
    public Future<Void> sendOneWayCommand(
            final TimeUntilDisconnectNotification notification,
            final String command,
            final String contentType,
            final Buffer payload,
            final Map<String, Object> properties) {

        return sendOneWayCommand(
                notification.getTenantId(),
                notification.getDeviceId(),
                command,
                contentType,
                payload,
                properties,
                notification.getMillisecondsUntilExpiry());
    }

    /**
     * Sends a one-way command to a device.
     * 
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The identifier of the device.
     * @param command The name of the command to send.
     * @param contentType The type of the command's input data.
     * @param payload The command's input data to send to the device.
     * @param properties The headers to include in the command message as AMQP application properties.
     * @param requestTimeout The number of milliseconds to wait for the command being sent to the device.
     * @param useLegacyEndpoint {@code true} if the legacy endpoint should be used for sending the command.
     * @return A future that is either succeeded if the command has been sent to the device or
     *         failed with a {@link ServiceInvocationException}.
     */
    public Future<Void> sendOneWayCommand(
            final String tenantId,
            final String deviceId,
            final String command,
            final String contentType,
            final Buffer payload,
            final Map<String, Object> properties,
            final long requestTimeout,
            final boolean useLegacyEndpoint) {

        if (useLegacyEndpoint) {
            return sendLegacyOneWayCommand(tenantId, deviceId, command, contentType, payload, properties, requestTimeout);
        } else {
            return sendOneWayCommand(tenantId, deviceId, command, contentType, payload, properties, requestTimeout);
        }
    }

    /**
     * Sends a one-way command to a device.
     * 
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The identifier of the device.
     * @param command The name of the command to send.
     * @param contentType The type of the command's input data.
     * @param payload The command's input data to send to the device.
     * @param properties The headers to include in the command message as AMQP application properties.
     * @param requestTimeout The number of milliseconds to wait for the command being sent to the device.
     * @return A future that is either succeeded if the command has been sent to the device or
     *         failed with a {@link ServiceInvocationException}.
     */
    public Future<Void> sendOneWayCommand(
            final String tenantId,
            final String deviceId,
            final String command,
            final String contentType,
            final Buffer payload,
            final Map<String, Object> properties,
            final long requestTimeout) {

        return applicationClientFactory.getOrCreateCommandClient(tenantId).compose(commandClient -> {

            commandClient.setRequestTimeout(requestTimeout);
            final Future<Void> result = Future.future();
            final Handler<Void> send = s -> {
                // send the command upstream to the device
                LOGGER.trace("sending one-way command [name: {}, contentType: {}, payload: {}]", command, contentType, payload);
                commandClient.sendOneWayCommand(deviceId, command, contentType, payload, properties).map(ok -> {
                    LOGGER.debug("successfully sent one-way command [name: {}, payload: {}]", command, payload);
                    commandClient.close(v -> {});
                    return (Void) null;
                }).recover(t -> {
                    LOGGER.debug("could not send one-way command: {}", t.getMessage());
                    commandClient.close(v -> {});
                    return Future.failedFuture(t);
                }).setHandler(result);
            };
            if (commandClient.getCredit() == 0) {
                commandClient.sendQueueDrainHandler(send);
            } else {
                send.handle(null);
            }
            return result;
        });
    }

    /**
     * Sends a one-way command to a device using the north bound legacy endpoint.
     * 
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The identifier of the device.
     * @param command The name of the command to send.
     * @param contentType The type of the command's input data.
     * @param payload The command's input data to send to the device.
     * @param properties The headers to include in the command message as AMQP application properties.
     * @param requestTimeout The number of milliseconds to wait for the command being sent to the device.
     * @return A future that is either succeeded if the command has been sent to the device or
     *         failed with a {@link ServiceInvocationException}.
     */
    public Future<Void> sendLegacyOneWayCommand(
            final String tenantId,
            final String deviceId,
            final String command,
            final String contentType,
            final Buffer payload,
            final Map<String, Object> properties,
            final long requestTimeout) {

        return applicationClientFactory.createLegacyCommandClient(tenantId, deviceId).compose(commandClient -> {

            commandClient.setRequestTimeout(requestTimeout);
            final Future<Void> result = Future.future();
            final Handler<Void> send = s -> {
                // send the command upstream to the device
                LOGGER.trace("sending one-way command [name: {}, contentType: {}, payload: {}]", command, contentType, payload);
                commandClient.sendOneWayCommand(command, contentType, payload, properties).map(ok -> {
                    LOGGER.debug("successfully sent legacy one-way command [name: {}, payload: {}]", command, payload);
                    commandClient.close(v -> {});
                    return (Void) null;
                }).recover(t -> {
                    LOGGER.debug("could not send legacy one-way command: {}", t.getMessage());
                    commandClient.close(v -> {});
                    return Future.failedFuture(t);
                }).setHandler(result);
            };
            if (commandClient.getCredit() == 0) {
                commandClient.sendQueueDrainHandler(send);
            } else {
                send.handle(null);
            }
            return result;
        });
    }

    /**
     * A simple implementation of subtree containment: all entries of the JsonObject that is tested to be contained
     * must be contained in the other JsonObject as well. Nested JsonObjects are treated the same by recursively calling
     * this method to test the containment.
     * JsonArrays are tested for containment as well: all elements in a JsonArray belonging to the contained JsonObject
     * must be present in the corresponding JsonArray of the other JsonObject as well. The sequence of the array elements
     * is not important (suitable for the current tests).
     * @param jsonObject The JsonObject that must fully contain the other JsonObject (but may contain more entries as well).
     * @param jsonObjectToBeContained The JsonObject that needs to be fully contained inside the other JsonObject.
     * @return The result of the containment test.
     */
    public static boolean testJsonObjectToBeContained(final JsonObject jsonObject, final JsonObject jsonObjectToBeContained) {
        if (jsonObjectToBeContained == null) {
            return true;
        }
        if (jsonObject == null) {
            return false;
        }
        final AtomicBoolean containResult = new AtomicBoolean(true);

        jsonObjectToBeContained.forEach(entry -> {
            if (!jsonObject.containsKey(entry.getKey())) {
                containResult.set(false);
            } else {
                if (entry.getValue() == null) {
                    if (jsonObject.getValue(entry.getKey()) != null) {
                        containResult.set(false);
                    }
                } else if (entry.getValue() instanceof JsonObject) {
                    if (!(jsonObject.getValue(entry.getKey()) instanceof JsonObject)) {
                        containResult.set(false);
                    } else {
                        if (!testJsonObjectToBeContained((JsonObject) entry.getValue(),
                                (JsonObject) jsonObject.getValue(entry.getKey()))) {
                            containResult.set(false);
                        }
                    }
                } else if (entry.getValue() instanceof JsonArray) {
                    if (!(jsonObject.getValue(entry.getKey()) instanceof JsonArray)) {
                        containResult.set(false);
                    } else {
                        // compare two JsonArrays
                        final JsonArray biggerArray = (JsonArray) jsonObject.getValue(entry.getKey());
                        final JsonArray smallerArray = (JsonArray) entry.getValue();

                        if (!testJsonArrayToBeContained(biggerArray, smallerArray)) {
                            containResult.set(false);
                        }
                    }
                } else {
                    if (!entry.getValue().equals(jsonObject.getValue(entry.getKey()))) {
                        containResult.set(false);
                    }
                }
            }
        });
        return containResult.get();
    }

    /**
     * A simple implementation of JsonArray containment: all elements of the JsonArray that is tested to be contained
     * must be contained in the other JsonArray as well. Contained JsonObjects are tested for subtree containment as
     * implemented in {@link #testJsonObjectToBeContained(JsonObject, JsonObject)}.
     * <p>
     * The order sequence of the elements is intentionally not important - the containing array is always iterated from
     * the beginning and the containment of an element is handled as successful if a suitable element in the containing
     * array was found (sufficient for the current tests).
     * <p>
     * For simplicity, the elements of the arrays must be of type JsonObject (sufficient for the current tests).
     * <p>
     * Also note that this implementation is by no means performance optimized - it is for sure not suitable for huge JsonArrays
     * (by using two nested iteration loops inside) and is meant only for quick test results on smaller JsonArrays.
     *
     * @param containingArray The JsonArray that must contain the elements of the other array (the sequence is not important).
     * @param containedArray The JsonArray that must consist only of elements that can be found in the containingArray
     *                       as well (by subtree containment test).
     * @return The result of the containment test.
     */
    public static boolean testJsonArrayToBeContained(final JsonArray containingArray, final JsonArray containedArray) {
        for (final Object containedElem: containedArray) {
            // currently only support contained JsonObjects
            if (!(containedElem instanceof JsonObject)) {
                return false;
            }

            boolean containingElemFound = false;
            for (final Object elemOfBiggerArray: containingArray) {
                if (!(elemOfBiggerArray instanceof JsonObject)) {
                    return false;
                }

                if (testJsonObjectToBeContained((JsonObject) elemOfBiggerArray, (JsonObject) containedElem)) {
                    containingElemFound = true;
                    break;
                }
            }
            if (!containingElemFound) {
                // a full iteration of the containing array did not find a matching element
                return false;
            }
        }
        return true;
    }

    /**
     * Creates an authentication identifier from a device and tenant ID.
     * <p>
     * The returned identifier can be used as the <em>username</em> with
     * Hono's protocol adapters that support username/password authentication.
     * 
     * @param deviceId The device identifier.
     * @param tenant The tenant that the device belongs to.
     * @return The authentication identifier.
     */
    public static String getUsername(final String deviceId, final String tenant) {
        return String.format("%s@%s", deviceId, tenant);
    }

    /**
     * Gets a hash for a password using a given digest based hash function.
     * 
     * @param hashFunction The hash function.
     * @param salt The salt.
     * @param clearTextPassword The password.
     * @return The Base64 encoded password hash.
     */
    public static String getBase64EncodedDigestPasswordHash(final String hashFunction, final byte[] salt, final String clearTextPassword) {
        try {
            final MessageDigest digest = MessageDigest.getInstance(hashFunction);
            if (salt != null) {
                digest.update(salt);
            }
            return Base64.getEncoder().encodeToString(digest.digest(clearTextPassword.getBytes(StandardCharsets.UTF_8)));
        } catch (final NoSuchAlgorithmException e) {
            return "hash function not supported";
        }
    }

    /**
     * Gets a hash for a password using the bcrypt hash function.
     * 
     * @param clearTextPassword The password.
     * @return The hashed password.
     */
    public static String getBcryptHash(final String clearTextPassword) {
        return bcryptPwdEncoder.encode(clearTextPassword);
    }

    /**
     * Generates a certificate object and initializes it with the data read from a file.
     * 
     * @param path The file-system path to load the certificate from.
     * @return A future with the generated certificate on success.
     */
    public Future<X509Certificate> getCertificate(final String path) {
        return loadFile(path).map(buffer -> {
            try (InputStream is = new ByteArrayInputStream(buffer.getBytes())) {
                final CertificateFactory factory = CertificateFactory.getInstance("X.509");
                return (X509Certificate) factory.generateCertificate(is);
            } catch (final Exception e) {
                throw new IllegalArgumentException("file cannot be parsed into X.509 certificate");
            }
        });
    }

    /**
     * Creates a new EC based private/public key pair.
     * 
     * @return The key pair.
     * @throws GeneralSecurityException if the JVM doesn't support ECC.
     */
    public static KeyPair newEcKeyPair() throws GeneralSecurityException {
        return generateKeyPair("EC");
    }

    /**
     * Creates a new RSA public key.
     * 
     * @return The key.
     */
    public static PublicKey newRsaKey() {
        return generateKeyPair("RSA").getPublic();
    }

    /**
     * Generates a public/private key pair for the specified algorithm.
     * 
     * @param algorithmn The algorithm to generate a keypair for.
     * @return The generated key pair.
     */
    private static KeyPair generateKeyPair(final String algorithmn) {
        try {
            final KeyPairGenerator keyGen = KeyPairGenerator.getInstance(algorithmn);
            if ("RSA".equals(algorithmn)) {
                keyGen.initialize(1024);
            }
            return keyGen.genKeyPair();
        } catch (NoSuchAlgorithmException e) {
            // cannot happen because RSA mandatory on every JRE
            throw new IllegalStateException("JRE does not support RSA algorithm");
        }
    }

    //----------------------------------< private methods >---

    /*
     * Reads a PEM certificate file using vertx in blocking mode
     * and returns its content as buffer.
     */
    private Future<Buffer> loadFile(final String path) {

        final Future<Buffer> result = Future.future();
        vertx.fileSystem().readFile(path, result);
        return result;
    }

    /**
     * Create a new password credential, suitable for use in the integration test environment.
     * 
     * @param authId The auth ID to use.
     * @param password The password to use.
     * @return The new instance.
     */
    public static PasswordCredential createPasswordCredential(final String authId, final String password) {
        return AbstractCredentialsServiceTest.createPasswordCredential(authId, password,
                OptionalInt.of(IntegrationTestSupport.MAX_BCRYPT_ITERATIONS));
    }

}
