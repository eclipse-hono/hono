/*******************************************************************************
 * Copyright (c) 2016, 2020 Contributors to the Eclipse Foundation
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
import java.util.function.Supplier;

import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
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
import io.vertx.junit5.Checkpoint;
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
     * Closes the AMQP 1.0 Messaging Network client.
     *
     * @param ctx The vert.x context.
     */
    @AfterEach
    public void postTest(final VertxTestContext ctx) {

        final Checkpoint done = ctx.checkpoint(2);
        final Promise<Void> disconnectHandler = Promise.promise();
        if (context == null) {
            disconnectHandler.complete();
        } else {
            context.runOnContext(go -> {
                mqttClient.disconnect(disconnectHandler);
            });
        }
        helper.deleteObjects(ctx);
        disconnectHandler.future().onComplete(closeAttempt -> {
            LOGGER.info("connection to MQTT adapter closed");
            context = null;
            done.flag();
        });
        helper.disconnect().onComplete(r -> done.flag());
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
                        .orElse(Future.succeededFuture()))
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
     *         from the adapter or failed with a {@link MqttConnectionException}
     *         if the connection could not be established.
     */
    protected final Future<MqttConnAckMessage> connectToAdapter(
            final String username,
            final String password) {

        final Promise<MqttConnAckMessage> result = Promise.promise();
        vertx.runOnContext(connect -> {
            final MqttClientOptions options = new MqttClientOptions()
                    .setUsername(username)
                    .setPassword(password);
            mqttClient = MqttClient.create(vertx, options);
            mqttClient.connect(IntegrationTestSupport.MQTT_PORT, IntegrationTestSupport.MQTT_HOST, result);
        });
        return result.future().map(conAck -> {
            LOGGER.info(
                    "MQTT connection to adapter [host: {}, port: {}] established",
                    IntegrationTestSupport.MQTT_HOST, IntegrationTestSupport.MQTT_PORT);
            this.context = Vertx.currentContext();
            return conAck;
        });
    }

    /**
     * Opens a connection to the MQTT adapter using an X.509 client certificate.
     *
     * @param cert The client certificate to use for authentication.
     * @return A future that will be completed with the CONNACK packet received
     *         from the adapter or failed with a {@link MqttConnectionException}
     *         if the connection could not be established.
     */
    protected final Future<MqttConnAckMessage> connectToAdapter(
            final SelfSignedCertificate cert) {

        final Promise<MqttConnAckMessage> result = Promise.promise();
        vertx.runOnContext(connect -> {
            final MqttClientOptions options = new MqttClientOptions()
                    .setTrustOptions(new PemTrustOptions().addCertPath(IntegrationTestSupport.TRUST_STORE_PATH))
                    .setKeyCertOptions(cert.keyCertOptions())
                    .setSsl(true);
            options.setHostnameVerificationAlgorithm("");
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
