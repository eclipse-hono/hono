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

package org.eclipse.hono.tests.mqtt;

import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.service.management.tenant.Tenant;
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
            .setEnabledSecureTransportProtocols(Set.of("TLSv1.2"));
    }

    /**
     * Sets up the fixture.
     *
     * @param testInfo The JUnit test info.
     * @param ctx The vert.x context.
     */
    @BeforeEach
    public void setUp(final TestInfo testInfo, final VertxTestContext ctx) {
        LOGGER.info("running {}", testInfo.getDisplayName());
        helper = new IntegrationTestSupport(vertx);
        helper.init().onComplete(ctx.completing());
    }

    /**
     * Deletes all temporary objects from the Device Registry which
     * have been created during the last test execution.
     *
     * @param ctx The vert.x context.
     */
    @AfterEach
    public void deleteObjects(final VertxTestContext ctx) {
        helper.deleteObjects(ctx);
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
     * Closes the connection to the MQTT adapter.
     *
     * @param ctx The vert.x context.
     */
    @AfterEach
    public void closeConnectionToMqttAdapter(final VertxTestContext ctx) {

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
            ctx.completeNow();
        });
    }

    /**
     * Registers a device and opens a connection to the MQTT adapter using the device's credentials.
     *
     * @param tenantId The id of the tenant that the device belongs to.
     * @param tenant The tenant that the device belongs to.
     * @param deviceId The identifier of the device.
     * @param password The password to use for authentication.
     * @param consumerFactory The factory for creating the consumer of messages published by the device or {@code null}
     *            if no consumer should be created.
     * @return A future that will be completed with the CONNACK packet received from the adapter or failed if the
     *         connection could not be established.
     */
    protected final Future<MqttConnAckMessage> connectToAdapter(
            final String tenantId,
            final Tenant tenant,
            final String deviceId,
            final String password,
            final Supplier<Future<MessageConsumer>> consumerFactory) {

        return helper.registry
                .addDeviceForTenant(tenantId, tenant, deviceId, password)
                .compose(ok -> Optional.ofNullable(consumerFactory)
                        .map(factory -> factory.get())
                        .orElseGet(() -> Future.succeededFuture()))
                .compose(ok -> connectToAdapter(IntegrationTestSupport.getUsername(deviceId, tenantId), password))
                .recover(t -> {
                    LOGGER.info("failed to establish connection to MQTT adapter [host: {}, port: {}]",
                            IntegrationTestSupport.MQTT_HOST, IntegrationTestSupport.MQTT_PORT, t);
                    return Future.failedFuture(t);
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
        return connectToAdapter("TLSv1.2", username, password);
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

        final Promise<MqttConnAckMessage> result = Promise.promise();
        vertx.runOnContext(connect -> {
            final MqttClientOptions options = new MqttClientOptions(defaultOptions)
                    .setUsername(username)
                    .setPassword(password);
            options.setEnabledSecureTransportProtocols(Set.of(tlsVersion));
            mqttClient = MqttClient.create(vertx, options);
            mqttClient.connect(IntegrationTestSupport.MQTTS_PORT, IntegrationTestSupport.MQTT_HOST, result);
        });
        return result.future().map(conAck -> {
            LOGGER.info(
                    "MQTTS connection to adapter [host: {}, port: {}] established",
                    IntegrationTestSupport.MQTT_HOST, IntegrationTestSupport.MQTTS_PORT);
            this.context = Vertx.currentContext();
            return conAck;
        });
    }

    /**
     * Opens a connection to the MQTT adapter using an X.509 client certificate.
     *
     * @param cert The client certificate to use for authentication.
     * @return A future that will be completed with the CONNACK packet received
     *         from the adapter or failed with a {@link io.vertx.mqtt.MqttConnectionException}
     *         if the connection could not be established.
     */
    protected final Future<MqttConnAckMessage> connectToAdapter(
            final SelfSignedCertificate cert) {

        final Promise<MqttConnAckMessage> result = Promise.promise();
        vertx.runOnContext(connect -> {
            final MqttClientOptions options = new MqttClientOptions(defaultOptions);
            options.setKeyCertOptions(cert.keyCertOptions());

            mqttClient = MqttClient.create(vertx, options);
            mqttClient.connect(IntegrationTestSupport.MQTTS_PORT, IntegrationTestSupport.MQTT_HOST, result);
        });
        return result.future().map(conAck -> {
            LOGGER.info(
                    "MQTTS connection to adapter [host: {}, port: {}] established",
                    IntegrationTestSupport.MQTT_HOST, IntegrationTestSupport.MQTTS_PORT);
            this.context = Vertx.currentContext();
            return conAck;
        });
    }
}
