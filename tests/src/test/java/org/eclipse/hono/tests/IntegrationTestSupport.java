/*******************************************************************************
 * Copyright (c) 2016, 2023 Contributors to the Eclipse Foundation
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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.errors.UnknownTopicOrPartitionException;
import org.eclipse.hono.application.client.ApplicationClient;
import org.eclipse.hono.application.client.DownstreamMessage;
import org.eclipse.hono.application.client.MessageConsumer;
import org.eclipse.hono.application.client.MessageContext;
import org.eclipse.hono.application.client.MessageProperties;
import org.eclipse.hono.application.client.TimeUntilDisconnectNotification;
import org.eclipse.hono.application.client.amqp.ProtonBasedApplicationClient;
import org.eclipse.hono.application.client.kafka.impl.KafkaApplicationClientImpl;
import org.eclipse.hono.client.SendMessageTimeoutException;
import org.eclipse.hono.client.amqp.GenericSenderLink;
import org.eclipse.hono.client.amqp.config.ClientConfigProperties;
import org.eclipse.hono.client.amqp.connection.HonoConnection;
import org.eclipse.hono.client.amqp.connection.SendMessageSampler;
import org.eclipse.hono.client.kafka.HonoTopic;
import org.eclipse.hono.client.kafka.KafkaAdminClientConfigProperties;
import org.eclipse.hono.client.kafka.consumer.AsyncHandlingAutoCommitKafkaConsumer;
import org.eclipse.hono.client.kafka.consumer.MessagingKafkaConsumerConfigProperties;
import org.eclipse.hono.client.kafka.producer.CachingKafkaProducerFactory;
import org.eclipse.hono.client.kafka.producer.KafkaProducerFactory;
import org.eclipse.hono.client.kafka.producer.MessagingKafkaProducerConfigProperties;
import org.eclipse.hono.service.management.device.Device;
import org.eclipse.hono.test.JUnitTests;
import org.eclipse.hono.test.VertxTools;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.MessagingType;
import org.eclipse.hono.util.Pair;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.eclipse.hono.util.RequestResponseApiConstants;
import org.eclipse.hono.util.Strings;
import org.eclipse.hono.util.TenantConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.opentracingshim.OpenTracingShim;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentracing.Tracer;
import io.opentracing.noop.NoopSpan;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.junit5.VertxTestContext;
import io.vertx.kafka.admin.KafkaAdminClient;

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
     * The name of the system property that contains the port number that the Auth service's
     * HTTP endpoint listens on.
     */
    public static final String PROPERTY_AUTH_PORT_HTTP = "auth.http.port";
    /**
     * The name of the system property that contains the port number that the Auth service's
     * AMQP endpoint listens on.
     */
    public static final String PROPERTY_AUTH_PORT_AMQPS = "auth.amqps.port";
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
     * The name of the system property to use for setting the user name for authenticating to the Device Registry's
     * HTTP endpoint.
     */
    public static final String PROPERTY_DEVICEREGISTRY_HTTP_AUTHCONFIG_USERNAME = "deviceregistry.http.authConfig.username";
    /**
     * The name of the system property to use for setting the password for authenticating to the Device Registry's
     * HTTP endpoint.
     */
    public static final String PROPERTY_DEVICEREGISTRY_HTTP_AUTHCONFIG_PASSWORD = "deviceregistry.http.authConfig.password";
    /**
     * The name of the system property to use for setting the port number that the Device Registry
     * should listen on for HTTP connections.
     */
    public static final String PROPERTY_DEVICEREGISTRY_HTTP_PORT = "deviceregistry.http.port";
    /**
     * The name of the system property to use for setting the IP address of the AMQP Messaging Network.
     */
    public static final String PROPERTY_DOWNSTREAM_HOST = "downstream.host";
    /**
     * The name of the system property to use for setting the port number that the AMQP Messaging
     * Network should listen on for connections.
     */
    public static final String PROPERTY_DOWNSTREAM_PORT = "downstream.amqps.port";
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
     * The name of the system property to use for setting the address of Kafka.
     */
    public static final String PROPERTY_DOWNSTREAM_BOOTSTRAP_SERVERS = "downstream.bootstrap.servers";
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
     * The name of the system property to use for setting the IP address of the Lora protocol adapter.
     */
    public static final String PROPERTY_LORA_HOST = "adapter.lora.host";
    /**
     * The name of the system property to use for setting the port number that the Lora adapter
     * should listen on for requests.
     */
    public static final String PROPERTY_LORA_PORT = "adapter.lora.port";
    /**
     * The name of the system property to use for setting the port number that the Lora adapter
     * should listen on for secure requests.
     */
    public static final String PROPERTY_LORA_SECURE_PORT = "adapter.lora.secure-port";
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
     * The name of the system property to use for setting the maximum time (in ms) that adapters
     * wait for an acknowledgement of a command sent to a device.
     */
    public static final String PROPERTY_SEND_MESSAGE_TO_DEVICE_TIMEOUT = "adapter.sendMessageToDeviceTimeout";

    /**
     * The IP address of the Auth service.
     */
    public static final String AUTH_HOST = System.getProperty(PROPERTY_AUTH_HOST, DEFAULT_HOST);
    /**
     * The port number that the Authentication Server's AMQP endpoint service listens on.
     */
    public static final int AUTH_PORT_AMQPS = Integer.getInteger(PROPERTY_AUTH_PORT_AMQPS, Constants.PORT_AMQPS);
    /**
     * The port number that the Authentication Server's HTTP endpoint listens on.
     */
    public static final int AUTH_PORT_HTTP = Integer.getInteger(PROPERTY_AUTH_PORT_HTTP, 8088);

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
     * The user name to use for authenticating to the Device Registry's HTTP endpoint.
     */
    public static final String HONO_DEVICEREGISTRY_AUTHCONFIG_USERNAME = System.getProperty(PROPERTY_DEVICEREGISTRY_HTTP_AUTHCONFIG_USERNAME);
    /**
     * The user name to use for authenticating to the Device Registry's HTTP endpoint.
     */
    public static final String HONO_DEVICEREGISTRY_AUTHCONFIG_PASSWORD = System.getProperty(PROPERTY_DEVICEREGISTRY_HTTP_AUTHCONFIG_PASSWORD);
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
     * The address of Kafka.
     */
    public static final String DOWNSTREAM_BOOTSTRAP_SERVERS = System.getProperty(PROPERTY_DOWNSTREAM_BOOTSTRAP_SERVERS);

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
     * The IP address of the Lora protocol adapter.
     */
    public static final String LORA_HOST = System.getProperty(PROPERTY_LORA_HOST, DEFAULT_HOST);
    /**
     * The  port number that the Lora adapter listens on for requests.
     */
    public static final int LORA_PORT = Integer.getInteger(PROPERTY_LORA_PORT, DEFAULT_HTTP_PORT);
    /**
     * The  port number that the Lora adapter listens on for secure requests.
     */
    public static final int LORA_SECURE_PORT = Integer.getInteger(PROPERTY_LORA_SECURE_PORT, DEFAULT_HTTPS_PORT);
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
    public static final int MSG_COUNT = Integer.getInteger("msg.count", 200);

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
     * The identifier of the TLS 1.2 protocol.
     */
    public static final String TLS_VERSION_1_2 = "TLSv1.2";
    /**
     * The identifier of the TLS 1.3 protocol.
     */
    public static final String TLS_VERSION_1_3 = "TLSv1.3";

    /**
     * Pattern used for the <em>name</em> field of the {@code @ParameterizedTest} annotation.
     */
    public static final String PARAMETERIZED_TEST_NAME_PATTERN = JUnitTests.PARAMETERIZED_TEST_NAME_PATTERN;

    /**
     * The default factor to apply when determining the timeout to use for executing test cases in a CI environment.
     */
    public static final int TEST_ENVIRONMENT_TIMEOUT_MULTIPLICATOR = 6;

    /**
     * The number of seconds to add to the default timeout when creating Kafka topics.
     */
    public static final int KAFKA_TOPIC_CREATION_ADD_TO_TIMEOUT = 2; // seconds to add

    private static final OpenTelemetry OPENTELEMETRY = OpenTelemetrySdk.builder()
            .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
            .build();
    /**
     * An OpenTracing tracer that can be used by devices and downstream clients to inject
     * and/or extract a trace context into/from messages.
     */
    public static Tracer CLIENT_TRACER = OpenTracingShim.createTracerShim(OPENTELEMETRY);

    private static final Logger LOGGER = LoggerFactory.getLogger(IntegrationTestSupport.class);

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

    private static final Deque<Pair<List<String>, Instant>> tenantsToDeleteTopicsForAfterDelay = new ConcurrentLinkedDeque<>();

    /**
     * A client for managing tenants/devices/credentials.
     */
    public DeviceRegistryHttpClient registry;
    /**
     * A client for connecting to Hono's north bound APIs
     * depending on the configured messaging network.
     */
    public ApplicationClient<?> applicationClient;

    private final Set<String> tenantsToDelete = new HashSet<>();
    private final Map<String, Set<String>> devicesToDelete = new HashMap<>();
    private final Vertx vertx;

    private HonoConnection protonBasedHonoConnection;
    private KafkaProducerFactory<String, Buffer> kafkaProducerFactory;

    static {
        final var type = System.getProperty("vertx.json.base64", "new");
        LOGGER.info("vert.x uses {} Base64 encoder", type);
    }

    /**
     * Creates a new helper instance.
     *
     * @param vertx The vert.x instance.
     * @throws NullPointerException if vert.x is {@code null}.
     */
    public IntegrationTestSupport(final Vertx vertx) {
        this.vertx = Objects.requireNonNull(vertx);
    }

    /**
     * Gets a host name in the {@code nip.io} domain for a given host name.
     *
     * @param hostname The host name.
     * @param virtualHost The virtual host name to prepend.
     * @return The host name.
     */
    public static String getSniHostname(final String hostname, final String virtualHost) {

        Objects.requireNonNull(hostname);
        final String literalIpAddress = "localhost".equals(hostname) ? DEFAULT_HOST : hostname;
        final var b = new StringJoiner(".");
        Optional.ofNullable(virtualHost).ifPresent(b::add);
        b.add(literalIpAddress).add("nip.io");
        return b.toString();
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
     * Get the messaging system type which is configured for the running integration test.
     *
     * @return The messaging system type.
     */
    public static MessagingType getConfiguredMessagingType() {
        if (Strings.isNullOrEmpty(IntegrationTestSupport.DOWNSTREAM_BOOTSTRAP_SERVERS)) {
            return MessagingType.amqp;
        } else {
            return MessagingType.kafka;
        }
    }

    /**
     * Checks whether Kafka based messaging is used.
     * @return {@code true} if Kafka is used.
     */
    public static boolean isUsingKafkaMessaging() {
        return getConfiguredMessagingType() == MessagingType.kafka;
    }

    /**
     * Checks whether AMQP based messaging is used.
     *
     * @return {@code true} if AMQP based messaging is used.
     */
    public static boolean isUsingAmqpMessaging() {
        return getConfiguredMessagingType() == MessagingType.amqp;
    }

    /**
     * Creates properties for connecting to the AMQP Messaging Network's secure port.
     *
     * @return The properties.
     */
    public static ClientConfigProperties getMessagingNetworkProperties() {

        final var props = getClientConfigProperties(
                IntegrationTestSupport.DOWNSTREAM_HOST,
                IntegrationTestSupport.DOWNSTREAM_PORT,
                IntegrationTestSupport.DOWNSTREAM_USER,
                IntegrationTestSupport.DOWNSTREAM_PWD);
        props.setTrustStorePath(TRUST_STORE_PATH);
        props.setHostnameVerificationRequired(false);
        props.setSecureProtocols(List.of("TLSv1.3"));
        props.setSendMessageTimeout(getSendCommandTimeout());
        return props;
    }

    /**
     * Creates properties for connecting a consumer to Kafka.
     *
     * @return The properties.
     */
    public static MessagingKafkaConsumerConfigProperties getKafkaConsumerConfig() {
        LOGGER.info("Kafka Consumers are configured to connect to broker(s) at {}", IntegrationTestSupport.DOWNSTREAM_BOOTSTRAP_SERVERS);
        final var configProps = new MessagingKafkaConsumerConfigProperties();
        configProps.setConsumerConfig(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, IntegrationTestSupport.DOWNSTREAM_BOOTSTRAP_SERVERS,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.GROUP_ID_CONFIG, "its-" + UUID.randomUUID()));
        return configProps;
    }

    /**
     * Creates properties for connecting a producer to Kafka.
     *
     * @return The properties.
     */
    public static MessagingKafkaProducerConfigProperties getKafkaProducerConfig() {
        LOGGER.info("Kafka Producers are configured to connect to broker(s) at {}", IntegrationTestSupport.DOWNSTREAM_BOOTSTRAP_SERVERS);
        final var configProps = new MessagingKafkaProducerConfigProperties();
        configProps.setProducerConfig(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, IntegrationTestSupport.DOWNSTREAM_BOOTSTRAP_SERVERS));
        return configProps;
    }

    /**
     * Creates properties for connecting an admin client to Kafka.
     *
     * @return The properties.
     */
    public static KafkaAdminClientConfigProperties getKafkaAdminClientConfig() {
        LOGGER.info("Kafka Admin Clients are configured to connect to broker(s) at {}", IntegrationTestSupport.DOWNSTREAM_BOOTSTRAP_SERVERS);
        final KafkaAdminClientConfigProperties adminClientConfig = new KafkaAdminClientConfigProperties();
        adminClientConfig.setAdminClientConfig(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, IntegrationTestSupport.DOWNSTREAM_BOOTSTRAP_SERVERS,
                ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, "2000"));
        return adminClientConfig;
    }

    /**
     * Creates properties for connecting to the AMQP protocol adapter's secure port.
     *
     * @param username The username to use for authenticating to the adapter.
     * @param password The password to use for authenticating to the adapter.
     * @return The properties.
     */
    public static ClientConfigProperties getAmqpAdapterProperties(final String username, final String password) {

        final var props = getClientConfigProperties(
                IntegrationTestSupport.AMQP_HOST,
                IntegrationTestSupport.AMQPS_PORT,
                username,
                password);
        props.setTrustStorePath(TRUST_STORE_PATH);
        props.setHostnameVerificationRequired(false);
        props.setSecureProtocols(List.of("TLSv1.3"));
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

        return getClientConfigProperties(
                IntegrationTestSupport.HONO_DEVICEREGISTRY_HOST,
                IntegrationTestSupport.HONO_DEVICEREGISTRY_AMQP_PORT,
                username,
                password);
    }

    /**
     * Gets the value for the HTTP *Authentication* header to use in requests to the Device Registry's HTTP endpoint.
     * <p>
     * The user name and password are read from system properties {@link #HONO_DEVICEREGISTRY_AUTHCONFIG_USERNAME} and
     * {@link #HONO_DEVICEREGISTRY_AUTHCONFIG_PASSWORD} respectively.
     *
     * @return The header value or {@code null} if no credentials have been set.
     */
    public static String getRegistryManagementApiAuthHeader() {
        return Optional.ofNullable(IntegrationTestSupport.HONO_DEVICEREGISTRY_AUTHCONFIG_USERNAME)
                .map(username -> {
                    final String credentials = username + ":" + IntegrationTestSupport.HONO_DEVICEREGISTRY_AUTHCONFIG_PASSWORD;
                    return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
                })
                .orElse(null);
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
     *         If Kafka as messaging system is used, an extra
     *         {@value IntegrationTestSupport#KAFKA_TOPIC_CREATION_ADD_TO_TIMEOUT} seconds are added
     *         (for creation/propagation of topics created during setup).
     */
    public static long getTestSetupTimeout() {
        return DEFAULT_TEST_SETUP_TIMEOUT_SECONDS * getTimeoutMultiplicator()
                + (isUsingKafkaMessaging() ? KAFKA_TOPIC_CREATION_ADD_TO_TIMEOUT : 0);
    }

    /**
     * Determines the time to wait before timing out a request to send
     * a command to a device.
     * <p>
     * Consider using {@link #getSendCommandTimeout(boolean)} instead to prevent timeouts in case Kafka is used and
     * the first command gets sent directly after the command subscription result got completed.
     *
     * @return The time out in milliseconds. The value will be
     *         {@link #SEND_MESSAGE_TO_DEVICE_TIMEOUT} + ({@link #AMQP_TIMEOUT} * {@link #getTimeoutMultiplicator()} * 2).
     */
    public static long getSendCommandTimeout() {
        return SEND_MESSAGE_TO_DEVICE_TIMEOUT + (AMQP_TIMEOUT * getTimeoutMultiplicator() * 2L);
    }

    /**
     * Determines the time to wait before timing out a request to send
     * a command to a device.
     * <p>
     * Supports specifying whether this is about the first command to be
     * sent after the command subscription result got completed.
     *
     * @param isFirstCommand {@code true} if the timeout for the first command after a command subscription shall be
     *            returned.
     * @return The time out in milliseconds. The value will be
     *         {@link #getSendCommandTimeout()}, plus an extra {@value IntegrationTestSupport#KAFKA_TOPIC_CREATION_ADD_TO_TIMEOUT}
     *         seconds if Kafka is used and <code>isFirstCommand</code> is {@code true}, letting the Kafka consumer
     *         be ready to receive commands.
     */
    public long getSendCommandTimeout(final boolean isFirstCommand) {
        long millis = getSendCommandTimeout();
        if (isUsingKafkaMessaging() && isFirstCommand) {
            millis += KAFKA_TOPIC_CREATION_ADD_TO_TIMEOUT * 1000L;
        }
        return millis;
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
     * Connects to the messaging network, i.e. Kafka if configured, AMQP otherwise.
     * <p>
     * Also creates an HTTP client for accessing the Device Registry.
     *
     * @return A future indicating the outcome of the operation.
     */
    public Future<Void> init() {
        if (isUsingAmqpMessaging()) {
            return init(getMessagingNetworkProperties());
        } else {
            return init(getKafkaConsumerConfig());
        }
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
    public Future<Void> init(final ClientConfigProperties downstreamProps) {

        initRegistryClient();
        protonBasedHonoConnection = HonoConnection.newConnection(vertx, downstreamProps);
        applicationClient = new ProtonBasedApplicationClient(protonBasedHonoConnection);

        return applicationClient.start()
                .onSuccess(connected -> LOGGER.info("connected to AMQP Messaging Network [{}:{}]",
                        downstreamProps.getHost(), downstreamProps.getPort()));
    }

    /**
     * Connects to Kafka.
     * <p>
     * Also creates an HTTP client for accessing the Device Registry.
     *
     * @param kafkaDownstreamProps The properties for connecting to Kafka.
     *
     * @return A future indicating the outcome of the operation.
     */
    public Future<Void> init(final MessagingKafkaConsumerConfigProperties kafkaDownstreamProps) {

        initRegistryClient();

        kafkaProducerFactory = CachingKafkaProducerFactory.sharedFactory(vertx);
        final Promise<Void> readyTracker = Promise.promise();
        final var client = new KafkaApplicationClientImpl(
                vertx,
                kafkaDownstreamProps,
                kafkaProducerFactory,
                getKafkaProducerConfig());
        client.addOnKafkaProducerReadyHandler(readyTracker);
        return client.start()
                .compose(ok -> readyTracker.future())
                .onSuccess(ok -> applicationClient = client);
    }

    /**
     * Creates an HTTP client for accessing the Device Registry.
     */
    public void initRegistryClient() {

        registry = new DeviceRegistryHttpClient(
                vertx,
                IntegrationTestSupport.HONO_DEVICEREGISTRY_HOST,
                IntegrationTestSupport.HONO_DEVICEREGISTRY_HTTP_PORT,
                Map.of(TenantConstants.FIELD_EXT_MESSAGING_TYPE, getConfiguredMessagingType().name())
        );
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

        // delete tenant-related Kafka topics
        final Future<Void> tenantTopicsDeletionDoneFuture = deleteTenantKafkaTopics(tenantsToDelete);

        // delete device registry entries
        // first delete devices
        if (!devicesToDelete.isEmpty()) {
            LOGGER.debug("deleting {} temporary devices ...", devicesToDelete.size());
        }
        final var deleteDevices = Future
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
                    return Future.join(tenantsToDelete.stream()
                                    .map(tenantId -> registry.removeTenant(tenantId, true))
                                    .collect(Collectors.toList()));
                })
                .compose(ok -> {
                    if (registry == null) {
                        return Future.succeededFuture(0);
                    } else {
                        return registry.searchTenants(
                                Optional.of(30),
                                Optional.of(0),
                                List.of(),
                                List.of())
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
                                    LOGGER.debug("search for tenants failed: {} - {}",
                                            searchResponse.statusCode(), searchResponse.statusMessage());
                                    return 0;
                                }
                            })
                            .otherwise(t -> {
                                LOGGER.info("error querying tenants endpoint", t);
                                return 0;
                            });
                    }
                })
                // wait for deletion of Kafka topics result
                .compose(tenantsInRegistry -> tenantTopicsDeletionDoneFuture.map(tenantsInRegistry))
                // complete, and check if we successfully deleted the devices
                .onComplete(ctx.succeeding(tenantsInRegistry -> {
                    ctx.verify(() -> {
                        assertWithMessage("devices successfully deleted").that(devicesDeleted.get()).isTrue();
                        assertWithMessage("no. of tenants in registry after successful deletion of temporary tenants")
                                .that(tenantsInRegistry)
                                .isEqualTo(0);
                    });
                    ctx.completeNow();
                }));

    }

    private Future<Void> deleteTenantKafkaTopics(final List<String> tenantsToDelete) {

        if (!isUsingKafkaMessaging()) {
            return Future.succeededFuture();
        }

        // topics for the given tenants are not deleted right away: It could be that the offset-commit
        // interval of the CommandRouter command consumer (5s) hasn't elapsed yet and it has to be avoided
        // to delete the topics before the consumer has committed corresponding offsets (otherwise the consumer
        // will retry the commit for some time and be blocked during that time)
        final Promise<Void> tenantTopicsDeletionDonePromise = Promise.promise();
        // commit interval with added buffer
        final var timeAtWhichTopicsShouldGetDeleted = Instant.now()
                .plus(AsyncHandlingAutoCommitKafkaConsumer.DEFAULT_COMMIT_INTERVAL)
                .plusSeconds(2);
        tenantsToDeleteTopicsForAfterDelay.addLast(Pair.of(tenantsToDelete, timeAtWhichTopicsShouldGetDeleted));

        // now determine topics that are "due"
        final var tenantsToDeleteTopicsForNow = new ArrayList<String>();
        final var now = Instant.now();
        final var iterator = tenantsToDeleteTopicsForAfterDelay.iterator();
        while (iterator.hasNext()) {
            final var tenantsToDeleteAndInstantPair = iterator.next();
            if (tenantsToDeleteAndInstantPair.two().isBefore(now)) {
                tenantsToDeleteTopicsForNow.addAll(tenantsToDeleteAndInstantPair.one());
                iterator.remove();
            }
        }

        if (tenantsToDeleteTopicsForNow.isEmpty()) {
            tenantTopicsDeletionDonePromise.complete();
        } else {
            // and delete them
            try {
                final var adminClient = KafkaAdminClient.create(
                        vertx,
                        getKafkaAdminClientConfig().getAdminClientConfig("test"));
                LOGGER.debug("deleting topics for temporary tenants {}", tenantsToDeleteTopicsForNow);
                final List<String> topicNames = tenantsToDeleteTopicsForNow.stream()
                        .flatMap(tenant -> HonoTopic.Type.MESSAGING_API_TYPES.stream()
                                .map(type -> new HonoTopic(type, tenant).toString()))
                        .collect(Collectors.toList());
                LOGGER.info("deleting {} topics for temporary tenants", topicNames.size());
                adminClient.deleteTopics(topicNames)
                    .recover(t -> {
                        if (t instanceof UnknownTopicOrPartitionException) {
                            LOGGER.debug("one or more topics/partitions could not be deleted, maybe because they didn't exist yet");
                        } else {
                            LOGGER.warn("error deleting topics", t);
                        }
                        return Future.succeededFuture();
                    })
                    .compose(ok -> {
                        LOGGER.debug("done triggering deletion of topics for tenants {}", tenantsToDeleteTopicsForNow);
                        return adminClient.close();
                    })
                    .recover(t -> {
                        LOGGER.warn("error closing Kafka admin client", t);
                        return Future.succeededFuture();
                    })
                    .onComplete(tenantTopicsDeletionDonePromise);
            } catch (final KafkaException e) {
                LOGGER.error("failed to create Kafka admin client", e);
                tenantTopicsDeletionDonePromise.complete();
            }
        }

        return tenantTopicsDeletionDonePromise.future();
    }

    /**
     * Closes the application client's connection to the messaging infrastructure.
     *
     * @return A future indicating the outcome of the operation.
     */
    public Future<Void> disconnect() {
        return applicationClient.stop()
                .onSuccess(ok -> LOGGER.info("connection to messaging infrastructure closed"));
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
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
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
     *         registered device or will be failed with a {@link org.eclipse.hono.client.ServiceInvocationException}.
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
     * @return A future that is either succeeded with the response payload from the device or
     *         failed with a {@link org.eclipse.hono.client.ServiceInvocationException}.
     */
    public Future<DownstreamMessage<?>> sendCommand(
            final TimeUntilDisconnectNotification notification,
            final String command,
            final String contentType,
            final Buffer payload) {

        return sendCommand(
                notification.getTenantId(),
                notification.getDeviceId(),
                command,
                contentType,
                payload,
                notification.getMillisecondsUntilExpiry());
    }

    /**
     * Sends a command to a device and receives a response.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The identifier of the device.
     * @param command The name of the command to send.
     * @param contentType The type of the command's input data.
     * @param payload The command's input data to send to the device.
     * @param requestTimeout The number of milliseconds to wait for a response from the device.
     * @return A future that is either succeeded with the response payload from the device or
     *         failed with a {@link org.eclipse.hono.client.ServiceInvocationException}.
     */
    public Future<DownstreamMessage<? extends MessageContext>> sendCommand(
            final String tenantId,
            final String deviceId,
            final String command,
            final String contentType,
            final Buffer payload,
            final long requestTimeout) {

        final Promise<Void> timeOutTracker = Promise.promise();
        final long timerId = vertx.setTimer(requestTimeout, id -> timeOutTracker
                .fail(new SendMessageTimeoutException("sending command timed out after " + requestTimeout + "ms")));

        LOGGER.trace("sending command [name: {}, contentType: {}, payload: {}]", command, contentType, payload);
        // send the command upstream to the device and receive the command response
        final Future<? extends DownstreamMessage<?>> sendCommandTracker = applicationClient
                .sendCommand(tenantId, deviceId, command, payload, contentType)
                .onComplete(ar -> {
                    vertx.cancelTimer(timerId);
                    timeOutTracker.tryComplete();
                });

        return Future.all(sendCommandTracker, timeOutTracker.future())
                .recover(error -> {
                    LOGGER.debug("got error sending command: {}", error.getMessage());
                    return Future.failedFuture(error);
                })
                .map(ok -> {
                    final DownstreamMessage<?> response = sendCommandTracker.result();
                    LOGGER.debug("successfully sent command [name: {}, payload: {}] and received response [payload: {}]",
                            command, payload, response.getPayload());
                    return response;
                });
    }

    /**
     * Sends a one-way command to a device.
     *
     * @param notification The empty notification indicating the device's readiness to receive a command.
     * @param command The name of the command to send.
     * @param contentType The type of the command's input data.
     * @param payload The command's input data to send to the device.
     * @return A future that is either succeeded if the command has been sent to the device or
     *         failed with a {@link org.eclipse.hono.client.ServiceInvocationException}.
     */
    public Future<Void> sendOneWayCommand(
            final TimeUntilDisconnectNotification notification,
            final String command,
            final String contentType,
            final Buffer payload) {

        return sendOneWayCommand(
                notification.getTenantId(),
                notification.getDeviceId(),
                command,
                contentType,
                payload,
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
     * @param requestTimeout The number of milliseconds to wait for the command being sent to the device.
     * @return A future that is either succeeded if the command has been sent to the device or
     *         failed with a {@link org.eclipse.hono.client.ServiceInvocationException}.
     */
    public Future<Void> sendOneWayCommand(
            final String tenantId,
            final String deviceId,
            final String command,
            final String contentType,
            final Buffer payload,
            final long requestTimeout) {

        final Promise<Void> timeOutTracker = Promise.promise();
        final long timerId = vertx.setTimer(requestTimeout, id -> timeOutTracker
                .fail(new SendMessageTimeoutException("sending command timed out after " + requestTimeout + "ms")));

        // send the one way command upstream to the device
        final Future<Void> sendOneWayCommandTracker = applicationClient
                .sendOneWayCommand(tenantId, deviceId, command, payload, contentType, NoopSpan.INSTANCE.context())
                .onComplete(ar -> {
                    vertx.cancelTimer(timerId);
                    timeOutTracker.tryComplete();
                    if (ar.succeeded()) {
                        LOGGER.debug("successfully sent one-way command [name: {}, payload: {}]", command, payload);
                    } else {
                        LOGGER.debug("could not send one-way command: {}", ar.cause().getMessage());
                    }
                });

        return Future.all(sendOneWayCommandTracker, timeOutTracker.future())
                .mapEmpty();
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

        final KeyPairGenerator gen = KeyPairGenerator.getInstance(CredentialsConstants.EC_ALG);
        return gen.generateKeyPair();
    }

    /**
     * Verifies that a device status' auto provisioning properties have certain values.
     *
     * @param deviceStatus The device status object.
     * @param expectedAutoProvisionedStatus The value to compare the auto-provisioned property to.
     * @throws AssertionError if any of the checks fail.
     */
    public static void assertDeviceStatusProperties(
            final JsonObject deviceStatus,
            final boolean expectedAutoProvisionedStatus) {

        assertWithMessage("status in device registration info").that(deviceStatus).isNotNull();
        if (expectedAutoProvisionedStatus) {
            assertWithMessage("auto-provisioned property")
                    .that(deviceStatus.getBoolean(RegistryManagementConstants.FIELD_AUTO_PROVISIONED))
                    .isTrue();
        } else {
            final Boolean autoProvisioned = deviceStatus.getBoolean(RegistryManagementConstants.FIELD_AUTO_PROVISIONED);
            assertWithMessage("auto-provisioned property is missing or has value 'false'")
                    .that(autoProvisioned == null || !autoProvisioned)
                    .isTrue();
        }
    }

    /**
     * Verifies that a response body contains a JSON object with a non-null <em>error</em> property.
     *
     * @param response The response to check.
     * @throws AssertionError if any of the checks fails.
     */
    public static void assertErrorPayload(final HttpResponse<Buffer> response) {
        assertThat(response).isNotNull();
        final JsonObject payload = response.bodyAsJsonObject();
        assertThat(payload.getString(RequestResponseApiConstants.FIELD_ERROR)).isNotNull();
    }

    /**
     * Creates a consumer for an expected auto-provisioning notification.
     *
     * @param ctx The test context to fail if the notification event does not contain all required properties.
     * @param autoProvisionedDeviceIdentifier The promise to complete with the auto-provisioned device's identifier.
     * @param tenantId The tenant for which the consumer should be created.
     *
     * @return A succeeded future if the message consumer has been created successfully.
     */
    public Future<Void> createAutoProvisioningNotificationConsumer(
            final VertxTestContext ctx,
            final Promise<String> autoProvisionedDeviceIdentifier,
            final String tenantId) {

        return applicationClient.createEventConsumer(
                tenantId,
                msg -> {
                    ctx.verify(() -> {
                        if (msg.getContentType().equals(EventConstants.CONTENT_TYPE_DEVICE_PROVISIONING_NOTIFICATION)) {
                            assertThat(msg.getTenantId()).isEqualTo(tenantId);
                            assertThat(getRegistrationStatus(msg)).isEqualTo(EventConstants.RegistrationStatus.NEW.name());
                            autoProvisionedDeviceIdentifier.complete(msg.getDeviceId());
                        }
                    });
                },
                close -> {})
            .mapEmpty();
    }

    /**
     * Create an event and a telemetry consumer which verify that at least one empty notification and at
     * least one further message, be it an event or telemetry, was received according to the specification
     * of gateway-based auto-provisioning.
     *
     * @param ctx The test context to fail if the notification event does not contain all required properties.
     * @param resultHandler The promise to complete, once the provisioning notification and the other message have been
     *                      received.
     * @param tenantId The tenant for which the consumer should be created.
     * @param deviceId The id of the device which sent the messages.
     *
     * @return A succeeded future if the message consumers have been created successfully.
     */
    public Future<Void> createAutoProvisioningMessageConsumers(
            final VertxTestContext ctx,
            final Promise<Void> resultHandler,
            final String tenantId,
            final String deviceId) {

        final Promise<Void> provisioningMessageReceived = Promise.promise();
        final Promise<Void> telemetryMessageReceived = Promise.promise();
        Future.all(provisioningMessageReceived.future(), telemetryMessageReceived.future())
            .onSuccess(ok -> resultHandler.complete())
            .onFailure(t -> resultHandler.fail(t));

        return applicationClient.createEventConsumer(
                tenantId,
                msg -> {
                    ctx.verify(() -> {
                        assertThat(msg.getDeviceId()).isEqualTo(deviceId);

                        if (msg.getContentType().equals(EventConstants.CONTENT_TYPE_DEVICE_PROVISIONING_NOTIFICATION)) {
                            assertThat(msg.getTenantId()).isEqualTo(tenantId);
                            assertThat(getRegistrationStatus(msg)).isEqualTo(EventConstants.RegistrationStatus.NEW.name());
                            provisioningMessageReceived.complete();
                        } else {
                            telemetryMessageReceived.tryComplete();
                        }
                    });
                },
                close -> {})
            .compose(ok -> applicationClient.createTelemetryConsumer(
                    tenantId,
                    msg -> {
                        ctx.verify(() -> {
                            if (!msg.getContentType().equals(EventConstants.CONTENT_TYPE_DEVICE_PROVISIONING_NOTIFICATION)) {
                                telemetryMessageReceived.tryComplete();
                            }
                        });
                    },
                    close -> {}))
            .mapEmpty();
    }

    /**
     * Creates a client for consuming command response messages with content-type
     * {@value CommandConstants#CONTENT_TYPE_DELIVERY_FAILURE_NOTIFICATION} for the given tenant identifier.
     *
     * @param ctx The test context to run the assertions on.
     * @param tenantId The identifier of the tenant to use.
     * @param expectedErrorStatus The status to test the received command response messages for.
     * @param messageHandler The handler to apply for each received message (may be {@code null}).
     * @param errorMessageValidator The validator checking the command response error message (may be {@code null}).
     * @return The created client.
     * @throws NullPointerException If ctx or tenantId is {@code null}.
     */
    public Future<MessageConsumer> createDeliveryFailureCommandResponseConsumer(
            final VertxTestContext ctx,
            final String tenantId,
            final int expectedErrorStatus,
            final Handler<DownstreamMessage<?>> messageHandler,
            final Function<String, Boolean> errorMessageValidator) {
        Objects.requireNonNull(ctx);
        Objects.requireNonNull(tenantId);
        return applicationClient.createCommandResponseConsumer(tenantId, "ignored",
                response -> {
                    ctx.verify(() -> {
                        assertThat(response.getContentType()).isEqualTo(CommandConstants.CONTENT_TYPE_DELIVERY_FAILURE_NOTIFICATION);
                        assertThat(response.getStatus()).isEqualTo(expectedErrorStatus);
                        final String errorMessage = response.getPayload().toJsonObject().getString("error");
                        assertThat(errorMessage).isNotNull();
                        LOGGER.trace("received delivery failure command response; correlation-id: {}, error: {}",
                                response.getCorrelationId(), errorMessage);
                        if (errorMessageValidator != null) {
                            assertThat(errorMessageValidator.apply(errorMessage)).isTrue();
                        }
                    });
                    if (messageHandler != null) {
                        messageHandler.handle(response);
                    }
                }, null);
    }

    /**
     * Creates a new AMQP sender link for publishing messages.
     *
     * @param endpoint The endpoint to send messages to.
     * @param tenantId The tenant identifier.
     * @return A future indicating the outcome.
     * @throws NullPointerException if any of the parameters are {@code null}.
     * @throws UnsupportedOperationException if the messaging network type is other than AMQP.
     */
    public Future<GenericSenderLink> createGenericAmqpMessageSender(final String endpoint, final String tenantId) {
        Objects.requireNonNull(endpoint);
        Objects.requireNonNull(tenantId);

        if (protonBasedHonoConnection == null) {
            throw new UnsupportedOperationException("messaging network type must be AMQP");
        }
        return GenericSenderLink.create(
                protonBasedHonoConnection,
                endpoint,
                tenantId,
                SendMessageSampler.noop(),
                s -> {})
                .onSuccess(ok -> LOGGER.info("created proton based message sender [endpoint: {}, tenant: {}]",
                        endpoint, tenantId))
                .onFailure(error -> LOGGER.error("error creating proton based message sender [endpoint: {}, tenant: {}]",
                        endpoint, tenantId, error));
    }

    /**
     * Creates a new generic Kafka based sender for producing messages.
     *
     * @return A future indicating the outcome.
     * @throws UnsupportedOperationException if the messaging network type is other than AMQP.
     */
    public Future<GenericKafkaSender> createGenericKafkaSender() {

        if (kafkaProducerFactory == null) {
            throw new UnsupportedOperationException("messaging network type must be Kafka");
        }
        final GenericKafkaSender genericKafkaSender = new GenericKafkaSender(kafkaProducerFactory,
                getKafkaProducerConfig());
        return genericKafkaSender.start().map(genericKafkaSender);
    }

    private static String getRegistrationStatus(final DownstreamMessage<? extends MessageContext> msg) {
        final MessageProperties properties = msg.getProperties();
        return properties.getProperty(MessageHelper.APP_PROPERTY_REGISTRATION_STATUS, String.class);
    }
}
