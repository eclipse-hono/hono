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

package org.eclipse.hono.tests.mqtt;

import java.util.Optional;
import java.util.function.Supplier;

import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.rules.TestName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.net.PemTrustOptions;
import io.vertx.core.net.SelfSignedCertificate;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.mqtt.MqttClient;
import io.vertx.mqtt.MqttClientOptions;
import io.vertx.mqtt.MqttConnectionException;
import io.vertx.mqtt.messages.MqttConnAckMessage;

/**
 * Base class for MQTT adapter integration tests.
 *
 */
public abstract class MqttTestBase {

    /**
     * The vert.xt instance to run on.
     */
    protected static final Vertx VERTX = Vertx.vertx();
    /**
     * A helper accessing the AMQP 1.0 Messaging Network and
     * for managing tenants/devices/credentials.
     */
    protected static IntegrationTestSupport helper;

    /**
     * Provide test name to unit tests.
     */
    @Rule
    public final TestName testName = new TestName();

    /**
     * A logger to be used by subclasses.
     */
    protected final Logger LOGGER = LoggerFactory.getLogger(getClass());

    /**
     * A client for publishing messages to the MQTT protocol adapter.
     */
    protected MqttClient mqttClient;
    /**
     * The vert.x {@code Context} that the MQTT client runs on.
     */
    protected Context context;

    /**
     * Sets up the helper.
     * 
     * @param ctx The vert.x test context.
     */
    @BeforeClass
    public static void init(final TestContext ctx) {

        helper = new IntegrationTestSupport(VERTX);
        helper.init(ctx);

    }

    /**
     * Sets up the fixture.
     */
    @Before
    public void setUp() {
        LOGGER.info("running {}", testName.getMethodName());
    }

    /**
     * Deletes all temporary objects from the Device Registry which
     * have been created during the last test execution.
     * 
     * @param ctx The vert.x context.
     */
    @After
    public void postTest(final TestContext ctx) {

        final Async clientDisconnect = ctx.async();
        final Future<Void> disconnectHandler = Future.future();
        if (context == null) {
            disconnectHandler.complete();
        } else {
            context.runOnContext(go -> {
                mqttClient.disconnect(disconnectHandler);
            });
        }
        disconnectHandler.setHandler(tidyUp -> {
            LOGGER.info("connection to MQTT adapter closed");
            context = null;
            clientDisconnect.complete();
        });
        clientDisconnect.await(2000);
        helper.deleteObjects(ctx);
    }

    /**
     * Closes the AMQP 1.0 Messaging Network client.
     * 
     * @param ctx The vert.x test context.
     */
    @AfterClass
    public static void disconnect(final TestContext ctx) {

        helper.disconnect(ctx);
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
                    LOGGER.debug("failed to establish connection to MQTT adapter [host: {}, port: {}]",
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

        final Future<MqttConnAckMessage> result = Future.future();
        VERTX.runOnContext(connect -> {
            final MqttClientOptions options = new MqttClientOptions()
                    .setUsername(username)
                    .setPassword(password);
            mqttClient = MqttClient.create(VERTX, options);
            mqttClient.connect(IntegrationTestSupport.MQTT_PORT, IntegrationTestSupport.MQTT_HOST, result);
        });
        return result.map(conAck -> {
            LOGGER.debug(
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

        final Future<MqttConnAckMessage> result = Future.future();
        VERTX.runOnContext(connect -> {
            final MqttClientOptions options = new MqttClientOptions()
                    .setTrustOptions(new PemTrustOptions().addCertPath(IntegrationTestSupport.TRUST_STORE_PATH))
                    .setKeyCertOptions(cert.keyCertOptions())
                    .setSsl(true);
            options.setHostnameVerificationAlgorithm("");
            mqttClient = MqttClient.create(VERTX, options);
            mqttClient.connect(IntegrationTestSupport.MQTTS_PORT, IntegrationTestSupport.MQTT_HOST, result);
        });
        return result.map(conAck -> {
            LOGGER.debug(
                    "MQTTS connection to adapter [host: {}, port: {}] established",
                    IntegrationTestSupport.MQTT_HOST, IntegrationTestSupport.MQTTS_PORT);
            this.context = Vertx.currentContext();
            return conAck;
        });
    }
}
