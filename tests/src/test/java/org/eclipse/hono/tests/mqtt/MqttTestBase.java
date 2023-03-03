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

package org.eclipse.hono.tests.mqtt;

import java.util.Objects;
import java.util.Set;

import org.eclipse.hono.tests.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.net.PemTrustOptions;
import io.vertx.core.net.SelfSignedCertificate;
import io.vertx.junit5.VertxTestContext;
import io.vertx.mqtt.MqttClient;
import io.vertx.mqtt.MqttClientOptions;
import io.vertx.mqtt.messages.MqttConnAckMessage;

/**
 * Base class for MQTT adapter integration tests.
 *
 */
public abstract class MqttTestBase {

    /**
     * Default options for connecting to the MQTT adapter.
     */
    protected static MqttClientOptions defaultOptions;

    /**
     * A logger to be used by subclasses.
     */
    protected final Logger LOGGER = LoggerFactory.getLogger(getClass());
    /**
     * The vert.xt instance to run on.
     */
    protected final Vertx vertx = Vertx.vertx();

    /**
     * A helper accessing the AMQP 1.0 Messaging Network and
     * for managing tenants/devices/credentials.
     */
    protected IntegrationTestSupport helper;
    /**
     * A client for publishing messages to the MQTT protocol adapter.
     */
    protected MqttClient mqttClient;
    /**
     * The vert.x {@code Context} that the MQTT client runs on.
     */
    protected Context context;

    /**
     * Creates default AMQP client options.
     */
    @BeforeAll
    public static void init() {

        defaultOptions = new MqttClientOptions();
        defaultOptions.setSsl(true)
            .setTrustOptions(new PemTrustOptions().addCertPath(IntegrationTestSupport.TRUST_STORE_PATH))
            .setHostnameVerificationAlgorithm("")
            .setEnabledSecureTransportProtocols(Set.of(IntegrationTestSupport.TLS_VERSION_1_2));
    }

    /**
     * Create integration test helper.
     *
     * @param testInfo The JUnit test info.
     * @param ctx The vert.x context.
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
     * @param ctx The vert.x context.
     */
    @AfterEach
    public void closeConnectionToAmqpMessagingNetwork(final VertxTestContext ctx) {

        helper.disconnect().onComplete(r -> ctx.completeNow());
    }

    /**
     * Closes the connection to the MQTT adapter and deletes device registry entries created during the test.
     *
     * @param ctx The vert.x context.
     */
    @AfterEach
    public void closeMqttAdapterConnectionAndCleanupDeviceRegistry(final VertxTestContext ctx) {

        final Promise<Void> disconnectHandler = Promise.promise();
        if (context == null || mqttClient == null || !mqttClient.isConnected()) {
            disconnectHandler.complete();
        } else {
            context.runOnContext(go -> {
                mqttClient.disconnect(disconnectHandler);
            });
        }
        disconnectHandler.future().onComplete(closeAttempt -> {
            LOGGER.info("connection to MQTT adapter closed");
            context = null;
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
    protected final Future<MqttConnAckMessage> connectToAdapter(
            final String username,
            final String password) {
        return connectToAdapter(IntegrationTestSupport.TLS_VERSION_1_2, username, password);
    }

    /**
     * Opens a connection to the MQTT adapter using given credentials.
     *
     * @param tlsVersion The TLS protocol version to use for connecting to the adapter.
     * @param username The username to use for authentication.
     * @param password The password to use for authentication.
     * @return A future that will be completed with the CONNACK packet received
     *         from the adapter or failed with a {@link io.vertx.mqtt.MqttConnectionException}
     *         if the connection could not be established.
     */
    protected final Future<MqttConnAckMessage> connectToAdapter(
            final String tlsVersion,
            final String username,
            final String password) {

        final MqttClientOptions options = new MqttClientOptions(defaultOptions)
                .setUsername(username)
                .setPassword(password);
        options.setEnabledSecureTransportProtocols(Set.of(tlsVersion));
        return connectToAdapter(options, IntegrationTestSupport.MQTT_HOST);
    }

    /**
     * Opens a connection to the MQTT adapter using an X.509 client certificate.
     *
     * @param cert The client certificate to use for authentication.
     * @return A future that will be completed with the CONNACK packet received
     *         from the adapter or failed with a {@link io.vertx.mqtt.MqttConnectionException}
     *         if the connection could not be established.
     * @throws NullPointerException if client certificate is {@code null}.
     */
    protected final Future<MqttConnAckMessage> connectToAdapter(final SelfSignedCertificate cert) {
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
     */
    protected final Future<MqttConnAckMessage> connectToAdapter(
            final SelfSignedCertificate cert,
            final String hostname) {

        Objects.requireNonNull(cert);
        Objects.requireNonNull(hostname);

        final MqttClientOptions options = new MqttClientOptions(defaultOptions);
        options.setKeyCertOptions(cert.keyCertOptions());
        return connectToAdapter(options, hostname);
    }

    /**
     * Opens a connection to the MQTT adapter using given options.
     *
     * @param options The options to use for connecting to the adapter.
     * @param hostname The name of the host to connect to.
     * @return A future that will be completed with the CONNACK packet received
     *         from the adapter or failed with a {@link io.vertx.mqtt.MqttConnectionException}
     *         if the connection could not be established.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    protected final Future<MqttConnAckMessage> connectToAdapter(
            final MqttClientOptions options,
            final String hostname) {

        Objects.requireNonNull(options);
        Objects.requireNonNull(hostname);

        final Promise<MqttConnAckMessage> result = Promise.promise();
        vertx.runOnContext(connect -> {
            mqttClient = MqttClient.create(vertx, options);
            mqttClient.connect(IntegrationTestSupport.MQTTS_PORT, hostname, result);
        });
        return result.future().map(conAck -> {
            LOGGER.info(
                    "MQTTS connection to adapter [host: {}, port: {}] established",
                    hostname, IntegrationTestSupport.MQTTS_PORT);
            this.context = Vertx.currentContext();
            return conAck;
        });
    }
}
