/*******************************************************************************
 * Copyright (c) 2016, 2021 Contributors to the Eclipse Foundation
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

import static org.assertj.core.api.Assertions.assertThat;

import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.eclipse.hono.application.client.DownstreamMessage;
import org.eclipse.hono.application.client.MessageContext;
import org.eclipse.hono.application.client.amqp.AmqpApplicationClientFactory;
import org.eclipse.hono.application.client.amqp.ProtonBasedApplicationClientFactory;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.service.management.credentials.Credentials;
import org.eclipse.hono.service.management.credentials.PasswordCredential;
import org.eclipse.hono.service.management.credentials.PskCredential;
import org.eclipse.hono.service.management.device.Device;
import org.eclipse.hono.test.VertxTools;
import org.eclipse.hono.util.BufferResult;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.TimeUntilDisconnectNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxTestContext;

/**
 * A helper class for integration tests.
 */
public final class IntegrationTestSupport {

    /**
     * The default number of milliseconds to wait for a response to an AMQP 1.0 performative.
     */
    public static final int DEFAULT_AMQP_TIMEOUT = 400;
    /**
     * The default port exposed by the AMQP adapter.
     */
    public static final int DEFAULT_AMQP_PORT = 5672;
    /**
     * The default TLS secured port exposed by the AMQP adapter.
     */
    public static final int DEFAULT_AMQPS_PORT = 5671;
    /**
     * The default port exposed by the CoAP adapter.
     */
    public static final int DEFAULT_COAP_PORT = 5683;
    /**
     * The default DTLS secured port exposed by the CoAP adapter.
     */
    public static final int DEFAULT_COAPS_PORT = 5684;
    /**
     * The default AMQP port exposed by the Device Connection service.
     */
    public static final int DEFAULT_DEVICECONNECTION_AMQP_PORT = 35672;
    /**
     * The default AMQP port exposed by the device registry.
     */
    public static final int DEFAULT_DEVICEREGISTRY_AMQP_PORT = 25672;
    /**
     * The default HTTP port exposed by the device registry.
     */
    public static final int DEFAULT_DEVICEREGISTRY_HTTP_PORT = 28080;
    /**
     * The default AMQP port exposed by the AMQP Messaging Network.
     */
    public static final int DEFAULT_DOWNSTREAM_PORT = 15672;
    /**
     * The default IP address that services and adapters bind their endpoints to.
     */
    public static final String DEFAULT_HOST = InetAddress.getLoopbackAddress().getHostAddress();
    /**
     * The default port exposed by the HTTP adapter.
     */
    public static final int DEFAULT_HTTP_PORT = 8080;
    /**
     * The default TLS secured port exposed by the HTTP adapter.
     */
    public static final int DEFAULT_HTTPS_PORT = 8443;
    /**
     * The default cost factor to use with the BCrypt hash algorithm.
     */
    public static final int DEFAULT_MAX_BCRYPT_COST_FACTOR = 10;
    /**
     * The default port exposed by the MQTT adapter.
     */
    public static final int DEFAULT_MQTT_PORT = 1883;
    /**
     * The default TLS secured port exposed by the MQTT adapter.
     */
    public static final int DEFAULT_MQTTS_PORT = 8883;
    /**
     * The default number of seconds to wait for the fixture of a test being established.
     */
    public static final long DEFAULT_TEST_SETUP_TIMEOUT_SECONDS = 5;

    /**
     * The name of the system property to use for setting the time to wait for a response
     * to an AMQP 1.0 performative.
     */
    public static final String PROPERTY_AMQP_TIMEOUT = "amqp.timeout";
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
     * The name of the system property to use for indicating whether the Device Connection service is enabled.
     */
    public static final String PROPERTY_DEVICECONNECTION_SERVICE_ENABLED = "deviceconnection.enabled";
    /**
     * The name of the system property to use for setting the IP address of the Device Connection service.
     */
    public static final String PROPERTY_DEVICECONNECTION_HOST = "deviceconnection.host";
    /**
     * The name of the system property to use for setting the port number that the Device Connection
     * service should listen on for AMQP connections.
     */
    public static final String PROPERTY_DEVICECONNECTION_AMQP_PORT = "deviceconnection.amqp.port";
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
    public static final String PROPERTY_COAP_HOST = "adapter.coap.host";
    /**
     * The name of the system property to use for setting the port number that the CoAP adapter
     * should listen on for requests.
     */
    public static final String PROPERTY_COAP_PORT = "adapter.coap.port";
    /**
     * The name of the system property to use for setting the port number that the CoAP adapter
     * should listen on for secure requests.
     */
    public static final String PROPERTY_COAPS_PORT = "adapter.coaps.port";
    /**
     * The name of the system property to use for setting the IP address of the HTTP protocol adapter.
     */
    public static final String PROPERTY_HTTP_HOST = "adapter.http.host";
    /**
     * The name of the system property to use for setting the port number that the HTTP adapter
     * should listen on for requests.
     */
    public static final String PROPERTY_HTTP_PORT = "adapter.http.port";
    /**
     * The name of the system property to use for setting the port number that the HTTP adapter
     * should listen on for secure requests.
     */
    public static final String PROPERTY_HTTPS_PORT = "adapter.https.port";
    /**
     * The name of the system property to use for setting the IP address of the MQTT protocol adapter.
     */
    public static final String PROPERTY_MQTT_HOST = "adapter.mqtt.host";
    /**
     * The name of the system property to use for setting the port number that the MQTT adapter
     * should listen on for connections.
     */
    public static final String PROPERTY_MQTT_PORT = "adapter.mqtt.port";
    /**
     * The name of the system property to use for setting the port number that the MQTT adapter
     * should listen on for secure connections.
     */
    public static final String PROPERTY_MQTTS_PORT = "adapter.mqtts.port";
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
     * The name of the system property to use for setting the maximum BCrypt cost factor supported
     * by Hono.
     */
    public static final String PROPERTY_MAX_BCRYPT_COST_FACTOR = "max.bcrypt.costFactor";
    /**
     * The name of the system property to use for setting the maximum time (in ms) that adapters
     * wait for an acknowledgement of a command sent to a device.
     */
    public static final String PROPERTY_SEND_MESSAGE_TO_DEVICE_TIMEOUT = "adapter.sendMessageToDeviceTimeout";

    /**
     * The IP address of the Auth service.
     */
    public static final String AUTH_HOST = System.getProperty(PROPERTY_AUTH_HOST, DEFAULT_HOST);
    /**
     * The port number that the Auth service listens on.
     */
    public static final int AUTH_PORT = Integer.getInteger(PROPERTY_AUTH_PORT, Constants.PORT_AMQP);

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
    public static final int HONO_DEVICEREGISTRY_AMQP_PORT = Integer.getInteger(PROPERTY_DEVICEREGISTRY_AMQP_PORT, DEFAULT_DEVICEREGISTRY_AMQP_PORT);
    /**
     * The port number that the Device Registry listens on for HTTP requests.
     */
    public static final int HONO_DEVICEREGISTRY_HTTP_PORT = Integer.getInteger(PROPERTY_DEVICEREGISTRY_HTTP_PORT, DEFAULT_DEVICEREGISTRY_HTTP_PORT);

    /**
     * The boolean value indicating whether the Device Connection service is enabled.
     */
    public static final boolean HONO_DEVICECONNECTION_SERVICE_ENABLED = Boolean.parseBoolean(System.getProperty(
            PROPERTY_DEVICECONNECTION_SERVICE_ENABLED, "true"));
    /**
     * The IP address of the Device Connection service.
     */
    public static final String HONO_DEVICECONNECTION_HOST = System.getProperty(PROPERTY_DEVICECONNECTION_HOST, DEFAULT_HOST);
    /**
     * The port number that the Device Connection service listens on for AMQP connections.
     */
    public static final int HONO_DEVICECONNECTION_AMQP_PORT = Integer.getInteger(PROPERTY_DEVICECONNECTION_AMQP_PORT, DEFAULT_DEVICECONNECTION_AMQP_PORT);

    /**
     * The IP address of the AMQP Messaging Network.
     */
    public static final String DOWNSTREAM_HOST = System.getProperty(PROPERTY_DOWNSTREAM_HOST, DEFAULT_HOST);
    /**
     * The port number that the AMQP Messaging Network listens on for connections.
     */
    public static final int DOWNSTREAM_PORT = Integer.getInteger(PROPERTY_DOWNSTREAM_PORT, DEFAULT_DOWNSTREAM_PORT);
    /**
     * The username that applications use for authenticating to the AMQP Messaging Network.
     */
    public static final String DOWNSTREAM_USER = System.getProperty(PROPERTY_DOWNSTREAM_USERNAME);
    /**
     * The password that applications use for authenticating to the AMQP Messaging Network.
     */
    public static final String DOWNSTREAM_PWD = System.getProperty(PROPERTY_DOWNSTREAM_PASSWORD);

    /**
     * The IP address of the CoAP protocol adapter.
     */
    public static final String COAP_HOST = System.getProperty(PROPERTY_COAP_HOST, DEFAULT_HOST);
    /**
     * The  port number that the CoAP adapter listens on for requests.
     */
    public static final int COAP_PORT = Integer.getInteger(PROPERTY_COAP_PORT, DEFAULT_COAP_PORT);
    /**
     * The  port number that the CoAP adapter listens on for secure requests.
     */
    public static final int COAPS_PORT = Integer.getInteger(PROPERTY_COAPS_PORT, DEFAULT_COAPS_PORT);
    /**
     * The IP address of the HTTP protocol adapter.
     */
    public static final String HTTP_HOST = System.getProperty(PROPERTY_HTTP_HOST, DEFAULT_HOST);
    /**
     * The  port number that the HTTP adapter listens on for requests.
     */
    public static final int HTTP_PORT = Integer.getInteger(PROPERTY_HTTP_PORT, DEFAULT_HTTP_PORT);
    /**
     * The  port number that the HTTP adapter listens on for secure requests.
     */
    public static final int HTTPS_PORT = Integer.getInteger(PROPERTY_HTTPS_PORT, DEFAULT_HTTPS_PORT);
    /**
     * The IP address of the MQTT protocol adapter.
     */
    public static final String MQTT_HOST = System.getProperty(PROPERTY_MQTT_HOST, DEFAULT_HOST);
    /**
     * The  port number that the MQTT adapter listens on for connections.
     */
    public static final int MQTT_PORT = Integer.getInteger(PROPERTY_MQTT_PORT, DEFAULT_MQTT_PORT);
    /**
     * The  port number that the MQTT adapter listens on for secure connections.
     */
    public static final int MQTTS_PORT = Integer.getInteger(PROPERTY_MQTTS_PORT, DEFAULT_MQTTS_PORT);
    /**
     * The IP address of the AMQP protocol adapter.
     */
    public static final String AMQP_HOST = System.getProperty(PROPERTY_AMQP_HOST, DEFAULT_HOST);
    /**
     * The  port number that the AMQP adapter listens on for connections.
     */
    public static final int AMQP_PORT = Integer.getInteger(PROPERTY_AMQP_PORT, DEFAULT_AMQP_PORT);
    /**
     * The  port number that the AMQP adapter listens on for secure connections.
     */
    public static final int AMQPS_PORT = Integer.getInteger(PROPERTY_AMQPS_PORT, DEFAULT_AMQPS_PORT);

    /**
     * The number of messages to send by default in protocol adapter tests.
     */
    public static final int MSG_COUNT = Integer.getInteger("msg.count", 400);

    /**
     * The maximum BCrypt cost factor supported by Hono.
     */
    public static final int MAX_BCRYPT_COST_FACTOR = Integer.getInteger(PROPERTY_MAX_BCRYPT_COST_FACTOR, DEFAULT_MAX_BCRYPT_COST_FACTOR);

    /**
     * The maximum time (in ms) that adapters wait for an acknowledgement of a command sent to a device.
     */
    public static final long SEND_MESSAGE_TO_DEVICE_TIMEOUT = Long.getLong(PROPERTY_SEND_MESSAGE_TO_DEVICE_TIMEOUT, 1000L);

    /**
     * The time to wait for the response to an AMQP 1.0 performative.
     */
    public static final int AMQP_TIMEOUT = Integer.getInteger(PROPERTY_AMQP_TIMEOUT, DEFAULT_AMQP_TIMEOUT);

    /**
     * The absolute path to the trust store to use for establishing secure connections with Hono.
     */
    public static final String TRUST_STORE_PATH = System.getProperty("trust-store.path");

    /**
     * Pattern used for the <em>name</em> field of the {@code @ParameterizedTest} annotation.
     */
    public static final String PARAMETERIZED_TEST_NAME_PATTERN = "{displayName} [{index}]; parameters: {argumentsWithNames}";

    private static final Logger LOGGER = LoggerFactory.getLogger(IntegrationTestSupport.class);
    private static final BCryptPasswordEncoder bcryptPwdEncoder = new BCryptPasswordEncoder(4);
    private static final int TEST_ENVIRONMENT_TIMEOUT_MULTIPLICATOR = 2;

    private static final boolean testEnv = Optional.ofNullable(System.getenv("CI"))
            .map(s -> {
                final boolean runningOnCiEnvironment = Boolean.parseBoolean(s);
                if (runningOnCiEnvironment) {
                    LOGGER.info("running on CI environment");
                }
                return runningOnCiEnvironment;
            })
            .orElseGet(() -> {
                final boolean runningOnTestEnvironment = Boolean.getBoolean("test.env");
                if (runningOnTestEnvironment) {
                    LOGGER.info("running on test environment");
                }
                return runningOnTestEnvironment;
            });

    /**
     * A client for managing tenants/devices/credentials.
     */
    public DeviceRegistryHttpClient registry;
    /**
     * A client for connecting to Hono's north bound APIs
     * via the AMQP Messaging Network using the legacy client.
     */
    public IntegrationTestApplicationClientFactory applicationClientFactory;
    /**
     * A client for connecting to Hono's north bound APIs
     * via the AMQP Messaging Network using the new client.
     */
    public AmqpApplicationClientFactory amqpApplicationClient;

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

        final long timeout = AMQP_TIMEOUT * getTimeoutMultiplicator();
        final ClientConfigProperties props = new ClientConfigProperties();
        props.setHost(host);
        props.setPort(port);
        props.setUsername(username);
        props.setPassword(password);
        props.setLinkEstablishmentTimeout(timeout);
        props.setRequestTimeout(timeout);
        props.setFlowLatency(timeout);
        return props;
    }

    /**
     * Creates properties for connecting to the AMQP Messaging Network.
     *
     * @return The properties.
     */
    public static ClientConfigProperties getMessagingNetworkProperties() {

        return getClientConfigProperties(
                IntegrationTestSupport.DOWNSTREAM_HOST,
                IntegrationTestSupport.DOWNSTREAM_PORT,
                IntegrationTestSupport.DOWNSTREAM_USER,
                IntegrationTestSupport.DOWNSTREAM_PWD);
    }

    /**
     * Creates properties for connecting to the AMQP protocol adapter.
     *
     * @param username The username to use for authenticating to the adapter.
     * @param password The password to use for authenticating to the adapter.
     * @return The properties.
     */
    public static ClientConfigProperties getAmqpAdapterProperties(final String username, final String password) {

        return getClientConfigProperties(
                IntegrationTestSupport.AMQP_HOST,
                IntegrationTestSupport.AMQP_PORT,
                username,
                password);
    }

    /**
     * Creates properties for connecting to the device registry.
     *
     * @param username The username to use for authenticating to the device registry.
     * @param password The password to use for authenticating to the device registry.
     * @return The properties.
     */
    public static ClientConfigProperties getDeviceRegistryProperties(final String username, final String password) {

        return getClientConfigProperties(
                IntegrationTestSupport.HONO_DEVICEREGISTRY_HOST,
                IntegrationTestSupport.HONO_DEVICEREGISTRY_AMQP_PORT,
                username,
                password);
    }

    /**
     * Checks if the Device Connection service is enabled.
     * <p>
     * Evaluates the system property <em>deviceconnection.enabled</em>. Returns {@code true} if it doesn't exist.
     *
     * @return {@code true} if the Device Connection service is enabled.
     */
    public static boolean isDeviceConnectionServiceEnabled() {
        return HONO_DEVICECONNECTION_SERVICE_ENABLED;
    }

    /**
     * Creates properties for connecting to the Device Connection service.
     *
     * @param username The username to use for authenticating to the service.
     * @param password The password to use for authenticating to the service.
     * @return The properties.
     */
    public static ClientConfigProperties getDeviceConnectionServiceProperties(final String username, final String password) {

        return getClientConfigProperties(
                IntegrationTestSupport.HONO_DEVICECONNECTION_HOST,
                IntegrationTestSupport.HONO_DEVICECONNECTION_AMQP_PORT,
                username,
                password);
    }

    /**
     * Checks if this method is executed on a test environment.
     * <p>
     * Evaluates system property <em>test.env</em> and environment variable <em>CI</em>.
     *
     * @return {@code true} if this is a test environment.
     */
    public static boolean isTestEnvironment() {
        return testEnv;
    }

    /**
     * Gets the factor to apply to timeout values based on the
     * environment that the code is running on.
     *
     * @return {@value IntegrationTestSupport#TEST_ENVIRONMENT_TIMEOUT_MULTIPLICATOR} when running
     *         on a test environment, 1 otherwise.
     */
    public static int getTimeoutMultiplicator() {
        return isTestEnvironment() ? TEST_ENVIRONMENT_TIMEOUT_MULTIPLICATOR : 1;
    }

    /**
     * Determines the time to wait before timing out the setup phase of a test case.
     *
     * @return The time out in seconds. The value will be
     *         {@value IntegrationTestSupport#DEFAULT_TEST_SETUP_TIMEOUT_SECONDS}
     *         multiplied by the value returned by {@link #getTimeoutMultiplicator()}.
     */
    public static long getTestSetupTimeout() {
        return DEFAULT_TEST_SETUP_TIMEOUT_SECONDS * getTimeoutMultiplicator();
    }

    /**
     * Determines the time to wait before timing out a request to send
     * a command to a device.
     *
     * @return The time out in milliseconds. The value will be
     *         {@link #SEND_MESSAGE_TO_DEVICE_TIMEOUT} + ({@link #AMQP_TIMEOUT} * {@link #getTimeoutMultiplicator()} * 2).
     */
    public static long getSendCommandTimeout() {
        return SEND_MESSAGE_TO_DEVICE_TIMEOUT + (AMQP_TIMEOUT * getTimeoutMultiplicator() * 2);
    }

    /**
     * Gets payload of a particular size.
     *
     * @param size The number of bytes that the payload should contain.
     * @return The payload.
     */
    public static byte[] getPayload(final int size) {
        final byte[] payload = new byte[size];
        Arrays.fill(payload, (byte) 0x66);
        return payload;
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
        amqpApplicationClient = new ProtonBasedApplicationClientFactory(HonoConnection.newConnection(vertx, downstreamProps));

        return CompositeFuture.all(
                applicationClientFactory.connect()
                    .onSuccess(con -> {
                        LOGGER.info("connected to AMQP Messaging Network using legacy client [{}:{}]",
                                downstreamProps.getHost(), downstreamProps.getPort());
                    }),
                amqpApplicationClient.connect()
                    .onSuccess(ok -> {
                        LOGGER.info("connected to AMQP Messaging Network using new client [{}:{}]",
                                downstreamProps.getHost(), downstreamProps.getPort());
                    }))
                .mapEmpty();
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
     * <p>
     * <strong>Note:</strong> This method either fails or completes the given test context.
     *
     * @param ctx The vert.x context.
     */
    public void deleteObjects(final VertxTestContext ctx) {

        // copy and reset

        final var devicesToDelete = Map.copyOf(this.devicesToDelete);
        this.devicesToDelete.clear();
        final var tenantsToDelete = List.copyOf(this.tenantsToDelete);
        this.tenantsToDelete.clear();

        // first delete devices

        final var deleteDevices = CompositeFuture
                .join(devicesToDelete.entrySet()
                        .stream().flatMap(entry ->
                                entry.getValue().stream()
                                        .map(deviceId -> registry.deregisterDevice(entry.getKey(), deviceId, true)))
                        .collect(Collectors.toList()));

        final var devicesDeleted = new AtomicBoolean();

        deleteDevices

                // record success of first operation ...

                .onSuccess(x -> devicesDeleted.set(true))

                // ... and reset error, will be checked at the end

                .otherwiseEmpty()

                // then delete tenants

                .compose(x -> {
                    if (!tenantsToDelete.isEmpty()) {
                        LOGGER.debug("deleting {} temporary tenants ...", tenantsToDelete.size());
                    }
                    return CompositeFuture.join(tenantsToDelete.stream()
                                    .map(tenantId -> registry.removeTenant(tenantId, true))
                                    .collect(Collectors.toList()));
                })

                .compose(ok -> registry.searchTenants(
                        Optional.of(30),
                        Optional.of(0),
                        List.of(),
                        List.of()))
                .map(searchResponse -> {
                    switch (searchResponse.statusCode()) {
                    case HttpURLConnection.HTTP_OK:
                        final JsonObject response = searchResponse.bodyAsJsonObject();
                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug("search tenants result: {}{}", System.lineSeparator(), response.encodePrettily());
                        }
                        return response.getInteger("total");
                    case HttpURLConnection.HTTP_NOT_FOUND:
                        return 0;
                    default:
                        LOGGER.info("search for tenants failed: {} - {}",
                                searchResponse.statusCode(), searchResponse.statusMessage());
                        return 0;
                    }
                })
                .otherwise(t -> {
                    LOGGER.info("error querying tenants endpoint", t);
                    return 0;
                })

                // complete, and check if we successfully deleted the devices

                .onComplete(ctx.succeeding(tenantsInRegistry -> {
                    ctx.verify(() -> {
                        assertThat(devicesDeleted.get())
                            .as("successfully deleted devices")
                            .isTrue();
                        assertThat(tenantsInRegistry)
                            .as("registry contains no other tenants after successful deletion of temporary tenants")
                            .isEqualTo(0);
                    });
                    ctx.completeNow();
                }));

    }

    /**
     * Closes the connections to the AMQP 1.0 Messaging Network.
     *
     * @return A future indicating the outcome of the operation.
     */
    public Future<?> disconnect() {

        final Promise<Void> legacyClientResult = Promise.promise();
        applicationClientFactory.disconnect(legacyClientResult);
        final Promise<Void> newClientResult = Promise.promise();
        amqpApplicationClient.disconnect(newClientResult);
        return CompositeFuture.all(
                legacyClientResult.future()
                    .onSuccess(ok -> LOGGER.info("legacy client's connection to AMQP Messaging Network closed")),
                newClientResult.future()
                    .onSuccess(ok -> LOGGER.info("new client's connection to AMQP Messaging Network closed")))
                .mapEmpty();
    }

    /**
     * Gets a random tenant identifier and adds it to the list
     * of tenants to be deleted after the current test has finished.
     *
     * @return The identifier.
     * @see #deleteObjects(VertxTestContext)
     */
    public String getRandomTenantId() {
        final String tenantId = UUID.randomUUID().toString();
        tenantsToDelete.add(tenantId);
        LOGGER.debug("registered random tenant [tenant-id: {}] for removal", tenantId);
        return tenantId;
    }

    /**
     * Adds a tenant identifier to the list
     * of tenants to be deleted after the current test has finished.
     *
     * @param tenantId The identifier.
     * @see #deleteObjects(VertxTestContext)
     */
    public void addTenantIdForRemoval(final String tenantId) {
        LOGGER.debug("registering tenant [tenant-id: {}] for removal", tenantId);
        tenantsToDelete.add(tenantId);
    }

    /**
     * Gets a random device identifier and adds it to the list
     * of devices to be deleted after the current test has finished.
     *
     * @param tenantId The tenant that he device belongs to.
     * @return The identifier.
     * @see #deleteObjects(VertxTestContext)
     */
    public String getRandomDeviceId(final String tenantId) {
        final String deviceId = UUID.randomUUID().toString();
        final Set<String> devices = devicesToDelete.computeIfAbsent(tenantId, t -> new HashSet<>());
        devices.add(deviceId);
        LOGGER.debug("registered random device [tenant-id: {}, device-id: {}] for removal", tenantId, deviceId);
        return deviceId;
    }

    /**
     * Adds a device identifier to the list
     * of devices to be deleted after the current test has finished.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The device's identifier.
     * @see #deleteObjects(VertxTestContext)
     */
    public void addDeviceIdForRemoval(final String tenantId, final String deviceId) {
        LOGGER.debug("registering device [tenant-id: {}, device-id: {}] for removal", tenantId, deviceId);
        devicesToDelete.computeIfAbsent(tenantId, t -> new HashSet<>()).add(deviceId);
    }

    /**
     * Registers a new device for a tenant that is connected via the given gateway.
     *
     * @param tenantId The tenant that the gateway and device belong to.
     * @param gatewayId The gateway identifier.
     * @param timeoutSeconds The number of seconds to wait for the setup to succeed.
     * @return The device identifier of the newly registered device.
     * @throws IllegalStateException if setup failed.
     */
    public String setupGatewayDeviceBlocking(
            final String tenantId,
            final String gatewayId,
            final int timeoutSeconds) {

        final var result = setupGatewayDevice(tenantId, gatewayId)
                .toCompletionStage().toCompletableFuture();

        try {
            return result.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new IllegalStateException("could not set up gateway device", e);
        }
    }

    /**
     * Registers a new device for a tenant that is connected via the given gateway.
     *
     * @param tenantId The tenant that the gateway and device belong to.
     * @param gatewayId The gateway identifier.
     * @return A future indicating the outcome of the operation.
     *         The future will be completed with the device identifier of the newly
     *         registered device or will be failed with a {@link ServiceInvocationException}.
     */
    public Future<String> setupGatewayDevice(final String tenantId, final String gatewayId) {

        final String newDeviceId = getRandomDeviceId(tenantId);
        final Device newDevice = new Device().setVia(List.of(gatewayId));
        return registry.addDeviceToTenant(tenantId, newDeviceId, newDevice, "pwd")
                .map(newDeviceId);

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
            final Promise<BufferResult> result = Promise.promise();
            final Handler<Void> send = s -> {
                // send the command upstream to the device
                LOGGER.trace("sending command [name: {}, contentType: {}, payload: {}]", command, contentType, payload);
                commandClient.sendCommand(deviceId, command, contentType, payload, properties).map(responsePayload -> {
                    LOGGER.debug("successfully sent command [name: {}, payload: {}] and received response [payload: {}]",
                            command, payload, responsePayload);
                    return responsePayload;
                }).recover(t -> {
                    LOGGER.debug("could not send command or did not receive a response: {}", t.getMessage());
                    return Future.failedFuture(t);
                }).onComplete(result);
            };
            if (commandClient.getCredit() == 0) {
                commandClient.sendQueueDrainHandler(send);
            } else {
                send.handle(null);
            }
            return result.future();
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
            final Promise<Void> result = Promise.promise();
            final Handler<Void> send = s -> {
                // send the command upstream to the device
                LOGGER.trace("sending one-way command [name: {}, contentType: {}, payload: {}]", command, contentType, payload);
                commandClient.sendOneWayCommand(deviceId, command, contentType, payload, properties).map(ok -> {
                    LOGGER.debug("successfully sent one-way command [name: {}, payload: {}]", command, payload);
                    return (Void) null;
                }).recover(t -> {
                    LOGGER.debug("could not send one-way command: {}", t.getMessage());
                    return Future.failedFuture(t);
                }).onComplete(result);
            };
            if (commandClient.getCredit() == 0) {
                commandClient.sendQueueDrainHandler(send);
            } else {
                send.handle(null);
            }
            return result.future();
        });
    }

    /**
     * Gets the properties to be set on a command message.
     *
     * @param forceCommandRerouting Supplies the value for the "force-command-rerouting" property. A {@code true}
     *                              value of this property causes the command message to be rerouted to the
     *                              AMQP messaging network, mimicking the behaviour when the command message
     *                              has reached a protocol adapter instance that the command target device is
     *                              not connected to, so that the message needs to be delegated to the correct
     *                              protocol adapter instance. See the <em>CommandConsumerFactoryImpl</em> class.
     * @return The properties map.
     */
    public static Map<String, Object> newCommandMessageProperties(final Supplier<Boolean> forceCommandRerouting) {
        final HashMap<String, Object> properties = new HashMap<>();
        properties.put("force-command-rerouting", forceCommandRerouting.get());
        return properties;
    }

    /**
     * A simple implementation of subtree containment: all entries of the JsonObject that is tested to be contained
     * must be contained in the other JsonObject as well. Nested JsonObjects are treated the same by recursively calling
     * this method to test the containment.
     * JsonArrays are tested for containment as well: all elements in a JsonArray belonging to the contained JsonObject
     * must be present in the corresponding JsonArray of the other JsonObject as well. The sequence of the array elements
     * is not important (suitable for the current tests).
     *
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
        for (final Object containedElem : containedArray) {
            // currently only support contained JsonObjects
            if (!(containedElem instanceof JsonObject)) {
                return false;
            }

            boolean containingElemFound = false;
            for (final Object elemOfBiggerArray : containingArray) {
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

        return VertxTools.getCertificate(vertx, path);
    }

    /**
     * Creates a new EC based private/public key pair.
     *
     * @return The key pair.
     * @throws GeneralSecurityException if the JVM doesn't support ECC.
     */
    public KeyPair newEcKeyPair() throws GeneralSecurityException {

        final KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
        return gen.generateKeyPair();
    }

    /**
     * Create a new password credential, suitable for use in the integration test environment.
     *
     * @param authId The auth ID to use.
     * @param password The password to use.
     * @return The new instance.
     */
    public static PasswordCredential createPasswordCredential(final String authId, final String password) {
        return Credentials.createPasswordCredential(authId, password,
                OptionalInt.of(IntegrationTestSupport.MAX_BCRYPT_COST_FACTOR));
    }

    /**
     * Create a new PSK credential, suitable for use in the integration test environment.
     *
     * @param authId The auth ID to use.
     * @param key The shared key to use.
     * @return The new instance.
     */
    public static PskCredential createPskCredentials(final String authId, final String key) {

        return Credentials.createPSKCredential(authId, key);
    }

    /**
     * Verifies that a telemetry message that has been received by a downstream consumer contains
     * all properties that are required by the north bound Telemetry API.
     *
     * @param msg The message to check.
     * @param expectedTenantId The identifier of the tenant that the origin device is expected to belong to.
     * @throws AssertionError if any of the checks fail.
     */
    public static void assertTelemetryMessageProperties(
            final DownstreamMessage<? extends MessageContext> msg,
            final String expectedTenantId) {

        assertThat(msg.getTenantId()).as("message includes matching tenant ID").isEqualTo(expectedTenantId);
        assertThat(msg.getDeviceId()).as("message includes non-null device ID").isNotNull();
        final var ttdValue = msg.getTimeTillDisconnect();
        if (ttdValue != null) {
            assertThat(ttdValue).as("ttd property contains an integer >= -1").isGreaterThanOrEqualTo(-1);
            assertThat(msg.getCreationTime()).as("message contains a non-null creation time").isNotNull();
        }
    }

    /**
     * Create an event and a telemetry consumer which verify that at least one empty notification and at
     * least one further message, be it an event or telemetry, was received according to the specification
     * of gateway-based auto-provisioning.
     *
     * @param ctx The test context to fail if the notification event does not contain all required properties.
     * @param tenantId The tenant for which the consumer should be created.
     * @param deviceId The id of the device which sent the messages.
     *
     * @return A succeeded future if the message consumers have been created successfully.
     */
    public Future<Void> createAutoProvisioningMessageConsumers(
            final VertxTestContext ctx,
            final String tenantId,
            final String deviceId) {

        final Checkpoint messagesReceived = ctx.checkpoint(2);
        return amqpApplicationClient.createEventConsumer(
                tenantId,
                msg -> {
                    ctx.verify(() -> {
                        assertThat(msg.getDeviceId()).isEqualTo(deviceId);

                        if (msg.getContentType().equals(EventConstants.CONTENT_TYPE_DEVICE_PROVISIONING_NOTIFICATION)) {
                            assertThat(msg.getTenantId()).isEqualTo(tenantId);
                            final var rawMessage = msg.getMessageContext().getRawMessage();
                            assertThat(MessageHelper.getRegistrationStatus(rawMessage))
                                    .isEqualTo(EventConstants.RegistrationStatus.NEW.name());
                            messagesReceived.flag();
                        } else {
                            messagesReceived.flag();
                        }
                    });
                },
                close -> {})
            .compose(ok -> amqpApplicationClient.createTelemetryConsumer(
                    tenantId,
                    msg -> {
                        ctx.verify(() -> {
                            if (!msg.getContentType().equals(EventConstants.CONTENT_TYPE_DEVICE_PROVISIONING_NOTIFICATION)) {
                                messagesReceived.flag();
                            }
                        });
                    },
                    close -> {}))
            .mapEmpty();
    }
}
