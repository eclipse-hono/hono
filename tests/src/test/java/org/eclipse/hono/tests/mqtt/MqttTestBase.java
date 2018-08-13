/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
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

import java.util.function.Supplier;

import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.util.TenantObject;
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
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.mqtt.MqttClient;
import io.vertx.mqtt.MqttClientOptions;
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
     * The maximum number of milliseconds a test case may run before it
     * is considered to have failed.
     */
    protected static final int TEST_TIMEOUT = 2000; // milliseconds
    /**
     * A helper accessing the AMQP 1.0 Messaging Network and
     * for managing tenants/devices/credentials.
     */
    protected static IntegrationTestSupport helper;

    /**
     * A client for publishing messages to the MQTT protocol adapter.
     */
    protected MqttClient mqttClient;
    /**
     * The vert.x {@code Context} that the MQTT client runs on.
     */
    protected Context context;

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
                mqttClient.disconnect(disconnectHandler.completer());
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
     * Registers a device and opens a connection to the MQTT adapter using
     * the device's credentials.
     * 
     * @param tenant The tenant that the device belongs to.
     * @param deviceId The identifier of the device.
     * @param password The password to use for authentication.
     * @param consumerFactory The factory for creating the consumer of messages
     *                   published by the device.
     * @return A future that will be completed with the CONNACK packet received
     *         from the adapter or failed if the connection could not be established. 
     */
    protected final Future<MqttConnAckMessage> connectToAdapter(
            final TenantObject tenant,
            final String deviceId,
            final String password,
            final Supplier<Future<MessageConsumer>> consumerFactory) {

        return helper.registry
        .addDeviceForTenant(tenant, deviceId, password)
        .compose(ok -> consumerFactory.get())
        .compose(ok -> {
            final Future<MqttConnAckMessage> result = Future.future();
            VERTX.runOnContext(connect -> {
                final MqttClientOptions options = new MqttClientOptions()
                        .setUsername(IntegrationTestSupport.getUsername(deviceId, tenant.getTenantId()))
                        .setPassword(password);
                mqttClient = MqttClient.create(VERTX, options);
                mqttClient.connect(IntegrationTestSupport.MQTT_PORT, IntegrationTestSupport.MQTT_HOST, result.completer());
            });
            return result;
        }).map(conAck -> {
            this.context = Vertx.currentContext();
            return conAck;
        });

    }
}
