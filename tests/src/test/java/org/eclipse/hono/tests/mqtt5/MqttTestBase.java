/*******************************************************************************
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.tests.mqtt5;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManagerFactory;

import org.eclipse.hono.tests.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hivemq.client.mqtt.MqttClientSslConfig;
import com.hivemq.client.mqtt.datatypes.MqttClientIdentifier;
import com.hivemq.client.mqtt.lifecycle.MqttClientDisconnectedListener;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import com.hivemq.client.mqtt.mqtt5.message.auth.Mqtt5SimpleAuth;
import com.hivemq.client.mqtt.mqtt5.message.connect.Mqtt5Connect;
import com.hivemq.client.mqtt.mqtt5.message.connect.connack.Mqtt5ConnAck;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.net.PemTrustOptions;
import io.vertx.core.net.SelfSignedCertificate;
import io.vertx.junit5.VertxTestContext;

/**
 * Base class for MQTT adapter integration tests using MQTT 5.0.
 *
 */
public abstract class MqttTestBase {

    private static final Vertx vertx = Vertx.vertx();

    private static TrustManagerFactory trustManagerFactory;
    private static HostnameVerifier acceptAllHostnames;

    /**
     * A logger to be used by subclasses.
     */
    protected final Logger LOGGER = LoggerFactory.getLogger(getClass());

    /**
     * A helper accessing the AMQP 1.0 Messaging Network and
     * for managing tenants/devices/credentials.
     */
    protected IntegrationTestSupport helper;
    /**
     * A client for publishing messages to the MQTT protocol adapter.
     */
    protected Mqtt5AsyncClient mqttClient;
    /**
     * Creates default MQTT client TLS options.
     *
     * @throws Exception if the trust manager factory cannot be created.
     */
    @BeforeAll
    public static void init() throws Exception {

        trustManagerFactory = new PemTrustOptions()
                .addCertPath(IntegrationTestSupport.TRUST_STORE_PATH)
                .getTrustManagerFactory(vertx);
        acceptAllHostnames = new HostnameVerifier() {
            @Override
            public boolean verify(final String hostname, final SSLSession session) {
                // disable host name verification
                return true;
            }
        };
    }

    /**
     * Create integration test helper.
     *
     * @param testInfo The JUnit test info.
     * @param ctx The vert.x test context.
     */
    @BeforeEach
    public void createHelper(final TestInfo testInfo, final VertxTestContext ctx) {
        LOGGER.info("running {}", testInfo.getDisplayName());
        helper = new IntegrationTestSupport(vertx);
        helper.init().onComplete(ctx.succeedingThenComplete());
    }

    /**
     * Closes the connection to the AMQP 1.0 Messaging Network.
     *
     * @param ctx The vert.x test context.
     */
    @AfterEach
    public void closeConnectionToAmqpMessagingNetwork(final VertxTestContext ctx) {

        helper.disconnect().onComplete(r -> ctx.completeNow());
    }

    /**
     * Closes the connection to the MQTT adapter and deletes device registry entries created during the test.
     *
     * @param ctx The vert.x test context.
     */
    @AfterEach
    public void closeMqttAdapterConnectionAndCleanupDeviceRegistry(final VertxTestContext ctx) {

        final Promise<Void> disconnectHandler = Promise.promise();
        if (mqttClient == null) {
            disconnectHandler.complete();
        } else {
            Future.fromCompletionStage(mqttClient.disconnect()).onComplete(disconnectHandler);
        }
        disconnectHandler.future().onComplete(closeAttempt -> {
            LOGGER.info("connection to MQTT adapter closed");
            mqttClient = null;
            // cleanup device registry - done after the adapter connection is closed because otherwise
            // the adapter would close the connection from its end after having received the device deletion notification
            helper.deleteObjects(ctx);
        });
    }

    /**
     * Opens a connection to the MQTT adapter using given credentials.
     *
     * @param username The username to use for authentication.
     * @param password The password to use for authentication.
     * @return A future that will be completed with the CONNACK packet received
     *         from the adapter or failed with a {@link io.vertx.mqtt.MqttConnectionException}
     *         if the connection could not be established.
     */
    protected final Future<Mqtt5ConnAck> connectToAdapter(
            final String username,
            final String password) {
        return connectToAdapter(IntegrationTestSupport.TLS_VERSION_1_2, username, password, null, null);
    }

    /**
     * Opens a connection to the MQTT adapter using given credentials.
     *
     * @param tlsVersion The TLS protocol version to use for connecting to the adapter.
     * @param username The username to use for authentication.
     * @param password The password to use for authentication.
     * @param mqttClientId MQTT client identifier to use when connecting to the MQTT adapter or
     *                     {@code null}, if an arbitrary identifier should be used.
     * @param disconnectedListener A listener to be invoked when the connection to the adapter is lost
     *                             or {@code null}, if no listener should be registered.
     * @return A future that will be completed with the CONNACK packet received
     *         from the adapter or failed with a {@link io.vertx.mqtt.MqttConnectionException}
     *         if the connection could not be established.
     */
    protected final Future<Mqtt5ConnAck> connectToAdapter(
            final String tlsVersion,
            final String username,
            final String password,
            final MqttClientIdentifier mqttClientId,
            final MqttClientDisconnectedListener disconnectedListener) {

        final var sslConfig = MqttClientSslConfig.builder()
                .trustManagerFactory(trustManagerFactory)
                .hostnameVerifier(acceptAllHostnames)
                .protocols(Set.of(tlsVersion))
                .build();
        final var auth = Mqtt5SimpleAuth.builder()
                .username(username)
                .password(password.getBytes(StandardCharsets.UTF_8))
                .build();
        return connectToAdapter(sslConfig, IntegrationTestSupport.MQTT_HOST, mqttClientId, auth, disconnectedListener);
    }

    /**
     * Opens a connection to the MQTT adapter using an X.509 client certificate.
     *
     * @param cert The client certificate to use for authentication.
     * @return A future that will be completed with the CONNACK packet received
     *         from the adapter or failed with a {@link io.vertx.mqtt.MqttConnectionException}
     *         if the connection could not be established.
     * @throws NullPointerException if client certificate is {@code null}.
     * @throws IllegalArgumentException if the certificate cannot be used for authenticating to the MQTT adapter.
     */
    protected final Future<Mqtt5ConnAck> connectToAdapter(final SelfSignedCertificate cert) {
        return connectToAdapter(cert, IntegrationTestSupport.MQTT_HOST);
    }

    /**
     * Opens a connection to the MQTT adapter using an X.509 client certificate.
     *
     * @param cert The client certificate to use for authentication.
     * @param hostname The name of the host to connect to.
     * @return A future that will be completed with the CONNACK packet received
     *         from the adapter or failed with a {@link io.vertx.mqtt.MqttConnectionException}
     *         if the connection could not be established.
     * @throws NullPointerException if any of the parameters are {@code null}.
     * @throws IllegalArgumentException if the certificate cannot be used for authenticating to the MQTT adapter.
     */
    protected final Future<Mqtt5ConnAck> connectToAdapter(
            final SelfSignedCertificate cert,
            final String hostname) {

        Objects.requireNonNull(cert);
        Objects.requireNonNull(hostname);

        final KeyManagerFactory selfSignedKeyManagerFactory;
        try {
            selfSignedKeyManagerFactory = cert.keyCertOptions().getKeyManagerFactory(vertx);
        } catch (final Exception e) {
            throw new IllegalArgumentException(e.getMessage(), e.getCause());
        }
        final var sslConfig = MqttClientSslConfig.builder()
                .trustManagerFactory(trustManagerFactory)
                .hostnameVerifier(acceptAllHostnames)
                .protocols(Set.of(IntegrationTestSupport.TLS_VERSION_1_2))
                .keyManagerFactory(selfSignedKeyManagerFactory)
                .build();

        return connectToAdapter(sslConfig, hostname, null, null, null);
    }

    /**
     * Opens a connection to the MQTT adapter using given options.
     *
     * @param sslConfig The SSL options to use for connecting to the adapter.
     * @param hostname The name of the host to connect to.
     * @param mqttClientId MQTT client identifier to use when connecting to the MQTT adapter or
     *                     {@code null}, if an arbitrary identifier should be used.
     * @param auth The credentials to use for authenticating to the adapter or {@code null}, if
     *             a client certificate (set in the SSL configuration) should be used.
     * @param disconnectedListener A listener to be invoked when the connection to the adapter is lost
     *                             or {@code null}, if no listener should be registered.
     * @return A future that will be completed with the CONNACK packet received
     *         from the adapter or failed with a {@link io.vertx.mqtt.MqttConnectionException}
     *         if the connection could not be established.
     * @throws NullPointerException if any of SSL config or host name are {@code null}.
     */
    protected final Future<Mqtt5ConnAck> connectToAdapter(
            final MqttClientSslConfig sslConfig,
            final String hostname,
            final MqttClientIdentifier mqttClientId,
            final Mqtt5SimpleAuth auth,
            final MqttClientDisconnectedListener disconnectedListener) {

        Objects.requireNonNull(sslConfig);
        Objects.requireNonNull(hostname);

        final var clientId = Optional.ofNullable(mqttClientId).orElse(MqttClientIdentifier.of(UUID.randomUUID().toString()));
        final var builder = Mqtt5Client.builder()
                .identifier(clientId)
                .sslConfig(sslConfig)
                .serverHost(hostname)
                .serverPort(IntegrationTestSupport.MQTTS_PORT);
        Optional.ofNullable(disconnectedListener).ifPresent(builder::addDisconnectedListener);
        mqttClient = builder.buildAsync();
        final var connect = Mqtt5Connect.builder()
                .cleanStart(true)
                .keepAlive(10)
                .simpleAuth(auth)
                .build();
        return Future.fromCompletionStage(mqttClient.connect(connect)).onSuccess(conAck -> {
            LOGGER.info(
                    "MQTTS connection to adapter [host: {}, port: {}] established",
                    hostname, IntegrationTestSupport.MQTTS_PORT);
        });
    }
}
